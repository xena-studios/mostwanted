package co.xenastudios.mostwanted.zone;

import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.world.World;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * A player's FAWE selection, as a box. The only class that touches WorldEdit's API,
 * and it's only loaded once {@link #available} says FAWE is there.
 */
record Selection(String world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

    static boolean available() {
        return Bukkit.getPluginManager().isPluginEnabled("FastAsyncWorldEdit")
                || Bukkit.getPluginManager().isPluginEnabled("WorldEdit");
    }

    /** Null if they haven't selected both corners. Any shape becomes the box around it. */
    static Selection of(Player player) {
        LocalSession session = WorldEdit.getInstance().getSessionManager().get(BukkitAdapter.adapt(player));
        World world = session.getSelectionWorld();
        if (world == null) {
            return null;
        }
        Region region;
        try {
            region = session.getSelection(world);
        } catch (IncompleteRegionException e) {
            return null;
        }
        BlockVector3 min = region.getMinimumPoint();
        BlockVector3 max = region.getMaximumPoint();
        return new Selection(BukkitAdapter.adapt(world).getName(), min.x(), min.y(), min.z(), max.x(), max.y(),
                max.z());
    }

    Zone toZone(String name, ZoneKind kind, Zone.Spawn spawn) {
        return new Zone(name, kind, world, minX, minY, minZ, maxX, maxY, maxZ, spawn);
    }
}
