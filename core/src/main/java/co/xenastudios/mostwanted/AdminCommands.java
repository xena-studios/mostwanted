package co.xenastudios.mostwanted;

import co.xenastudios.mostwanted.hud.Hud;
import co.xenastudios.mostwanted.message.Messages;
import co.xenastudios.mostwanted.player.PlayerState;
import co.xenastudios.mostwanted.player.PlayerStore;
import co.xenastudios.mostwanted.storage.GameLog;
import co.xenastudios.mostwanted.storage.Storage;
import co.xenastudios.mostwanted.zone.Spawns;
import co.xenastudios.mostwanted.zone.Zone;
import co.xenastudios.mostwanted.zone.ZoneStore;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.lang.management.ManagementFactory;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * {@code /mw} commands for running the server and finding out what's wrong:
 * {@code debug} (how the server's doing), {@code tp <zone>}, {@code release <player>}
 * and {@code save}. Changes go to the game log like the other staff commands.
 */
final class AdminCommands {

    private final Plugin plugin;
    private final PlayerStore players;
    private final ZoneStore zones;
    private final Storage storage;
    private final Messages messages;
    private final GameLog gameLog;
    private final Clock clock;

    AdminCommands(Plugin plugin, PlayerStore players, ZoneStore zones, Storage storage, Messages messages,
            GameLog gameLog, Clock clock) {
        this.plugin = plugin;
        this.players = players;
        this.zones = zones;
        this.storage = storage;
        this.messages = messages;
        this.gameLog = gameLog;
        this.clock = clock;
    }

    List<LiteralArgumentBuilder<CommandSourceStack>> create() {
        return List.of(debug(), tp(), release(), save());
    }

    /** How the server is doing: ticks, memory, players, the database. */
    private LiteralArgumentBuilder<CommandSourceStack> debug() {
        return Commands.literal("debug").executes(context -> {
            CommandSender sender = context.getSource().getSender();
            double[] tps = Bukkit.getTPS();
            Runtime runtime = Runtime.getRuntime();
            long usedMb = (runtime.totalMemory() - runtime.freeMemory()) >> 20;
            Duration up = Duration.ofMillis(ManagementFactory.getRuntimeMXBean().getUptime());
            messages.send(sender, "admin.debug.server",
                    Messages.value("tps", String.format(Locale.US, "%.1f, %.1f, %.1f", tps[0], tps[1], tps[2])),
                    Messages.value("mspt", String.format(Locale.US, "%.1f", Bukkit.getAverageTickTime())),
                    Messages.value("memory", usedMb + " / " + (runtime.maxMemory() >> 20) + " MB"),
                    Messages.value("uptime", Hud.clockTime(up)));
            long jailed = players.all().stream().filter(s -> s.jailed(clock.instant())).count();
            messages.send(sender, "admin.debug.players",
                    Messages.value("online", Bukkit.getOnlinePlayers().size()),
                    Messages.value("loaded", players.all().size()),
                    Messages.value("jailed", jailed),
                    Messages.value("zones", zones.all().size()),
                    Messages.value("chunks", Bukkit.getWorlds().stream().mapToInt(w -> w.getLoadedChunks().length).sum()),
                    Messages.value("entities", Bukkit.getWorlds().stream().mapToInt(World::getEntityCount).sum()));
            // The database, off the main thread: a round trip, timed.
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                long start = System.nanoTime();
                String result;
                try {
                    storage.sql().selectOne().fetch();
                    result = (System.nanoTime() - start) / 1_000_000 + " ms";
                } catch (RuntimeException e) {
                    result = "down: " + e.getMessage();
                }
                String line = result;
                Bukkit.getScheduler().runTask(plugin, () ->
                        messages.send(sender, "admin.debug.database", Messages.value("result", line)));
            });
            return Command.SINGLE_SUCCESS;
        });
    }

    /** To a zone's spawn, or its middle if it has none. */
    private LiteralArgumentBuilder<CommandSourceStack> tp() {
        return Commands.literal("tp")
                .requires(source -> source.getExecutor() instanceof Player)
                .then(Commands.argument("zone", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            zones.all().forEach(zone -> builder.suggest(zone.name()));
                            return builder.buildFuture();
                        })
                        .executes(context -> {
                            Player player = (Player) context.getSource().getExecutor();
                            String name = StringArgumentType.getString(context, "zone");
                            Zone zone = zones.get(name).orElse(null);
                            World world = zone == null ? null : Bukkit.getWorld(zone.world());
                            if (zone == null || world == null) {
                                messages.send(player, "command.zone.not-found", Messages.value("zone", name));
                                return 0;
                            }
                            Location to = Spawns.of(zone).orElseGet(() -> new Location(world,
                                    (zone.minX() + zone.maxX()) / 2.0 + 0.5, zone.maxY(),
                                    (zone.minZ() + zone.maxZ()) / 2.0 + 0.5));
                            player.teleportAsync(to);
                            messages.send(player, "admin.tp", Messages.value("zone", zone.name()));
                            return Command.SINGLE_SUCCESS;
                        }));
    }

    /** Ends someone's sentence now: they're let out on the next second's check, as usual. */
    private LiteralArgumentBuilder<CommandSourceStack> release() {
        return Commands.literal("release").then(Commands.argument("player", StringArgumentType.word())
                .suggests((context, builder) -> {
                    Bukkit.getOnlinePlayers().forEach(p -> builder.suggest(p.getName()));
                    return builder.buildFuture();
                })
                .executes(context -> {
                    CommandSender sender = context.getSource().getSender();
                    String name = StringArgumentType.getString(context, "player");
                    Player player = Bukkit.getPlayerExact(name);
                    PlayerState state = player == null ? null : players.get(player.getUniqueId()).orElse(null);
                    if (state == null) {
                        messages.send(sender, "command.player-not-found", Messages.value("player", name));
                        return 0;
                    }
                    if (!state.jailed(clock.instant())) {
                        messages.send(sender, "admin.not-jailed", Messages.value("player", state.name()));
                        return 0;
                    }
                    state.jailedUntil(Instant.now(clock));
                    gameLog.record("staff.release", sender instanceof Player p ? p.getUniqueId() : null, state.uuid(),
                            Map.of());
                    messages.send(sender, "admin.released", Messages.value("player", state.name()));
                    return Command.SINGLE_SUCCESS;
                }));
    }

    /** Writes every changed player now, instead of at the next save. */
    private LiteralArgumentBuilder<CommandSourceStack> save() {
        return Commands.literal("save").executes(context -> {
            players.saveChanged();
            messages.send(context.getSource().getSender(), "admin.saved",
                    Messages.value("count", players.all().size()));
            return Command.SINGLE_SUCCESS;
        });
    }
}
