package co.xenastudios.mostwanted.zone;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Finds the zones at a block by looking only at the zones touching its chunk.
 * Immutable: built again whenever a zone changes, which is rare.
 */
public final class ZoneIndex {

    public static final ZoneIndex EMPTY = new ZoneIndex(List.of());

    /** Per world, chunk key to the zones overlapping that chunk. */
    private final Map<String, Map<Long, List<Zone>>> byChunk = new HashMap<>();

    public ZoneIndex(Collection<Zone> zones) {
        for (Zone zone : zones) {
            Map<Long, List<Zone>> chunks = byChunk.computeIfAbsent(zone.world(), world -> new HashMap<>());
            for (int cx = zone.minX() >> 4; cx <= zone.maxX() >> 4; cx++) {
                for (int cz = zone.minZ() >> 4; cz <= zone.maxZ() >> 4; cz++) {
                    chunks.computeIfAbsent(key(cx, cz), key -> new ArrayList<>(2)).add(zone);
                }
            }
        }
    }

    /** The zones containing this block, in no particular order. */
    public List<Zone> at(String world, int x, int y, int z) {
        Map<Long, List<Zone>> chunks = byChunk.get(world);
        List<Zone> nearby = chunks == null ? null : chunks.get(key(x >> 4, z >> 4));
        if (nearby == null) {
            return List.of();
        }
        List<Zone> found = null;
        for (Zone zone : nearby) {
            if (zone.contains(world, x, y, z)) {
                if (found == null) {
                    found = new ArrayList<>(2);
                }
                found.add(zone);
            }
        }
        return found == null ? List.of() : found;
    }

    /** Whether a zone of this kind overlaps the chunks within {@code reach} blocks of x, z: a cheap first check. */
    public boolean near(String world, int x, int z, int reach, ZoneKind kind) {
        Map<Long, List<Zone>> chunks = byChunk.get(world);
        if (chunks == null) {
            return false;
        }
        for (int cx = (x - reach) >> 4; cx <= (x + reach) >> 4; cx++) {
            for (int cz = (z - reach) >> 4; cz <= (z + reach) >> 4; cz++) {
                for (Zone zone : chunks.getOrDefault(key(cx, cz), List.of())) {
                    if (zone.kind() == kind) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static long key(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }
}
