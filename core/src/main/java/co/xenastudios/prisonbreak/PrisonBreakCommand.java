package co.xenastudios.prisonbreak;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;

/** {@code /prisonbreak}: admin subcommands. */
final class PrisonBreakCommand {

    private PrisonBreakCommand() {}

    static LiteralCommandNode<CommandSourceStack> create(PrisonBreak plugin) {
        return Commands.literal("prisonbreak")
                .requires(source -> source.getSender().hasPermission("prisonbreak.admin"))
                .then(Commands.literal("info").executes(context -> {
                    String line = info(plugin.getPluginMeta().getVersion(), Bukkit.getName(), Bukkit.getMinecraftVersion());
                    context.getSource().getSender().sendMessage(Component.text(line));
                    return Command.SINGLE_SUCCESS;
                }))
                .build();
    }

    /** "PrisonBreak 0.1.0 on Paper 26.3". */
    static String info(String version, String server, String minecraft) {
        return "PrisonBreak " + version + " on " + server + " " + minecraft;
    }
}
