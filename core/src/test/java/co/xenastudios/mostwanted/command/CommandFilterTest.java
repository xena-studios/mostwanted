package co.xenastudios.mostwanted.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class CommandFilterTest {

    private final CommandFilter filter = new CommandFilter(List.of("menu", "/MSG ", "r"), null);

    @Test
    void onlyTheListedCommands() {
        assertTrue(filter.allows("menu"));
        assertTrue(filter.allows("Msg"));
        assertFalse(filter.allows("plugins"));
        assertFalse(filter.allows("version"));
    }

    @Test
    void namespacedFormsAreRefusedEvenForAllowedCommands() {
        assertEquals("zchat:msg", CommandFilter.label("/zchat:msg Alex hi"));
        assertFalse(filter.allows(CommandFilter.label("/zchat:msg Alex hi")));
        assertFalse(filter.allows(CommandFilter.label("/minecraft:me waves")));
        assertEquals("msg", CommandFilter.label("/MSG Alex hi"));
        assertEquals("menu", CommandFilter.label("/menu"));
    }
}
