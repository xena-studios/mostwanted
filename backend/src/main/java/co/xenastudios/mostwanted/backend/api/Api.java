package co.xenastudios.mostwanted.backend.api;

import co.xenastudios.mostwanted.common.Board;
import co.xenastudios.mostwanted.backend.BackendConfig;
import co.xenastudios.mostwanted.backend.accounts.Accounts;
import co.xenastudios.mostwanted.backend.accounts.DiscordOAuth;
import co.xenastudios.mostwanted.backend.accounts.LinkedRole;
import co.xenastudios.mostwanted.backend.accounts.Sessions;
import co.xenastudios.mostwanted.backend.stats.Stats;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.json.JavalinJackson3;
import io.javalin.validation.ValidationException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

/**
 * api.mostwantedmc.com: public stats for the website, and /health. JSON only. Stats
 * responses are cached at Cloudflare for a couple of minutes, so most visits never
 * reach the VPS (docs/08-website.md#player-stats). On the VPS it serves HTTPS and
 * only answers Cloudflare ({@link OriginTls}); locally and in tests, plain HTTP.
 */
public final class Api {

    private static final Logger LOG = LoggerFactory.getLogger(Api.class);
    /** Java names, plus the characters Bedrock names can have through Minehut. */
    static final Pattern NAME = Pattern.compile("[A-Za-z0-9_.* -]{1,32}");
    private static final String CACHE_STATS = "public, max-age=60, s-maxage=120";
    static final int MAX_LIMIT = 50;

    private final Javalin app;

    private Api(Javalin app) {
        this.app = app;
    }

    /**
     * Builds the account routes; {@code discord} null when Discord sign-in isn't set up.
     */
    public static AccountRoutes accounts(BackendConfig config, Accounts accounts, Sessions sessions,
            DiscordOAuth discord, LinkedRole linkedRole) {
        return new AccountRoutes(config, accounts, sessions, discord, linkedRole);
    }

    /** Reads and checks the TLS files, if any; throws IllegalArgumentException with the reason. */
    public static void checkTls(BackendConfig config) {
        if (config.tls() != null) {
            OriginTls.sslContext(config.tls());
        }
    }

    /** {@code discordUp} is for /health: whether the bot is connected, or null if it's switched off. */
    public static Api start(BackendConfig config, Stats stats, AccountRoutes accounts, BooleanSupplier discordUp) {
        RateLimits limits = new RateLimits(config.requestsPerMinute());
        // Read now: a missing or wrong certificate stops the start with the reason.
        SslContextFactory.Server ssl = config.tls() == null ? null : OriginTls.sslContext(config.tls());
        Javalin app = Javalin.create(javalin -> {
            javalin.concurrency.useVirtualThreads = true;
            if (ssl != null) {
                // HTTPS only: with a connector of our own, Javalin doesn't add its plain HTTP one.
                javalin.jetty.addConnector((server, http) ->
                        OriginTls.connector(server, http, ssl, config.host(), config.port()));
            }
            javalin.startup.showJavalinBanner = false;
            javalin.jsonMapper(new JavalinJackson3(JsonMapper.builder().build(), false));
            javalin.bundledPlugins.enableCors(cors -> cors.addRule(rule -> {
                for (String origin : config.origins()) {
                    rule.allowHost(origin);
                }
                // The session cookie goes with the site's requests.
                rule.allowCredentials = true;
            }));

            javalin.routes.before(ctx -> {
                if (!limits.allow(client(ctx, config.behindCloudflare()))) {
                    ctx.header("Retry-After", "60");
                    throw new Failure(HttpStatus.TOO_MANY_REQUESTS, "too many requests, try again in a minute");
                }
            });

            javalin.routes.get("/health", ctx -> {
                boolean database = stats.databaseUp();
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("status", database ? "ok" : "degraded");
                body.put("database", database ? "up" : "down");
                body.put("discord", discordUp == null ? "off" : discordUp.getAsBoolean() ? "up" : "down");
                ctx.header("Cache-Control", "no-store").status(database ? 200 : 503).json(body);
            });

            javalin.routes.get("/v1/players/{name}", ctx -> {
                String name = ctx.pathParam("name");
                if (!NAME.matcher(name).matches()) {
                    throw new Failure(HttpStatus.BAD_REQUEST, "that isn't a Minecraft name");
                }
                Stats.Profile profile = stats.player(name)
                        .orElseThrow(() -> new Failure(HttpStatus.NOT_FOUND, "no player called " + name));
                ctx.header("Cache-Control", CACHE_STATS).json(profile);
            });

            javalin.routes.get("/v1/leaderboards/{board}", ctx -> {
                Board board = Board.byId(ctx.pathParam("board"));
                if (board == null) {
                    throw new Failure(HttpStatus.NOT_FOUND, "no such board");
                }
                Stats.Period period = Stats.Period.byKey(ctx.queryParamAsClass("period", String.class)
                        .getOrDefault("all"));
                if (period == null) {
                    throw new Failure(HttpStatus.BAD_REQUEST, "period is all, month or week");
                }
                int limit = ctx.queryParamAsClass("limit", Integer.class)
                        .check(n -> n >= 1 && n <= MAX_LIMIT, "limit is 1 to " + MAX_LIMIT)
                        .getOrDefault(10);
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("board", board.id());
                // Most wanted is the bounty right now, whatever the period.
                body.put("period", board.stat() == null ? Stats.Period.ALL.key() : period.key());
                body.put("entries", stats.board(board, period, limit));
                ctx.header("Cache-Control", CACHE_STATS).json(body);
            });

            accounts.register(javalin);

            javalin.routes.exception(Failure.class, (e, ctx) ->
                    ctx.status(e.status).json(Map.of("error", e.getMessage())));
            javalin.routes.exception(ValidationException.class, (e, ctx) -> {
                String message = e.getErrors().values().stream().flatMap(List::stream)
                        .map(error -> error.getMessage()).findFirst().orElse("bad query parameters");
                ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", message));
            });
            javalin.routes.exception(Exception.class, (e, ctx) -> {
                LOG.error("{} {} failed", ctx.method(), ctx.path(), e);
                ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).json(Map.of("error", "something went wrong"));
            });
            // A path with no route: JSON like every other error (a missing player is already JSON).
            javalin.routes.error(404, ctx -> {
                String type = ctx.res().getContentType();
                if (type == null || !type.contains("json")) {
                    ctx.json(Map.of("error", "not found"));
                }
            });
        });
        app.start(config.host(), config.port());
        Thread.ofVirtual().name("rate-limit-sweep").start(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(Duration.ofMinutes(5));
                } catch (InterruptedException e) {
                    return;
                }
                limits.sweep();
            }
        });
        return new Api(app);
    }

    public int port() {
        return app.port();
    }

    public void stop() {
        app.stop();
    }

    /**
     * Who's asking. Behind Cloudflare every request comes from Cloudflare's IPs (the
     * firewall allows nothing else), so the visitor is in CF-Connecting-IP.
     */
    static String client(Context ctx, boolean behindCloudflare) {
        String forwarded = behindCloudflare ? ctx.header("CF-Connecting-IP") : null;
        return forwarded != null && !forwarded.isBlank() ? forwarded : ctx.ip();
    }

    /** An expected failure: its message goes to the client as is. */
    static final class Failure extends RuntimeException {

        final HttpStatus status;

        Failure(HttpStatus status, String message) {
            super(message, null, false, false);
            this.status = status;
        }
    }
}
