package co.xenastudios.mostwanted.player;

import co.xenastudios.mostwanted.hud.Hud;
import co.xenastudios.mostwanted.message.Messages;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Loads players as they log in, and saves them as they leave. */
public final class PlayerListener implements Listener {

    /** How long a loaded player has to finish joining before their state is dropped. */
    private static final long JOIN_TIMEOUT_MILLIS = 60_000;

    private final PlayerStore store;
    private final Messages messages;
    private final Hud hud;
    private final Logger log;
    /** Loaded but not joined yet, with when they were loaded. */
    private final Map<UUID, Long> joining = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> firstTime = new ConcurrentHashMap<>();

    public PlayerListener(PlayerStore store, Messages messages, Hud hud, Logger log) {
        this.store = store;
        this.messages = messages;
        this.hud = hud;
        this.log = log;
    }

    /** On Paper's login thread: the load can take a moment without holding up the game. */
    @EventHandler(priority = EventPriority.LOW)
    public void load(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }
        UUID uuid = event.getUniqueId();
        if (joining.remove(uuid) != null) {
            // Loaded before but never got in: refused after this event (the whitelist,
            // a full server) or dropped mid-login. Without this they'd count as still
            // connected until the sweep.
            firstTime.remove(uuid);
            store.forget(uuid);
        }
        try {
            switch (store.load(uuid, event.getName())) {
                case ALREADY_ONLINE -> event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                        messages.get("login.already-online"));
                case FIRST_TIME -> loaded(uuid, true);
                case RETURNING -> loaded(uuid, false);
            }
        } catch (RuntimeException e) {
            log.log(Level.WARNING, "Couldn't load " + event.getName() + "; refused them", e);
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, messages.get("login.failed"));
        }
    }

    private void loaded(UUID uuid, boolean first) {
        joining.put(uuid, System.currentTimeMillis());
        firstTime.put(uuid, first);
    }

    /** Another plugin (a ban, a full server) refused them after we loaded them. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void refused(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED
                && joining.remove(event.getUniqueId()) != null) {
            firstTime.remove(event.getUniqueId());
            store.forget(event.getUniqueId());
        }
    }

    @EventHandler
    public void join(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        joining.remove(player.getUniqueId());
        boolean first = Boolean.TRUE.equals(firstTime.remove(player.getUniqueId()));
        if (store.get(player.getUniqueId()).isEmpty()) {
            // Can't happen unless the join outlasted JOIN_TIMEOUT_MILLIS; safer to send them back.
            player.kick(messages.get("login.failed"));
            return;
        }
        messages.send(player, first ? "join.first" : "join.welcome", Messages.value("player", player.getName()));
        hud.show(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void quit(PlayerQuitEvent event) {
        hud.hide(event.getPlayer());
        store.quit(event.getPlayer().getUniqueId());
    }

    /** Drops players who were loaded but never arrived, such as a connection that dropped mid-login. */
    public void sweep() {
        long cutoff = System.currentTimeMillis() - JOIN_TIMEOUT_MILLIS;
        joining.entrySet().removeIf(entry -> {
            if (entry.getValue() > cutoff || Bukkit.getPlayer(entry.getKey()) != null) {
                return false;
            }
            firstTime.remove(entry.getKey());
            store.forget(entry.getKey());
            return true;
        });
    }
}
