package co.xenastudios.mostwanted.zone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ZoneIndexTest {

    static Zone zone(String name, ZoneKind kind, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        return new Zone(name, kind, "world", minX, minY, minZ, maxX, maxY, maxZ, null);
    }

    private static Set<String> names(List<Zone> zones) {
        return zones.stream().map(Zone::name).collect(Collectors.toSet());
    }

    private final Zone prison = zone("prison", ZoneKind.PRISON, -40, 0, -40, 40, 120, 40);
    private final Zone cell = zone("cell-1", ZoneKind.CELL, 10, 64, 10, 12, 67, 12);
    private final Zone bank = zone("bank", ZoneKind.ROBBERY, 500, 60, -200, 530, 80, -180);
    private final ZoneIndex index = new ZoneIndex(List.of(prison, cell, bank));

    @Test
    void findsEveryZoneContainingABlock() {
        assertEquals(Set.of("prison", "cell-1"), names(index.at("world", 11, 65, 11)));
        assertEquals(Set.of("prison"), names(index.at("world", 0, 64, 0)));
        assertEquals(Set.of("bank"), names(index.at("world", 515, 70, -190)));
    }

    @Test
    void cornersAreInsideAndOneBlockPastIsNot() {
        assertEquals(Set.of("prison"), names(index.at("world", 40, 120, 40)));
        assertEquals(Set.of("prison"), names(index.at("world", -40, 0, -40)));
        assertTrue(index.at("world", 41, 64, 0).isEmpty());
        assertTrue(index.at("world", -41, 64, 0).isEmpty());
        assertTrue(index.at("world", 0, 121, 0).isEmpty());
    }

    @Test
    void negativeChunksAreIndexedCorrectly() {
        // -1 >> 4 is chunk -1, not 0: a naive divide would miss these.
        assertEquals(Set.of("prison"), names(index.at("world", -1, 64, -1)));
        assertEquals(Set.of("prison"), names(index.at("world", -33, 64, -17)));
        assertEquals(Set.of("bank"), names(index.at("world", 500, 60, -200)));
    }

    @Test
    void otherWorldsAreSeparate() {
        assertTrue(index.at("world_nether", 0, 64, 0).isEmpty());
    }

    @Test
    void emptyIndexFindsNothing() {
        assertTrue(ZoneIndex.EMPTY.at("world", 0, 64, 0).isEmpty());
    }

    @Test
    void cornersMustBeInOrder() {
        assertThrows(IllegalArgumentException.class, () -> zone("bad", ZoneKind.SAFE, 5, 0, 0, 4, 0, 0));
    }

    @Test
    void namesAreShortAndSimple() {
        assertTrue(Zone.NAME.matcher("cell-1").matches());
        assertTrue(Zone.NAME.matcher("bank_vault").matches());
        assertTrue(!Zone.NAME.matcher("Bank").matches());
        assertTrue(!Zone.NAME.matcher("a b").matches());
        assertTrue(!Zone.NAME.matcher("x".repeat(33)).matches());
    }

    @Test
    void sizeCountsBothCorners() {
        assertEquals("3×4×3", cell.size());
    }
}
