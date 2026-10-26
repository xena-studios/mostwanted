package co.xenastudios.mostwanted.common;

import java.util.Map;
import java.util.Objects;

/**
 * Where PostgreSQL is and how to sign in. core reads it from its config, backend from
 * the same {@code DB_*} variables as the server's {@code .env}.
 *
 * @param poolSize connections to keep open; small, since the server's cores are shared
 */
public record DatabaseConfig(String host, int port, String name, String user, String password, int poolSize) {

    public DatabaseConfig {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(user, "user");
        Objects.requireNonNull(password, "password");
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("port must be 1-65535, not " + port);
        }
        if (poolSize < 1) {
            throw new IllegalArgumentException("poolSize must be at least 1, not " + poolSize);
        }
    }

    /** Reads {@code DB_HOST}, {@code DB_PORT}, {@code DB_NAME}, {@code DB_USER} and {@code DB_PASSWORD}. */
    public static DatabaseConfig fromEnv(Map<String, String> env, int poolSize) {
        return new DatabaseConfig(
                env.getOrDefault("DB_HOST", "localhost"),
                Integer.parseInt(env.getOrDefault("DB_PORT", "5432")),
                env.getOrDefault("DB_NAME", "mostwanted"),
                env.getOrDefault("DB_USER", "mostwanted"),
                required(env, "DB_PASSWORD"),
                poolSize);
    }

    public String jdbcUrl() {
        return "jdbc:postgresql://" + host + ":" + port + "/" + name;
    }

    /** Never shows the password, so logging a config can't leak it. */
    @Override
    public String toString() {
        return "DatabaseConfig[" + user + "@" + host + ":" + port + "/" + name + ", poolSize=" + poolSize + "]";
    }

    private static String required(Map<String, String> env, String key) {
        String value = env.get(key);
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException(key + " is not set");
        }
        return value;
    }
}
