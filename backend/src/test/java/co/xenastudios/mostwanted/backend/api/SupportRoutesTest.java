package co.xenastudios.mostwanted.backend.api;

import static co.xenastudios.mostwanted.common.db.Tables.PLAYERS;
import static co.xenastudios.mostwanted.common.db.Tables.RANK_GRANTS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.xenastudios.mostwanted.backend.BackendConfig;
import co.xenastudios.mostwanted.backend.TestDatabase;
import co.xenastudios.mostwanted.backend.accounts.Accounts;
import co.xenastudios.mostwanted.backend.accounts.LinkedRole;
import co.xenastudios.mostwanted.backend.accounts.Sessions;
import co.xenastudios.mostwanted.backend.stats.Stats;
import co.xenastudios.mostwanted.backend.support.Applications;
import co.xenastudios.mostwanted.backend.support.Inbox;
import co.xenastudios.mostwanted.backend.support.Staff;
import co.xenastudios.mostwanted.backend.support.Tickets;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Tickets, applications and the staff panel over real HTTP. Skipped without Docker; CI has it. */
@Testcontainers(disabledWithoutDocker = true)
class SupportRoutesTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

    static final String SITE = "https://mostwantedmc.com";
    static final HttpClient HTTP = HttpClient.newHttpClient();
    static final List<String> dms = new CopyOnWriteArrayList<>();
    static TestDatabase db;
    static Api api;

    @BeforeAll
    static void start() {
        db = new TestDatabase(POSTGRES);
        db.database.sql().execute("""
                CREATE TABLE luckperms_user_permissions (id SERIAL PRIMARY KEY, uuid VARCHAR(36) NOT NULL,
                  permission VARCHAR(200) NOT NULL, value BOOL NOT NULL, server VARCHAR(36) NOT NULL,
                  world VARCHAR(64) NOT NULL, expiry BIGINT NOT NULL, contexts VARCHAR(200) NOT NULL)""");
        BackendConfig config = new BackendConfig(null, "127.0.0.1", 0, List.of(SITE), false, 1000,
                ZoneId.of("America/New_York"), SITE, null, "https://api.mostwantedmc.com", "1", null, null, false, null);
        var sql = db.database.sql();
        Accounts accounts = new Accounts(sql);
        AccountRoutes routes = Api.accounts(config, accounts, new Sessions(sql, Clock.systemUTC()), null, LinkedRole.NONE);
        Inbox inbox = new Inbox(sql, SITE);
        inbox.dm((discord, text) -> dms.add(discord + ": " + text));
        Api.support(routes, accounts, new Tickets(sql), new Applications(sql), inbox, new Staff(sql, Clock.systemUTC()));
        api = Api.start(config, new Stats(sql, config.zone(), Clock.systemUTC()), routes, null);
    }

    @AfterAll
    static void stop() {
        api.stop();
        db.database.close();
    }

    /** Signs in as a new player with a /website code; returns the session cookie. */
    static String signIn(String name, String code) throws Exception {
        UUID uuid = db.player(name, "inmate", 0, false);
        db.code(uuid, code, Duration.ofMinutes(10));
        return AccountRoutesTest.cookie(send("POST", "/v1/auth/code", "{\"code\":\"" + code + "\"}", null),
                AccountRoutes.SESSION);
    }

    static void rank(String name, String group) {
        UUID uuid = db.database.sql().select(PLAYERS.UUID).from(PLAYERS).where(PLAYERS.NAME.eq(name)).fetchSingle(PLAYERS.UUID);
        db.database.sql().execute("INSERT INTO luckperms_user_permissions (uuid, permission, value, server, world, expiry,"
                + " contexts) VALUES (?, ?, true, 'global', 'global', 0, '{}')", uuid.toString(), "group." + group);
    }

    static HttpResponse<String> send(String method, String path, String body, String cookie) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + api.port() + path))
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body))
                .header("Content-Type", "application/json")
                .header("Origin", SITE);
        if (cookie != null) {
            request.header("Cookie", cookie);
        }
        return HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    static long id(HttpResponse<String> response) {
        Matcher id = Pattern.compile("\"id\":(\\d+)").matcher(response.body());
        assertTrue(id.find(), response.body());
        return Long.parseLong(id.group(1));
    }

    @Test
    void aTicketGoesBackAndForthWithStaff() throws Exception {
        String player = signIn("Reporter", "FFFFF2");
        String helper = signIn("Helper1", "FFFFF3");
        rank("Helper1", "helper");
        String stranger = signIn("Stranger", "FFFFF4");

        HttpResponse<String> opened = send("POST", "/v1/me/tickets",
                "{\"category\":\"report\",\"subject\":\"Fly hacker\",\"body\":\"Saw them fly at the bank.\"}", player);
        assertEquals(201, opened.statusCode(), opened.body());
        long ticket = id(opened);

        assertEquals(404, send("GET", "/v1/tickets/" + ticket, null, stranger).statusCode(), "not someone else's");
        assertEquals(403, send("GET", "/v1/staff/tickets", null, player).statusCode(), "players aren't staff");
        assertTrue(send("GET", "/v1/staff/tickets", null, helper).body().contains("Fly hacker"));
        assertTrue(send("GET", "/v1/me", null, helper).body().contains("\"staff\":\"helper\""));

        assertEquals(201, send("POST", "/v1/tickets/" + ticket + "/messages", "{\"body\":\"Thanks, looking now.\"}", helper)
                .statusCode());
        String thread = send("GET", "/v1/tickets/" + ticket, null, player).body();
        assertTrue(thread.contains("\"status\":\"answered\"") && thread.contains("\"staff\":true"), thread);
        String notices = send("GET", "/v1/me/notifications", null, player).body();
        assertTrue(notices.contains("Staff replied to your ticket"), notices);
        assertTrue(send("GET", "/v1/me", null, player).body().contains("\"unread\":1"));
        send("POST", "/v1/me/notifications/read", null, player);
        assertTrue(send("GET", "/v1/me", null, player).body().contains("\"unread\":0"));

        assertEquals(200, send("POST", "/v1/tickets/" + ticket + "/close", null, player).statusCode());
        assertEquals(409, send("POST", "/v1/tickets/" + ticket + "/messages", "{\"body\":\"one more\"}", player).statusCode());
    }

    @Test
    void ticketsAreCheckedAndLimited() throws Exception {
        String player = signIn("Spammer", "GGGGG2");
        assertEquals(400, send("POST", "/v1/me/tickets", "{\"category\":\"nope\",\"subject\":\"a\",\"body\":\"b\"}", player)
                .statusCode());
        assertEquals(400, send("POST", "/v1/me/tickets", "{\"category\":\"bug\",\"subject\":\"\",\"body\":\"b\"}", player)
                .statusCode());
        for (int i = 0; i < SupportRoutes.TICKETS_PER_HOUR; i++) {
            assertEquals(201, send("POST", "/v1/me/tickets", "{\"category\":\"bug\",\"subject\":\"s\",\"body\":\"b\"}", player)
                    .statusCode());
        }
        assertEquals(429, send("POST", "/v1/me/tickets", "{\"category\":\"bug\",\"subject\":\"s\",\"body\":\"b\"}", player)
                .statusCode());
        assertEquals(401, send("POST", "/v1/me/tickets", "{\"category\":\"bug\",\"subject\":\"s\",\"body\":\"b\"}", null)
                .statusCode());
    }

    @Test
    void anAcceptedGuardIsGivenTheRankInGame() throws Exception {
        String player = signIn("Hopeful", "HHHHH2");
        String helper = signIn("Helper2", "HHHHH3");
        rank("Helper2", "helper");
        String mod = signIn("Mod1", "HHHHH4");
        rank("Mod1", "moderator");
        String answers = "{\"kind\":\"guard\",\"answers\":{\"why\":\"I like order\",\"experience\":\"None\","
                + "\"scenario\":\"Wait outside\",\"availability\":\"Evenings ET\"}}";

        assertEquals(400, send("POST", "/v1/me/applications", "{\"kind\":\"guard\",\"answers\":{\"why\":\"x\"}}", player)
                .statusCode(), "every question answered");
        assertEquals(201, send("POST", "/v1/me/applications", answers, player).statusCode());
        assertEquals(409, send("POST", "/v1/me/applications", answers, player).statusCode(), "one at a time");

        String queue = send("GET", "/v1/staff/applications", null, mod).body();
        long application = id(send("GET", "/v1/staff/applications", null, mod));
        assertTrue(queue.contains("Hopeful") && queue.contains("Wait outside"), queue);
        assertEquals(403, send("POST", "/v1/staff/applications/" + application, "{\"decision\":\"accept\"}", helper)
                .statusCode(), "helpers don't decide");

        assertEquals(200, send("POST", "/v1/staff/applications/" + application,
                "{\"decision\":\"accept\",\"note\":\"Welcome!\"}", mod).statusCode());
        assertEquals(409, send("POST", "/v1/staff/applications/" + application, "{\"decision\":\"deny\"}", mod).statusCode());
        assertEquals(1, db.database.sql().fetchCount(RANK_GRANTS, RANK_GRANTS.DONE_AT.isNull()), "the rank for core to give");
        assertTrue(send("GET", "/v1/me/notifications", null, player).body().contains("guard application was accepted"));
        assertTrue(send("GET", "/v1/me/applications", null, player).body().contains("\"status\":\"accepted\""));
    }

    @Test
    void applyingNeedsMinecraftLinked() throws Exception {
        long id = new Accounts(db.database.sql()).signInWithDiscord("777", "OnlyDiscord");
        String session = AccountRoutes.SESSION + "=" + new Sessions(db.database.sql(), Clock.systemUTC()).create(id);
        HttpResponse<String> response = send("POST", "/v1/me/applications", "{\"kind\":\"creator\",\"answers\":{}}", session);
        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("Link your Minecraft account"), response.body());
    }
}
