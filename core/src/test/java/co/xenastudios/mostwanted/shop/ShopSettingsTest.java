package co.xenastudios.mostwanted.shop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.xenastudios.mostwanted.gun.GunSettings;
import co.xenastudios.mostwanted.player.Role;
import co.xenastudios.mostwanted.zone.ZoneKind;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class ShopSettingsTest {

    static YamlConfiguration resource(String name) throws Exception {
        try (var in = ShopSettingsTest.class.getResourceAsStream("/" + name)) {
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    static GunSettings guns() throws Exception {
        return GunSettings.read(resource("guns.yml"));
    }

    @Test
    void theShippedShopsLoadAgainstTheShippedGuns() throws Exception {
        ShopSettings settings = ShopSettings.read(resource("shops.yml"), guns());
        ShopSettings.Shop hideout = settings.shops().get("hideout");
        assertEquals(ZoneKind.HIDEOUT, hideout.zone());
        assertEquals(Role.CRIMINAL, hideout.role());
        ShopSettings.Entry pistol = hideout.items().getFirst();
        assertTrue(pistol.gun());
        assertEquals("pistol", pistol.id());
        assertEquals(1500, pistol.price());
        assertEquals(12, hideout.items().get(1).amount());
        assertEquals(Role.GUARD, settings.shops().get("armory").role());
        assertTrue(hideout.items().stream().noneMatch(e -> e.id().equals("taser")));
    }

    @Test
    void mistakesInTheFileAreCaughtAtStartup() throws Exception {
        GunSettings guns = guns();
        YamlConfiguration file = new YamlConfiguration();
        file.loadFromString("shops:\n  s:\n    zone: hideout\n    role: criminal\n    items:\n      - { item: \"gun:laser\", price: 1 }\n");
        assertThrows(IllegalArgumentException.class, () -> ShopSettings.read(file, guns), "a gun that doesn't exist");
        file.loadFromString("shops:\n  s:\n    zone: hideout\n    role: criminal\n    items:\n      - { item: \"gun:taser\", price: 1 }\n");
        assertThrows(IllegalArgumentException.class, () -> ShopSettings.read(file, guns), "guard gear isn't sold");
        file.loadFromString("shops:\n  s:\n    zone: hideout\n    role: criminal\n    items:\n      - { item: \"minecraft:apple\" }\n");
        assertThrows(IllegalArgumentException.class, () -> ShopSettings.read(file, guns), "no price");
        file.loadFromString("shops:\n  s:\n    zone: bank\n    role: criminal\n");
        assertThrows(IllegalArgumentException.class, () -> ShopSettings.read(file, guns), "not a zone kind");
    }

    @Test
    void pricesComeOutOfCashFirstThenTheBank() {
        assertEquals(new ShopSettings.Payment(500, 0), ShopSettings.Payment.of(800, 1000, 500));
        assertEquals(new ShopSettings.Payment(200, 300), ShopSettings.Payment.of(200, 1000, 500));
        assertEquals(new ShopSettings.Payment(0, 500), ShopSettings.Payment.of(0, 500, 500));
        assertNull(ShopSettings.Payment.of(100, 100, 500));
    }
}
