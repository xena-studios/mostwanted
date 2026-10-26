package co.xenastudios.mostwanted.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;

class DatabaseConfigTest {

    @Test
    void readsTheServersEnvFile() {
        DatabaseConfig config = DatabaseConfig.fromEnv(Map.of("DB_PASSWORD", "hunter2", "DB_PORT", "5433"), 4);
        assertEquals("jdbc:postgresql://localhost:5433/mostwanted", config.jdbcUrl());
        assertEquals("mostwanted", config.user());
    }

    @Test
    void needsAPassword() {
        assertThrows(IllegalArgumentException.class, () -> DatabaseConfig.fromEnv(Map.of(), 4));
        assertThrows(IllegalArgumentException.class, () -> DatabaseConfig.fromEnv(Map.of("DB_PASSWORD", ""), 4));
    }

    @Test
    void neverPrintsThePassword() {
        DatabaseConfig config = new DatabaseConfig("localhost", 5432, "mostwanted", "mostwanted", "hunter2", 4);
        assertFalse(config.toString().contains("hunter2"), config.toString());
    }
}
