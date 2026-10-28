package co.xenastudios.mostwanted.robbery;

import co.xenastudios.mostwanted.common.Stat;
import co.xenastudios.mostwanted.common.style.Money;
import co.xenastudios.mostwanted.config.RobberySettings;
import co.xenastudios.mostwanted.hud.Hud;
import co.xenastudios.mostwanted.message.Messages;
import co.xenastudios.mostwanted.player.PlayerState;
import co.xenastudios.mostwanted.player.PlayerStore;
import co.xenastudios.mostwanted.player.Role;
import co.xenastudios.mostwanted.storage.GameLog;
import co.xenastudios.mostwanted.zone.Zone;
import co.xenastudios.mostwanted.zone.ZoneKind;
import co.xenastudios.mostwanted.zone.ZoneStore;
import co.xenastudios.mostwanted.zone.ZoneTracker;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Every robbery zone is a place to rob. A criminal walking into an open one starts the
 * robbery and sets off the alarm for guards; the robbers must stay inside until the
 * time is up, watching a bar fill. If they all leave or are caught, it fails. Either
 * way it closes, then reopens with an announcement. Runs once a second, main thread.
 */
public final class Robberies {

    private static final Sound ALARM = Sound.sound(Key.key("minecraft:event.raid.horn"), Sound.Source.MASTER, 1f, 1f);

    private final ZoneStore zones;
    private final ZoneTracker tracker;
    private final PlayerStore players;
    private final Messages messages;
    private final GameLog gameLog;
    private final RobberySettings settings;
    private final Clock clock;
    private final Map<String, RobberySpot> spots = new HashMap<>();
    private final Map<String, BossBar> bars = new HashMap<>();
    /** Who sees each bar, so robbers who leave stop seeing it. */
    private final Map<String, Set<UUID>> watching = new HashMap<>();

    public Robberies(ZoneStore zones, ZoneTracker tracker, PlayerStore players, Messages messages, GameLog gameLog,
            RobberySettings settings, Clock clock) {
        this.zones = zones;
        this.tracker = tracker;
        this.players = players;
        this.messages = messages;
        this.gameLog = gameLog;
        this.settings = settings;
        this.clock = clock;
    }

    public void tick() {
        Instant now = clock.instant();
        Set<String> live = new HashSet<>();
        Map<String, List<Player>> robbing = robbers(now);
        for (Zone zone : zones.all()) {
            if (zone.kind() != ZoneKind.ROBBERY) {
                continue;
            }
            live.add(zone.name());
            RobberySpot spot = spots.computeIfAbsent(zone.name(), name -> new RobberySpot());
            RobberySettings.Spot config = settings.spot(zone.name());
            List<Player> robbers = robbing.getOrDefault(zone.name(), List.of());
            String place = settings.displayName(zone.name());
            switch (spot.step(now, !robbers.isEmpty(), config.time(), config.closed())) {
                case OPENED -> messages.send(Bukkit.getServer(), "robbery.open", Messages.value("place", place));
                case STARTED -> started(zone, place, robbers, config);
                case FAILED -> {
                    hideBar(zone.name());
                    gameLog.record("robbery.failed", null, null, Map.of("place", zone.name()));
                    messages.send(Bukkit.getServer(), "robbery.failed", Messages.value("place", place));
                }
                case FINISHED -> {
                    hideBar(zone.name());
                    finished(zone, place, robbers, config);
                }
                case NONE -> { }
            }
            if (spot.state() == RobberySpot.State.ROBBING) {
                showBar(zone.name(), place, spot, config, robbers);
            }
        }
        // Zones deleted mid-robbery: drop their spot and bar.
        spots.keySet().removeIf(name -> !live.contains(name));
        new ArrayList<>(bars.keySet()).stream().filter(name -> !live.contains(name)).forEach(this::hideBar);
    }

