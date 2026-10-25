package co.xenastudios.prisonbreak;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PrisonBreakCommandTest {

    @Test
    void infoNamesThePluginAndServer() {
        assertEquals("PrisonBreak 0.1.0 on Leaf 26.3", PrisonBreakCommand.info("0.1.0", "Leaf", "26.3"));
    }
}
