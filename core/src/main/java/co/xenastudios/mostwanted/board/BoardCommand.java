package co.xenastudios.mostwanted.board;

import co.xenastudios.mostwanted.common.Board;
import co.xenastudios.mostwanted.message.Messages;
import co.xenastudios.mostwanted.storage.GameLog;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.Map;
import org.bukkit.entity.Player;

/** {@code /mw board add|remove|list}: leaderboards standing in the world. */
public final class BoardCommand {

    private final Leaderboards boards;
    private final Messages messages;
    private final GameLog gameLog;

    public BoardCommand(Leaderboards boards, Messages messages, GameLog gameLog) {
        this.boards = boards;
        this.messages = messages;
        this.gameLog = gameLog;
    }

    public LiteralArgumentBuilder<CommandSourceStack> create() {
        return Commands.literal("board")
                .then(Commands.literal("add").then(Commands.argument("board", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            for (Board board : Board.values()) {
                                builder.suggest(board.id());
                            }
                            return builder.buildFuture();
                        })
                        .executes(context -> add(context, Leaderboards.Period.ALL))
                        .then(Commands.argument("period", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    for (Leaderboards.Period period : Leaderboards.Period.values()) {
                                        builder.suggest(period.key());
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(context -> add(context,
                                        Leaderboards.Period.byKey(StringArgumentType.getString(context, "period")))))))
                .then(Commands.literal("remove").executes(context -> {
                    Player player = player(context);
                    if (player == null) {
                        return 0;
                    }
                    Leaderboards.Placed removed = boards.remove(player.getLocation());
                    if (removed == null) {
                        messages.send(player, "board.none-near");
                        return 0;
                    }
                    gameLog.record("staff.board", player.getUniqueId(), null,
                            Map.of("action", "remove", "board", removed.board().id(), "period", removed.period().key()));
                    messages.send(player, "board.removed", Messages.value("board", removed.board().id()));
                    return Command.SINGLE_SUCCESS;
                }))
                .then(Commands.literal("list").executes(context -> {
                    var sender = context.getSource().getSender();
                    if (boards.all().isEmpty()) {
                        messages.send(sender, "board.list-empty");
                    }
                    for (Leaderboards.Placed each : boards.all()) {
                        messages.send(sender, "board.list", Messages.value("board", each.board().id()),
                                Messages.value("period", each.period().key()), Messages.value("world", each.world()),
                                Messages.value("x", Math.round(each.x())), Messages.value("y", Math.round(each.y())),
                                Messages.value("z", Math.round(each.z())));
                    }
                    return Command.SINGLE_SUCCESS;
                }));
    }

    private int add(CommandContext<CommandSourceStack> context, Leaderboards.Period period) {
        Player player = player(context);
        if (player == null) {
            return 0;
        }
        Board board = Board.byId(StringArgumentType.getString(context, "board"));
        if (board == null || period == null) {
            messages.send(player, "board.unknown");
            return 0;
        }
        Leaderboards.Placed placed = boards.place(board, period, player.getLocation());
        gameLog.record("staff.board", player.getUniqueId(), null,
                Map.of("action", "add", "board", board.id(), "period", placed.period().key()));
        messages.send(player, "board.added", Messages.value("board", board.id()));
        return Command.SINGLE_SUCCESS;
    }

    private Player player(CommandContext<CommandSourceStack> context) {
        if (context.getSource().getExecutor() instanceof Player player) {
            return player;
        }
        messages.send(context.getSource().getSender(), "command.zone.players-only");
        return null;
    }
}
