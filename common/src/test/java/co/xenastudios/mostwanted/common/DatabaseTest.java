package co.xenastudios.mostwanted.common;

import static co.xenastudios.mostwanted.common.db.Tables.GAME_LOG;
import static co.xenastudios.mostwanted.common.db.Tables.PLAYERS;
import static co.xenastudios.mostwanted.common.db.Tables.STATS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.jooq.JSONB;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Against a real PostgreSQL, the version the VPS runs. Skipped where Docker isn't available; CI has it. */
@Testcontainers(disabledWithoutDocker = true)
class DatabaseTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

    static Database database;

    @BeforeAll
    static void start() {
        database = Database.start(config(), "test");
    }

    @AfterAll
    static void stop() {
        database.close();
    }

    static DatabaseConfig config() {
        return new DatabaseConfig(POSTGRES.getHost(), POSTGRES.getMappedPort(5432),
                POSTGRES.getDatabaseName(), POSTGRES.getUsername(), POSTGRES.getPassword(), 2);
    }

    @Test
    void startingAgainChangesNothing() {
        var history = DSL.table(DSL.name(Database.SCHEMA, "flyway_schema_history"));
        int applied = database.sql().fetchCount(history);
        // A second app (backend next to core) finds the schema up to date.
        try (Database again = Database.start(config(), "again")) {
            assertEquals(applied, again.sql().fetchCount(history));
        }
    }

    @Test
    void savesAndLoadsAPlayer() {
        UUID uuid = UUID.randomUUID();
        database.sql().insertInto(PLAYERS).set(PLAYERS.UUID, uuid).set(PLAYERS.NAME, "Alex").execute();
        database.sql().update(PLAYERS)
                .set(PLAYERS.CASH, PLAYERS.CASH.plus(1_200L))
                .set(PLAYERS.ROLE, "criminal")
                .where(PLAYERS.UUID.eq(uuid))
                .execute();

        var player = database.sql().fetchSingle(PLAYERS, PLAYERS.UUID.eq(uuid));
        assertEquals("Alex", player.getName());
        assertEquals("criminal", player.getRole());
        assertEquals(1_200L, player.getCash());
        assertEquals(0L, player.getBounty());
        assertNull(player.getJailedUntil());
    }

    @Test
    void refusesImpossibleState() {
        UUID uuid = UUID.randomUUID();
        database.sql().insertInto(PLAYERS).set(PLAYERS.UUID, uuid).set(PLAYERS.NAME, "Sam").execute();
        assertThrows(DataAccessException.class, () -> database.sql().update(PLAYERS)
                .set(PLAYERS.CASH, -1L).where(PLAYERS.UUID.eq(uuid)).execute());
        assertThrows(DataAccessException.class, () -> database.sql().update(PLAYERS)
                .set(PLAYERS.ROLE, "warden").where(PLAYERS.UUID.eq(uuid)).execute());
    }

    @Test
    void countsStatsPerPeriod() {
        UUID uuid = UUID.randomUUID();
        database.sql().insertInto(PLAYERS).set(PLAYERS.UUID, uuid).set(PLAYERS.NAME, "Steve").execute();
        // How core will save a batch: add to the counter, creating it the first time.
        for (int i = 0; i < 3; i++) {
            database.sql().insertInto(STATS)
                    .set(STATS.UUID, uuid).set(STATS.STAT, "arrests").set(STATS.PERIOD, "2026-W44").set(STATS.VALUE, 1L)
                    .onConflict(STATS.UUID, STATS.STAT, STATS.PERIOD)
                    .doUpdate().set(STATS.VALUE, STATS.VALUE.plus(1L))
                    .execute();
        }
        assertEquals(3L, database.sql().fetchValue(STATS.VALUE,
                STATS.UUID.eq(uuid).and(STATS.STAT.eq("arrests")).and(STATS.PERIOD.eq("2026-W44"))));
    }

    @Test
    void logsGameEvents() {
        long id = database.sql().insertInto(GAME_LOG)
                .set(GAME_LOG.KIND, "robbery")
                .set(GAME_LOG.ACTOR, UUID.randomUUID())
                .set(GAME_LOG.DETAILS, JSONB.valueOf("{\"location\": \"bank\", \"payout\": 2500}"))
                .returning(GAME_LOG.ID)
                .fetchSingle(GAME_LOG.ID);
        assertTrue(id > 0);
    }

    @Test
    void notifiesListenersOnlyOnceCommitted() throws InterruptedException {
        BlockingQueue<String> received = new LinkedBlockingQueue<>();
        Semaphore connects = new Semaphore(0);
        try (var listener = Notifications.listen(database, List.of("test_commit"), connects::release,
                (channel, payload) -> received.add(channel + ":" + payload))) {
            assertTrue(connects.tryAcquire(10, TimeUnit.SECONDS), "connected");
            assertTrue(listener.connected());

            database.sql().transaction(tx -> {
                Notifications.publish(tx.dsl(), "test_commit", "ticket:12");
                // Not sent until the transaction commits.
                assertNull(received.poll(1_500, TimeUnit.MILLISECONDS));
            });
            assertEquals("test_commit:ticket:12", received.poll(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void reconnectsAndCatchesUpAfterLosingTheConnection() throws InterruptedException {
        BlockingQueue<String> received = new LinkedBlockingQueue<>();
        Semaphore connects = new Semaphore(0);
        try (var listener = Notifications.listen(database, List.of("test_reconnect"), connects::release,
                (channel, payload) -> received.add(payload))) {
            assertTrue(connects.tryAcquire(10, TimeUnit.SECONDS), "connected");

            // As if PostgreSQL restarted under it.
            database.sql().execute("""
                    SELECT pg_terminate_backend(pid) FROM pg_stat_activity
                    WHERE application_name = 'mostwanted-test' AND query LIKE 'LISTEN%'""");

            assertTrue(connects.tryAcquire(15, TimeUnit.SECONDS), "reconnected and caught up");
            assertTrue(listener.connected());
            Notifications.publish(database.sql(), "test_reconnect", "after");
            assertEquals("after", received.poll(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void refusesBadChannelsAndHugePayloads() {
        assertThrows(IllegalArgumentException.class, () -> Notifications.publish(database.sql(), "Bad; DROP", "x"));
        assertThrows(IllegalArgumentException.class,
                () -> Notifications.publish(database.sql(), "test", "x".repeat(Notifications.MAX_PAYLOAD_BYTES + 1)));
    }
}
