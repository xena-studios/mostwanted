package co.xenastudios.mostwanted.player;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.IsoFields;
import java.util.List;

/** The periods each stat is counted in: all-time, the month ("2026-10") and the ISO week ("2026-W44"). */
final class Periods {

    static final String ALL = "all";

    private Periods() {}

    static List<String> of(Instant now, ZoneId zone) {
        LocalDate date = now.atZone(zone).toLocalDate();
        String month = String.format("%04d-%02d", date.getYear(), date.getMonthValue());
        String week = String.format("%04d-W%02d",
                date.get(IsoFields.WEEK_BASED_YEAR), date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
        return List.of(ALL, month, week);
    }
}
