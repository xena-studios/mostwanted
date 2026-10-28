package co.xenastudios.mostwanted.player;

import co.xenastudios.mostwanted.common.style.Palette;
import java.util.Locale;

/** What a player is right now. Stored as its lower-case name (the players.role column). */
public enum Role {
    /** Inside the prison. Everyone starts here. */
    INMATE(Palette.INMATE),
    /** Escaped, out in the world. */
    CRIMINAL(Palette.CRIMINAL),
    /** On duty as a guard. */
    GUARD(Palette.GUARD);

    private final int colour;

    Role(int colour) {
        this.colour = colour;
    }

    public int colour() {
        return colour;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** "Inmate". */
    public String displayName() {
        return name().charAt(0) + key().substring(1);
    }

    public static Role fromKey(String key) {
        return valueOf(key.toUpperCase(Locale.ROOT));
    }
}
