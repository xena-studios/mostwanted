package co.xenastudios.mostwanted.config;

import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;

/**
 * How each robbery spot plays, from config.yml's robberies section. Every {@code robbery}
 * zone is a spot; one without its own section uses the defaults.
 *
 * @param perGuardPercent the payout grows this much for each guard on duty...
 * @param maxBonusPercent ...up to this much more
 */
public record RobberySettings(Spot defaults, Map<String, Spot> spots, int perGuardPercent, int maxBonusPercent) {

    /**
     * One spot.
     *
     * @param name shown to players, such as "Bank"; null to make one from the zone's name
     * @param time how long robbers must stay inside
     * @param payout split between the robbers, before the guard bonus
     * @param bounty added to each robber
     * @param closed how long it stays shut afterwards, robbed or not
     */
    public record Spot(String name, Duration time, long payout, long bounty, Duration closed) {}

    public static final RobberySettings DEFAULTS = new RobberySettings(
            new Spot(null, Duration.ofSeconds(60), 2_500, 1_500, Duration.ofMinutes(10)), Map.of(), 10, 100);

    static RobberySettings read(ConfigurationSection section) {
        if (section == null) {
            return DEFAULTS;
        }
        Spot defaults = spot(section.getConfigurationSection("defaults"), DEFAULTS.defaults);
        Map<String, Spot> spots = new HashMap<>();
        for (String key : section.getKeys(false)) {
            if (section.isConfigurationSection(key) && !key.equals("defaults")) {
                spots.put(key.toLowerCase(Locale.ROOT), spot(section.getConfigurationSection(key), defaults));
            }
        }
        RobberySettings read = new RobberySettings(defaults, Map.copyOf(spots),
                section.getInt("payout-per-guard-percent", DEFAULTS.perGuardPercent),
                section.getInt("max-guard-bonus-percent", DEFAULTS.maxBonusPercent));
        if (read.perGuardPercent < 0 || read.maxBonusPercent < 0) {
            throw new IllegalArgumentException("robbery guard bonus can't be negative");
        }
        return read;
    }

    private static Spot spot(ConfigurationSection section, Spot fallback) {
        if (section == null) {
            return fallback;
        }
        Spot spot = new Spot(section.getString("name", null),
                Duration.ofSeconds(section.getLong("seconds", fallback.time().toSeconds())),
                section.getLong("payout", fallback.payout()),
                section.getLong("bounty", fallback.bounty()),
                Duration.ofSeconds(section.getLong("closed-minutes", fallback.closed().toMinutes()) * 60));
        if (spot.time().toSeconds() < 1 || spot.payout() < 0 || spot.bounty() < 0 || spot.closed().isNegative()) {
            throw new IllegalArgumentException("a robbery in config.yml has a value out of range: " + section.getName());
        }
        return spot;
    }

    public Spot spot(String zone) {
        return spots.getOrDefault(zone, defaults);
    }

    /** The payout with the bonus for guards on duty: more guards, more risk, more reward. */
    public long payout(Spot spot, int guardsOnDuty) {
        int bonus = Math.min(perGuardPercent * guardsOnDuty, maxBonusPercent);
        return spot.payout() * (100 + bonus) / 100;
    }

    /** "Bank", "Gas Station": the spot's name if set, otherwise its zone name tidied up. */
    public String displayName(String zone) {
        String name = spot(zone).name();
        if (name != null) {
            return name;
        }
        StringBuilder out = new StringBuilder();
        for (String word : zone.split("[-_]")) {
            if (!word.isEmpty()) {
                out.append(out.isEmpty() ? "" : " ").append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
        }
        return out.toString();
    }
}
