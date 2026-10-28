package co.xenastudios.mostwanted.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class HudTest {

    @Test
    void wantedStarsFollowTheBounty() {
        assertEquals(0, Hud.stars(0));
        assertEquals(1, Hud.stars(500));
        assertEquals(3, Hud.stars(5_000));
        assertEquals(5, Hud.stars(1_000_000));
    }

    @Test
    void timersRoundUpToTheSecond() {
        assertEquals("2:14", Hud.clockTime(Duration.ofMillis(133_400)));
        assertEquals("0:00", Hud.clockTime(Duration.ofSeconds(-5)));
        assertEquals("1:02:05", Hud.clockTime(Duration.ofSeconds(3_725)));
    }
}
