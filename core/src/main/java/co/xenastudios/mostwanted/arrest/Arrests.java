package co.xenastudios.mostwanted.arrest;

import co.xenastudios.mostwanted.common.Stat;
import co.xenastudios.mostwanted.common.style.Money;
import co.xenastudios.mostwanted.config.Rules;
import co.xenastudios.mostwanted.message.Messages;
import co.xenastudios.mostwanted.player.PlayerState;
import co.xenastudios.mostwanted.player.PlayerStore;
import co.xenastudios.mostwanted.player.Role;
import co.xenastudios.mostwanted.storage.GameLog;
import co.xenastudios.mostwanted.zone.Spawns;
import co.xenastudios.mostwanted.zone.Zone;
import co.xenastudios.mostwanted.zone.ZoneKind;
import co.xenastudios.mostwanted.zone.ZoneListener;
import co.xenastudios.mostwanted.zone.ZoneStore;
import co.xenastudios.mostwanted.zone.ZoneTracker;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.Plugin;

/**
 * Arresting criminals, and their time in a cell. An arrest takes their bounty (paid to
 * the guard) and their unbanked cash, makes them an inmate again, and locks them in
 * the emptiest cell until their sentence ends. Also sends everyone to the right place
 * when they respawn. Main thread only.
 */
public final class Arrests implements Listener, ZoneListener {

    /** How the arrest happened: cuffed pays the whole bounty, killed pays a share, died pays nobody. */
    public enum How { CUFFED, KILLED, DIED }

    private final Plugin plugin;
    private final PlayerStore players;
    private final ZoneStore zones;
    private final ZoneTracker tracker;
    private final Spawns spawns;
    private final Messages messages;
    private final GameLog gameLog;
    private final Rules rules;
    private final Clock clock;
    private final Logger log;
    /** Each prisoner's cell, while they serve their sentence. */
    private final Map<UUID, String> cells = new HashMap<>();
    private boolean warnedNoCells;
    /** Told about each arrest, after the state changes: Guns confiscates. */
    private final List<Consumer<Player>> onArrest = new ArrayList<>();

    public Arrests(Plugin plugin, PlayerStore players, ZoneStore zones, ZoneTracker tracker, Spawns spawns,
            Messages messages, GameLog gameLog, Rules rules, Clock clock) {
        this.plugin = plugin;
        this.players = players;
        this.zones = zones;
        this.tracker = tracker;
        this.spawns = spawns;
        this.messages = messages;
        this.gameLog = gameLog;
        this.rules = rules;
        this.clock = clock;
        this.log = plugin.getLogger();
    }

    public void onArrest(Consumer<Player> hook) {
        onArrest.add(hook);
    }

    /**
     * Arrests a criminal. {@code guard} is null when nobody gets the credit (they died
     * some other way). A dead criminal goes to their cell when they respawn.
     */
    public void arrest(Player criminal, PlayerState prisoner, Player guard, PlayerState officer, How how) {
        long bounty = prisoner.bounty();
        long cashLost = prisoner.cash();
        long payout = officer == null ? 0 : rules.payout(bounty, how == How.KILLED);
        Duration sentence = rules.sentence(bounty);

        prisoner.cash(0);
        prisoner.bounty(0);
        prisoner.role(Role.INMATE);
        prisoner.jailedUntil(clock.instant().plus(sentence));
        prisoner.add(Stat.TIMES_ARRESTED, 1);
        if (officer != null) {
            officer.cash(officer.cash() + payout);
            officer.add(Stat.ARRESTS, 1);
            officer.add(Stat.BOUNTY_EARNED, payout);
        }

        gameLog.record("arrest", officer == null ? null : officer.uuid(), prisoner.uuid(), Map.of(
                "how", how.name().toLowerCase(java.util.Locale.ROOT), "bounty", bounty, "payout", payout,
                "cash_lost", cashLost, "sentence_seconds", sentence.toSeconds()));

        String time = minutes(sentence);
        if (guard == null) {
            messages.send(criminal, "arrest.you-died", Messages.value("time", time),
                    Messages.value("cash", Money.format(cashLost)));
            messages.send(Bukkit.getServer(), "arrest.everyone-died", Messages.value("player", criminal.getName()));
        } else {
            messages.send(criminal, "arrest.you", Messages.value("officer", guard.getName()),
                    Messages.value("time", time), Messages.value("cash", Money.format(cashLost)));
            messages.send(guard, how == How.KILLED ? "arrest.guard-killed" : "arrest.guard",
                    Messages.value("player", criminal.getName()), Messages.value("amount", Money.format(payout)));
            messages.send(Bukkit.getServer(), "arrest.everyone", Messages.value("player", criminal.getName()),
                    Messages.value("officer", guard.getName()));
        }
        onArrest.forEach(hook -> hook.accept(criminal));
        if (!criminal.isDead()) {
            cell(criminal).ifPresent(criminal::teleportAsync);
        }
    }

