package co.xenastudios.mostwanted.backend.stats;

import static co.xenastudios.mostwanted.common.db.Tables.PLAYERS;
import static co.xenastudios.mostwanted.common.db.Tables.STATS;

import co.xenastudios.mostwanted.common.Periods;
import co.xenastudios.mostwanted.common.Stat;
import co.xenastudios.mostwanted.common.db.tables.records.PlayersRecord;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import org.jooq.DSLContext;
import org.jooq.Record3;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;

/**
 * Player pages and leaderboards, read from the tables core writes. Players who chose
 * {@code /stats private} are left out of both, as if they'd never played.
 */
public final class Stats {

    /** The periods a board or page can show, as the API names them. */
    public enum Period { ALL, MONTH, WEEK;

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }

        /** Null if there's no such period. */
        public static Period byKey(String key) {
            for (Period period : values()) {
                if (period.key().equals(key)) {
                    return period;
                }
            }
            return null;
        }
    }

    /** {@code supporter} is their store rank ("vip", "vip-plus", "mvp", "legend"), or null. */
    public record Profile(UUID uuid, String name, boolean bedrock, String role, long bounty, Instant firstJoined,
            Instant lastSeen, Map<String, Map<String, Long>> stats, String supporter) {}

    /** LuckPerms' store groups, highest first, and the key the website knows each by. */
    private static final List<String> RANKS = List.of("legend", "mvp", "vip_plus", "vip");

    public record Entry(int rank, UUID uuid, String name, long value) {}

    private final DSLContext sql;
    private final ZoneId zone;
    private final Clock clock;

    /** {@code zone} must match core's clock.timezone, or the month and week won't line up. */
    public Stats(DSLContext sql, ZoneId zone, Clock clock) {
        this.sql = sql;
        this.zone = zone;
        this.clock = clock;
    }

    /** The stats.period value for a period, as of now. */
    String periodKey(Period period) {
        Instant now = clock.instant();
        return switch (period) {
            case ALL -> Periods.ALL;
            case MONTH -> Periods.month(now, zone);
            case WEEK -> Periods.week(now, zone);
        };
    }

    /** By name, ignoring case. A name used by several players over time finds the latest. */
    public Optional<Profile> player(String name) {
        PlayersRecord row = sql.selectFrom(PLAYERS)
                .where(DSL.lower(PLAYERS.NAME).eq(name.toLowerCase(Locale.ROOT)))
                .and(PLAYERS.STATS_PRIVATE.isFalse())
                .orderBy(PLAYERS.LAST_SEEN.desc())
                .limit(1)
                .fetchOne();
        if (row == null) {
            return Optional.empty();
        }
        Map<String, Period> periods = new LinkedHashMap<>();
        Map<String, Map<String, Long>> stats = new LinkedHashMap<>();
        for (Period period : Period.values()) {
            periods.put(periodKey(period), period);
            Map<String, Long> values = new LinkedHashMap<>();
            for (Stat stat : Stat.values()) {
                values.put(stat.key(), 0L);
            }
            stats.put(period.key(), values);
        }
        sql.select(STATS.STAT, STATS.PERIOD, STATS.VALUE)
                .from(STATS)
                .where(STATS.UUID.eq(row.getUuid()))
                .and(STATS.PERIOD.in(periods.keySet()))
                .forEach(r -> {
                    Map<String, Long> values = stats.get(periods.get(r.value2()).key());
                    // Only stats core still writes: an old key isn't shown.
                    values.computeIfPresent(r.value1(), (key, old) -> r.value3());
                });
        return Optional.of(new Profile(row.getUuid(), row.getName(), row.getBedrock(), row.getRole(),
                row.getBounty(), row.getFirstJoined().toInstant(), row.getLastSeen().toInstant(), stats,
                supporter(row.getUuid())));
    }

    /**
     * Their highest store rank, from LuckPerms' own table (it shares our database in
     * production). Null without one, or when LuckPerms isn't there, as in development.
     */
    String supporter(UUID uuid) {
        List<String> groups;
        try {
            groups = sql.select(DSL.field("permission", String.class))
                    .from(DSL.table("luckperms_user_permissions"))
                    .where(DSL.field("uuid", String.class).eq(uuid.toString()))
                    .and(DSL.field("value", Boolean.class).isTrue())
                    .and(DSL.field("permission", String.class).in(RANKS.stream().map(rank -> "group." + rank).toList()))
                    .and(DSL.field("server", String.class).in("global", "mostwanted"))
                    .and(DSL.field("expiry", Long.class).eq(0L)
                            .or(DSL.field("expiry", Long.class).gt(clock.instant().getEpochSecond())))
                    .fetch(DSL.field("permission", String.class));
        } catch (DataAccessException e) {
            return null;
        }
        for (String rank : RANKS) {
            if (groups.contains("group." + rank)) {
                return rank.replace('_', '-');
            }
        }
        return null;
    }

    /** The top {@code limit} on a board, highest first; ties by name. Zero values aren't listed. */
    public List<Entry> board(Board board, Period period, int limit) {
        List<Record3<UUID, String, Long>> rows;
        if (board.stat() == null) {
            rows = sql.select(PLAYERS.UUID, PLAYERS.NAME, PLAYERS.BOUNTY)
                    .from(PLAYERS)
                    .where(PLAYERS.BOUNTY.gt(0L))
                    .and(PLAYERS.STATS_PRIVATE.isFalse())
                    .orderBy(PLAYERS.BOUNTY.desc(), PLAYERS.NAME)
                    .limit(limit)
                    .fetch();
        } else {
            rows = sql.select(PLAYERS.UUID, PLAYERS.NAME, STATS.VALUE)
                    .from(STATS)
                    .join(PLAYERS).on(PLAYERS.UUID.eq(STATS.UUID))
                    .where(STATS.STAT.eq(board.stat().key()))
                    .and(STATS.PERIOD.eq(periodKey(period)))
                    .and(STATS.VALUE.gt(0L))
                    .and(PLAYERS.STATS_PRIVATE.isFalse())
                    .orderBy(STATS.VALUE.desc(), PLAYERS.NAME)
                    .limit(limit)
                    .fetch();
        }
        return IntStream.range(0, rows.size())
                .mapToObj(i -> new Entry(i + 1, rows.get(i).value1(), rows.get(i).value2(), rows.get(i).value3()))
                .toList();
    }

    /** For /health: true if a trivial query answers. */
    public boolean databaseUp() {
        try {
            sql.selectOne().fetch();
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
