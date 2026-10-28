package co.xenastudios.mostwanted.backend.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.xenastudios.mostwanted.backend.TestDatabase;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Against a real PostgreSQL, with rows as core writes them. Skipped without Docker; CI has it. */
@Testcontainers(disabledWithoutDocker = true)
class StatsTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

    static TestDatabase db;
    static Stats stats;
    static UUID ada;

    @BeforeAll
    static void start() {
        db = new TestDatabase(POSTGRES);
        // 2026-10-27 in New York: month 2026-10, ISO week 2026-W44.
        Clock clock = Clock.fixed(Instant.parse("2026-10-27T16:00:00Z"), ZoneOffset.UTC);
        stats = new Stats(db.database.sql(), ZoneId.of("America/New_York"), clock);

        ada = db.player("Ada", "criminal", 7_500, false);
        db.stat(ada, "escapes", "all", 12);
        db.stat(ada, "escapes", "2026-10", 5);
        db.stat(ada, "escapes", "2026-W44", 2);
        db.stat(ada, "escapes", "2026-W43", 3);
        UUID bo = db.player("Bo", "guard", 0, false);
        db.stat(bo, "escapes", "all", 12);
        db.stat(bo, "arrests", "all", 40);
        UUID hidden = db.player("Hidden", "criminal", 99_000, true);
        db.stat(hidden, "escapes", "all", 500);
        UUID cy = db.player("Cy", "inmate", 0, false);
        db.stat(cy, "escapes", "all", 0);
    }

    @AfterAll
    static void stop() {
        db.database.close();
    }

    @Test
    void periodsFollowTheServersTimezone() {
        assertEquals("2026-10", stats.periodKey(Stats.Period.MONTH));
        assertEquals("2026-W44", stats.periodKey(Stats.Period.WEEK));
    }

    @Test
    void aPlayerPageHasEveryStatInEveryPeriod() {
        Stats.Profile profile = stats.player("ada").orElseThrow();
        assertEquals("Ada", profile.name());
        assertEquals(7_500, profile.bounty());
        assertEquals(12L, profile.stats().get("all").get("escapes"));
        assertEquals(5L, profile.stats().get("month").get("escapes"));
        assertEquals(2L, profile.stats().get("week").get("escapes"), "last week's escapes don't count");
        assertEquals(0L, profile.stats().get("all").get("arrests"));
    }

    @Test
    void privatePlayersArentFound() {
        assertTrue(stats.player("Hidden").isEmpty());
        assertTrue(stats.player("Nobody").isEmpty());
    }

    @Test
    void boardsRankHighestFirstTiesByNameAndSkipZeroAndPrivate() {
        List<Stats.Entry> board = stats.board(Board.ESCAPES, Stats.Period.ALL, 10);
        assertEquals(List.of("Ada", "Bo"), board.stream().map(Stats.Entry::name).toList());
        assertEquals(1, board.get(0).rank());
        assertEquals(2, board.get(1).rank());
        assertEquals(1, stats.board(Board.ESCAPES, Stats.Period.ALL, 1).size());
        assertEquals(List.of("Ada"), stats.board(Board.ESCAPES, Stats.Period.WEEK, 10).stream()
                .map(Stats.Entry::name).toList());
    }

    @Test
    void mostWantedIsTheBountyRightNow() {
        List<Stats.Entry> board = stats.board(Board.MOST_WANTED, Stats.Period.WEEK, 10);
        assertEquals(1, board.size());
        assertEquals("Ada", board.get(0).name());
        assertEquals(7_500, board.get(0).value());
    }
}
