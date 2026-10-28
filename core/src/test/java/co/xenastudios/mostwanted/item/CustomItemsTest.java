package co.xenastudios.mostwanted.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.xenastudios.mostwanted.gun.GunSettings;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class CustomItemsTest {

    @Test
    void modelNamesMatchThePack() {
        assertEquals("mostwanted:pistol", CustomItems.model("gun:pistol"));
        assertEquals("mostwanted:ammo_shells", CustomItems.model("ammo:shells"));
        assertEquals("mostwanted:handcuffs", CustomItems.model("handcuffs"));
    }

    /** Every item the game makes has a texture in pack/, so none shows up untextured. */
    @Test
    void everyItemHasATextureInThePack() throws Exception {
        GunSettings guns;
        try (var in = getClass().getResourceAsStream("/guns.yml")) {
            guns = GunSettings.read(YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)));
        }
        List<String> ids = new ArrayList<>(List.of("handcuffs", "keycard"));
        guns.guns().keySet().forEach(id -> ids.add("gun:" + id));
        guns.ammo().keySet().forEach(id -> ids.add("ammo:" + id));
        // Tests run in core/; the pack is next to it.
        Path textures = Path.of("..", "pack", "textures");
        for (String id : ids) {
            String name = CustomItems.model(id).substring("mostwanted:".length());
            assertTrue(Files.exists(textures.resolve(name + ".png")), "no pack/textures/" + name + ".png for " + id);
        }
    }
}
