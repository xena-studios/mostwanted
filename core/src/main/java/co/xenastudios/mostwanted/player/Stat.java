package co.xenastudios.mostwanted.player;

/** The counters in the stats table. Each is kept all-time, per month and per week. */
public enum Stat {
    ESCAPES("escapes"),
    TIMES_ARRESTED("times_arrested"),
    ARRESTS("arrests"),
    BOUNTY_EARNED("bounty_earned"),
    ROBBERIES("robberies"),
    CASH_STOLEN("cash_stolen");

    private final String key;

    Stat(String key) {
        this.key = key;
    }

    /** The stats.stat value. Never change one: it would split a stat's history. */
    public String key() {
        return key;
    }
}
