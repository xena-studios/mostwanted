package co.xenastudios.mostwanted.backend.accounts;

import static co.xenastudios.mostwanted.common.db.Tables.NOTIFICATIONS;
import static co.xenastudios.mostwanted.common.db.Tables.SESSIONS;
import static co.xenastudios.mostwanted.common.db.Tables.TICKETS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.xenastudios.mostwanted.backend.TestDatabase;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** The account rules against a real PostgreSQL. Skipped without Docker; CI has it. */
@Testcontainers(disabledWithoutDocker = true)
class AccountsTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

    static TestDatabase db;
    static Accounts accounts;

    @BeforeAll
    static void start() {
        db = new TestDatabase(POSTGRES);
        accounts = new Accounts(db.database.sql());
    }

    @AfterAll
    static void stop() {
        db.database.close();
    }

    @Test
    void aCodeSignsInOnceAndOnlyInDate() {
        UUID uuid = db.player("Coder", "inmate", 0, false);
        db.code(uuid, "AAAAA2", Duration.ofMinutes(10));
        long id = accounts.signInWithCode("AAAAA2").orElseThrow();
        assertEquals("Coder", accounts.get(id).orElseThrow().minecraftName());
        assertTrue(accounts.signInWithCode("AAAAA2").isEmpty(), "used up");

        db.code(uuid, "AAAAA3", Duration.ofMinutes(-1));
        assertTrue(accounts.signInWithCode("AAAAA3").isEmpty(), "run out");

        db.code(uuid, "AAAAA4", Duration.ofMinutes(10));
        assertEquals(id, accounts.signInWithCode("AAAAA4").orElseThrow(), "the same account next time");
    }

    @Test
    void discordThenMinecraftLinksAndFoldsInTheCodeOnlyAccount() {
        UUID uuid = db.player("Linker", "criminal", 0, false);
        db.code(uuid, "BBBBB2", Duration.ofMinutes(10));
        long minecraftOnly = accounts.signInWithCode("BBBBB2").orElseThrow();

        long id = accounts.signInWithDiscord("1001", "Linker#1");
        assertNotEquals(minecraftOnly, id);
        db.code(uuid, "BBBBB3", Duration.ofMinutes(10));
        assertEquals(Accounts.Link.LINKED, accounts.linkMinecraft(id, "BBBBB3"));
        Accounts.Account account = accounts.get(id).orElseThrow();
        assertTrue(account.linked());
        assertTrue(accounts.get(minecraftOnly).isEmpty(), "folded into the linked account");
        assertEquals(id, accounts.signInWithDiscord("1001", "New name"));
        assertEquals("New name", accounts.get(id).orElseThrow().discordName());
    }

    @Test
    void foldingKeepsTheFoldedAccountsTicketsAndNotifications() {
        UUID uuid = db.player("Keeper", "inmate", 0, false);
        db.code(uuid, "FFFFF2", Duration.ofMinutes(10));
        long minecraftOnly = accounts.signInWithCode("FFFFF2").orElseThrow();
        var sql = db.database.sql();
        sql.insertInto(TICKETS).set(TICKETS.ACCOUNT_ID, minecraftOnly).set(TICKETS.CATEGORY, "appeal")
                .set(TICKETS.SUBJECT, "Unban me").execute();
        sql.insertInto(NOTIFICATIONS).set(NOTIFICATIONS.ACCOUNT_ID, minecraftOnly).set(NOTIFICATIONS.TEXT, "Hi").execute();

        long id = accounts.signInWithDiscord("1101", "Keeper#1");
        db.code(uuid, "FFFFF3", Duration.ofMinutes(10));
        assertEquals(Accounts.Link.LINKED, accounts.linkMinecraft(id, "FFFFF3"));
        assertTrue(accounts.get(minecraftOnly).isEmpty(), "folded in");
        assertEquals(1, sql.fetchCount(TICKETS, TICKETS.ACCOUNT_ID.eq(id)), "the ticket moved");
        assertEquals(1, sql.fetchCount(NOTIFICATIONS, NOTIFICATIONS.ACCOUNT_ID.eq(id)), "the notification moved");
    }

    @Test
    void linkedListsOnlyAccountsWithBothSides() {
        UUID uuid = db.player("Both", "inmate", 0, false);
        long id = accounts.signInWithDiscord("4001", "Both");
        db.code(uuid, "EEEEE2", Duration.ofMinutes(10));
        assertEquals(Accounts.Link.LINKED, accounts.linkMinecraft(id, "EEEEE2"));
        accounts.signInWithDiscord("4002", "DiscordOnly");
        assertEquals(uuid, accounts.linked().get("4001"));
        assertFalse(accounts.linked().containsKey("4002"));
    }

    @Test
    void someoneElsesLinkedAccountCantBeTaken() {
        UUID uuid = db.player("Owner", "guard", 0, false);
        long owner = accounts.signInWithDiscord("2001", "Owner");
        db.code(uuid, "CCCCC2", Duration.ofMinutes(10));
        assertEquals(Accounts.Link.LINKED, accounts.linkMinecraft(owner, "CCCCC2"));

        long thief = accounts.signInWithDiscord("2002", "Thief");
        db.code(uuid, "CCCCC3", Duration.ofMinutes(10));
        assertEquals(Accounts.Link.TAKEN, accounts.linkMinecraft(thief, "CCCCC3"));
        long otherPlayer = accounts.signInWithCode(code(db.player("Other", "inmate", 0, false), "CCCCC4")).orElseThrow();
        assertEquals(Accounts.Link.TAKEN, accounts.linkDiscord(otherPlayer, "2001", "Owner"),
                "the owner's Discord is linked to their Minecraft already");
        assertEquals(Accounts.Link.BAD_CODE, accounts.linkMinecraft(thief, "ZZZZZ9"));
    }

    @Test
    void anAccountKeepsTheFirstOneItLinks() {
        UUID first = db.player("First", "inmate", 0, false);
        UUID second = db.player("Second", "inmate", 0, false);
        long id = accounts.signInWithDiscord("3001", "Both");
        assertEquals(Accounts.Link.LINKED, accounts.linkMinecraft(id, code(first, "DDDDD2")));
        assertEquals(Accounts.Link.ALREADY_YOURS, accounts.linkMinecraft(id, code(first, "DDDDD3")));
        assertEquals(Accounts.Link.ALREADY_LINKED, accounts.linkMinecraft(id, code(second, "DDDDD4")));
    }

    @Test
    void deletingTakesTheAccountAndItsSessions() {
        Sessions sessions = new Sessions(db.database.sql(), Clock.systemUTC());
        long id = accounts.signInWithDiscord("4001", "Leaver");
        String token = sessions.create(id);
        assertEquals(id, sessions.account(token).orElseThrow());
        assertEquals("4001", accounts.delete(id).orElseThrow().discordId());
        assertTrue(sessions.account(token).isEmpty());
        assertTrue(accounts.get(id).isEmpty());
    }

    @Test
    void sessionsLastThirtyDaysAndAreStoredHashed() {
        long id = accounts.signInWithDiscord("5001", "Timer");
        Instant start = Instant.parse("2026-10-27T12:00:00Z");
        String token = new Sessions(db.database.sql(), Clock.fixed(start, ZoneOffset.UTC)).create(id);
        Sessions later = new Sessions(db.database.sql(), Clock.fixed(start.plus(Duration.ofDays(29)), ZoneOffset.UTC));
        assertEquals(id, later.account(token).orElseThrow());
        Sessions expired = new Sessions(db.database.sql(), Clock.fixed(start.plus(Duration.ofDays(31)), ZoneOffset.UTC));
        assertTrue(expired.account(token).isEmpty());
        assertTrue(expired.cleanup() >= 1);
        String other = new Sessions(db.database.sql(), Clock.systemUTC()).create(id);
        assertFalse(db.database.sql().fetchExists(SESSIONS, SESSIONS.TOKEN_HASH.eq(other)), "the raw token is never stored");
        assertTrue(db.database.sql().fetchExists(SESSIONS, SESSIONS.TOKEN_HASH.eq(Sessions.hash(other))));
    }

    private static String code(UUID uuid, String code) {
        db.code(uuid, code, Duration.ofMinutes(10));
        return code;
    }
}