    /** The red HEIST section for everyone's scoreboard: the robbery closest to finishing, or nothing. */
    public List<Component> hudLines() {
        String closest = null;
        Duration least = null;
        for (Map.Entry<String, RobberySpot> entry : spots.entrySet()) {
            if (entry.getValue().state() == RobberySpot.State.ROBBING) {
                Duration left = entry.getValue().left(settings.spot(entry.getKey()).time());
                if (least == null || left.compareTo(least) < 0) {
                    closest = entry.getKey();
                    least = left;
                }
            }
        }
        if (closest == null) {
            return List.of();
        }
        return List.of(
                messages.get("hud.heist"),
                messages.get("hud.heist-place", Messages.value("place", settings.displayName(closest))),
                messages.get("hud.heist-time", Messages.value("time", Hud.clockTime(least))),
                messages.get("hud.heist-guards", Messages.value("count", players.count(Role.GUARD))));
    }

    private void started(Zone zone, String place, List<Player> robbers, RobberySettings.Spot config) {
        List<String> names = robbers.stream().map(Player::getName).toList();
        gameLog.record("robbery.started", robbers.getFirst().getUniqueId(), null,
                Map.of("place", zone.name(), "robbers", String.join(",", names)));
        for (Player robber : robbers) {
            messages.send(robber, "robbery.started", Messages.value("place", place),
                    Messages.value("time", Hud.clockTime(config.time())));
        }
        for (Player guard : players.online(Role.GUARD)) {
            messages.send(guard, "robbery.alarm", Messages.value("place", place));
            guard.playSound(ALARM, Sound.Emitter.self());
        }
    }

    private void finished(Zone zone, String place, List<Player> robbers, RobberySettings.Spot config) {
        long total = settings.payout(config, players.count(Role.GUARD));
        long share = total / robbers.size();
        for (Player robber : robbers) {
            PlayerState state = players.get(robber.getUniqueId()).orElseThrow();
            state.cash(state.cash() + share);
            state.bounty(state.bounty() + config.bounty());
            state.add(Stat.ROBBERIES, 1);
            state.add(Stat.CASH_STOLEN, share);
            messages.send(robber, "robbery.got-away", Messages.value("place", place),
                    Messages.value("amount", Money.format(share)), Messages.value("bounty", Money.format(config.bounty())));
        }
        gameLog.record("robbery.finished", robbers.getFirst().getUniqueId(), null, Map.of("place", zone.name(),
                "robbers", robbers.size(), "total", total, "share", share, "bounty_each", config.bounty()));
        messages.send(Bukkit.getServer(), "robbery.robbed", Messages.value("place", place));
    }

    /** Criminals not serving a sentence, by the robbery zone they're in: one pass over the players. */
    private Map<String, List<Player>> robbers(Instant now) {
        Map<String, List<Player>> robbers = new HashMap<>();
        for (Player player : players.online(Role.CRIMINAL)) {
            if (players.get(player.getUniqueId()).map(state -> state.jailed(now)).orElse(true)) {
                continue;
            }
            for (Zone in : tracker.zonesOf(player)) {
                if (in.kind() == ZoneKind.ROBBERY) {
                    robbers.computeIfAbsent(in.name(), name -> new ArrayList<>()).add(player);
                }
            }
        }
        return robbers;
    }


    private void showBar(String zone, String place, RobberySpot spot, RobberySettings.Spot config,
            List<Player> robbers) {
        Component name = messages.get("robbery.bar", Messages.value("place", place),
                Messages.value("time", Hud.clockTime(spot.left(config.time()))));
        BossBar bar = bars.computeIfAbsent(zone, key -> BossBar.bossBar(name, 0, BossBar.Color.RED,
                BossBar.Overlay.PROGRESS));
        bar.name(name);
        bar.progress(spot.done(config.time()));
        Set<UUID> now = new HashSet<>();
        for (Player robber : robbers) {
            now.add(robber.getUniqueId());
            robber.showBossBar(bar);
        }
        Set<UUID> before = watching.put(zone, now);
        if (before != null) {
            before.removeAll(now);
            before.forEach(uuid -> {
                Player gone = Bukkit.getPlayer(uuid);
                if (gone != null) {
                    gone.hideBossBar(bar);
                }
            });
        }
    }

    /** On shutdown: no robbery's bar is left on anyone's screen. */
    public void hideBars() {
        List.copyOf(bars.keySet()).forEach(this::hideBar);
    }

    private void hideBar(String zone) {
        BossBar bar = bars.remove(zone);
        Set<UUID> viewers = watching.remove(zone);
        if (bar != null && viewers != null) {
            viewers.forEach(uuid -> {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null) {
                    player.hideBossBar(bar);
                }
            });
        }
    }
}
