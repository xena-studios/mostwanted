package co.xenastudios.mostwanted.backend.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.xenastudios.mostwanted.backend.BackendConfig;
import co.xenastudios.mostwanted.backend.TestDatabase;
import co.xenastudios.mostwanted.backend.accounts.Accounts;
import co.xenastudios.mostwanted.backend.accounts.LinkedRole;
import co.xenastudios.mostwanted.backend.accounts.Sessions;
import co.xenastudios.mostwanted.backend.stats.Stats;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** The API over real HTTP, on a free port. Skipped without Docker; CI has it. */
@Testcontainers(disabledWithoutDocker = true)
class ApiTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

    static TestDatabase db;
    static Api api;
    static final HttpClient HTTP = HttpClient.newHttpClient();

    @BeforeAll
    static void start() {
        db = new TestDatabase(POSTGRES);
        db.player("Ada", "criminal", 2_500, false);
        BackendConfig config = new BackendConfig(null, "127.0.0.1", 0, List.of("https://mostwantedmc.com"),
                true, 30, ZoneId.of("America/New_York"), "https://mostwantedmc.com", null,
                "https://api.mostwantedmc.com", "1", null, null, true, null);
        api = Api.start(config, new Stats(db.database.sql(), config.zone(), Clock.systemUTC()),
                Api.accounts(config, new Accounts(db.database.sql()), new Sessions(db.database.sql(), Clock.systemUTC()),
                        null, LinkedRole.NONE), null);
    }

    @AfterAll
    static void stop() {
        api.stop();
        db.database.close();
    }

    static HttpResponse<String> get(String path, String client, String... headers) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + api.port() + path))
                .header("CF-Connecting-IP", client);
        if (headers.length > 0) {
            request.headers(headers);
        }
        return HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void healthSaysWhatsUp() throws Exception {
        HttpResponse<String> response = get("/health", "1.1.1.1");
        assertEquals(200, response.statusCode());
        assertEquals("{\"status\":\"ok\",\"database\":\"up\",\"discord\":\"off\",\"discordSignIn\":\"off\"}", response.body());
        assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
    }

    @Test
    void playersAndBoardsAreJsonAndCached() throws Exception {
        HttpResponse<String> player = get("/v1/players/ADA", "1.1.1.2");
        assertEquals(200, player.statusCode());
        assertTrue(player.body().contains("\"name\":\"Ada\""), player.body());
        assertTrue(player.headers().firstValue("Cache-Control").orElseThrow().contains("s-maxage"));

        HttpResponse<String> board = get("/v1/leaderboards/most-wanted?limit=5", "1.1.1.2");
        assertEquals(200, board.statusCode());
        assertTrue(board.body().startsWith("{\"board\":\"most-wanted\",\"period\":\"all\",\"entries\":[{\"rank\":1"),
                board.body());
    }

    @Test
    void statusSaysTheGameIsDownUntilItReports() throws Exception {
        HttpResponse<String> status = get("/v1/status", "1.1.1.4");
        assertEquals(200, status.statusCode());
        assertEquals("{\"online\":false,\"players\":0,\"max\":0}", status.body());
    }

    @Test
    void errorsAreJsonWithAReason() throws Exception {
        assertEquals("{\"error\":\"Nobody called Nobody has played yet, or their stats are private.\"}", get("/v1/players/Nobody", "1.1.1.3").body());
        assertEquals(400, get("/v1/players/bad%20name%3F", "1.1.1.3").statusCode());
        assertEquals("{\"error\":\"There's no such board.\"}", get("/v1/leaderboards/fastest", "1.1.1.3").body());
        assertEquals("{\"error\":\"The limit is 1 to 50.\"}", get("/v1/leaderboards/escapes?limit=0", "1.1.1.3").body());
        HttpResponse<String> missing = get("/nothing/here", "1.1.1.3");
        assertEquals(404, missing.statusCode());
        assertEquals("{\"error\":\"Not found.\"}", missing.body());
    }

    @Test
    void onlyTheWebsiteMayCallFromABrowser() throws Exception {
        assertEquals("https://mostwantedmc.com", get("/health", "1.1.1.4", "Origin", "https://mostwantedmc.com")
                .headers().firstValue("Access-Control-Allow-Origin").orElseThrow());
        assertTrue(get("/health", "1.1.1.4", "Origin", "https://evil.example")
                .headers().firstValue("Access-Control-Allow-Origin").isEmpty());
    }

    @Test
    void eachVisitorIsRateLimitedOnTheirOwn() throws Exception {
        for (int i = 0; i < 30; i++) {
            assertEquals(200, get("/health", "9.9.9.9").statusCode());
        }
        HttpResponse<String> limited = get("/health", "9.9.9.9");
        assertEquals(429, limited.statusCode());
        assertEquals("60", limited.headers().firstValue("Retry-After").orElseThrow());
        assertEquals(200, get("/health", "8.8.8.8").statusCode(), "someone else isn't affected");
    }
}
