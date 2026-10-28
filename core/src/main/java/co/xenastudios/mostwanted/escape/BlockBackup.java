package co.xenastudios.mostwanted.escape;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.function.Supplier;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Blocks taken out of the map for a while (broken walls, open gates), kept in a file
 * so a crash can't leave them out: they're put back at the next start. Written at
 * most once a second, from the owner's tick, rather than on every change.
 */
final class BlockBackup {

    private final File file;
    private final String what;
    private final Logger log;
    private boolean dirty;

    BlockBackup(File file, String what, Logger log) {
        this.file = file;
        this.what = what;
        this.log = log;
    }

    void changed() {
        dirty = true;
    }

    /** Writes {@code blocks} (what each one was) if anything changed since the last write. */
    void flush(Supplier<Map<Location, BlockData>> what) {
        if (!dirty) {
            return;
        }
        dirty = false;
        Map<Location, BlockData> blocks = what.get();
        if (blocks.isEmpty()) {
            file.delete();
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("blocks", blocks.entrySet().stream().map(e -> Map.of(
                "world", e.getKey().getWorld().getName(), "x", e.getKey().getBlockX(), "y", e.getKey().getBlockY(),
                "z", e.getKey().getBlockZ(), "block", e.getValue().getAsString())).toList());
        try {
            yaml.save(file);
        } catch (IOException e) {
            log.warning("Couldn't save " + file.getName() + ": " + e.getMessage());
        }
    }

    /** At startup: puts back whatever the last run left out, then forgets it. */
    void restore() {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        int restored = 0;
        // "broken" is what broken-walls.yml called it before gates were kept too.
        for (Map<?, ?> entry : yaml.contains("blocks") ? yaml.getMapList("blocks") : yaml.getMapList("broken")) {
            World world = Bukkit.getWorld(String.valueOf(entry.get("world")));
            if (world == null) {
                continue;
            }
            world.getBlockAt(((Number) entry.get("x")).intValue(), ((Number) entry.get("y")).intValue(),
                    ((Number) entry.get("z")).intValue())
                    .setBlockData(Bukkit.createBlockData(String.valueOf(entry.get("block"))), false);
            restored++;
        }
        if (restored > 0) {
            log.info("Put back " + restored + " " + what + " blocks the last shutdown left out");
        }
        file.delete();
    }
}
