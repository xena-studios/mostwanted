package co.xenastudios.mostwanted.gun;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class GunSettingsTest {

    static GunSettings shipped() throws Exception {
        try (var in = GunSettingsTest.class.getResourceAsStream("/guns.yml")) {
            return GunSettings.read(YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)));
        }
    }

    @Test
    void theShippedGunsLoad() throws Exception {
        GunSettings settings = shipped();
        GunSettings.Gun pistol = settings.guns().get("pistol");
        assertEquals(Material.IRON_HOE, pistol.item());
        assertEquals("pistol", pistol.ammo());
        assertEquals(12, pistol.magazine());
        assertFalse(pistol.taser());
        assertEquals(6, settings.guns().get("shotgun").pellets());

        GunSettings.Gun taser = settings.guns().get("taser");
        assertTrue(taser.taser());
        assertTrue(taser.guardOnly());
        assertNull(taser.ammo(), "the taser recharges by itself");
        assertEquals(Duration.ofSeconds(3), taser.stun());
        assertEquals(Material.COPPER_NUGGET, settings.ammo().get("rifle").item());
        assertTrue(settings.confiscate());
    }

    @Test
    void mistakesInTheFileAreCaughtAtStartup() throws Exception {
        YamlConfiguration file = new YamlConfiguration();
        file.loadFromString("guns:\n  laser:\n    item: iron_hoe\n    ammo: batteries\n");
        assertThrows(IllegalArgumentException.class, () -> GunSettings.read(file), "ammo that doesn't exist");
        file.loadFromString("guns:\n  laser:\n    item: not_an_item\n");
        assertThrows(IllegalArgumentException.class, () -> GunSettings.read(file));
        file.loadFromString("guns:\n  laser:\n    item: iron_hoe\n    magazine: 0\n");
        assertThrows(IllegalArgumentException.class, () -> GunSettings.read(file));
    }

    @Test
    void reloadsFillAsFarAsTheAmmoGoes() {
        assertEquals(12, GunSettings.reloadAmount(0, 12, 40));
        assertEquals(5, GunSettings.reloadAmount(7, 12, 40));
        assertEquals(3, GunSettings.reloadAmount(0, 12, 3));
        assertEquals(0, GunSettings.reloadAmount(12, 12, 40));
    }

    @Test
    void headshotsAreNearTheEyes() {
        assertTrue(GunSettings.headshot(65.5, 65.62));
        assertFalse(GunSettings.headshot(64.8, 65.62));
    }
}
