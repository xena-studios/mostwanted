package co.xenastudios.mostwanted.backend.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.xenastudios.mostwanted.backend.BackendConfig;
import co.xenastudios.mostwanted.backend.TestDatabase;
import co.xenastudios.mostwanted.backend.accounts.Accounts;
import co.xenastudios.mostwanted.backend.accounts.DiscordOAuth;
import co.xenastudios.mostwanted.backend.accounts.Sessions;
import co.xenastudios.mostwanted.backend.stats.Stats;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Signing in and linking over real HTTP, with a stand-in for Discord's API. Cookies are
 * handled by hand, like a browser would. Skipped without Docker; CI has it.
 */
@Testcontainers(disabledWithoutDocker = true)
class AccountRoutesTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

    static final String SITE = "https://mostwantedmc.com";
    static TestDatabase db;
    static Api api;
    static HttpServer fakeDiscord;
    static final Map<String, Boolean> roles = new ConcurrentHashMap<>();
    static final HttpClient HTTP = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    @BeforeAll
    static void start() throws Exception {
        db = new TestDatabase(POSTGRES);
        // Discord's token and user endpoints: any code is user 9001.
        fakeDiscord = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        fakeDiscord.createContext("/oauth2/token", exchange -> reply(exchange, "{\"access_token\":\"t\"}"));
        fakeDiscord.createContext("/users/@me", exchange -> reply(exchange,
                "{\"id\":\"9001\",\"username\":\"discorduser\",\"global_name\":\"Discord User\"}"));
        fakeDiscord.start();
        String discordApi = "http://127.0.0.1:" + fakeDiscord.getAddress().getPort();

        BackendConfig config = new BackendConfig(null, "127.0.0.1", 0, List.of(SITE), false, 1000,
                ZoneId.of("America/New_York"), SITE, null, "https://api.mostwantedmc.com", "1", "secret", null, false, null);
        DiscordOAuth discord = new DiscordOAuth("1", "secret", "https://api.mostwantedmc.com/v1/auth/discord/callback",
                discordApi);
        api = Api.start(config, new Stats(db.database.sql(), config.zone(), Clock.systemUTC()),
                Api.accounts(config, new Accounts(db.database.sql()), new Sessions(db.database.sql(), Clock.systemUTC()),
                        discord, roles::put), null);
    }

    static void reply(com.sun.net.httpserver.HttpExchange exchange, String body) throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @AfterAll
    static void stop() {
        api.stop();
        fakeDiscord.stop(0);
        db.database.close();
    }

    static HttpResponse<String> send(String method, String path, String body, String cookie, boolean fromSite)
            throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + api.port() + path))
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body))
                .header("Content-Type", "application/json");
        if (cookie != null) {
            request.header("Cookie", cookie);
        }
        if (fromSite) {
            request.header("Origin", SITE);
        }
        return HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    /** "name=value" from a response's Set-Cookie for that name. */
    static String cookie(HttpResponse<?> response, String name) {
        return response.headers().allValues("Set-Cookie").stream()
                .filter(c -> c.startsWith(name + "="))
                .map(c -> c.substring(0, c.indexOf(';')))
                .findFirst().orElseThrow(() -> new AssertionError("no " + name + " cookie"));
    }

    @Test
    void signInWithACodeThenSeeAndDeleteTheAccount() throws Exception {
        UUID uuid = db.player("WebUser", "inmate", 0, false);
        db.code(uuid, "EEEEE2", Duration.ofMinutes(10));

        HttpResponse<String> signIn = send("POST", "/v1/auth/code", "{\"code\":\"eeeee2\"}", null, true);
        assertEquals(200, signIn.statusCode(), signIn.body());
        String session = cookie(signIn, AccountRoutes.SESSION);
        String setCookie = signIn.headers().firstValue("Set-Cookie").orElseThrow();
        assertTrue(setCookie.contains("HttpOnly") && setCookie.contains("SameSite=Lax"), setCookie);

        HttpResponse<String> me = send("GET", "/v1/me", null, session, false);
        assertTrue(me.body().contains("\"name\":\"WebUser\""), me.body());
        assertTrue(me.body().contains("\"discord\":null"), me.body());
        assertEquals("no-store", me.headers().firstValue("Cache-Control").orElseThrow());

        assertEquals(204, send("DELETE", "/v1/me", null, session, true).statusCode());
        assertEquals(401, send("GET", "/v1/me", null, session, false).statusCode());
    }

    @Test
    void changesFromOtherSitesAreRefused() throws Exception {
        assertEquals(403, send("POST", "/v1/auth/code", "{\"code\":\"EEEEE3\"}", null, false).statusCode());
        assertEquals(400, send("POST", "/v1/auth/code", "{\"code\":\"nope\"}", null, true).statusCode());
        assertEquals(400, send("POST", "/v1/auth/code", "{\"code\":\"ZZZZZ8\"}", null, true).statusCode());
        assertEquals(401, send("GET", "/v1/me", null, null, false).statusCode());
    }

    @Test
    void signInWithDiscordThenLinkMinecraftGivesTheLinkedRole() throws Exception {
        HttpResponse<String> start = send("GET", "/v1/auth/discord", null, null, false);
        assertEquals(302, start.statusCode());
        String location = start.headers().firstValue("Location").orElseThrow();
        assertTrue(location.startsWith("https://discord.com/oauth2/authorize?") && location.contains("scope=identify"),
                location);
        String state = URLDecoder.decode(location.replaceAll(".*[?&]state=([^&]+).*", "$1"), StandardCharsets.UTF_8);
        String stateCookie = cookie(start, AccountRoutes.STATE);

        // Wrong state: someone else's link, or an old tab.
        HttpResponse<String> forged = send("GET", "/v1/auth/discord/callback?code=x&state=wrong", null, stateCookie, false);
        assertEquals(SITE + "/login?error=discord", forged.headers().firstValue("Location").orElseThrow());

        HttpResponse<String> back = send("GET", "/v1/auth/discord/callback?code=x&state=" + state, null, stateCookie, false);
        assertEquals(SITE + "/account", back.headers().firstValue("Location").orElseThrow());
        String session = cookie(back, AccountRoutes.SESSION);
        assertTrue(send("GET", "/v1/me", null, session, false).body().contains("\"name\":\"Discord User\""));

        UUID uuid = db.player("DiscordGamer", "criminal", 0, false);
        db.code(uuid, "FFFFF2", Duration.ofMinutes(10));
        HttpResponse<String> link = send("POST", "/v1/me/minecraft", "{\"code\":\"FFFFF2\"}", session, true);
        assertEquals(200, link.statusCode(), link.body());
        assertTrue(link.body().contains("\"linked\":true"), link.body());
        assertEquals(Boolean.TRUE, roles.get("9001"));

        assertEquals(204, send("DELETE", "/v1/me", null, session, true).statusCode());
        assertEquals(Boolean.FALSE, roles.get("9001"), "deleting takes the role back");
    }
}
