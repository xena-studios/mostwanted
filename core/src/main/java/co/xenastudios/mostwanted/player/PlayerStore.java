package co.xenastudios.mostwanted.player;

import static co.xenastudios.mostwanted.common.db.Tables.PLAYERS;
import static co.xenastudios.mostwanted.common.db.Tables.STATS;

import co.xenastudios.mostwanted.common.Periods;
import co.xenastudios.mostwanted.common.Stat;
import co.xenastudios.mostwanted.storage.Storage;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jooq.DSLContext;
import org.jooq.Query;
import org.jooq.impl.DSL;

/**
 * Online players' state: loaded before they join, kept in memory, written back in
 * batches. Loads run on the login thread; everything else on the main thread, with
 * the writes themselves on {@link Storage}'s writer.
 */
public final class PlayerStore {

    private final Storage storage;
    private final Executor mainThread;
    private final ZoneId zone;
    private final Clock clock;
    private final Logger log;
    private final Map<UUID, PlayerState> online = new ConcurrentHashMap<>();
    /** Players whose quit save hasn't finished: they can't load again until it has. */
    private final Map<UUID, CompletableFuture<Void>> leaving = new ConcurrentHashMap<>();

    public PlayerStore(Storage storage, Executor mainThread, ZoneId zone, Clock clock, Logger log) {
        this.storage = storage;
        this.mainThread = mainThread;
        this.zone = zone;
        this.clock = clock;
        this.log = log;
    }

    /**
     * Loads (or creates) a player's row, on the login thread. Waits for their last
     * quit save first, so a quick rejoin never loads old data. Throws if the database
     * can't be reached: they're refused rather than playing on a blank state that
     * would overwrite their real one.
     *
     * @return whether it's their first time, or that they're still connected (refuse them:
     *     loading now would be overwritten when the old connection quits)
     */
    public Loaded load(UUID uuid, String name) {
        if (online.containsKey(uuid)) {
            return Loaded.ALREADY_ONLINE;
        }
        CompletableFuture<Void> lastSave = leaving.get(uuid);
        if (lastSave != null) {
            try {
                lastSave.get(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.warning("Loading " + name + " before their last save finished: " + e.getMessage());
            }
        }
        DSLContext sql = storage.sql();
        // Bedrock players get Floodgate-style UUIDs, which start with 64 zero bits.
        // (How Minehut marks them is still open, docs/05-open-questions.md; this is the
        // standard, so the website shows them a default head rather than a Java skin.)
        boolean bedrock = uuid.getMostSignificantBits() == 0;
        var row = sql.insertInto(PLAYERS)
                .set(PLAYERS.UUID, uuid)
                .set(PLAYERS.NAME, name)
                .set(PLAYERS.BEDROCK, bedrock)
                .onConflict(PLAYERS.UUID)
                .doUpdate()
                .set(PLAYERS.NAME, name)
                .set(PLAYERS.BEDROCK, bedrock)
                .set(PLAYERS.LAST_SEEN, DSL.currentOffsetDateTime())
                .returning()
                .fetchSingle();
        Map<Stat, Long> totals = new EnumMap<>(Stat.class);
        for (Stat stat : Stat.values()) {
            totals.put(stat, 0L);
        }
        sql.select(STATS.STAT, STATS.VALUE)
                .from(STATS)
                .where(STATS.UUID.eq(uuid).and(STATS.PERIOD.eq(Periods.ALL)))
                .forEach(r -> {
                    for (Stat stat : Stat.values()) {
                        if (stat.key().equals(r.value1())) {
                            totals.put(stat, r.value2());
                        }
                    }
                });
        OffsetDateTime jailed = row.getJailedUntil();
        online.put(uuid, new PlayerState(uuid, name, Role.fromKey(row.getRole()), row.getCash(), row.getBank(),
                row.getBounty(), jailed == null ? null : jailed.toInstant(), row.getStashedCash(),
                row.getStashedBank(), row.getStatsPrivate(), row.getShowScoreboard(), row.getShowBroadcasts(),
                row.getShowJoins(), totals));
        // Inserted just now: both default to the same now().
        return row.getFirstJoined().equals(row.getLastSeen()) ? Loaded.FIRST_TIME : Loaded.RETURNING;
    }

    public enum Loaded { FIRST_TIME, RETURNING, ALREADY_ONLINE }

    /** Drops a loaded player who then wasn't let in (banned, server full). */
    public void forget(UUID uuid) {
        online.remove(uuid);
    }

    public Optional<PlayerState> get(UUID uuid) {
        return Optional.ofNullable(online.get(uuid));
    }

    public Collection<PlayerState> all() {
        return online.values();
    }

    /** How many are playing this role. */
    public int count(Role role) {
        int count = 0;
        for (PlayerState state : online.values()) {
            if (state.role() == role) {
                count++;
            }
        }
        return count;
    }

    /** Who wants to see what a setting covers: everyone with it on, and anyone not loaded. */
    public Predicate<Player> showing(Setting setting) {
        return player -> {
            PlayerState state = online.get(player.getUniqueId());
            return state == null || state.on(setting);
        };
    }

    /** Everyone online playing this role: who gets an alarm, say. */
    public List<Player> online(Role role) {
        List<Player> found = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerState state = online.get(player.getUniqueId());
            if (state != null && state.role() == role) {
                found.add(player);
            }
        }
        return found;
    }

