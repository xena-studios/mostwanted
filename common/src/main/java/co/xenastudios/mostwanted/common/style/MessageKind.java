package co.xenastudios.mostwanted.common.style;

/**
 * The kinds of everyday message: a coloured symbol, then grey text. The same in game,
 * on Discord and on the website. See docs/09-brand.md#messages.
 */
public enum MessageKind {
    /** Done, turned on. */
    SUCCESS("✔", Palette.SUCCESS),
    /** Refused, error. */
    ERROR("✖", Palette.ERROR),
    /** Money, bounty. */
    MONEY("★", Palette.MONEY),
    /** An alarm or arrest on the criminals' side. */
    ALARM_CRIMINAL("⚑", Palette.CRIMINAL),
    /** An alarm or arrest on the guards' side. */
    ALARM_GUARD("⚑", Palette.GUARD),
    /** Info, tips, links. */
    INFO("ⓘ", Palette.INFO),
    /** Private messages. */
    PRIVATE("✉", Palette.INFO);

    private final String symbol;
    private final int colour;

    MessageKind(String symbol, int colour) {
        this.symbol = symbol;
        this.colour = colour;
    }

    public String symbol() {
        return symbol;
    }

    /** RGB, from the {@link Palette}. */
    public int colour() {
        return colour;
    }
}
