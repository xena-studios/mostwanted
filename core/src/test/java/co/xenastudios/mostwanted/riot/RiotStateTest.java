package co.xenastudios.mostwanted.riot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RiotStateTest {

    static final Duration WINDOW = Duration.ofSeconds(30);
    static final Duration LENGTH = Duration.ofMinutes(5);
    static final Duration COOLDOWN = Duration.ofMinutes(30);
    static final Instant T = Instant.parse("2026-10-27T20:00:00Z");

    final RiotState riot = new RiotState();
    final UUID a = UUID.randomUUID();
    final UUID b = UUID.randomUUID();
    final UUID c = UUID.randomUUID();

    RiotState.Press press(UUID who, Instant at) {
        return riot.press(who, at, 3, WINDOW, LENGTH, true);
    }

    @Test
    void threeInmatesTogetherStartIt() {
        assertEquals(RiotState.Press.COUNTED, press(a, T));
        assertEquals(RiotState.Press.COUNTED, press(a, T.plusSeconds(1)), "the same inmate twice counts once");
        assertEquals(RiotState.Press.COUNTED, press(b, T.plusSeconds(5)));
        assertEquals(RiotState.Press.STARTED, press(c, T.plusSeconds(10)));
        assertTrue(riot.active());
        assertEquals(Set.of(a, b, c), riot.rioters());
        assertEquals(RiotState.Press.ALREADY, press(a, T.plusSeconds(11)));
    }

    @Test
    void pressesTooFarApartDontAddUp() {
        press(a, T);
        press(b, T.plusSeconds(10));
        assertEquals(RiotState.Press.COUNTED, press(c, T.plusSeconds(35)), "a's press has run out");
        assertFalse(riot.active());
    }

    @Test
    void noGuardsNoRiot() {
        assertEquals(RiotState.Press.NO_GUARDS, riot.press(a, T, 3, WINDOW, LENGTH, false));
    }

    @Test
    void itEndsOnTimeOrWhenEveryRioterIsCaughtThenCoolsDown() {
        press(a, T);
        press(b, T);
        press(c, T);
        assertEquals(RiotState.End.NONE, riot.tick(T.plusSeconds(60), who -> who.equals(a), COOLDOWN));
        assertEquals(RiotState.End.QUELLED, riot.tick(T.plusSeconds(61), who -> false, COOLDOWN));
        assertFalse(riot.active());
        assertEquals(RiotState.Press.COOLDOWN, press(a, T.plusSeconds(62)));

        Instant later = T.plus(COOLDOWN).plusSeconds(62);
        press(a, later);
        press(b, later);
        press(c, later);
        assertEquals(RiotState.End.TIME_UP, riot.tick(later.plus(LENGTH), who -> true, COOLDOWN));
    }

    @Test
    void inmatesWhoJoinInCount() {
        UUID late = UUID.randomUUID();
        riot.join(late);
        assertFalse(riot.rioters().contains(late), "no riot to join");
        press(a, T);
        press(b, T);
        press(c, T);
        riot.join(late);
        assertTrue(riot.rioters().contains(late));
    }
}
