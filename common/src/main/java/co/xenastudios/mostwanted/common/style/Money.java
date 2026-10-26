package co.xenastudios.mostwanted.common.style;

import java.util.Locale;

/** Money is whole dollars, shown the same everywhere: {@code $1,200}. */
public final class Money {

    private Money() {}

    public static String format(long dollars) {
        return String.format(Locale.US, dollars < 0 ? "-$%,d" : "$%,d", Math.abs(dollars));
    }
}
