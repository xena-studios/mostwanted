package co.xenastudios.mostwanted.zone;

import java.util.regex.Pattern;

/**
 * A box of blocks, inclusive at both corners, with an optional spawn point inside or
 * near it. Immutable: changing a zone replaces it.
 */
public record Zone(String name, ZoneKind kind, String world, int minX, int minY, int minZ, int maxX, int maxY,
        int maxZ, Spawn spawn) {

    /** Names are typed in commands, so they stay short and simple. */
    public static final Pattern NAME = Pattern.compile("[a-z0-9_-]{1,32}");

    public Zone {
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            throw new IllegalArgumentException("min corner is above max corner");
        }
    }

    public record Spawn(double x, double y, double z, float yaw, float pitch) {}

    public boolean contains(String world, int x, int y, int z) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ
                && this.world.equals(world);
    }

    public Zone withSpawn(Spawn spawn) {
        return new Zone(name, kind, world, minX, minY, minZ, maxX, maxY, maxZ, spawn);
    }

    /** "12×8×30". */
    public String size() {
        return (maxX - minX + 1) + "×" + (maxY - minY + 1) + "×" + (maxZ - minZ + 1);
    }

    /** How many blocks the box holds. */
    public long volume() {
        return (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
    }
}
