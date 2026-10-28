package co.xenastudios.mostwanted.board;

import static co.xenastudios.mostwanted.common.db.Tables.PLAYERS;
import static co.xenastudios.mostwanted.common.db.Tables.STATS;

import co.xenastudios.mostwanted.common.Board;
import co.xenastudios.mostwanted.common.Periods;
import co.xenastudios.mostwanted.common.Stat;
import co.xenastudios.mostwanted.common.style.Money;
import co.xenastudios.mostwanted.message.Messages;
import co.xenastudios.mostwanted.storage.Storage;
import java.io.File;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.jooq.Record2;

/**
 * Leaderboards standing in the world: floating text (a vanilla text display, no
 * plugin) placed with {@code /mw board}, kept in boards.yml and refreshed every minute
 * from the same tables as the website's. Players with private stats aren't listed,
 * as on the website.
 */
public final class Leaderboards implements Listener {

    public static final int SIZE = 10;

    /** all, month or week; the most-wanted board has no period. */
    public enum Period { ALL, MONTH, WEEK;

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Period byKey(String key) {
            for (Period period : values()) {
                if (period.key().equals(key)) {
                    return period;
                }
            }
            return null;
        }
    }

    /** A board placed in the world. */
    public record Placed(String id, Board board, Period period, String world, double x, double y, double z) {

        Location location() {
            World w = Bukkit.getWorld(world);
            return w == null ? null : new Location(w, x, y, z);
        }
    }

    public record Line(String name, long value) {}

    private final Plugin plugin;
    private final Storage storage;
    private final Messages messages;
    private final ZoneId zone;
    private final Clock clock;
    private final File file;
    private final Logger log;
    private final NamespacedKey key;
    private final Map<String, Placed> placed = new HashMap<>();
    /** The latest top 10 for each board shown, by board and period. */
    private final Map<String, List<Line>> latest = new HashMap<>();

    public Leaderboards(Plugin plugin, Storage storage, Messages messages, ZoneId zone, Clock clock, File file,
            Logger log) {
        this.plugin = plugin;
        this.storage = storage;
        this.messages = messages;
        this.zone = zone;
        this.clock = clock;
        this.file = file;
        this.log = log;
        this.key = new NamespacedKey(plugin, "board");
    }

    // ---------------------------------------------------------------- placing

