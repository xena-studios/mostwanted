package co.xenastudios.mostwanted.gun;

import co.xenastudios.mostwanted.message.Messages;
import co.xenastudios.mostwanted.storage.GameLog;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.Collection;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** {@code /mostwanted give <player> gun|ammo <id> [amount]}: for staff and testing. Logged. */
public final class GiveCommand {

    private final Guns guns;
    private final Messages messages;
    private final GameLog gameLog;

    public GiveCommand(Guns guns, Messages messages, GameLog gameLog) {
        this.guns = guns;
        this.messages = messages;
        this.gameLog = gameLog;
    }

    public LiteralArgumentBuilder<CommandSourceStack> create() {
        return Commands.literal("give").then(Commands.argument("player", StringArgumentType.word())
                .then(Commands.literal("gun").then(id(guns.settings().guns().keySet())
                        .executes(context -> give(context, "gun", 1))))
                .then(Commands.literal("ammo").then(id(guns.settings().ammo().keySet())
                        .executes(context -> give(context, "ammo", 64))
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1, 64 * 36))
                                .executes(context -> give(context, "ammo",
                                        IntegerArgumentType.getInteger(context, "amount")))))));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> id(
            Collection<String> ids) {
        return Commands.argument("id", StringArgumentType.word()).suggests((context, builder) -> {
            ids.forEach(builder::suggest);
            return builder.buildFuture();
        });
    }

    private int give(CommandContext<CommandSourceStack> context, String kind, int amount) {
        CommandSender sender = context.getSource().getSender();
        String name = StringArgumentType.getString(context, "player");
        String id = StringArgumentType.getString(context, "id");
        Player target = Bukkit.getPlayerExact(name);
        if (target == null) {
            messages.send(sender, "command.player-not-found", Messages.value("player", name));
            return 0;
        }
        ItemStack item;
        if (kind.equals("gun")) {
            GunSettings.Gun gun = guns.settings().guns().get(id);
            item = gun == null ? null : guns.gun(gun);
        } else {
            GunSettings.Ammo ammo = guns.settings().ammo().get(id);
            item = ammo == null ? null : guns.ammo(ammo, 1);
        }
        if (item == null) {
            messages.send(sender, "command.give-unknown", Messages.value("kind", kind), Messages.value("id", id));
            return 0;
        }
        // Over a stack: addItem splits it.
        item.setAmount(1);
        for (int i = 0; i < amount; i++) {
            target.getInventory().addItem(item.clone()).values()
                    .forEach(left -> target.getWorld().dropItem(target.getLocation(), left));
        }
        gameLog.record("staff.give", sender instanceof Player p ? p.getUniqueId() : null, target.getUniqueId(),
                Map.of("item", kind + ":" + id, "amount", amount));
        messages.send(sender, "command.given", Messages.value("player", target.getName()),
                Messages.value("amount", amount), Messages.value("item", id));
        return Command.SINGLE_SUCCESS;
    }
}
