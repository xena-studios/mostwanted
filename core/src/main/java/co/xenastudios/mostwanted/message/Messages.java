package co.xenastudios.mostwanted.message;

import co.xenastudios.mostwanted.common.style.Palette;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

/**
 * Every message players see, from messages.yml, in the message style
 * (docs/09-brand.md#messages): grey small-caps text, a coloured symbol, values in white.
 *
 * <p>The text is converted to small caps once when it loads. Messages with no
 * placeholders are parsed once too; the rest are parsed when sent.
 */
public final class Messages {

    /** The palette as tags, so messages say what something is, not which hex it is. */
    static final TagResolver STYLE = TagResolver.resolver(
            color("success", Palette.SUCCESS),
            color("error", Palette.ERROR),
            color("money", Palette.MONEY),
            color("info", Palette.INFO),
            color("brand", Palette.BRAND),
            color("inmate", Palette.INMATE),
            color("criminal", Palette.CRIMINAL),
            color("guard", Palette.GUARD),
            color("staff", Palette.STAFF),
            color("value", Palette.WHITE),
            TagResolver.resolver("dim", Tag.styling(NamedTextColor.DARK_GRAY)));

    private static final MiniMessage MINI = MiniMessage.builder().editTags(tags -> tags.resolver(STYLE)).build();

    private final Map<String, String> texts;
    private final Map<String, Component> parsed = new HashMap<>();

    Messages(Map<String, String> texts) {
        this.texts = texts;
        texts.forEach((key, text) -> {
            if (!hasPlaceholder(text)) {
                parsed.put(key, parse(text));
            }
        });
    }

    /** Loads messages.yml from the plugin folder, writing the default first time; new keys fall back to the defaults. */
    public static Messages load(Plugin plugin) throws IOException {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        try (InputStream in = plugin.getResource("messages.yml")) {
            if (in != null) {
                config.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)));
            }
        }
        Map<String, String> texts = new HashMap<>();
        for (String key : config.getKeys(true)) {
            if (config.isString(key)) {
                texts.put(key, SmallCapsText.convert(config.getString(key)));
            }
        }
        return new Messages(texts);
    }

    /** The message, with {@code <name>} placeholders filled in as plain text. */
    public Component get(String key, TagResolver... placeholders) {
        Component cached = parsed.get(key);
        if (cached != null && placeholders.length == 0) {
            return cached;
        }
        String text = texts.get(key);
        if (text == null) {
            return Component.text(key, NamedTextColor.RED);
        }
        return parse(text, placeholders);
    }

    public void send(Audience to, String key, TagResolver... placeholders) {
        to.sendMessage(get(key, placeholders));
    }

    /** A placeholder whose value is shown exactly as given, never parsed as tags. */
    public static TagResolver value(String name, Object value) {
        return Placeholder.unparsed(name, String.valueOf(value));
    }

    public static TagResolver component(String name, Component value) {
        return Placeholder.component(name, value);
    }

    private static Component parse(String text, TagResolver... placeholders) {
        // Grey unless a tag says otherwise.
        return MINI.deserialize(text, placeholders).colorIfAbsent(NamedTextColor.GRAY);
    }

    /** Whether the text has a tag that isn't one of the style's own. */
    private static boolean hasPlaceholder(String text) {
        int i = text.indexOf('<');
        while (i >= 0) {
            int end = text.indexOf('>', i);
            if (end < 0) {
                return false;
            }
            String name = text.substring(i + 1, end).replace("/", "").split(":", 2)[0];
            if (!name.isEmpty() && !STYLE.has(name) && !MiniMessage.miniMessage().tags().has(name)) {
                return true;
            }
            i = text.indexOf('<', end);
        }
        return false;
    }

    private static TagResolver color(String name, int rgb) {
        return TagResolver.resolver(name, Tag.styling(TextColor.color(rgb)));
    }
}
