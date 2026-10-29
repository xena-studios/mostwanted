package co.xenastudios.mostwanted.backend.api;

import co.xenastudios.mostwanted.backend.BackendConfig;
import co.xenastudios.mostwanted.backend.accounts.Accounts;
import co.xenastudios.mostwanted.backend.accounts.DiscordOAuth;
import co.xenastudios.mostwanted.backend.accounts.LinkedRole;
import co.xenastudios.mostwanted.backend.accounts.Sessions;
import co.xenastudios.mostwanted.common.LoginCode;
import io.javalin.config.JavalinConfig;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Signing in, linking and the account itself (docs/08-website.md#accounts-and-the-api).
 * The session is a cookie scripts can't read. Anything that changes something must
 * come from the website itself (its Origin header), so other sites can't do it for you.
 */
public final class AccountRoutes {

    static final String SESSION = "mw_session";
    static final String STATE = "mw_oauth_state";
    private static final Logger LOG = LoggerFactory.getLogger(AccountRoutes.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final BackendConfig config;
    private final Accounts accounts;
    private final Sessions sessions;
    private final DiscordOAuth discord;
    private final LinkedRole linkedRole;
    /** Tighter than the general limit: codes are guessable at scale. */
    private final RateLimits codeAttempts = new RateLimits(10);

    /** {@code discord} is null when Discord sign-in isn't set up (no client secret). */
    AccountRoutes(BackendConfig config, Accounts accounts, Sessions sessions, DiscordOAuth discord, LinkedRole linkedRole) {
        this.config = config;
        this.accounts = accounts;
        this.sessions = sessions;
        this.discord = discord;
        this.linkedRole = linkedRole;
    }

    private SupportRoutes support;

    /** Tickets, applications and notifications: registered with these, and shown on /v1/me. */
    void support(SupportRoutes support) {
        this.support = support;
    }

    /** Forgets code attempts from more than a minute ago (with the general limit's sweep). */
    void sweep() {
        codeAttempts.sweep();
    }

    void register(JavalinConfig javalin) {
        javalin.routes.before("/v1/*", ctx -> {
            String method = ctx.method().name();
            String origin = ctx.header("Origin");
            if (!method.equals("GET") && !method.equals("HEAD") && !method.equals("OPTIONS")
                    && (origin == null || !config.origins().contains(origin))) {
                throw new Api.Failure(HttpStatus.FORBIDDEN, "Changes must come from the website.");
            }
        });

        javalin.routes.get("/v1/me", ctx -> {
            long id = signedIn(ctx).orElseThrow(() -> new Api.Failure(HttpStatus.UNAUTHORIZED, "You're not signed in."));
            Map<String, Object> view = view(accounts.get(id)
                    .orElseThrow(() -> new Api.Failure(HttpStatus.UNAUTHORIZED, "You're not signed in.")));
            if (support != null) {
                // Their staff rank (for the panel link) and unread notifications.
                view.put("staff", support.level(id));
                view.put("unread", support.unread(id));
            }
            ctx.json(view);
        });

        javalin.routes.post("/v1/auth/code", ctx -> {
            String code = code(ctx);
            long id = accounts.signInWithCode(code)
                    .orElseThrow(() -> new Api.Failure(HttpStatus.BAD_REQUEST, "That code is wrong or has run out. Get a new one with /website in game."));
            startSession(ctx, id);
            ctx.json(view(accounts.get(id).orElseThrow()));
        });

        javalin.routes.post("/v1/me/minecraft", ctx -> {
            long id = signedIn(ctx).orElseThrow(() -> new Api.Failure(HttpStatus.UNAUTHORIZED, "You're not signed in."));
            String code = code(ctx);
            linked(ctx, id, accounts.linkMinecraft(id, code));
        });

        javalin.routes.get("/v1/auth/discord", ctx -> {
            if (discord == null) {
                throw new Api.Failure(HttpStatus.SERVICE_UNAVAILABLE, "Discord sign-in isn't set up yet.");
            }
            String state = random(24);
            ctx.res().addHeader("Set-Cookie", cookie(STATE, state, 600, "/v1/auth/discord"));
            ctx.redirect(discord.authorizeUrl(state));
        });

        javalin.routes.get("/v1/auth/discord/callback", ctx -> {
            String state = ctx.queryParam("state");
            String code = ctx.queryParam("code");
            ctx.res().addHeader("Set-Cookie", cookie(STATE, "", 0, "/v1/auth/discord"));
            if (discord == null || state == null || code == null || !state.equals(ctx.cookie(STATE))) {
                // Cancelled on Discord, an old tab, or not this browser: start again.
                ctx.redirect(config.site() + "/login?error=discord");
                return;
            }
            DiscordOAuth.User user;
            try {
                user = discord.user(code);
            } catch (Exception e) {
                LOG.warn("Discord sign-in failed: {}", e.getMessage());
                ctx.redirect(config.site() + "/login?error=discord");
                return;
            }
            Optional<Long> current = signedIn(ctx);
            if (current.isPresent()) {
                Accounts.Link result = accounts.linkDiscord(current.get(), user.id(), user.name());
                if (result == Accounts.Link.LINKED) {
                    syncRole(current.get());
                }
                // A short code in the URL; the site words it.
                ctx.redirect(config.site() + "/account" + switch (result) {
                    case LINKED, ALREADY_YOURS -> "";
                    case ALREADY_LINKED -> "?error=already-linked";
                    default -> "?error=taken";
                });
                return;
            }
            long id = accounts.signInWithDiscord(user.id(), user.name());
            startSession(ctx, id);
            syncRole(id);
            ctx.redirect(config.site() + "/account");
        });

        javalin.routes.post("/v1/auth/logout", ctx -> {
            sessions.delete(ctx.cookie(SESSION));
            ctx.res().addHeader("Set-Cookie", cookie(SESSION, "", 0, "/"));
            ctx.status(HttpStatus.NO_CONTENT);
        });

        javalin.routes.delete("/v1/me", ctx -> {
            long id = signedIn(ctx).orElseThrow(() -> new Api.Failure(HttpStatus.UNAUTHORIZED, "You're not signed in."));
            accounts.delete(id).filter(Accounts.Account::linked)
                    .ifPresent(account -> linkedRole.update(account.discordId(), false));
            ctx.res().addHeader("Set-Cookie", cookie(SESSION, "", 0, "/"));
            ctx.status(HttpStatus.NO_CONTENT);
        });

        if (support != null) {
            support.register(javalin);
        }
        javalin.routes.after("/v1/me*", ctx -> ctx.header("Cache-Control", "no-store"));
        javalin.routes.after("/v1/auth/*", ctx -> ctx.header("Cache-Control", "no-store"));
    }

    Optional<Long> signedIn(Context ctx) {
        return sessions.account(ctx.cookie(SESSION));
    }

    /** The code from the JSON body, checked for shape and rate-limited per visitor. */
    private String code(Context ctx) {
        if (!codeAttempts.allow(Api.client(ctx, config.behindCloudflare()))) {
            ctx.header("Retry-After", "60");
            throw new Api.Failure(HttpStatus.TOO_MANY_REQUESTS, "Too many tries. Try again in a minute.");
        }
        Map<?, ?> body;
        try {
            body = ctx.bodyAsClass(Map.class);
        } catch (RuntimeException e) {
            throw new Api.Failure(HttpStatus.BAD_REQUEST, "Send {\"code\": \"...\"}.");
        }
        if (body == null) {
            throw new Api.Failure(HttpStatus.BAD_REQUEST, "Send {\"code\": \"...\"}.");
        }
        String code = LoginCode.normalize(body.get("code") instanceof String s ? s : null);
        if (code == null) {
            throw new Api.Failure(HttpStatus.BAD_REQUEST, "Codes are 6 letters and numbers, from /website in game.");
        }
        return code;
    }

    private void linked(Context ctx, long id, Accounts.Link result) {
        switch (result) {
            case LINKED -> {
                syncRole(id);
                ctx.json(view(accounts.get(id).orElseThrow()));
            }
            case ALREADY_YOURS -> ctx.json(view(accounts.get(id).orElseThrow()));
            case BAD_CODE -> throw new Api.Failure(HttpStatus.BAD_REQUEST, "That code is wrong or has run out. Get a new one with /website in game.");
            default -> throw new Api.Failure(HttpStatus.CONFLICT, linkError(result));
        }
    }

    private static String linkError(Accounts.Link result) {
        return switch (result) {
            case ALREADY_LINKED -> "This account already has a Minecraft account linked.";
            case TAKEN -> "That Minecraft account is linked to someone else's Discord account.";
            default -> "Couldn't link your accounts. Try again.";
        };
    }

    private void syncRole(long id) {
        accounts.get(id).filter(Accounts.Account::linked).ifPresent(a -> linkedRole.update(a.discordId(), true));
    }

    private void startSession(Context ctx, long id) {
        ctx.res().addHeader("Set-Cookie", cookie(SESSION, sessions.create(id), (int) Sessions.LIFETIME.toSeconds(), "/"));
    }

    /** What /v1/me shows: the account and what's linked. */
    static Map<String, Object> view(Accounts.Account account) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", account.id());
        view.put("discord", account.discordId() == null ? null
                : Map.of("id", account.discordId(), "name", account.discordName() == null ? "" : account.discordName()));
        if (account.minecraft() == null) {
            view.put("minecraft", null);
        } else {
            Map<String, Object> minecraft = new LinkedHashMap<>();
            minecraft.put("uuid", account.minecraft());
            minecraft.put("name", account.minecraftName());
            view.put("minecraft", minecraft);
        }
        view.put("linked", account.linked());
        return view;
    }

    /** A cookie scripts can't read, sent only over HTTPS in production, and not on cross-site requests. */
    String cookie(String name, String value, int maxAge, String path) {
        StringBuilder cookie = new StringBuilder(name).append('=').append(value)
                .append("; Path=").append(path)
                .append("; Max-Age=").append(maxAge)
                .append("; HttpOnly; SameSite=Lax");
        if (config.cookieSecure()) {
            cookie.append("; Secure");
        }
        if (config.cookieDomain() != null) {
            cookie.append("; Domain=").append(config.cookieDomain());
        }
        return cookie.toString();
    }

    private static String random(int bytes) {
        byte[] data = new byte[bytes];
        RANDOM.nextBytes(data);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }
}