    /** Lets out everyone whose sentence is over. Runs every second. */
    public void tick() {
        Instant now = clock.instant();
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerState state = players.get(player.getUniqueId()).orElse(null);
            if (state != null && state.jailedUntil() != null && !state.jailed(now)) {
                release(player, state);
            }
        }
    }

    private void release(Player player, PlayerState state) {
        state.jailedUntil(null);
        cells.remove(player.getUniqueId());
        messages.send(player, "arrest.released");
        // No prison spawn: they stay where they are, with the cell no longer holding them.
        spawns.of(ZoneKind.PRISON).ifPresent(player::teleportAsync);
    }

    /** A prisoner who gets out of their cell is put straight back. */
    @Override
    public void left(Player player, PlayerState state, Zone zone) {
        if (zone.kind() != ZoneKind.CELL || !state.jailed(clock.instant()) || tracker.isIn(player, ZoneKind.CELL)) {
            return;
        }
        // Next tick: this runs inside the move or teleport that took them out.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && !player.isDead() && state.jailed(clock.instant())) {
                cell(player).ifPresent(player::teleportAsync);
                messages.send(player, "arrest.stay");
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void respawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        PlayerState state = players.get(player.getUniqueId()).orElse(null);
        if (state == null) {
            return;
        }
        Optional<Location> to;
        if (state.jailed(clock.instant())) {
            to = cell(player);
        } else {
            to = switch (state.role()) {
                case GUARD -> spawns.of(ZoneKind.POLICE);
                case INMATE -> spawns.of(ZoneKind.PRISON);
                // Can't happen: a criminal's death is an arrest.
                case CRIMINAL -> Optional.empty();
            };
        }
        to.ifPresent(event::setRespawnLocation);
    }

    /** Back in their cell if they logged off mid-sentence. Expired sentences end on the next tick. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void join(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        players.get(player.getUniqueId())
                .filter(state -> state.jailed(clock.instant()))
                .flatMap(state -> cell(player))
                .ifPresent(player::teleportAsync);
    }

    @EventHandler
    public void quit(PlayerQuitEvent event) {
        cells.remove(event.getPlayer().getUniqueId());
    }

    /**
     * Their cell's spawn, picking the emptiest cell if they don't have one. Falls back to
     * the prison's spawn when no cell has a spawn point set.
     */
    private Optional<Location> cell(Player player) {
        String name = cells.get(player.getUniqueId());
        Zone cell = name == null ? null : zones.get(name).filter(zone -> zone.spawn() != null).orElse(null);
        if (cell == null) {
            cell = emptiestCell();
            if (cell == null) {
                if (!warnedNoCells) {
                    warnedNoCells = true;
                    log.warning("No cell zone has a spawn point: arrested players go to the prison's spawn. "
                            + "Mark cells with /mw zone create <name> cell, then /mw zone spawn <name>.");
                }
                return spawns.of(ZoneKind.PRISON);
            }
            cells.put(player.getUniqueId(), cell.name());
        }
        return Spawns.of(cell);
    }

    private Zone emptiestCell() {
        Map<String, Integer> taken = new HashMap<>();
        cells.values().forEach(name -> taken.merge(name, 1, Integer::sum));
        Zone best = null;
        int fewest = Integer.MAX_VALUE;
        for (Zone zone : zones.all()) {
            if (zone.kind() == ZoneKind.CELL && zone.spawn() != null) {
                int count = taken.getOrDefault(zone.name(), 0);
                if (count < fewest) {
                    best = zone;
                    fewest = count;
                }
            }
        }
        return best;
    }

    /** "3 minutes", "1 minute", "90 seconds". */
    static String minutes(Duration duration) {
        long seconds = duration.toSeconds();
        if (seconds % 60 != 0) {
            return seconds + " seconds";
        }
        long minutes = seconds / 60;
        return minutes == 1 ? "1 minute" : minutes + " minutes";
    }
}
