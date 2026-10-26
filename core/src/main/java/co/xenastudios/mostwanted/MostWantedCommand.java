package co.xenastudios.mostwanted;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;

/** {@code /mostwanted}: admin subcommands. */
final class MostWantedCommand {

    private MostWantedCommand() {}

    static LiteralCommandNode<CommandSourceStack> create(MostWanted plugin) {
        return Commands.literal("mostwanted")
                .requires(source -> source.getSender().hasPermission("mostwanted.admin"))
                .then(Commands.literal("info").executes(context -> {
                    String line = info(plugin.getPluginMeta().getVersion(), Bukkit.getName(), Bukkit.getMinecraftVersion());
                    context.getSource().getSender().sendMessage(Component.text(line));
                    return Command.SINGLE_SUCCESS;
                }))
                .build();
    }

    /** "MostWanted 0.1.0 on Paper 26.3". */
    static String info(String version, String server, String minecraft) {
        return "MostWanted " + version + " on " + server + " " + minecraft;
    }
}
