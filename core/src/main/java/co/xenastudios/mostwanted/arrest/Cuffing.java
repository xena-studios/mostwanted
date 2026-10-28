package co.xenastudios.mostwanted.arrest;

import co.xenastudios.mostwanted.combat.Combat;
import co.xenastudios.mostwanted.combat.CombatRules;
import co.xenastudios.mostwanted.config.Rules;
import co.xenastudios.mostwanted.item.Handcuffs;
import co.xenastudios.mostwanted.message.Messages;
import co.xenastudios.mostwanted.player.PlayerState;
import co.xenastudios.mostwanted.player.PlayerStore;
import co.xenastudios.mostwanted.player.Role;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Right-clicking a criminal with handcuffs, within reach, arrests them for their whole
 * bounty. One click, so it works the same with Bedrock's touch controls.
 */
public final class Cuffing implements Listener {

    /** Stops one tap registering twice (Bedrock can send both hands). */
    private static final long COOLDOWN_MILLIS = 500;

    private final PlayerStore players;
    private final Combat combat;
    private final Arrests arrests;
    private final Handcuffs handcuffs;
    private final Messages messages;
    private final Rules rules;
    private final Map<UUID, Long> lastTry = new HashMap<>();

    public Cuffing(PlayerStore players, Combat combat, Arrests arrests, Handcuffs handcuffs, Messages messages,
            Rules rules) {
        this.players = players;
        this.combat = combat;
        this.arrests = arrests;
        this.handcuffs = handcuffs;
        this.messages = messages;
        this.rules = rules;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void cuff(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !(event.getRightClicked() instanceof Player target)) {
            return;
        }
        Player guard = event.getPlayer();
        if (!handcuffs.is(guard.getInventory().getItemInMainHand())) {
            return;
        }
        event.setCancelled(true);
        long now = System.currentTimeMillis();
        Long last = lastTry.put(guard.getUniqueId(), now);
        if (last != null && now - last < COOLDOWN_MILLIS) {
            return;
        }
        PlayerState guardState = players.get(guard.getUniqueId()).orElse(null);
        PlayerState targetState = players.get(target.getUniqueId()).orElse(null);
        if (guardState == null || targetState == null) {
            return;
        }
        if (guard.getLocation().distanceSquared(target.getLocation()) > rules.cuffRange() * rules.cuffRange()) {
            messages.send(guard, "cuffs.too-far", Messages.value("player", target.getName()));
            return;
        }
        switch (CombatRules.canCuff(combat.fighter(guard, guardState), combat.fighter(target, targetState))) {
            case OK -> arrests.arrest(target, targetState, guard, guardState, Arrests.How.CUFFED);
            case NOT_A_GUARD -> messages.send(guard, "cuffs.not-a-guard");
            case NOT_A_CRIMINAL -> messages.send(guard, "cuffs.not-a-criminal", Messages.value("player", target.getName()));
            case PROTECTED -> messages.send(guard, "cuffs.protected", Messages.value("player", target.getName()));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void drop(PlayerDropItemEvent event) {
        if (handcuffs.is(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    /** Only guards keep cuffs: anyone else who has a pair (an old one, a demoted guard) loses it. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void join(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        players.get(player.getUniqueId()).ifPresent(state -> {
            if (state.role() == Role.GUARD) {
                handcuffs.give(player);
            } else {
                handcuffs.take(player);
            }
        });
    }

    @EventHandler
    public void quit(PlayerQuitEvent event) {
        lastTry.remove(event.getPlayer().getUniqueId());
    }
}
