package co.xenastudios.mostwanted.config;

import java.time.Duration;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Escape routes, from config.yml's escapes section.
 *
 * @param wallHits punches it takes to break a wall block
 * @param hitGap the most time between punches before the count starts over
 * @param wallRegrow how long a broken wall block stays open
 * @param gateOpen how long a gate stays open
 * @param keycardDropPercent the chance a guard's keycard drops when they die
 */
public record EscapeSettings(int wallHits, Duration hitGap, Duration wallRegrow, Duration gateOpen,
        int keycardDropPercent) {

    public static final EscapeSettings DEFAULTS = new EscapeSettings(20, Duration.ofMillis(1500),
            Duration.ofMinutes(2), Duration.ofSeconds(5), 50);

    static EscapeSettings read(ConfigurationSection section) {
        if (section == null) {
            return DEFAULTS;
        }
        EscapeSettings read = new EscapeSettings(
                section.getInt("wall-hits", DEFAULTS.wallHits),
                Duration.ofMillis(section.getLong("hit-gap-ms", DEFAULTS.hitGap.toMillis())),
                Duration.ofSeconds(section.getLong("wall-regrow-seconds", DEFAULTS.wallRegrow.toSeconds())),
                Duration.ofSeconds(section.getLong("gate-open-seconds", DEFAULTS.gateOpen.toSeconds())),
                section.getInt("keycard-drop-percent", DEFAULTS.keycardDropPercent));
        if (read.wallHits < 1 || read.keycardDropPercent < 0 || read.keycardDropPercent > 100
                || read.gateOpen.isNegative() || read.wallRegrow.isNegative()) {
            throw new IllegalArgumentException("a value in escapes is out of range");
        }
        return read;
    }
}
