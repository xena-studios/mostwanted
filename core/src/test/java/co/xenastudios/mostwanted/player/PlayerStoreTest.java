package co.xenastudios.mostwanted.player;

import static co.xenastudios.mostwanted.common.db.Tables.PLAYERS;
import static co.xenastudios.mostwanted.common.db.Tables.STATS;
import static org.junit.jupiter.api.Assertions.assertEquals;

import co.xenastudios.mostwanted.common.Database;
import co.xenastudios.mostwanted.common.DatabaseConfig;
import co.xenastudios.mostwanted.common.Stat;
import co.xenastudios.mostwanted.storage.Storage;
import java.time.Clock;
import java.time.ZoneId;
import java.util.UUID;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Loading and saving against a real PostgreSQL. Skipped without Docker; CI has it. */
@Testcontainers(disabledWithoutDocker = true)
class PlayerStoreTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

    static Storage storage;
    static PlayerStore store;

    @BeforeAll
    static void start() {
        Database database = Database.start(new DatabaseConfig(POSTGRES.getHost(), POSTGRES.getMappedPort(5432),
                POSTGRES.getDatabaseName(), POSTGRES.getUsername(), POSTGRES.getPassword(), 2), "test");
        storage = new Storage(database, Logger.getLogger("test"));
        // Runs "main thread" work straight away: there's no server here.
        store = new PlayerStore(storage, Runnable::run, ZoneId.of("America/New_York"), Clock.systemUTC(),
                Logger.getLogger("test"));
    }

    @AfterAll
    static void stop() {
        storage.close();
    }

    @Test
    void newPlayersStartAsInmatesWithNothing() {
        UUID uuid = UUID.randomUUID();
        assertEquals(PlayerStore.Loaded.FIRST_TIME, store.load(uuid, "Alex"));
        PlayerState state = store.get(uuid).orElseThrow();
        assertEquals(Role.INMATE, state.role());
        assertEquals(0, state.cash());
        store.quit(uuid);
    }

    @Test
    void changesSurviveQuittingAndRejoiningStraightAway() {
        UUID uuid = UUID.randomUUID();
        store.load(uuid, "Steve");
        PlayerState state = store.get(uuid).orElseThrow();
        state.role(Role.CRIMINAL);
        state.cash(6_750);
        state.bounty(2_500);
        state.add(Stat.ESCAPES, 1);
        store.quit(uuid);

        // No wait: load must see the quit save.
        assertEquals(PlayerStore.Loaded.RETURNING, store.load(uuid, "Steve"));
        PlayerState again = store.get(uuid).orElseThrow();
        assertEquals(Role.CRIMINAL, again.role());
        assertEquals(6_750, again.cash());
        assertEquals(2_500, again.bounty());
        assertEquals(1, again.total(Stat.ESCAPES));
        store.quit(uuid);
    }

    @Test
    void savesChangedPlayersAndCountsStatsPerPeriod() throws Exception {
        UUID uuid = UUID.randomUUID();
        store.load(uuid, "Sam");
        PlayerState state = store.get(uuid).orElseThrow();
        state.add(Stat.ARRESTS, 2);
        store.saveChanged();
        state.add(Stat.ARRESTS, 1);
        store.saveChanged();
        // Queue a read behind the writes.
        storage.write("nothing", sql -> { }).get();

        assertEquals(3, storage.sql().fetchCount(STATS, STATS.UUID.eq(uuid)));
        assertEquals(3L, storage.sql().fetchValue(STATS.VALUE,
                STATS.UUID.eq(uuid).and(STATS.STAT.eq("arrests")).and(STATS.PERIOD.eq("all"))));
        store.quit(uuid);
    }

    @Test
    void aSecondConnectionIsRefusedWhileTheFirstIsOnline() {
        UUID uuid = UUID.randomUUID();
        store.load(uuid, "Jinx");
        assertEquals(PlayerStore.Loaded.ALREADY_ONLINE, store.load(uuid, "Jinx"));
        store.quit(uuid);
    }

    @Test
    void namesFollowTheLatestLogin() throws Exception {
        UUID uuid = UUID.randomUUID();
        store.load(uuid, "OldName");
        store.quit(uuid);
        store.load(uuid, "NewName");
        store.quit(uuid);
        storage.write("nothing", sql -> { }).get();
        assertEquals("NewName", storage.sql().fetchValue(PLAYERS.NAME, PLAYERS.UUID.eq(uuid)));
    }

    @Test
    void dutyMoneyStaysApartAcrossARejoin() {
        UUID uuid = UUID.randomUUID();
        store.load(uuid, "Officer");
        PlayerState state = store.get(uuid).orElseThrow();
        state.cash(300);
        state.bank(1_000);
        // On duty: starts with the (empty) guard money; earns some.
        state.swapStash();
        assertEquals(0, state.cash());
        state.cash(5_000);
        store.quit(uuid);

        store.load(uuid, "Officer");
        PlayerState again = store.get(uuid).orElseThrow();
        assertEquals(5_000, again.cash());
        // Off duty: the inmate money comes back, and the guard money waits.
        again.swapStash();
        assertEquals(300, again.cash());
        assertEquals(1_000, again.bank());
        assertEquals(5_000, again.stashedCash());
        store.quit(uuid);
    }
}
