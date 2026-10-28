package co.xenastudios.mostwanted.player;

import static co.xenastudios.mostwanted.common.db.Tables.PLAYERS;
import static co.xenastudios.mostwanted.common.db.Tables.STATS;

import co.xenastudios.mostwanted.common.Periods;
import co.xenastudios.mostwanted.common.Stat;
import co.xenastudios.mostwanted.common.db.tables.records.PlayersRecord;
import co.xenastudios.mostwanted.storage.Storage;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.jooq.Condition;
import org.jooq.impl.DSL;

/**
 * A player's profile for the profile menu: read from the database off the main
 * thread, then topped up with what an online player has done since the last save.
 */
public final class Profiles {

    /** The periods a profile shows, in order. */
    public enum Period { ALL, MONTH, WEEK }

    /** {@code stats} by period, then stat; all of them, zero if none. */
    public record Profile(UUID uuid, String name, Role role, long bounty, boolean statsPrivate, Instant firstJoined,
            Instant lastSeen, Map<Period, Map<Stat, Long>> stats) {

        public long stat(Period period, Stat stat) {
            return stats.get(period).getOrDefault(stat, 0L);
        }
    }

    private final Plugin plugin;
    private final Storage storage;
    private final PlayerStore players;
    private final ZoneId zone;
    private final Clock clock;

    public Profiles(Plugin plugin, Storage storage, PlayerStore players, ZoneId zone, Clock clock) {
        this.plugin = plugin;
        this.storage = storage;
        this.players = players;
        this.zone = zone;
        this.clock = clock;
    }

    /** By name (any case; the latest to use it). {@code then} runs on the main thread, with null if there's nobody. */
    public void byName(String name, Consumer<Profile> then) {
        read(DSL.lower(PLAYERS.NAME).eq(name.toLowerCase(Locale.ROOT)), then);
    }

    public void byUuid(UUID uuid, Consumer<Profile> then) {
        read(PLAYERS.UUID.eq(uuid), then);
    }

    private void read(Condition who, Consumer<Profile> then) {
        Instant now = clock.instant();
        Map<String, Period> periods = new LinkedHashMap<>();
        periods.put(Periods.ALL, Period.ALL);
        periods.put(Periods.month(now, zone), Period.MONTH);
        periods.put(Periods.week(now, zone), Period.WEEK);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Profile found;
            try {
                found = load(who, periods);
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Couldn't read a profile: " + e.getMessage());
                found = null;
            }
            Profile profile = found;
            Bukkit.getScheduler().runTask(plugin, () -> then.accept(profile == null ? null : live(profile)));
        });
    }

    private Profile load(Condition who, Map<String, Period> periods) {
        PlayersRecord row = storage.sql().selectFrom(PLAYERS).where(who)
                .orderBy(PLAYERS.LAST_SEEN.desc()).limit(1).fetchOne();
        if (row == null) {
            return null;
        }
        Map<Period, Map<Stat, Long>> stats = new EnumMap<>(Period.class);
        for (Period period : Period.values()) {
            stats.put(period, new EnumMap<>(Stat.class));
        }
        storage.sql().select(STATS.STAT, STATS.PERIOD, STATS.VALUE).from(STATS)
                .where(STATS.UUID.eq(row.getUuid())).and(STATS.PERIOD.in(periods.keySet()))
                .forEach(r -> {
                    for (Stat stat : Stat.values()) {
                        if (stat.key().equals(r.value1())) {
                            stats.get(periods.get(r.value2())).put(stat, r.value3());
                        }
                    }
                });
        return new Profile(row.getUuid(), row.getName(), Role.fromKey(row.getRole()), row.getBounty(),
                row.getStatsPrivate(), row.getFirstJoined().toInstant(), row.getLastSeen().toInstant(), stats);
    }

    /** On the main thread: an online player's live state, over what was saved. */
    private Profile live(Profile saved) {
        PlayerState state = players.get(saved.uuid()).orElse(null);
        if (state == null) {
            return saved;
        }
        Map<Period, Map<Stat, Long>> stats = new EnumMap<>(Period.class);
        for (Period period : Period.values()) {
            Map<Stat, Long> values = new EnumMap<>(Stat.class);
            for (Stat stat : Stat.values()) {
                values.put(stat, period == Period.ALL ? state.total(stat)
                        : saved.stat(period, stat) + state.unsaved(stat));
            }
            stats.put(period, values);
        }
        return new Profile(saved.uuid(), state.name(), state.role(), state.bounty(), state.statsPrivate(),
                saved.firstJoined(), clock.instant(), stats);
    }
}
