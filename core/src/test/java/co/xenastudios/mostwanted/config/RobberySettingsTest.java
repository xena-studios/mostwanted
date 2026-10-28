package co.xenastudios.mostwanted.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class RobberySettingsTest {

    static RobberySettings read(String yaml) throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString(yaml);
        return RobberySettings.read(config.getConfigurationSection("robberies"));
    }

    @Test
    void spotsOverrideTheDefaultsAndTheRestUseThem() throws Exception {
        RobberySettings settings = read("""
                robberies:
                  defaults:
                    seconds: 30
                    payout: 1000
                  bank:
                    name: First National Bank
                    seconds: 120
                """);
        RobberySettings.Spot bank = settings.spot("bank");
        assertEquals(Duration.ofSeconds(120), bank.time());
        assertEquals(1000, bank.payout(), "not set on the bank: from defaults");
        assertEquals(Duration.ofSeconds(30), settings.spot("gas-station").time());
        assertEquals(RobberySettings.DEFAULTS.defaults().bounty(), settings.spot("gas-station").bounty());
        assertEquals("First National Bank", settings.displayName("bank"));
        assertEquals("Gas Station", settings.displayName("gas-station"));
        assertEquals("Jewelry Store", settings.displayName("jewelry_store"));
    }

    @Test
    void moreGuardsOnDutyPayMoreUpToTheCap() {
        RobberySettings settings = RobberySettings.DEFAULTS;
        RobberySettings.Spot spot = settings.defaults();
        assertEquals(2_500, settings.payout(spot, 0));
        assertEquals(3_250, settings.payout(spot, 3));
        assertEquals(5_000, settings.payout(spot, 10));
        assertEquals(5_000, settings.payout(spot, 40), "capped at +100%");
    }

    @Test
    void badValuesAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> read("robberies:\n  bank:\n    seconds: 0\n"));
        assertThrows(IllegalArgumentException.class, () -> read("robberies:\n  payout-per-guard-percent: -5\n"));
        assertEquals(RobberySettings.DEFAULTS, RobberySettings.read(null));
    }
}
