package co.xenastudios.mostwanted.player;

import co.xenastudios.mostwanted.message.Messages;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.entity.Player;

/**
 * {@code /stats private} and {@code /stats public}: whether your page and leaderboard
 * places show on the website and in Discord (the privacy page promises this). In-game
 * boards still show you, since other players see you in game anyway.
 */
public final class StatsCommand {

    private final PlayerStore players;
    private final Messages messages;

    public StatsCommand(PlayerStore players, Messages messages) {
        this.players = players;
        this.messages = messages;
    }

    public LiteralCommandNode<CommandSourceStack> command() {
        return Commands.literal("stats")
                .requires(source -> source.getExecutor() instanceof Player)
                .executes(context -> {
                    Player player = (Player) context.getSource().getExecutor();
                    players.get(player.getUniqueId()).ifPresent(state ->
                            messages.send(player, state.statsPrivate() ? "stats.is-private" : "stats.is-public"));
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.literal("private").executes(context -> set(context.getSource(), true)))
                .then(Commands.literal("public").executes(context -> set(context.getSource(), false)))
                .build();
    }

    private int set(CommandSourceStack source, boolean hidden) {
        Player player = (Player) source.getExecutor();
        players.get(player.getUniqueId()).ifPresent(state -> {
            state.statsPrivate(hidden);
            messages.send(player, hidden ? "stats.now-private" : "stats.now-public");
        });
        return Command.SINGLE_SUCCESS;
    }
}
