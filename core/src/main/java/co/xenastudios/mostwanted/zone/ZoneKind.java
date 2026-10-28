package co.xenastudios.mostwanted.zone;

import java.util.Locale;

/** What a zone is for. Stored as its lower-case name (the zones.kind column). */
public enum ZoneKind {
    /** The prison's boundary: an inmate who leaves it has escaped. */
    PRISON,
    /** Where arrested players serve their sentence. */
    CELL,
    /** The police station: guards spawn here, and it's safe for them. */
    POLICE,
    /** A criminal base: safe for criminals, and where they bank their cash. */
    HIDEOUT,
    /** A place to rob, such as the bank. */
    ROBBERY,
    /** No fighting at all, such as the prison's spawn. */
    SAFE,
    /** A weak spot in the prison: inmates can punch through it, and it grows back. */
    WALL,
    /** Bars or doors that open for a guard, or for anyone with a keycard. */
    GATE,
    /** The riot alarm: enough inmates pressing it together start a riot. */
    ALARM;

    /** Small, so walking into one isn't announced. */
    public boolean quiet() {
        return this == WALL || this == GATE;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Null if there's no such kind. */
    public static ZoneKind fromKey(String key) {
        for (ZoneKind kind : values()) {
            if (kind.key().equals(key)) {
                return kind;
            }
        }
        return null;
    }
}
