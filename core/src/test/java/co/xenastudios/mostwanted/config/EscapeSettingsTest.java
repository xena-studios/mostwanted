package co.xenastudios.mostwanted.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class EscapeSettingsTest {

    @Test
    void readsOverridesAndChecksThem() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString("escapes:\n  wall-hits: 10\n  gate-open-seconds: 8\n");
        EscapeSettings read = EscapeSettings.read(config.getConfigurationSection("escapes"));
        assertEquals(10, read.wallHits());
        assertEquals(Duration.ofSeconds(8), read.gateOpen());
        assertEquals(EscapeSettings.DEFAULTS.keycardDropPercent(), read.keycardDropPercent());

        config.loadFromString("escapes:\n  keycard-drop-percent: 120\n");
        assertThrows(IllegalArgumentException.class, () -> EscapeSettings.read(config.getConfigurationSection("escapes")));
        assertEquals(EscapeSettings.DEFAULTS, EscapeSettings.read(null));
    }
}
