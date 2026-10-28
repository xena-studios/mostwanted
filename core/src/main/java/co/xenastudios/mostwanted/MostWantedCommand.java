package co.xenastudios.mostwanted;

import co.xenastudios.mostwanted.board.BoardCommand;
import co.xenastudios.mostwanted.common.style.Money;
import co.xenastudios.mostwanted.cosmetic.Cosmetics;
import co.xenastudios.mostwanted.gun.GiveCommand;
import co.xenastudios.mostwanted.message.Messages;
import co.xenastudios.mostwanted.player.PlayerState;
import co.xenastudios.mostwanted.player.PlayerStore;
import co.xenastudios.mostwanted.player.Role;
import co.xenastudios.mostwanted.storage.GameLog;
import co.xenastudios.mostwanted.zone.ZoneCommand;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** {@code /mostwanted}: admin subcommands. Every change is written to the game log. */
final class MostWantedCommand {

    private MostWantedCommand() {}

    static LiteralCommandNode<CommandSourceStack> create(MostWanted plugin, PlayerStore players, Messages messages,
            GameLog gameLog, ZoneCommand zones, GiveCommand give, Cosmetics cosmetics,
            BoardCommand boards, AdminCommands admin) {
        var set = Commands.literal("set").then(Commands.argument("player", StringArgumentType.word())
                .then(Commands.literal("role").then(Commands.argument("role", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            for (Role role : Role.values()) {
                                builder.suggest(role.key());
                            }
                            return builder.buildFuture();
                        })
                        .executes(context -> setRole(context, players, messages, gameLog))))
                .then(amount("cash", "command.cash-set", PlayerState::cash, players, messages, gameLog))
                .then(amount("bank", "command.bank-set", PlayerState::bank, players, messages, gameLog))
                .then(amount("bounty", "command.bounty-set", PlayerState::bounty, players, messages, gameLog)));

        var root = Commands.literal("mostwanted")
                .requires(source -> source.getSender().hasPermission("mostwanted.admin"));
        admin.create().forEach(root::then);
        return root
                .then(Commands.literal("info").executes(context -> {
                    messages.send(context.getSource().getSender(), "command.info",
                            Messages.value("version", info(plugin.getPluginMeta().getVersion(), Bukkit.getName(),
                                    Bukkit.getMinecraftVersion())),
                            Messages.value("database", plugin.databaseReady() ? "connected" : "off"));
                    return Command.SINGLE_SUCCESS;
                }))
                .then(Commands.literal("player").then(Commands.argument("player", StringArgumentType.word())
                        .executes(context -> {
                            PlayerState state = target(context, players, messages);
                            if (state != null) {
                                messages.send(context.getSource().getSender(), "command.player",
                                        Messages.value("player", state.name()),
                                        Messages.component("role", role(state.role())),
                                        Messages.value("cash", Money.format(state.cash())),
                                        Messages.value("bank", Money.format(state.bank())),
                                        Messages.value("bounty", Money.format(state.bounty())));
                            }
                            return Command.SINGLE_SUCCESS;
                        })))
                .then(set)
                .then(zones.create())
                .then(give.create())
                .then(boards.create())
                // Run by Tebex for the Server Boost: the buyer's name, who needn't be online.
                .then(Commands.literal("boost").then(Commands.argument("player", StringArgumentType.word())
                        .executes(context -> {
                            cosmetics.boost(StringArgumentType.getString(context, "player"));
                            return Command.SINGLE_SUCCESS;
                        })))
                .build();
    }

    /** "MostWanted 0.1.0 on Paper 26.3". */
    static String info(String version, String server, String minecraft) {
        return "MostWanted " + version + " on " + server + " " + minecraft;
    }

    private static int setRole(CommandContext<CommandSourceStack> context, PlayerStore players, Messages messages,
            GameLog gameLog) {
        CommandSender sender = context.getSource().getSender();
        Role role;
        try {
            role = Role.fromKey(StringArgumentType.getString(context, "role"));
        } catch (IllegalArgumentException e) {
            messages.send(sender, "command.bad-role");
            return 0;
        }
        PlayerState state = target(context, players, messages);
        if (state == null) {
            return 0;
        }
        state.role(role);
        gameLog.record("staff.set", actor(sender), state.uuid(), Map.of("field", "role", "value", role.key()));
        messages.send(sender, "command.role-set", Messages.value("player", state.name()),
                Messages.component("role", role(role)));
        return Command.SINGLE_SUCCESS;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> amount(String field, String message,
            BiConsumer<PlayerState, Long> setter, PlayerStore players, Messages messages, GameLog gameLog) {
        return Commands.literal(field).then(Commands.argument("amount", LongArgumentType.longArg(0))
                .executes(context -> {
                    PlayerState state = target(context, players, messages);
                    if (state == null) {
                        return 0;
                    }
                    long amount = LongArgumentType.getLong(context, "amount");
                    setter.accept(state, amount);
                    CommandSender sender = context.getSource().getSender();
                    gameLog.record("staff.set", actor(sender), state.uuid(), Map.of("field", field, "value", amount));
                    messages.send(sender, message, Messages.value("player", state.name()),
                            Messages.value("amount", Money.format(amount)));
                    return Command.SINGLE_SUCCESS;
                }));
    }

    private static PlayerState target(CommandContext<CommandSourceStack> context, PlayerStore players,
            Messages messages) {
        String name = StringArgumentType.getString(context, "player");
        Player player = Bukkit.getPlayerExact(name);
        PlayerState state = player == null ? null : players.get(player.getUniqueId()).orElse(null);
        if (state == null) {
            messages.send(context.getSource().getSender(), "command.player-not-found", Messages.value("player", name));
        }
        return state;
    }

    private static UUID actor(CommandSender sender) {
        return sender instanceof Player player ? player.getUniqueId() : null;
    }

    private static Component role(Role role) {
        return Component.text(role.displayName(), TextColor.color(role.colour()));
    }
}
