package co.xenastudios.mostwanted.cosmetic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.permissions.Permissible;
import org.junit.jupiter.api.Test;

class CosmeticTest {

    @Test
    void everyCosmeticTheStoreSellsExistsInGame() throws IOException {
        String catalog = Files.readString(Path.of("../website/src/store/catalog.ts"));
        Matcher sold = Pattern.compile("cosmetic\\(\"([a-z-]+)\"").matcher(catalog);
        Set<String> store = new TreeSet<>();
        while (sold.find()) {
            store.add(sold.group(1));
        }
        Set<String> game = new TreeSet<>();
        for (Cosmetic cosmetic : Cosmetic.values()) {
            game.add(cosmetic.id());
        }
        assertEquals(store, game);
    }

    @Test
    void skinsNameTheirPackModel() {
        assertEquals("pistol", Cosmetic.SKIN_GILDED_PISTOL.gun());
        assertEquals("pistol_gilded", Cosmetic.SKIN_GILDED_PISTOL.model());
        assertEquals("taser_diamond", Cosmetic.SKIN_DIAMOND_TASER.model());
        assertNull(Cosmetic.TRAIL_CASH.gun());
        assertEquals("mostwanted.cosmetic.trail-cash", Cosmetic.TRAIL_CASH.permission());
    }

    @Test
    void thePackHasEverySkin() throws IOException {
        String build = Files.readString(Path.of("../pack/build.py"));
        for (Cosmetic cosmetic : Cosmetic.values()) {
            if (cosmetic.gun() != null) {
                assertTrue(build.contains("\"" + cosmetic.model() + "\": \"" + cosmetic.gun() + "\""), cosmetic.model());
                assertTrue(Files.exists(Path.of("../pack/textures/" + cosmetic.model() + ".png")), cosmetic.model());
            }
        }
    }

    @Test
    void theHighestRankTheyReallyHaveCounts() {
        assertEquals(Rank.MVP, Rank.of(permissible(Set.of("group.vip", "group.vip_plus", "group.mvp"), false)));
        assertNull(Rank.of(permissible(Set.of(), false)));
        // An op has every permission by default: that isn't a rank.
        assertNull(Rank.of(permissible(Set.of(), true)));
        assertEquals("vip-plus", Rank.VIP_PLUS.key());
    }

    @Test
    void joinMessagesAreOneShortLine() {
        assertEquals("hello there", CosmeticMenu.joinMessage("  hello \n  there "));
        assertNull(CosmeticMenu.joinMessage("   "));
        assertNull(CosmeticMenu.joinMessage("x".repeat(49)));
        assertEquals("<b>hi</b>", CosmeticMenu.joinMessage("<b>hi</b>"));
    }

    @Test
    void chatColoursGoRoundAndOff() {
        assertEquals(0, CosmeticMenu.nextHue(null, false));
        assertEquals(30, CosmeticMenu.nextHue(0, false));
        assertNull(CosmeticMenu.nextHue(330, false));
        assertEquals(330, CosmeticMenu.nextHue(null, true));
        assertNull(CosmeticMenu.nextHue(0, true));
        assertEquals(90, CosmeticMenu.hue("hue:90"));
        assertNull(CosmeticMenu.hue("hue:91"));
        assertNull(CosmeticMenu.hue("hue:x"));
        assertTrue(Cosmetics.CHAT_HUES.stream().allMatch(hue -> hue % 15 == 0));
    }

    /** Set permissions are the ones listed; an op has every other one by default. */
    private static Permissible permissible(Set<String> set, boolean op) {
        return (Permissible) Proxy.newProxyInstance(Permissible.class.getClassLoader(),
                new Class<?>[] {Permissible.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "isPermissionSet" -> set.contains((String) args[0]);
                    case "hasPermission" -> op || set.contains((String) args[0]);
                    case "isOp" -> op;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
