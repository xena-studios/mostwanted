package co.xenastudios.mostwanted.config;

import java.time.Duration;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Riots, from config.yml's riots section.
 *
 * @param inmates how many inmates must press the alarm within {@code window} to start one
 * @param length how long a riot lasts, unless the guards stop it first
 * @param cooldown how long until the next can start
 * @param minGuards guards on duty needed: no riot nobody can answer
 */
public record RiotSettings(int inmates, Duration window, Duration length, Duration cooldown, int minGuards) {

    public static final RiotSettings DEFAULTS = new RiotSettings(3, Duration.ofSeconds(30), Duration.ofMinutes(5),
            Duration.ofMinutes(30), 1);

    static RiotSettings read(ConfigurationSection section) {
        if (section == null) {
            return DEFAULTS;
        }
        RiotSettings read = new RiotSettings(
                section.getInt("inmates-needed", DEFAULTS.inmates),
                Duration.ofSeconds(section.getLong("press-window-seconds", DEFAULTS.window.toSeconds())),
                Duration.ofSeconds(section.getLong("length-minutes", DEFAULTS.length.toMinutes()) * 60),
                Duration.ofSeconds(section.getLong("cooldown-minutes", DEFAULTS.cooldown.toMinutes()) * 60),
                section.getInt("min-guards", DEFAULTS.minGuards));
        if (read.inmates < 1 || read.length.isZero() || read.length.isNegative() || read.minGuards < 0
                || read.cooldown.isNegative()) {
            throw new IllegalArgumentException("a value in riots is out of range");
        }
        return read;
    }
}
