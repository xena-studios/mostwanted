package co.xenastudios.mostwanted.zone;

import co.xenastudios.mostwanted.message.Messages;
import co.xenastudios.mostwanted.storage.GameLog;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** {@code /mostwanted zone}: marking zones out with a FAWE selection. Changes go in the game log. */
public final class ZoneCommand {

    /** The biggest box a gate may be: room for a wide double door and its frame. */
    static final int GATE_MAX_VOLUME = 2048;

    private final ZoneStore zones;
    private final ZoneTracker tracker;
    private final Messages messages;
    private final GameLog gameLog;

    public ZoneCommand(ZoneStore zones, ZoneTracker tracker, Messages messages, GameLog gameLog) {
        this.zones = zones;
        this.tracker = tracker;
        this.messages = messages;
        this.gameLog = gameLog;
    }

    public LiteralArgumentBuilder<CommandSourceStack> create() {
        return Commands.literal("zone")
                .then(Commands.literal("list").executes(this::list))
                .then(Commands.literal("here").executes(this::here))
                .then(Commands.literal("create").then(Commands.argument("name", StringArgumentType.word())
                        .then(Commands.argument("kind", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    for (ZoneKind kind : ZoneKind.values()) {
                                        builder.suggest(kind.key());
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(this::create))))
                .then(Commands.literal("redefine").then(zoneName().executes(this::redefine)))
                .then(Commands.literal("spawn").then(zoneName().executes(this::spawn)))
                .then(Commands.literal("tp").then(zoneName().executes(this::teleport)))
                .then(Commands.literal("delete").then(zoneName().executes(this::delete)));
    }

    private RequiredArgumentBuilder<CommandSourceStack, String> zoneName() {
        return Commands.argument("name", StringArgumentType.word()).suggests((context, builder) -> {
            for (Zone zone : zones.all()) {
                builder.suggest(zone.name());
            }
            return builder.buildFuture();
        });
    }

    private int list(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        if (zones.all().isEmpty()) {
            messages.send(sender, "command.zone.list-empty");
            return Command.SINGLE_SUCCESS;
        }
        messages.send(sender, "command.zone.list", Messages.value("count", zones.all().size()));
        for (Zone zone : zones.all()) {
            messages.send(sender, "command.zone.list-entry", Messages.value("zone", zone.name()),
                    Messages.value("kind", zone.kind().key()), Messages.value("size", zone.size()),
                    Messages.value("world", zone.world()));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int here(CommandContext<CommandSourceStack> context) {
        Player player = player(context);
        if (player == null) {
            return 0;
        }
        List<String> names = new ArrayList<>();
        for (Zone zone : tracker.zonesOf(player)) {
            names.add(zone.name() + " (" + zone.kind().key() + ")");
        }
        if (names.isEmpty()) {
            messages.send(player, "command.zone.here-none");
        } else {
            messages.send(player, "command.zone.here", Messages.value("zones", String.join(", ", names)));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int create(CommandContext<CommandSourceStack> context) {
        Player player = player(context);
        String name = StringArgumentType.getString(context, "name");
        String kindKey = StringArgumentType.getString(context, "kind");
        if (player == null) {
            return 0;
        }
        if (!Zone.NAME.matcher(name).matches()) {
            messages.send(player, "command.zone.bad-name");
            return 0;
        }
        ZoneKind kind = ZoneKind.fromKey(kindKey);
        if (kind == null) {
            messages.send(player, "command.zone.bad-kind", Messages.value("kinds", kinds()));
            return 0;
        }
        if (zones.get(name).isPresent()) {
            messages.send(player, "command.zone.exists", Messages.value("zone", name));
            return 0;
        }
        Selection selection = selection(player);
        if (selection == null) {
            return 0;
        }
        Zone zone = selection.toZone(name, kind, null);
        if (tooBig(player, zone)) {
            return 0;
        }
        save(player, zone, "create");
        messages.send(player, "command.zone.created", Messages.value("zone", name), Messages.value("kind", kind.key()),
                Messages.value("size", zone.size()));
        return Command.SINGLE_SUCCESS;
    }

    private int redefine(CommandContext<CommandSourceStack> context) {
        Player player = player(context);
        Zone old = zone(context);
        if (player == null || old == null) {
            return 0;
        }
        Selection selection = selection(player);
        if (selection == null) {
            return 0;
        }
        Zone zone = selection.toZone(old.name(), old.kind(), old.spawn());
        if (tooBig(player, zone)) {
            return 0;
        }
        save(player, zone, "redefine");
        messages.send(player, "command.zone.redefined", Messages.value("zone", zone.name()),
                Messages.value("size", zone.size()));
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Gates open block by block on the main thread whenever a guard uses one, so a
     * gate is the gate itself, not a whole yard.
     */
    private boolean tooBig(Player player, Zone zone) {
        if (zone.kind() != ZoneKind.GATE || zone.volume() <= GATE_MAX_VOLUME) {
            return false;
        }
        messages.send(player, "command.zone.gate-too-big", Messages.value("size", zone.size()),
                Messages.value("max", GATE_MAX_VOLUME));
        return true;
    }

    private int spawn(CommandContext<CommandSourceStack> context) {
        Player player = player(context);
        Zone old = zone(context);
        if (player == null || old == null) {
            return 0;
        }
        Location at = player.getLocation();
        if (!at.getWorld().getName().equals(old.world())) {
            messages.send(player, "command.zone.other-world", Messages.value("zone", old.name()),
                    Messages.value("world", old.world()));
            return 0;
        }
        save(player, old.withSpawn(new Zone.Spawn(at.getX(), at.getY(), at.getZ(), at.getYaw(), at.getPitch())),
                "spawn");
        messages.send(player, "command.zone.spawn-set", Messages.value("zone", old.name()));
        return Command.SINGLE_SUCCESS;
    }

    private int teleport(CommandContext<CommandSourceStack> context) {
        Player player = player(context);
        Zone zone = zone(context);
        if (player == null || zone == null) {
            return 0;
        }
        World world = Bukkit.getWorld(zone.world());
        if (world == null) {
            messages.send(player, "command.zone.no-world", Messages.value("world", zone.world()));
            return 0;
        }
        Location to;
        if (zone.spawn() != null) {
            Zone.Spawn spawn = zone.spawn();
            to = new Location(world, spawn.x(), spawn.y(), spawn.z(), spawn.yaw(), spawn.pitch());
        } else {
            // The middle, standing on whatever's highest there.
            int x = Math.floorDiv(zone.minX() + zone.maxX(), 2);
            int z = Math.floorDiv(zone.minZ() + zone.maxZ(), 2);
            to = new Location(world, x + 0.5, world.getHighestBlockYAt(x, z) + 1, z + 0.5);
        }
        player.teleportAsync(to);
        return Command.SINGLE_SUCCESS;
    }

    private int delete(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        Zone zone = zone(context);
        if (zone == null) {
            return 0;
        }
        zones.delete(zone.name());
        gameLog.record("staff.zone", actor(sender), null, Map.of("action", "delete", "zone", zone.name()));
        messages.send(sender, "command.zone.deleted", Messages.value("zone", zone.name()));
        return Command.SINGLE_SUCCESS;
    }

    private void save(Player player, Zone zone, String action) {
        zones.put(zone, player.getUniqueId());
        gameLog.record("staff.zone", player.getUniqueId(), null, Map.of("action", action, "zone", zone.name(),
                "kind", zone.kind().key(), "world", zone.world(),
                "min", zone.minX() + " " + zone.minY() + " " + zone.minZ(),
                "max", zone.maxX() + " " + zone.maxY() + " " + zone.maxZ()));
    }

    private Selection selection(Player player) {
        if (!Selection.available()) {
            messages.send(player, "command.zone.needs-fawe");
            return null;
        }
        Selection selection = Selection.of(player);
        if (selection == null) {
            messages.send(player, "command.zone.no-selection");
        }
        return selection;
    }

    private Zone zone(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");
        Zone zone = zones.get(name).orElse(null);
        if (zone == null) {
            messages.send(context.getSource().getSender(), "command.zone.not-found", Messages.value("zone", name));
        }
        return zone;
    }

    private Player player(CommandContext<CommandSourceStack> context) {
        if (context.getSource().getSender() instanceof Player player) {
            return player;
        }
        messages.send(context.getSource().getSender(), "command.zone.players-only");
        return null;
    }

    private static String kinds() {
        List<String> keys = new ArrayList<>();
        for (ZoneKind kind : ZoneKind.values()) {
            keys.add(kind.key());
        }
        return String.join(", ", keys);
    }

    private static UUID actor(CommandSender sender) {
        return sender instanceof Player player ? player.getUniqueId() : null;
    }
}
