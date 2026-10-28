package co.xenastudios.mostwanted.zone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.xenastudios.mostwanted.common.Database;
import co.xenastudios.mostwanted.common.DatabaseConfig;
import co.xenastudios.mostwanted.storage.Storage;
import java.util.UUID;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Saving and loading zones against a real PostgreSQL. Skipped without Docker; CI has it. */
@Testcontainers(disabledWithoutDocker = true)
class ZoneStoreTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

    static Storage storage;

    @BeforeAll
    static void start() {
        Database database = Database.start(new DatabaseConfig(POSTGRES.getHost(), POSTGRES.getMappedPort(5432),
                POSTGRES.getDatabaseName(), POSTGRES.getUsername(), POSTGRES.getPassword(), 2), "test");
        storage = new Storage(database, Logger.getLogger("test"));
    }

    @AfterAll
    static void stop() {
        storage.close();
    }

    @Test
    void zonesSurviveARestart() {
        ZoneStore store = new ZoneStore(storage);
        Zone cell = ZoneIndexTest.zone("cell-9", ZoneKind.CELL, 1, 64, 1, 3, 67, 3)
                .withSpawn(new Zone.Spawn(2.5, 64, 2.5, 90, 0));
        store.put(cell, UUID.randomUUID()).join();
        store.put(ZoneIndexTest.zone("yard", ZoneKind.SAFE, -5, 60, -5, 5, 70, 5), null).join();

        ZoneStore restarted = new ZoneStore(storage);
        restarted.load();
        assertEquals(cell, restarted.get("cell-9").orElseThrow());
        assertNull(restarted.get("yard").orElseThrow().spawn());
        // Inside the cell, which is inside the yard.
        assertEquals(2, restarted.index().at("world", 2, 65, 2).size());
    }

    @Test
    void redefiningReplacesAndDeletingRemoves() {
        ZoneStore store = new ZoneStore(storage);
        store.put(ZoneIndexTest.zone("shop", ZoneKind.ROBBERY, 0, 0, 0, 1, 1, 1), null).join();
        store.put(ZoneIndexTest.zone("shop", ZoneKind.ROBBERY, 100, 0, 100, 110, 10, 110), null).join();
        assertTrue(store.index().at("world", 0, 0, 0).isEmpty());
        assertEquals(1, store.index().at("world", 105, 5, 105).size());

        assertTrue(store.delete("shop"));
        assertFalse(store.delete("shop"));
        // Writes land in order, so waiting on a later one waits for the delete too.
        store.put(ZoneIndexTest.zone("marker", ZoneKind.SAFE, 0, 0, 0, 0, 0, 0), null).join();
        ZoneStore restarted = new ZoneStore(storage);
        restarted.load();
        assertTrue(restarted.get("shop").isEmpty());
    }
}
