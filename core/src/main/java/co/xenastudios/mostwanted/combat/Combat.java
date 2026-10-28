package co.xenastudios.mostwanted.combat;

import co.xenastudios.mostwanted.arrest.Arrests;
import co.xenastudios.mostwanted.common.style.Money;
import co.xenastudios.mostwanted.config.Rules;
import co.xenastudios.mostwanted.item.Handcuffs;
import co.xenastudios.mostwanted.message.Messages;
import co.xenastudios.mostwanted.player.PlayerState;
import co.xenastudios.mostwanted.player.PlayerStore;
import co.xenastudios.mostwanted.player.Role;
import co.xenastudios.mostwanted.player.Stat;
import co.xenastudios.mostwanted.storage.GameLog;
import co.xenastudios.mostwanted.zone.Zone;
import co.xenastudios.mostwanted.zone.ZoneKind;
import co.xenastudios.mostwanted.zone.ZoneTracker;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Applies {@link CombatRules} to every hit between players, and turns deaths into
 * arrests: a criminal killed by a guard is arrested for a share of the bounty, and one
 * who dies any other way is arrested with no one paid. Killing a guard raises bounty.
 */
public final class Combat implements Listener {

    private final PlayerStore players;
    private final ZoneTracker tracker;
    private final Arrests arrests;
    private final Handcuffs handcuffs;
    private final Messages messages;
    private final GameLog gameLog;
    private final Rules rules;
    private final Clock clock;
    /** When each player last hit or was hit by another player. */
    private final Map<UUID, Instant> lastFight = new HashMap<>();

    public Combat(PlayerStore players, ZoneTracker tracker, Arrests arrests, Handcuffs handcuffs, Messages messages,
            GameLog gameLog, Rules rules, Clock clock) {
        this.players = players;
        this.tracker = tracker;
        this.arrests = arrests;
        this.handcuffs = handcuffs;
        this.messages = messages;
        this.gameLog = gameLog;
        this.rules = rules;
        this.clock = clock;
    }

    /** Whether they've fought another player within the combat tag. */
    public boolean inCombat(Player player) {
        Instant last = lastFight.get(player.getUniqueId());
        return last != null && last.plus(rules.combatTag()).isAfter(clock.instant());
    }

    public CombatRules.Fighter fighter(Player player, PlayerState state) {
        Set<ZoneKind> kinds = EnumSet.noneOf(ZoneKind.class);
        for (Zone zone : tracker.zonesOf(player)) {
            kinds.add(zone.kind());
        }
        return new CombatRules.Fighter(state.role(), state.jailed(clock.instant()), kinds);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void hit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = attacker(event.getDamager());
        if (attacker == null || attacker.equals(victim)) {
            return;
        }
        PlayerState attackerState = players.get(attacker.getUniqueId()).orElse(null);
        PlayerState victimState = players.get(victim.getUniqueId()).orElse(null);
        if (attackerState == null || victimState == null
                || !CombatRules.canHurt(fighter(attacker, attackerState), fighter(victim, victimState))) {
            event.setCancelled(true);
            return;
        }
        Instant now = clock.instant();
        lastFight.put(attacker.getUniqueId(), now);
        lastFight.put(victim.getUniqueId(), now);
    }

    /** Nothing hurts a prisoner in their cell: no suffocating, no lava, no fall. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void anyDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && players.get(player.getUniqueId())
                .map(state -> state.jailed(clock.instant())).orElse(false)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void death(PlayerDeathEvent event) {
        Player victim = event.getPlayer();
        PlayerState victimState = players.get(victim.getUniqueId()).orElse(null);
        if (victimState == null) {
            return;
        }
        event.getDrops().removeIf(handcuffs::is);
        lastFight.remove(victim.getUniqueId());
        Player killer = victim.getKiller();
        PlayerState killerState = killer == null ? null : players.get(killer.getUniqueId()).orElse(null);

        if (victimState.role() == Role.CRIMINAL) {
            if (killerState != null && killerState.role() == Role.GUARD) {
                arrests.arrest(victim, victimState, killer, killerState, Arrests.How.KILLED);
            } else {
                arrests.arrest(victim, victimState, null, null, Arrests.How.DIED);
            }
        } else if (victimState.role() == Role.GUARD && killerState != null && killerState.role() == Role.CRIMINAL) {
            killerState.bounty(killerState.bounty() + rules.guardKillBounty());
            killerState.add(Stat.GUARDS_KILLED, 1);
            gameLog.record("guard_killed", killer.getUniqueId(), victim.getUniqueId(),
                    Map.of("bounty_added", rules.guardKillBounty()));
            messages.send(killer, "bounty.guard-killed", Messages.value("player", victim.getName()),
                    Messages.value("amount", Money.format(rules.guardKillBounty())));
        }
    }

    @EventHandler
    public void quit(PlayerQuitEvent event) {
        lastFight.remove(event.getPlayer().getUniqueId());
    }

    /** The player behind a hit: the attacker, or whoever shot, threw or lit it. */
    private static Player attacker(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        if (damager instanceof AreaEffectCloud cloud && cloud.getSource() instanceof Player source) {
            return source;
        }
        if (damager instanceof TNTPrimed tnt && tnt.getSource() instanceof Player source) {
            return source;
        }
        return null;
    }
}
