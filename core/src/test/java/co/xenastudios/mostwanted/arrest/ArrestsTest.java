package co.xenastudios.mostwanted.arrest;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class ArrestsTest {

    @Test
    void sentencesReadNaturally() {
        assertEquals("1 minute", Arrests.minutes(Duration.ofMinutes(1)));
        assertEquals("5 minutes", Arrests.minutes(Duration.ofMinutes(5)));
        assertEquals("90 seconds", Arrests.minutes(Duration.ofSeconds(90)));
    }
}
