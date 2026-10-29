package co.xenastudios.mostwanted.common;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.conf.Settings;
import org.jooq.impl.DSL;

/**
 * The connection pool, with the schema brought up to date on start. One per app:
 * core and backend each open their own, against the same database.
 *
 * <p>Every query blocks, so callers run them off the game's main thread.
 */
public final class Database implements AutoCloseable {

    /** Our tables' schema, apart from LuckPerms' and LibertyBans'. */
    public static final String SCHEMA = "mostwanted";

    static {
        // jOOQ prints a logo and a tip of the day on first use: noise in the server console.
        System.setProperty("org.jooq.no-logo", "true");
        System.setProperty("org.jooq.no-tips", "true");
    }

    private final DatabaseConfig config;
    private final String app;
    private final HikariDataSource pool;
    private final DSLContext sql;

    private Database(DatabaseConfig config, String app, HikariDataSource pool) {
        this.config = config;
        this.app = app;
        this.pool = pool;
        // No per-query debug logging: it costs time on every query even when it prints nothing.
        this.sql = DSL.using(pool, SQLDialect.POSTGRES, new Settings().withExecuteLogging(false));
    }

    /**
     * Opens the pool and runs any migrations not yet applied. Fails fast if the
     * database can't be reached or a migration fails, so nothing runs on a half-made schema.
     *
     * @param app shown in PostgreSQL's {@code pg_stat_activity}, such as {@code "core"}
     */
    public static Database start(DatabaseConfig config, String app) {
        HikariConfig hikari = new HikariConfig();
        hikari.setPoolName("mostwanted-" + app);
        hikari.setJdbcUrl(config.jdbcUrl());
        // Named, so the driver is found from Paper's library loader, which DriverManager can't see.
        hikari.setDriverClassName("org.postgresql.Driver");
        hikari.setUsername(config.user());
        hikari.setPassword(config.password());
        hikari.setMaximumPoolSize(config.poolSize());
        // A fixed pool: no connections opening and closing under load.
        hikari.setMinimumIdle(config.poolSize());
        // Wait at most 5 s for a connection rather than Hikari's 30 s, so a dead
        // database shows up as an error quickly instead of piling up waiting tasks.
        hikari.setConnectionTimeout(5_000);
        hikari.setDataSourceProperties(driverProperties(app));

        HikariDataSource pool = new HikariDataSource(hikari);
        try {
            migrate(pool);
            return new Database(config, app, pool);
        } catch (RuntimeException e) {
            pool.close();
            throw e;
        }
    }

    private static void migrate(DataSource pool) {
        // Our class loader, not the thread's: on Paper, the migrations are in the
        // plugin jar while Flyway is loaded by the library loader.
        Flyway.configure(Database.class.getClassLoader())
                .dataSource(pool)
                .schemas(SCHEMA)
                .locations("classpath:db/migration")
                // Never wipe the database, whatever the config says.
                .cleanDisabled(true)
                .load()
                .migrate();
    }

    private static Properties driverProperties(String app) {
        Properties properties = new Properties();
        properties.setProperty("ApplicationName", "mostwanted-" + app);
        // Batched inserts become one multi-row insert: much faster for write-behind saves.
        properties.setProperty("reWriteBatchedInserts", "true");
        // Kill a query that hangs rather than holding a connection forever.
        properties.setProperty("options", "-c statement_timeout=30000");
        return properties;
    }

    /** Builds and runs queries with the generated table classes in {@code common.db}. */
    public DSLContext sql() {
        return sql;
    }

    /**
     * A connection of its own, outside the pool, for holding open as long as needed
     * (as {@link Notifications.Listener} does). The caller closes it.
     */
    Connection openDedicated() throws SQLException {
        Properties properties = driverProperties(app);
        properties.setProperty("user", config.user());
        properties.setProperty("password", config.password());
        // It sits idle between notifications; the timeout is for queries, not for waiting.
        properties.remove("options");
        // The driver directly, not DriverManager, for the same reason as the pool's driver class.
        Connection connection = new org.postgresql.Driver().connect(config.jdbcUrl(), properties);
        if (connection == null) {
            throw new SQLException("Not a PostgreSQL URL: " + config.jdbcUrl());
        }
        return connection;
    }

    @Override
    public void close() {
        pool.close();
    }
}
