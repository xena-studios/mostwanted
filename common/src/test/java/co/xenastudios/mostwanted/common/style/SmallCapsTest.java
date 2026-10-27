package co.xenastudios.mostwanted.common.style;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SmallCapsTest {

    @Test
    void lettersInEitherCase() {
        assertEquals("ᴍᴏѕᴛᴡᴀɴᴛᴇᴅᴍᴄ.ᴄᴏᴍ", SmallCaps.of("mostwantedmc.com"));
        assertEquals("ᴘʀᴏꜰɪʟᴇ", SmallCaps.of("PROFILE"));
    }

    @Test
    void keepsEverythingElse() {
        assertEquals("ʙᴏᴜɴᴛʏ: $2,500 ★", SmallCaps.of("Bounty: $2,500 ★"));
        assertEquals("", SmallCaps.of(""));
    }

    @Test
    void everyLetterHasOne() {
        assertEquals(26, SmallCaps.of("abcdefghijklmnopqrstuvwxyz").length());
    }
}
