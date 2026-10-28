package co.xenastudios.mostwanted.config;

import org.bukkit.configuration.ConfigurationSection;

/**
 * How long logs are kept, from config.yml's retention section. The privacy page
 * (website/src/pages/privacy.astro) promises these: change both together.
 *
 * @param serverLogDays the server's log files (chat, commands, IP addresses)
 * @param gameLogMonths the game_log table (arrests, robberies, staff actions)
 */
public record Retention(int serverLogDays, int gameLogMonths) {

    public static final Retention DEFAULTS = new Retention(90, 12);

    static Retention read(ConfigurationSection section) {
        if (section == null) {
            return DEFAULTS;
        }
        Retention read = new Retention(section.getInt("server-log-days", DEFAULTS.serverLogDays),
                section.getInt("game-log-months", DEFAULTS.gameLogMonths));
        if (read.serverLogDays < 1 || read.gameLogMonths < 1) {
            throw new IllegalArgumentException("retention values must be at least 1");
        }
        return read;
    }
}
