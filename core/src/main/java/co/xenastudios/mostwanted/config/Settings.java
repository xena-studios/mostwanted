package co.xenastudios.mostwanted.config;

import co.xenastudios.mostwanted.common.DatabaseConfig;
import java.time.Duration;
import java.time.ZoneId;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * config.yml, read once on start. On the server it's rendered from
 * server/files/plugins/MostWanted/config.yml.tmpl, so the password comes from .env.
 *
 * @param saveEvery how often changed players are written to the database
 * @param zone the clock on the scoreboard; one zone for everyone, so screenshots match the logs
 * @param zoneLabel shown after the time, such as "ET"
 */
public record Settings(DatabaseConfig database, Duration saveEvery, ZoneId zone, String zoneLabel) {

    public static Settings read(FileConfiguration config) {
        ConfigurationSection db = config.getConfigurationSection("database");
        if (db == null) {
            throw new IllegalArgumentException("config.yml has no database section");
        }
        String password = db.getString("password", "");
        if (password.isEmpty()) {
            throw new IllegalArgumentException("database.password is empty in config.yml");
        }
        DatabaseConfig database = new DatabaseConfig(
                db.getString("host", "localhost"),
                db.getInt("port", 5432),
                db.getString("name", "mostwanted"),
                db.getString("user", "mostwanted"),
                password,
                db.getInt("pool-size", 4));
        int saveSeconds = config.getInt("save-every-seconds", 5);
        if (saveSeconds < 1) {
            throw new IllegalArgumentException("save-every-seconds must be at least 1");
        }
        return new Settings(database, Duration.ofSeconds(saveSeconds),
                ZoneId.of(config.getString("clock.timezone", "America/New_York")),
                config.getString("clock.label", "ET"));
    }
}
