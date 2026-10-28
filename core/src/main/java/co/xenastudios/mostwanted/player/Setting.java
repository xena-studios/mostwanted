package co.xenastudios.mostwanted.player;

/**
 * What a player can turn on or off in the settings menu. Each is "on" by default;
 * {@link #STATS} on means their stats are public.
 */
public enum Setting {
    /** Their page and leaderboard places, on the website, in Discord and in game (/stats). */
    STATS("stats"),
    /** The sidebar. */
    SCOREBOARD("scoreboard"),
    /** Chat broadcasts: robberies, riots, escapes, arrests. Guards' alarms aren't these. */
    BROADCASTS("broadcasts"),
    /** Store ranks' join announcements. */
    JOINS("joins");

    private final String key;

    Setting(String key) {
        this.key = key;
    }

    /** The messages.yml key under settings. */
    public String key() {
        return key;
    }
}
