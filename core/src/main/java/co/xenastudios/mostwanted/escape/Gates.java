package co.xenastudios.mostwanted.escape;

import co.xenastudios.mostwanted.config.EscapeSettings;
import co.xenastudios.mostwanted.message.Messages;
import co.xenastudios.mostwanted.player.PlayerState;
import co.xenastudios.mostwanted.player.PlayerStore;
import co.xenastudios.mostwanted.player.Role;
import co.xenastudios.mostwanted.storage.GameLog;
import co.xenastudios.mostwanted.zone.Zone;
import co.xenastudios.mostwanted.zone.ZoneKind;
import co.xenastudios.mostwanted.zone.ZoneStore;
import java.io.File;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Openable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.BoundingBox;

/**
 * Gates: right-click one with a keycard (or as a guard) and it opens for a few seconds.
 * Doors, trapdoors and fence gates swing open; anything else (bars, blocks) is lifted
 * out and put back, never onto someone standing in the way.
 */
public final class Gates implements Listener {

    /** Gates are small; this stops a mis-marked one from emptying half the map. */
    static final int MAX_BLOCKS = 512;
    private static final Sound OPEN = Sound.sound(Key.key("minecraft:block.iron_door.open"), Sound.Source.BLOCK, 1f, 0.8f);
    private static final Sound CLOSE = Sound.sound(Key.key("minecraft:block.iron_door.close"), Sound.Source.BLOCK, 1f, 0.8f);

    /** An open gate: what each block was, and when it closes. */
    private record Open(Map<Location, BlockData> was, Instant closes) {}

    private final ZoneStore zones;
    private final PlayerStore players;
    private final Keycards keycards;
    private final Messages messages;
    private final GameLog gameLog;
    private final EscapeSettings settings;
    private final Clock clock;
    private final Map<String, Open> open = new HashMap<>();
    private final BlockBackup backup;

    public Gates(ZoneStore zones, PlayerStore players, Keycards keycards, Messages messages, GameLog gameLog,
            EscapeSettings settings, Clock clock, File saved, Logger log) {
        this.zones = zones;
        this.players = players;
        this.keycards = keycards;
        this.messages = messages;
        this.gameLog = gameLog;
        this.settings = settings;
        this.clock = clock;
        this.backup = new BlockBackup(saved, "gate", log);
    }

    /** At startup: closes any gate a crash left open. */
    public void restoreSaved() {
        backup.restore();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void click(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK
                || event.getClickedBlock() == null) {
            return;
        }
        Zone gate = gateAt(event.getClickedBlock());
        if (gate == null) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        PlayerState state = players.get(player.getUniqueId()).orElse(null);
        if (state == null || open.containsKey(gate.name())) {
            return;
        }
        boolean guard = state.role() == Role.GUARD;
        if (!guard && !keycards.is(player.getInventory().getItemInMainHand())) {
            messages.send(player, "escape.gate-locked");
            return;
        }
        if (state.jailed(clock.instant())) {
            return;
        }
        openGate(gate);
        if (!guard) {
            gameLog.record("escape.keycard", player.getUniqueId(), null, Map.of("gate", gate.name()));
            for (Player other : players.online(Role.GUARD)) {
                messages.send(other, "escape.keycard-alert", Messages.value("gate", gate.name()),
                        Messages.value("player", player.getName()));
            }
        }
    }

    private void openGate(Zone gate) {
        World world = Bukkit.getWorld(gate.world());
        if (world == null) {
            return;
        }
        Map<Location, BlockData> was = new HashMap<>();
        scan:
        for (int x = gate.minX(); x <= gate.maxX(); x++) {
            for (int y = gate.minY(); y <= gate.maxY(); y++) {
                for (int z = gate.minZ(); z <= gate.maxZ(); z++) {
                    if (was.size() >= MAX_BLOCKS) {
                        break scan;
                    }
                    Block block = world.getBlockAt(x, y, z);
                    if (block.getType().isAir()) {
                        continue;
                    }
                    was.put(block.getLocation(), block.getBlockData());
                    if (block.getBlockData() instanceof Openable openable) {
                        openable.setOpen(true);
                        block.setBlockData(openable, false);
                    } else {
                        block.setType(Material.AIR, false);
                    }
                }
            }
        }
        open.put(gate.name(), new Open(was, clock.instant().plus(settings.gateOpen())));
        // Now, not on the next tick: a gate is open for seconds, and a crash in them would leave a hole.
        backup.changed();
        backup.flush(this::was);
        playAt(world, gate, OPEN);
    }

    /** Closes gates that are due, waiting while anyone stands in the way. Runs every second. */
    public void tick() {
        Instant now = clock.instant();
        for (Iterator<Map.Entry<String, Open>> it = open.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, Open> entry = it.next();
            if (entry.getValue().closes.isAfter(now) || blocked(entry.getValue())) {
                continue;
            }
            close(entry.getValue());
            zones.get(entry.getKey()).ifPresent(gate -> {
                World world = Bukkit.getWorld(gate.world());
                if (world != null) {
                    playAt(world, gate, CLOSE);
                }
            });
            it.remove();
            backup.changed();
        }
        backup.flush(this::was);
    }

    private Map<Location, BlockData> was() {
        Map<Location, BlockData> was = new HashMap<>();
        open.values().forEach(gate -> was.putAll(gate.was));
        return was;
    }

    /** Shuts everything: on shutdown, so no gate is left open. */
    public void closeAll() {
        open.values().forEach(Gates::close);
        open.clear();
        backup.changed();
        backup.flush(Map::of);
    }

    private static void close(Open gate) {
        gate.was.forEach((at, data) -> at.getBlock().setBlockData(data, false));
    }

    private static boolean blocked(Open gate) {
        List<Location> lifted = new ArrayList<>();
        gate.was.forEach((at, data) -> {
            if (!(data instanceof Openable)) {
                lifted.add(at);
            }
        });
        for (Location at : lifted) {
            if (!at.getWorld().getNearbyEntities(BoundingBox.of(at.getBlock()), e -> e instanceof Player).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private Zone gateAt(Block block) {
        for (Zone zone : zones.index().at(block.getWorld().getName(), block.getX(), block.getY(), block.getZ())) {
            if (zone.kind() == ZoneKind.GATE) {
                return zone;
            }
        }
        return null;
    }

    private static void playAt(World world, Zone gate, Sound sound) {
        world.playSound(sound, (gate.minX() + gate.maxX()) / 2.0 + 0.5, (gate.minY() + gate.maxY()) / 2.0 + 0.5,
                (gate.minZ() + gate.maxZ()) / 2.0 + 0.5);
    }
}
