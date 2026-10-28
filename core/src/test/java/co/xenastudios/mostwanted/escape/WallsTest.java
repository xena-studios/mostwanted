package co.xenastudios.mostwanted.escape;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class WallsTest {

    private static final Instant T = Instant.parse("2026-10-27T20:00:00Z");
    private static final Duration GAP = Duration.ofMillis(1500);

    @Test
    void punchesInARowAddUpAndAPauseStartsOver() {
        assertEquals(6, Walls.hits(5, T, T.plusMillis(400), GAP));
        assertEquals(6, Walls.hits(5, T, T.plusMillis(1500), GAP), "right on the gap still counts");
        assertEquals(1, Walls.hits(19, T, T.plusMillis(1501), GAP));
    }
}
