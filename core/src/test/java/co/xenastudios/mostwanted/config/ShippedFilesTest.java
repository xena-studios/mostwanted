package co.xenastudios.mostwanted.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/** The files the plugin ships with: complete, consistent, and readable by the code. */
class ShippedFilesTest {

    static final List<String> SECTIONS = List.of("database", "save-every-seconds", "clock", "rules", "robberies",
            "retention", "escapes", "riots", "resource-pack", "store");

    static YamlConfiguration resource(String name) throws Exception {
        try (var in = ShippedFilesTest.class.getResourceAsStream("/" + name)) {
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    @Test
    void theDefaultConfigHasEverySectionAndReads() throws Exception {
        YamlConfiguration config = resource("config.yml");
        for (String section : SECTIONS) {
            assertTrue(config.contains(section), "config.yml is missing " + section);
        }
        // The password is left out on purpose; with one, it must read as is.
        config.set("database.password", "test");
        Settings settings = Settings.read(config);
        assertEquals(Duration.ofSeconds(120), settings.robberies().spot("bank").time(), "the bank's own settings");
        assertTrue(settings.pack().enabled());
    }

    @Test
    void theServerTemplateHasTheSameSections() throws Exception {
        // Tests run in core/; the template is in server/.
        YamlConfiguration template = YamlConfiguration.loadConfiguration(
                Path.of("..", "server", "files", "plugins", "MostWanted", "config.yml.tmpl").toFile());
        assertEquals(resource("config.yml").getKeys(false), template.getKeys(false),
                "server/files/plugins/MostWanted/config.yml.tmpl and config.yml have different sections");
    }

    /** Every message the code sends by a fixed key exists, so players never see a key. */
    @Test
    void everyMessageTheCodeSendsExists() throws Exception {
        Set<String> keys = resource("messages.yml").getKeys(true);
        Pattern call = Pattern.compile("messages\\.(?:send|get)\\((?:[^\"()]*?,\\s*)?\"([a-z0-9.-]+)\"");
        Set<String> missing = new TreeSet<>();
        List<String> found = new ArrayList<>();
        try (Stream<Path> files = Files.walk(Path.of("src", "main", "java"))) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                Matcher m = call.matcher(Files.readString(file));
                while (m.find()) {
                    String key = m.group(1);
                    found.add(key);
                    // "zone.entered." + kind: the section must exist.
                    if (key.endsWith(".")) {
                        key = key.substring(0, key.length() - 1);
                    }
                    if (!keys.contains(key)) {
                        missing.add(m.group(1) + " (" + file.getFileName() + ")");
                    }
                }
            }
        }
        assertTrue(found.size() > 50, "the scan found only " + found.size() + " message keys: is the pattern still right?");
        assertTrue(missing.isEmpty(), "messages.yml has no " + missing);
    }
}
