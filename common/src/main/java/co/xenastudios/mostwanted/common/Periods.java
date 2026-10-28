package co.xenastudios.mostwanted.common;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.IsoFields;
import java.util.List;

/**
 * The periods each stat is counted in: all-time, the month ("2026-10") and the ISO
 * week ("2026-W44"), in the server's timezone. core writes them; the API reads them.
 */
public final class Periods {

    public static final String ALL = "all";

    private Periods() {}

    /** All-time, this month, this week: the stats.period values a stat is added to now. */
    public static List<String> of(Instant now, ZoneId zone) {
        return List.of(ALL, month(now, zone), week(now, zone));
    }

    public static String month(Instant now, ZoneId zone) {
        LocalDate date = now.atZone(zone).toLocalDate();
        return String.format("%04d-%02d", date.getYear(), date.getMonthValue());
    }

    public static String week(Instant now, ZoneId zone) {
        LocalDate date = now.atZone(zone).toLocalDate();
        return String.format("%04d-W%02d",
                date.get(IsoFields.WEEK_BASED_YEAR), date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
    }
}
