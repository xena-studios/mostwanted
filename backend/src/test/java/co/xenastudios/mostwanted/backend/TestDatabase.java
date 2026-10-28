package co.xenastudios.mostwanted.backend;

import static co.xenastudios.mostwanted.common.db.Tables.PLAYERS;
import static co.xenastudios.mostwanted.common.db.Tables.STATS;

import co.xenastudios.mostwanted.common.Database;
import co.xenastudios.mostwanted.common.DatabaseConfig;
import java.util.UUID;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** One PostgreSQL for a test class, with helpers to write what core would have. */
public final class TestDatabase {

    public final Database database;

    public TestDatabase(PostgreSQLContainer postgres) {
        database = Database.start(new DatabaseConfig(postgres.getHost(), postgres.getMappedPort(5432),
                postgres.getDatabaseName(), postgres.getUsername(), postgres.getPassword(), 2), "test");
    }

    public UUID player(String name, String role, long bounty, boolean statsPrivate) {
        UUID uuid = UUID.randomUUID();
        database.sql().insertInto(PLAYERS)
                .set(PLAYERS.UUID, uuid)
                .set(PLAYERS.NAME, name)
                .set(PLAYERS.ROLE, role)
                .set(PLAYERS.BOUNTY, bounty)
                .set(PLAYERS.STATS_PRIVATE, statsPrivate)
                .execute();
        return uuid;
    }

    public void stat(UUID uuid, String stat, String period, long value) {
        database.sql().insertInto(STATS)
                .set(STATS.UUID, uuid).set(STATS.STAT, stat).set(STATS.PERIOD, period).set(STATS.VALUE, value)
                .execute();
    }
}
