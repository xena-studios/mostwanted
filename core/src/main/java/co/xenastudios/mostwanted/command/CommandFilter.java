package co.xenastudios.mostwanted.command;

import co.xenastudios.mostwanted.message.Messages;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;

/**
 * What players can see and run: only the commands in config.yml's
 * {@code commands.allowed}. Everything else is left out of the commands a player's
 * game suggests, and refused like a command that doesn't exist, so nobody can list
 * our plugins (/plugins, /version) or reach one around its permissions with
 * {@code /plugin:command}. Staff ({@value #ALL}, which ops have) see everything.
 * Our own CommandWhitelist, without the plugin.
 */
public final class CommandFilter implements Listener {

    public static final String ALL = "mostwanted.commands.all";

    /** When config.yml doesn't say: the game's own player commands, and chat's and the info links'. */
    public static final List<String> DEFAULT = List.of(
            "menu", "profile", "settings", "leaderboards", "lb", "store", "buy", "shop", "cosmetics", "joinmessage",
            "website", "stats", "duty",
            "msg", "tell", "w", "whisper", "m", "pm", "reply", "r", "togglechat", "tc",
            "rules", "wiki", "guide", "apply", "discord");

    private final Set<String> allowed;
    private final Messages messages;

    public CommandFilter(List<String> allowed, Messages messages) {
        this.allowed = allowed.stream().map(command -> command.strip().replaceFirst("^/", "").toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
        this.messages = messages;
    }

    /** Whether a player without {@value #ALL} may use this command (its first word, no slash). */
    boolean allows(String label) {
        return allowed.contains(label.toLowerCase(Locale.ROOT));
    }

    /** The list the game suggests from, as a player joins or their permissions change. */
    @EventHandler
    public void suggest(PlayerCommandSendEvent event) {
        if (!event.getPlayer().hasPermission(ALL)) {
            event.getCommands().removeIf(command -> !allows(command));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void run(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission(ALL)) {
            return;
        }
        String label = label(event.getMessage());
        if (!allows(label)) {
            event.setCancelled(true);
            messages.send(player, "command.unknown");
        }
    }

    /** "/Msg Alex hi" is "msg"; "/minecraft:me hi" is "minecraft:me". */
    static String label(String message) {
        String text = message.startsWith("/") ? message.substring(1) : message;
        int space = text.indexOf(' ');
        return (space < 0 ? text : text.substring(0, space)).toLowerCase(Locale.ROOT);
    }
}
