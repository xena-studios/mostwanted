package co.xenastudios.mostwanted.player;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class PeriodsTest {

    @Test
    void allTimeMonthAndIsoWeek() {
        assertEquals(List.of("all", "2026-10", "2026-W44"),
                Periods.of(Instant.parse("2026-10-27T12:00:00Z"), ZoneId.of("America/New_York")));
    }

    @Test
    void theServersZoneDecidesTheDay() {
        // Still 2026-12-31 in New York; the ISO week belongs to 2026's week 53.
        assertEquals(List.of("all", "2026-12", "2026-W53"),
                Periods.of(Instant.parse("2027-01-01T03:00:00Z"), ZoneId.of("America/New_York")));
    }
}
