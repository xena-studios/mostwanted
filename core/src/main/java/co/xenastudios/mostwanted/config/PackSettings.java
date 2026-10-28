package co.xenastudios.mostwanted.config;

import org.bukkit.configuration.ConfigurationSection;

/**
 * The resource pack, from config.yml's resource-pack section.
 *
 * @param url the pack zip; empty switches sending it off
 * @param sha1Url a file holding the zip's SHA-1, published next to it, so a new pack
 *     needs no config change
 * @param required whether Java players must accept it
 */
public record PackSettings(String url, String sha1Url, boolean required) {

    public static final PackSettings DEFAULTS = new PackSettings(
            "https://github.com/xena-studios/mostwanted/releases/download/pack/mostwanted-pack.zip",
            "https://github.com/xena-studios/mostwanted/releases/download/pack/mostwanted-pack.sha1", true);

    static PackSettings read(ConfigurationSection section) {
        if (section == null) {
            return DEFAULTS;
        }
        return new PackSettings(section.getString("url", DEFAULTS.url), section.getString("sha1-url", DEFAULTS.sha1Url),
                section.getBoolean("required", DEFAULTS.required));
    }

    public boolean enabled() {
        return url != null && !url.isBlank();
    }
}
