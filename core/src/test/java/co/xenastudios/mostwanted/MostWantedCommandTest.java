package co.xenastudios.mostwanted;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MostWantedCommandTest {

    @Test
    void infoNamesThePluginAndServer() {
        assertEquals("MostWanted 0.1.0 on Leaf 26.3", MostWantedCommand.info("0.1.0", "Leaf", "26.3"));
    }
}
