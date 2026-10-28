package co.xenastudios.mostwanted.robbery;

import static co.xenastudios.mostwanted.robbery.RobberySpot.Change.FAILED;
import static co.xenastudios.mostwanted.robbery.RobberySpot.Change.FINISHED;
import static co.xenastudios.mostwanted.robbery.RobberySpot.Change.NONE;
import static co.xenastudios.mostwanted.robbery.RobberySpot.Change.OPENED;
import static co.xenastudios.mostwanted.robbery.RobberySpot.Change.STARTED;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class RobberySpotTest {

    private static final Duration TIME = Duration.ofSeconds(3);
    private static final Duration CLOSED = Duration.ofMinutes(10);

    private final RobberySpot spot = new RobberySpot();
    private Instant now = Instant.parse("2026-10-27T20:00:00Z");

    private RobberySpot.Change step(boolean robbers) {
        now = now.plusSeconds(1);
        return spot.step(now, robbers, TIME, CLOSED);
    }

    @Test
    void staysOpenUntilARobberWalksIn() {
        assertEquals(NONE, step(false));
        assertEquals(RobberySpot.State.OPEN, spot.state());
        assertEquals(STARTED, step(true));
    }

    @Test
    void robbersWhoStayTheWholeTimeGetAway() {
        step(true);
        assertEquals(NONE, step(true));
        assertEquals(NONE, step(true));
        assertEquals(2f / 3, spot.done(TIME), 0.001);
        assertEquals(Duration.ofSeconds(1), spot.left(TIME));
        assertEquals(FINISHED, step(true));
        assertEquals(RobberySpot.State.CLOSED, spot.state());
    }

    @Test
    void itFailsTheMomentNoRobberIsInside() {
        step(true);
        step(true);
        assertEquals(FAILED, step(false));
        assertEquals(RobberySpot.State.CLOSED, spot.state());
    }

    @Test
    void itReopensWhenTheClosedTimeIsUp() {
        step(true);
        step(false);
        now = now.plus(CLOSED).minusSeconds(2);
        assertEquals(NONE, step(true), "still closed: walking in does nothing");
        assertEquals(OPENED, step(false));
        assertEquals(STARTED, step(true));
    }
}
