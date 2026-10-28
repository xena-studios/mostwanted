package co.xenastudios.mostwanted.backend.discord;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ServerStatusTest {

    @Test
    void saysHowManyAreOnOrThatItsDown() {
        assertEquals("12 players on MostWanted · mostwantedmc.com", ServerStatus.text(true, 12));
        assertEquals("1 player on MostWanted · mostwantedmc.com", ServerStatus.text(true, 1));
        assertEquals("0 players on MostWanted · mostwantedmc.com", ServerStatus.text(true, 0));
        assertEquals("Server offline · mostwantedmc.com", ServerStatus.text(false, 12));
    }
}
