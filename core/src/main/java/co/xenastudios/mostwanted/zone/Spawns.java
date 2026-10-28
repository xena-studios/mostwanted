package co.xenastudios.mostwanted.zone;

import java.util.Optional;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/** Where players are sent: the spawn point staff set on a zone with /mw zone spawn. */
public final class Spawns {

    private final ZoneStore zones;

    public Spawns(ZoneStore zones) {
        this.zones = zones;
    }

    /** The first zone of this kind, by name, that has a spawn point: the prison, the police station. */
    public Optional<Location> of(ZoneKind kind) {
        for (Zone zone : zones.all()) {
            if (zone.kind() == kind) {
                Optional<Location> at = of(zone);
                if (at.isPresent()) {
                    return at;
                }
            }
        }
        return Optional.empty();
    }

    /** Empty if it has no spawn point or its world isn't loaded. */
    public static Optional<Location> of(Zone zone) {
        Zone.Spawn spawn = zone.spawn();
        World world = Bukkit.getWorld(zone.world());
        if (spawn == null || world == null) {
            return Optional.empty();
        }
        return Optional.of(new Location(world, spawn.x(), spawn.y(), spawn.z(), spawn.yaw(), spawn.pitch()));
    }
}
