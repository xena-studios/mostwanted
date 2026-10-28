package co.xenastudios.mostwanted.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.xenastudios.mostwanted.cosmetic.Cosmetic;
import co.xenastudios.mostwanted.cosmetic.Rank;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class MenusTest {

    @Test
    void wrapsBetweenWords() {
        assertEquals(List.of("Leave a trail of bills", "behind you."), Menus.wrap("Leave a trail of bills behind you.", 22));
        assertEquals(List.of("short"), Menus.wrap("  short  ", 30));
        assertEquals(List.of(), Menus.wrap("", 30));
    }

    /** The store menu shows every package the website sells, each with an icon. */
    @Test
    void everyPackageOnTheWebsiteHasAnIcon() throws Exception {
        String catalog = Files.readString(Path.of("../website/src/store/catalog.ts"));
        Matcher ids = Pattern.compile("(?:id: |cosmetic\\()\"([a-z-]+)\"").matcher(catalog);
        int found = 0;
        while (ids.find()) {
            String id = ids.group(1);
            found++;
            assertTrue(Cosmetic.byId(id) != null || StoreMenu.ICONS.containsKey(id), "no icon for " + id);
        }
        assertTrue(found > 15, "read the catalogue");
        for (Rank rank : Rank.values()) {
            assertNotNull(StoreMenu.ICONS.get(rank.key()), rank.key());
        }
    }
}
