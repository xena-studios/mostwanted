package co.xenastudios.mostwanted.backend.stats;

import co.xenastudios.mostwanted.common.Stat;

/**
 * The leaderboards the API serves (docs/08-website.md#leaderboards). The ids are in the
 * website's URLs. Fastest escapes, biggest heists and richest come when core records them.
 */
public enum Board {
    /** The highest bounties right now: no periods. */
    MOST_WANTED("most-wanted", null),
    ESCAPES("escapes", Stat.ESCAPES),
    GUARDS("guards", Stat.ARRESTS),
    ROBBERIES("robberies", Stat.ROBBERIES),
    BOUNTY_EARNED("bounty-earned", Stat.BOUNTY_EARNED),
    MOST_ARRESTED("most-arrested", Stat.TIMES_ARRESTED);

    private final String id;
    private final Stat stat;

    Board(String id, Stat stat) {
        this.id = id;
        this.stat = stat;
    }

    public String id() {
        return id;
    }

    /** Null for most-wanted, which reads the bounty itself. */
    public Stat stat() {
        return stat;
    }

    /** Null if there's no such board. */
    public static Board byId(String id) {
        for (Board board : values()) {
            if (board.id.equals(id)) {
                return board;
            }
        }
        return null;
    }
}
