package co.xenastudios.mostwanted.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class RulesTest {

    private final Rules rules = Rules.DEFAULTS;

    @Test
    void sentenceIsAMinutePlusOnePerTwentyFiveHundredUpToFive() {
        assertEquals(Duration.ofMinutes(1), rules.sentence(0));
        assertEquals(Duration.ofMinutes(1), rules.sentence(2499));
        assertEquals(Duration.ofMinutes(2), rules.sentence(2500));
        assertEquals(Duration.ofMinutes(4), rules.sentence(7500));
        assertEquals(Duration.ofMinutes(5), rules.sentence(10_000));
        assertEquals(Duration.ofMinutes(5), rules.sentence(1_000_000));
    }

    @Test
    void killingPaysHalfAndCuffingPaysAll() {
        assertEquals(20_000, rules.payout(20_000, false));
        assertEquals(10_000, rules.payout(20_000, true));
        assertEquals(0, rules.payout(1, true));
    }

    @Test
    void configOverridesDefaultsAndChecksThem() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString("rules:\n  sentence:\n    max-seconds: 120\n  kill-share-percent: 25\n");
        Rules read = Rules.read(config.getConfigurationSection("rules"));
        assertEquals(Duration.ofMinutes(2), read.sentence(1_000_000));
        assertEquals(250, read.payout(1000, true));
        assertEquals(Rules.DEFAULTS.cuffRange(), read.cuffRange());

        config.loadFromString("rules:\n  kill-share-percent: 150\n");
        assertThrows(IllegalArgumentException.class, () -> Rules.read(config.getConfigurationSection("rules")));
        assertEquals(Rules.DEFAULTS, Rules.read(null));
    }
}
