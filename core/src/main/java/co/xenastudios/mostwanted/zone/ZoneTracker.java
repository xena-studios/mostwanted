package co.xenastudios.mostwanted.zone;

import co.xenastudios.mostwanted.player.PlayerState;
import co.xenastudios.mostwanted.player.PlayerStore;
import com.destroystokyo.paper.event.player.PlayerPostRespawnEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 * Which zones each player is in, kept up to date as they move. Movement is the
 * hottest event, so it returns straight away unless the player changed block, and
 * then only checks the zones touching their chunk.
 */
public final class ZoneTracker implements Listener {

    private final ZoneStore zones;
    private final PlayerStore players;
    private final List<ZoneListener> listeners = new ArrayList<>();
    /** Main thread only. */
    private final Map<UUID, List<Zone>> inside = new HashMap<>();

    public ZoneTracker(ZoneStore zones, PlayerStore players) {
        this.zones = zones;
        this.players = players;
    }

    public void listen(ZoneListener listener) {
        listeners.add(listener);
    }

    /** The zones a player is in right now. */
    public List<Zone> zonesOf(Player player) {
        return inside.getOrDefault(player.getUniqueId(), List.of());
    }

    public boolean isIn(Player player, ZoneKind kind) {
        for (Zone zone : zonesOf(player)) {
            if (zone.kind() == kind) {
                return true;
            }
        }
        return false;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void move(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getBlockX() == to.getBlockX() && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) {
            return;
        }
        update(event.getPlayer(), to);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void teleport(PlayerTeleportEvent event) {
        update(event.getPlayer(), event.getTo());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void join(PlayerJoinEvent event) {
        update(event.getPlayer(), event.getPlayer().getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void respawn(PlayerPostRespawnEvent event) {
        update(event.getPlayer(), event.getRespawnedLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void quit(PlayerQuitEvent event) {
        inside.remove(event.getPlayer().getUniqueId());
    }

    /**
     * After zones change: everyone's zones are worked out again where they stand,
     * without telling the listeners. Deleting the prison mustn't count as everyone escaping.
     */
    public void refreshAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            update(player, player.getLocation(), false);
        }
    }

    private void update(Player player, Location at) {
        update(player, at, true);
    }

    private void update(Player player, Location at, boolean notify) {
        PlayerState state = players.get(player.getUniqueId()).orElse(null);
        if (state == null) {
            return;
        }
        List<Zone> now = zones.index().at(at.getWorld().getName(), at.getBlockX(), at.getBlockY(), at.getBlockZ());
        List<Zone> before = inside.getOrDefault(player.getUniqueId(), List.of());
        if (now.isEmpty() && before.isEmpty()) {
            return;
        }
        if (now.isEmpty()) {
            inside.remove(player.getUniqueId());
        } else {
            inside.put(player.getUniqueId(), now);
        }
        if (!notify) {
            return;
        }
        // By name: a zone that was edited is a new object but the same place.
        for (Zone zone : before) {
            if (!containsName(now, zone.name())) {
                for (ZoneListener listener : listeners) {
                    listener.left(player, state, zone);
                }
            }
        }
        for (Zone zone : now) {
            if (!containsName(before, zone.name())) {
                for (ZoneListener listener : listeners) {
                    listener.entered(player, state, zone);
                }
            }
        }
    }

    private static boolean containsName(List<Zone> zones, String name) {
        for (Zone zone : zones) {
            if (zone.name().equals(name)) {
                return true;
            }
        }
        return false;
    }
}
