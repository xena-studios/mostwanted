package co.xenastudios.mostwanted.duty;

import co.xenastudios.mostwanted.combat.Combat;
import co.xenastudios.mostwanted.item.GuardKit;
import co.xenastudios.mostwanted.message.Messages;
import co.xenastudios.mostwanted.player.PlayerState;
import co.xenastudios.mostwanted.player.PlayerStore;
import co.xenastudios.mostwanted.player.Role;
import co.xenastudios.mostwanted.storage.GameLog;
import co.xenastudios.mostwanted.zone.Spawns;
import co.xenastudios.mostwanted.zone.ZoneKind;
import com.destroystokyo.paper.event.player.PlayerPostRespawnEvent;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * {@code /duty}: guards go on duty from the prison as an inmate, and off duty back to it.
 * Guard money is kept apart: each switch swaps cash and bank with the stashed pair.
 * Who can be a guard is the {@code mostwanted.guard} permission, given by a LuckPerms rank.
 */
public final class Duty implements Listener {

    public static final String PERMISSION = "mostwanted.guard";

    private final PlayerStore players;
    private final Combat combat;
    private final List<GuardKit> kit;
    private final Spawns spawns;
    private final Messages messages;
    private final GameLog gameLog;
    private final Clock clock;

    /** {@code kit} is everything guards carry on duty: handcuffs, the taser, a keycard. */
    public Duty(PlayerStore players, Combat combat, List<GuardKit> kit, Spawns spawns, Messages messages,
            GameLog gameLog, Clock clock) {
        this.players = players;
        this.combat = combat;
        this.kit = List.copyOf(kit);
        this.spawns = spawns;
        this.messages = messages;
        this.gameLog = gameLog;
        this.clock = clock;
    }

    public LiteralCommandNode<CommandSourceStack> command() {
        return Commands.literal("duty")
                .requires(source -> source.getSender().hasPermission(PERMISSION)
                        && source.getExecutor() instanceof Player)
                .executes(context -> {
                    Player player = (Player) context.getSource().getExecutor();
                    players.get(player.getUniqueId()).ifPresent(state -> toggle(player, state));
                    return Command.SINGLE_SUCCESS;
                })
                .build();
    }

    private void toggle(Player player, PlayerState state) {
        switch (state.role()) {
            case GUARD -> {
                if (combat.inCombat(player)) {
                    messages.send(player, "duty.in-combat");
                    return;
                }
                off(player, state);
                messages.send(player, "duty.off");
            }
            case INMATE -> {
                if (state.jailed(clock.instant())) {
                    messages.send(player, "duty.jailed");
                    return;
                }
                state.swapStash();
                state.role(Role.GUARD);
                kit.forEach(gear -> gear.give(player));
                spawns.of(ZoneKind.POLICE).ifPresent(player::teleportAsync);
                gameLog.record("duty", player.getUniqueId(), null, Map.of("on", "true"));
                messages.send(player, "duty.on");
            }
            case CRIMINAL -> messages.send(player, "duty.criminal");
        }
    }

    private void off(Player player, PlayerState state) {
        state.swapStash();
        state.role(Role.INMATE);
        kit.forEach(gear -> gear.take(player));
        spawns.of(ZoneKind.PRISON).ifPresent(player::teleportAsync);
        gameLog.record("duty", player.getUniqueId(), null, Map.of("on", "false"));
    }

    /** A guard whose rank was taken away goes off duty as they join. Before Cuffing's join, which hands out cuffs. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void join(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        players.get(player.getUniqueId()).ifPresent(state -> {
            if (state.role() == Role.GUARD && !player.hasPermission(PERMISSION)) {
                off(player, state);
                messages.send(player, "duty.removed");
            }
        });
    }

    @EventHandler
    public void respawn(PlayerPostRespawnEvent event) {
        Player player = event.getPlayer();
        players.get(player.getUniqueId())
                .filter(state -> state.role() == Role.GUARD)
                .ifPresent(state -> kit.forEach(gear -> gear.give(player)));
    }
}