    /** Writes every changed player, on the main thread's schedule. */
    public void saveChanged() {
        List<PlayerState> changed = online.values().stream().filter(PlayerState::dirty).toList();
        if (changed.isEmpty()) {
            return;
        }
        List<PlayerState.Snapshot> snapshots = changed.stream().map(PlayerState::snapshot).toList();
        write(snapshots).exceptionally(e -> {
            // Put the changes back so the next save tries again.
            mainThread.execute(() -> snapshots.forEach(s -> {
                PlayerState state = online.get(s.uuid());
                if (state != null) {
                    state.restore(s);
                } else if (!s.stats().isEmpty()) {
                    // They quit since, and their quit save didn't have these: save the stats on their own.
                    writeStatsWithRetries(s.uuid(), s.stats(), 3);
                }
            }));
            return null;
        });
    }

    /** Saves and drops a player who quit. Retried a few times: their session's changes are at stake. */
    public void quit(UUID uuid) {
        PlayerState state = online.remove(uuid);
        if (state == null) {
            return;
        }
        PlayerState.Snapshot snapshot = state.snapshot();
        CompletableFuture<Void> saved = writeWithRetries(snapshot, 3);
        leaving.put(uuid, saved);
        saved.whenComplete((ok, e) -> leaving.remove(uuid, saved));
    }

    /** Saves everyone, for shutdown. Blocks until the writes are queued; Storage.close waits for them. */
    public void saveAll() {
        List<PlayerState.Snapshot> snapshots = online.values().stream().map(PlayerState::snapshot).toList();
        if (!snapshots.isEmpty()) {
            write(snapshots);
        }
        online.clear();
    }

    private CompletableFuture<Void> writeWithRetries(PlayerState.Snapshot snapshot, int attempts) {
        return write(List.of(snapshot)).exceptionallyCompose(e -> {
            if (attempts > 1) {
                return writeWithRetries(snapshot, attempts - 1);
            }
            log.severe("Gave up saving " + snapshot.name() + " (" + snapshot.uuid() + "): " + snapshot);
            return CompletableFuture.failedFuture(e);
        });
    }

    /** Stats only: the rest of the row may already have a newer save. */
    private void writeStatsWithRetries(UUID uuid, Map<Stat, Long> stats, int attempts) {
        List<String> periods = Periods.of(clock.instant(), zone);
        storage.write("stats of " + uuid, sql -> {
            List<Query> queries = new ArrayList<>();
            addStats(sql, queries, uuid, stats, periods);
            sql.batch(queries).execute();
        }).exceptionally(e -> {
            if (attempts > 1) {
                writeStatsWithRetries(uuid, stats, attempts - 1);
            } else {
                log.severe("Gave up saving the stats of " + uuid + ": " + stats);
            }
            return null;
        });
    }

    private static void addStats(DSLContext sql, List<Query> queries, UUID uuid, Map<Stat, Long> stats,
            List<String> periods) {
        stats.forEach((stat, amount) -> {
            for (String period : periods) {
                queries.add(sql.insertInto(STATS)
                        .set(STATS.UUID, uuid)
                        .set(STATS.STAT, stat.key())
                        .set(STATS.PERIOD, period)
                        .set(STATS.VALUE, amount)
                        .onConflict(STATS.UUID, STATS.STAT, STATS.PERIOD)
                        .doUpdate()
                        .set(STATS.VALUE, STATS.VALUE.plus(amount)));
            }
        });
    }

    private CompletableFuture<Void> write(List<PlayerState.Snapshot> snapshots) {
        List<String> periods = Periods.of(clock.instant(), zone);
        return storage.write(snapshots.size() + " player(s)", sql -> {
            List<Query> queries = new ArrayList<>();
            for (PlayerState.Snapshot s : snapshots) {
                queries.add(sql.update(PLAYERS)
                        .set(PLAYERS.NAME, s.name())
                        .set(PLAYERS.ROLE, s.role().key())
                        .set(PLAYERS.CASH, s.cash())
                        .set(PLAYERS.BANK, s.bank())
                        .set(PLAYERS.BOUNTY, s.bounty())
                        .set(PLAYERS.JAILED_UNTIL, offset(s.jailedUntil()))
                        .set(PLAYERS.STASHED_CASH, s.stashedCash())
                        .set(PLAYERS.STASHED_BANK, s.stashedBank())
                        .set(PLAYERS.STATS_PRIVATE, s.statsPrivate())
                        .set(PLAYERS.SHOW_SCOREBOARD, s.showScoreboard())
                        .set(PLAYERS.SHOW_BROADCASTS, s.showBroadcasts())
                        .set(PLAYERS.SHOW_JOINS, s.showJoins())
                        .set(PLAYERS.LAST_SEEN, DSL.currentOffsetDateTime())
                        .where(PLAYERS.UUID.eq(s.uuid())));
                addStats(sql, queries, s.uuid(), s.stats(), periods);
            }
            sql.batch(queries).execute();
        });
    }

    private static OffsetDateTime offset(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }
}