    public void load() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection boards = yaml.getConfigurationSection("boards");
        if (boards == null) {
            return;
        }
        for (String id : boards.getKeys(false)) {
            ConfigurationSection each = boards.getConfigurationSection(id);
            Board board = Board.byId(each.getString("board", ""));
            Period period = Period.byKey(each.getString("period", "all"));
            if (board == null || period == null) {
                log.warning("boards.yml: board " + id + " isn't a board this version has; skipped");
                continue;
            }
            placed.put(id, new Placed(id, board, period, each.getString("world"), each.getDouble("x"),
                    each.getDouble("y"), each.getDouble("z")));
        }
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Placed each : placed.values()) {
            String path = "boards." + each.id();
            yaml.set(path + ".board", each.board().id());
            yaml.set(path + ".period", each.period().key());
            yaml.set(path + ".world", each.world());
            yaml.set(path + ".x", each.x());
            yaml.set(path + ".y", each.y());
            yaml.set(path + ".z", each.z());
        }
        try {
            yaml.save(file);
        } catch (IOException e) {
            log.log(Level.WARNING, "Couldn't save boards.yml", e);
        }
    }

    public Placed place(Board board, Period period, Location at) {
        String id = board.id() + "-" + period.key() + "-" + Long.toString(clock.millis(), 36);
        Placed each = new Placed(id, board, board.stat() == null ? Period.ALL : period, at.getWorld().getName(),
                Math.round(at.getX() * 2) / 2.0, Math.round(at.getY() * 2) / 2.0 + 1.5, Math.round(at.getZ() * 2) / 2.0);
        placed.put(id, each);
        save();
        refresh();
        return each;
    }

    /** Takes away the board nearest to {@code at}, within 5 blocks. */
    public Placed remove(Location at) {
        Placed nearest = null;
        double best = 25;
        for (Placed each : placed.values()) {
            Location there = each.location();
            if (there != null && there.getWorld().equals(at.getWorld()) && there.distanceSquared(at) < best) {
                best = there.distanceSquared(at);
                nearest = each;
            }
        }
        if (nearest != null) {
            placed.remove(nearest.id());
            despawn(nearest.id());
            save();
        }
        return nearest;
    }

    public List<Placed> all() {
        return List.copyOf(placed.values());
    }

    // ---------------------------------------------------------------- refreshing

    /** Every minute: reads the boards off the main thread, then redraws them on it. */
    public void refresh() {
        // Which boards to read, taken on the main thread: placing one changes the map.
        List<Placed> shown = List.copyOf(placed.values());
        if (shown.isEmpty()) {
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Map<String, List<Line>> read = new HashMap<>();
            try {
                for (Placed each : shown) {
                    read.computeIfAbsent(cacheKey(each.board(), each.period()), k -> top(each.board(), each.period()));
                }
            } catch (RuntimeException e) {
                log.log(Level.WARNING, "Couldn't read the leaderboards", e);
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                latest.putAll(read);
                placed.values().forEach(this::draw);
            });
        });
    }

    private static String cacheKey(Board board, Period period) {
        return board.id() + "/" + period.key();
    }

    /** The top 10. Blocks: off the main thread only. */
    List<Line> top(Board board, Period period) {
        List<Record2<String, Long>> rows;
        if (board.stat() == null) {
            rows = storage.sql().select(PLAYERS.NAME, PLAYERS.BOUNTY)
                    .from(PLAYERS)
                    .where(PLAYERS.BOUNTY.gt(0L)).and(PLAYERS.STATS_PRIVATE.isFalse())
                    .orderBy(PLAYERS.BOUNTY.desc(), PLAYERS.NAME)
                    .limit(SIZE)
                    .fetch();
        } else {
            rows = storage.sql().select(PLAYERS.NAME, STATS.VALUE)
                    .from(STATS).join(PLAYERS).on(PLAYERS.UUID.eq(STATS.UUID))
                    .where(STATS.STAT.eq(board.stat().key()))
                    .and(STATS.PERIOD.eq(periodKey(period)))
                    .and(STATS.VALUE.gt(0L)).and(PLAYERS.STATS_PRIVATE.isFalse())
                    .orderBy(STATS.VALUE.desc(), PLAYERS.NAME)
                    .limit(SIZE)
                    .fetch();
        }
        return rows.stream().map(row -> new Line(row.value1(), row.value2())).toList();
    }

    private String periodKey(Period period) {
        Instant now = clock.instant();
        return switch (period) {
            case ALL -> Periods.ALL;
            case MONTH -> Periods.month(now, zone);
            case WEEK -> Periods.week(now, zone);
        };
    }

    /** Draws a board if its spot is loaded; it's drawn again as the spot loads. */
    private void draw(Placed each) {
        Location at = each.location();
        if (at == null || !at.isChunkLoaded()) {
            return;
        }
        Component text = text(each);
        TextDisplay display = find(each.id());
        if (display == null) {
            at.getWorld().spawn(at, TextDisplay.class, spawned -> {
                spawned.setPersistent(false);
                spawned.setBillboard(Display.Billboard.VERTICAL);
                spawned.setShadowed(true);
                spawned.setLineWidth(240);
                spawned.getPersistentDataContainer().set(key, PersistentDataType.STRING, each.id());
                spawned.text(text);
            });
        } else {
            display.text(text);
        }
    }

    Component text(Placed each) {
        List<Line> lines = latest.getOrDefault(cacheKey(each.board(), each.period()), List.of());
        List<Component> parts = new ArrayList<>();
        parts.add(messages.get("board.title." + each.board().id()));
        if (each.board().stat() != null) {
            parts.add(messages.get("board.period." + each.period().key()));
        }
        boolean money = each.board() == Board.MOST_WANTED || each.board().stat() == Stat.BOUNTY_EARNED;
        for (int i = 0; i < lines.size(); i++) {
            Line line = lines.get(i);
            parts.add(messages.get(money ? "board.line-money" : "board.line", Messages.value("rank", i + 1),
                    Messages.value("player", line.name()),
                    Messages.value("amount", money ? Money.format(line.value()) : String.format(Locale.US, "%,d", line.value()))));
        }
        if (lines.isEmpty()) {
            parts.add(messages.get("board.empty"));
        }
        return Component.join(JoinConfiguration.newlines(), parts);
    }

    private TextDisplay find(String id) {
        Placed each = placed.get(id);
        Location at = each == null ? null : each.location();
        if (at == null) {
            return null;
        }
        for (Entity entity : at.getWorld().getNearbyEntitiesByType(TextDisplay.class, at, 1)) {
            if (id.equals(entity.getPersistentDataContainer().get(key, PersistentDataType.STRING))) {
                return (TextDisplay) entity;
            }
        }
        return null;
    }

    private void despawn(String id) {
        for (World world : Bukkit.getWorlds()) {
            for (TextDisplay display : world.getEntitiesByClass(TextDisplay.class)) {
                if (id.equals(display.getPersistentDataContainer().get(key, PersistentDataType.STRING))) {
                    display.remove();
                }
            }
        }
    }

    /** A board's spot loading: draw it from what was last read. */
    @EventHandler
    public void loaded(EntitiesLoadEvent event) {
        for (Placed each : placed.values()) {
            Location at = each.location();
            if (at != null && at.getWorld().equals(event.getWorld())
                    && at.getBlockX() >> 4 == event.getChunk().getX() && at.getBlockZ() >> 4 == event.getChunk().getZ()) {
                draw(each);
            }
        }
    }
}
