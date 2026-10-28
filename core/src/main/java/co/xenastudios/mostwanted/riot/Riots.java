package co.xenastudios.mostwanted.riot;

import co.xenastudios.mostwanted.combat.Combat;
import co.xenastudios.mostwanted.common.Stat;
import co.xenastudios.mostwanted.config.RiotSettings;
import co.xenastudios.mostwanted.hud.Hud;
import co.xenastudios.mostwanted.message.Messages;
import co.xenastudios.mostwanted.player.PlayerState;
import co.xenastudios.mostwanted.player.PlayerStore;
import co.xenastudios.mostwanted.player.Role;
import co.xenastudios.mostwanted.storage.GameLog;
import co.xenastudios.mostwanted.zone.ZoneKind;
import co.xenastudios.mostwanted.zone.ZoneTracker;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Riots (docs/02-gameplay.md#riots): enough inmates pressing the alarm together start
 * one. While it's on, guards and inmates fight inside the prison and guards can cuff
 * inmates there. Everyone's told; it ends on time, or once every rioter is caught.
 */
public final class Riots implements Listener {

    private static final Sound HORN = Sound.sound(Key.key("minecraft:event.raid.horn"), Sound.Source.MASTER, 1f, 0.8f);

    private final RiotState state = new RiotState();
    private final RiotSettings settings;
    private final PlayerStore players;
    private final ZoneTracker tracker;
    private final Messages messages;
    private final GameLog gameLog;
    private final Clock clock;

    public Riots(RiotSettings settings, PlayerStore players, ZoneTracker tracker, Messages messages, GameLog gameLog,
            Clock clock) {
        this.settings = settings;
        this.players = players;
        this.tracker = tracker;
        this.messages = messages;
        this.gameLog = gameLog;
        this.clock = clock;
    }

    public boolean active() {
        return state.active();
    }

    /** Right-clicking anything while standing in the alarm zone presses it. */
    @EventHandler(priority = EventPriority.HIGH)
    public void press(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        if (!tracker.isIn(player, ZoneKind.ALARM)) {
            return;
        }
        PlayerState inmate = players.get(player.getUniqueId()).orElse(null);
        Instant now = clock.instant();
        if (inmate == null || inmate.role() != Role.INMATE || inmate.jailed(now)) {
            return;
        }
        event.setCancelled(true);
        switch (state.press(player.getUniqueId(), now, settings.inmates(), settings.window(), settings.length(),
                guardsOnDuty() >= settings.minGuards())) {
            case COUNTED -> messages.send(player, "riot.pressed", Messages.value("needed", settings.inmates()));
            case STARTED -> started();
            case COOLDOWN -> messages.send(player, "riot.cooldown");
            case NO_GUARDS -> messages.send(player, "riot.no-guards");
            case ALREADY -> { }
        }
    }

    private void started() {
        List<UUID> rioters = List.copyOf(state.rioters());
        for (UUID uuid : rioters) {
            players.get(uuid).ifPresent(rioter -> rioter.add(Stat.RIOTS, 1));
        }
        gameLog.record("riot.start", null, null, Map.of("rioters", rioters.size()));
        messages.send(Bukkit.getServer(), "riot.started");
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (players.get(player.getUniqueId()).map(s -> s.role() == Role.GUARD).orElse(false)) {
                messages.send(player, "riot.alarm");
                player.playSound(HORN, Sound.Emitter.self());
            }
        }
    }

    /** An inmate who hits a guard during the riot is in it too. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void joinIn(EntityDamageByEntityEvent event) {
        if (!state.active() || !(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = Combat.attacker(event.getDamager());
        if (attacker != null && role(attacker) == Role.INMATE && role(victim) == Role.GUARD
                && !state.rioters().contains(attacker.getUniqueId())) {
            state.join(attacker.getUniqueId());
            players.get(attacker.getUniqueId()).ifPresent(rioter -> rioter.add(Stat.RIOTS, 1));
        }
    }

    /** Once a second: ends it on time, or when every rioter is caught or gone. */
    public void tick() {
        Instant now = clock.instant();
        switch (state.tick(now, uuid -> {
            Player player = Bukkit.getPlayer(uuid);
            PlayerState rioter = player == null ? null : players.get(uuid).orElse(null);
            return rioter != null && rioter.role() == Role.INMATE && !rioter.jailed(now);
        }, settings.cooldown())) {
            case TIME_UP -> {
                gameLog.record("riot.end", null, null, Map.of("how", "time"));
                messages.send(Bukkit.getServer(), "riot.over");
            }
            case QUELLED -> {
                gameLog.record("riot.end", null, null, Map.of("how", "quelled"));
                messages.send(Bukkit.getServer(), "riot.quelled");
            }
            case NONE -> { }
        }
    }

    /** The red RIOT section for everyone's scoreboard while one is on. */
    public List<Component> hudLines() {
        if (!state.active()) {
            return List.of();
        }
        return List.of(
                messages.get("hud.riot"),
                messages.get("hud.riot-rioters", Messages.value("count", state.rioters().size())),
                messages.get("hud.heist-time", Messages.value("time", Hud.clockTime(state.left(clock.instant())))),
                messages.get("hud.heist-guards", Messages.value("count", guardsOnDuty())));
    }

    private Role role(Player player) {
        return players.get(player.getUniqueId()).map(PlayerState::role).orElse(null);
    }

    private int guardsOnDuty() {
        int guards = 0;
        for (PlayerState player : players.all()) {
            if (player.role() == Role.GUARD) {
                guards++;
            }
        }
        return guards;
    }
}
