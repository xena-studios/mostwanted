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
import io.papermc.paper.event.player.PlayerArmSwingEvent;
import java.io.File;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.logging.Logger;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.BoundingBox;

/**
 * Weak walls: blocks in a wall zone break after enough punches in a row, from an inmate
 * inside or a criminal breaking in, and grow back later. Punches are arm swings, so
 * this works in adventure mode and the same on Bedrock (tap). Broken blocks are written
 * to a file, so a crash can't leave a hole in the prison for good.
 */
public final class Walls implements Listener {

    private static final Sound CRACK = Sound.sound(Key.key("minecraft:block.stone.hit"), Sound.Source.BLOCK, 1f, 0.8f);
    private static final Sound BREAK = Sound.sound(Key.key("minecraft:block.stone.break"), Sound.Source.BLOCK, 1.5f, 0.7f);

    /** A block being punched: how many times, and when last. */
    private record Damage(int hits, Instant last) {}

    /** A broken block: what it was, and when it grows back. */
    private record Broken(BlockData was, Instant regrows) {}

    private final ZoneStore zones;
    private final PlayerStore players;
    private final Messages messages;
    private final GameLog gameLog;
    private final EscapeSettings settings;
    private final Clock clock;
    private final BlockBackup backup;
    private final Map<Location, Damage> damage = new HashMap<>();
    private final Map<Location, Broken> broken = new HashMap<>();
    /** Alerts once per wall until it has fully grown back. */
    private final Map<String, Instant> alerted = new HashMap<>();

    public Walls(ZoneStore zones, PlayerStore players, Messages messages, GameLog gameLog, EscapeSettings settings,
            Clock clock, File saved, Logger log) {
        this.zones = zones;
        this.players = players;
        this.messages = messages;
        this.gameLog = gameLog;
        this.settings = settings;
        this.clock = clock;
        this.backup = new BlockBackup(saved, "wall", log);
    }

    @EventHandler(ignoreCancelled = true)
    public void punch(PlayerArmSwingEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        PlayerState state = players.get(player.getUniqueId()).orElse(null);
        Instant now = clock.instant();
        if (state == null || state.jailed(now) || state.role() == Role.GUARD) {
            return;
        }
        // Most swings are nowhere near a wall: skip the ray trace for those.
        Location feet = player.getLocation();
        if (!zones.index().near(feet.getWorld().getName(), feet.getBlockX(), feet.getBlockZ(), 5, ZoneKind.WALL)) {
            return;
        }
        Block block = player.getTargetBlockExact(4);
        if (block == null || block.getType().isAir()) {
            return;
        }
        Zone wall = wallAt(block);
        if (wall == null) {
            return;
        }
        Location at = block.getLocation();
        Damage before = damage.get(at);
        int hits = before == null ? 1 : hits(before.hits, before.last, now, settings.hitGap());
        if (hits < settings.wallHits()) {
            damage.put(at, new Damage(hits, now));
            float progress = (float) hits / settings.wallHits();
            for (Player viewer : block.getWorld().getPlayers()) {
                viewer.sendBlockDamage(at, progress, at.hashCode());
            }
            block.getWorld().playSound(CRACK, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5);
            return;
        }
        damage.remove(at);
        breakBlock(block, now);
        alert(player, wall, now);
    }

    private void breakBlock(Block block, Instant now) {
        Location at = block.getLocation();
        broken.put(at, new Broken(block.getBlockData(), now.plus(settings.wallRegrow())));
        block.getWorld().spawnParticle(Particle.BLOCK, at.clone().add(0.5, 0.5, 0.5), 30, 0.3, 0.3, 0.3,
                block.getBlockData());
        block.getWorld().playSound(BREAK, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5);
        for (Player viewer : block.getWorld().getPlayers()) {
            viewer.sendBlockDamage(at, 0f, at.hashCode());
        }
        // No physics: nothing around it should fall or pop off.
        block.setType(Material.AIR, false);
        save();
    }

    private void alert(Player breaker, Zone wall, Instant now) {
        Instant last = alerted.get(wall.name());
        if (last != null && last.plus(settings.wallRegrow()).isAfter(now)) {
            return;
        }
        alerted.put(wall.name(), now);
        gameLog.record("escape.wall", breaker.getUniqueId(), null, Map.of("wall", wall.name()));
        for (Player guard : players.online(Role.GUARD)) {
            messages.send(guard, "escape.wall-alert", Messages.value("wall", wall.name()));
        }
    }

    /** Grows back what's due, unless someone is standing in it. Runs every second. */
    public void tick() {
        Instant now = clock.instant();
        boolean changed = false;
        for (Iterator<Map.Entry<Location, Broken>> it = broken.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Location, Broken> entry = it.next();
            Location at = entry.getKey();
            if (entry.getValue().regrows.isAfter(now) || occupied(at)) {
                continue;
            }
            at.getBlock().setBlockData(entry.getValue().was, false);
            it.remove();
            changed = true;
        }
        damage.entrySet().removeIf(entry -> entry.getValue().last.plus(settings.hitGap()).isBefore(now));
        if (changed) {
            save();
        }
        backup.flush(this::was);
    }

    /** Puts every broken block back now: on shutdown, so the map is never left with holes. */
    public void restoreAll() {
        broken.forEach((at, was) -> at.getBlock().setBlockData(was.was, false));
        broken.clear();
        save();
        backup.flush(Map::of);
    }

    /** At startup: puts back anything a crash left broken. */
    public void restoreSaved() {
        backup.restore();
    }

    private void save() {
        backup.changed();
    }

    private Map<Location, BlockData> was() {
        Map<Location, BlockData> was = new HashMap<>();
        broken.forEach((at, each) -> was.put(at, each.was));
        return was;
    }

    /** The count after a punch: one more, or back to one if they paused too long. */
    static int hits(int before, Instant last, Instant now, Duration gap) {
        return last.plus(gap).isBefore(now) ? 1 : before + 1;
    }

    private Zone wallAt(Block block) {
        for (Zone zone : zones.index().at(block.getWorld().getName(), block.getX(), block.getY(), block.getZ())) {
            if (zone.kind() == ZoneKind.WALL) {
                return zone;
            }
        }
        return null;
    }

    private static boolean occupied(Location at) {
        BoundingBox box = BoundingBox.of(at.getBlock());
        return !at.getWorld().getNearbyEntities(box, entity -> entity instanceof Player).isEmpty();
    }
}
