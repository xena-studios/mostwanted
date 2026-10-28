package co.xenastudios.mostwanted.player;

import co.xenastudios.mostwanted.common.Stat;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * One online player's game state, kept in memory while they play. Gameplay reads
 * and changes it on the main thread only; {@link PlayerStore} writes changes to the
 * database in the background.
 */
public final class PlayerState {

    private final UUID uuid;
    private String name;
    private Role role;
    private long cash;
    private long bank;
    private long bounty;
    private Instant jailedUntil;
    /** The other side's money while on or off duty: see {@link #swapStash}. */
    private long stashedCash;
    private long stashedBank;
    /** Hidden from the website, Discord and every leaderboard (/stats private). */
    private boolean statsPrivate;
    /** The rest of the settings menu: see {@link Setting}. */
    private boolean showScoreboard;
    private boolean showBroadcasts;
    private boolean showJoins;
    /** All-time totals, for the scoreboard. */
    private final Map<Stat, Long> totals;
    /** Added since the last save, for every period. */
    private final Map<Stat, Long> unsaved = new EnumMap<>(Stat.class);
    private boolean dirty;
    private boolean hudDirty = true;

    PlayerState(UUID uuid, String name, Role role, long cash, long bank, long bounty, Instant jailedUntil,
            long stashedCash, long stashedBank, boolean statsPrivate, boolean showScoreboard, boolean showBroadcasts,
            boolean showJoins, Map<Stat, Long> totals) {
        this.uuid = uuid;
        this.name = name;
        this.role = role;
        this.cash = cash;
        this.bank = bank;
        this.bounty = bounty;
        this.jailedUntil = jailedUntil;
        this.stashedCash = stashedCash;
        this.stashedBank = stashedBank;
        this.statsPrivate = statsPrivate;
        this.showScoreboard = showScoreboard;
        this.showBroadcasts = showBroadcasts;
        this.showJoins = showJoins;
        this.totals = new EnumMap<>(Stat.class);
        this.totals.putAll(totals);
    }

    /** A new player: an inmate with nothing. */
    static PlayerState fresh(UUID uuid, String name) {
        return new PlayerState(uuid, name, Role.INMATE, 0, 0, 0, null, 0, 0, false, true, true, true, Map.of());
    }

    public UUID uuid() { return uuid; }
    public String name() { return name; }
    public Role role() { return role; }
    public long cash() { return cash; }
    public long bank() { return bank; }
    public long bounty() { return bounty; }
    public Instant jailedUntil() { return jailedUntil; }
    public long stashedCash() { return stashedCash; }
    public long stashedBank() { return stashedBank; }
    public boolean statsPrivate() { return statsPrivate; }

    public void statsPrivate(boolean statsPrivate) {
        this.statsPrivate = statsPrivate;
        changed();
    }

    /** Whether a setting is on (for {@link Setting#STATS}: whether their stats are public). */
    public boolean on(Setting setting) {
        return switch (setting) {
            case STATS -> !statsPrivate;
            case SCOREBOARD -> showScoreboard;
            case BROADCASTS -> showBroadcasts;
            case JOINS -> showJoins;
        };
    }

    public void set(Setting setting, boolean on) {
        switch (setting) {
            case STATS -> statsPrivate = !on;
            case SCOREBOARD -> showScoreboard = on;
            case BROADCASTS -> showBroadcasts = on;
            case JOINS -> showJoins = on;
        }
        changed();
    }

    /** Whether they're serving a sentence at this moment. */
    public boolean jailed(Instant now) {
        return jailedUntil != null && jailedUntil.isAfter(now);
    }

    public long total(Stat stat) {
        return totals.getOrDefault(stat, 0L);
    }

    public void role(Role role) {
        if (this.role != Objects.requireNonNull(role)) {
            this.role = role;
            changed();
        }
    }

    public void cash(long cash) {
        this.cash = nonNegative(cash, "cash");
        changed();
    }

    public void bank(long bank) {
        this.bank = nonNegative(bank, "bank");
        changed();
    }

    public void bounty(long bounty) {
        this.bounty = nonNegative(bounty, "bounty");
        changed();
    }

    public void jailedUntil(Instant until) {
        this.jailedUntil = until;
        changed();
    }

    /** Going on or off duty: guard money and inmate/criminal money trade places. */
    public void swapStash() {
        long cash = this.cash;
        long bank = this.bank;
        this.cash = stashedCash;
        this.bank = stashedBank;
        stashedCash = cash;
        stashedBank = bank;
        changed();
    }

    /** Adds to a stat in every period. */
    public void add(Stat stat, long amount) {
        if (amount == 0) {
            return;
        }
        totals.merge(stat, amount, Long::sum);
        unsaved.merge(stat, amount, Long::sum);
        changed();
    }

    void name(String name) {
        this.name = name;
    }

    private void changed() {
        dirty = true;
        hudDirty = true;
    }

    boolean dirty() { return dirty; }

    /** Added to a stat since the last save: what the database doesn't have yet. */
    long unsaved(Stat stat) {
        return unsaved.getOrDefault(stat, 0L);
    }

    /** Whether the scoreboard needs redrawing; clears the flag. */
    public boolean takeHudDirty() {
        boolean was = hudDirty;
        hudDirty = false;
        return was;
    }

    public void markHudDirty() {
        hudDirty = true;
    }

    /** What to save, taken on the main thread; clears the dirty flag and the unsaved stats. */
    Snapshot snapshot() {
        Snapshot snapshot = new Snapshot(uuid, name, role, cash, bank, bounty, jailedUntil, stashedCash, stashedBank,
                statsPrivate, showScoreboard, showBroadcasts, showJoins, Map.copyOf(unsaved));
        unsaved.clear();
        dirty = false;
        return snapshot;
    }

    /** A save failed: its stats go back to be saved next time. */
    void restore(Snapshot failed) {
        failed.stats().forEach((stat, amount) -> unsaved.merge(stat, amount, Long::sum));
        dirty = true;
    }

    private static long nonNegative(long value, String what) {
        if (value < 0) {
            throw new IllegalArgumentException(what + " can't be negative: " + value);
        }
        return value;
    }

    /** An immutable copy of the state to write, safe to hand to another thread. */
    record Snapshot(UUID uuid, String name, Role role, long cash, long bank, long bounty, Instant jailedUntil,
            long stashedCash, long stashedBank, boolean statsPrivate, boolean showScoreboard, boolean showBroadcasts,
            boolean showJoins, Map<Stat, Long> stats) {}
}
