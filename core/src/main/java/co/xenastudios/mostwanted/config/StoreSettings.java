package co.xenastudios.mostwanted.config;

import java.util.List;
import java.util.regex.Pattern;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Store delivery, from config.yml's store section: core runs the commands Tebex queues
 * for each purchase, so the Tebex plugin isn't needed (docs/11-store.md).
 *
 * @param tebexSecret the webstore's secret key; empty or "none" switches delivery off
 * @param allowed patterns a delivered command must match in full. Anything else stays
 *     in the queue and is logged, so a leaked key can't run any console command it likes.
 */
public record StoreSettings(String tebexSecret, List<Pattern> allowed) {

    private static final String NAME = "[A-Za-z0-9_.*]{1,32}";
    /** What the store's packages run (docs/11-store.md), and nothing more. */
    public static final List<Pattern> DEFAULT_ALLOWED = List.of(
            Pattern.compile("lp user " + NAME + " parent add (vip|vip_plus|mvp|legend)"),
            Pattern.compile("lp user " + NAME + " permission set mostwanted\\.cosmetic\\.[a-z-]+ true"),
            Pattern.compile("mostwanted boost " + NAME));

    static StoreSettings read(ConfigurationSection section) {
        if (section == null) {
            return new StoreSettings("", DEFAULT_ALLOWED);
        }
        List<Pattern> allowed = section.isList("allowed-commands")
                ? section.getStringList("allowed-commands").stream().map(Pattern::compile).toList()
                : DEFAULT_ALLOWED;
        return new StoreSettings(section.getString("tebex-secret", "").strip(), allowed);
    }

    public boolean enabled() {
        return !tebexSecret.isEmpty() && !tebexSecret.equalsIgnoreCase("none");
    }

    /** Whether a command may be run, with or without its leading slash. */
    public boolean allows(String command) {
        String plain = command.strip().replaceFirst("^/", "");
        return allowed.stream().anyMatch(pattern -> pattern.matcher(plain).matches());
    }

    @Override
    public String toString() {
        // Never the key: this can end up in a log.
        return "StoreSettings[delivery " + (enabled() ? "on" : "off") + ", allowed " + allowed + "]";
    }
}
