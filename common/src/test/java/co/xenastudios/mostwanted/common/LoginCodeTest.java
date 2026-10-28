package co.xenastudios.mostwanted.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class LoginCodeTest {

    @Test
    void codesAreSixReadableCharacters() {
        for (int i = 0; i < 200; i++) {
            String code = LoginCode.generate();
            assertEquals(code, LoginCode.normalize(code));
        }
    }

    @Test
    void typedCodesForgiveCaseSpacesAndDashes() {
        assertEquals("ABC234", LoginCode.normalize(" abc-234 "));
        assertNull(LoginCode.normalize("ABC23"), "too short");
        assertNull(LoginCode.normalize("ABC230"), "0 is never in a code");
        assertNull(LoginCode.normalize(null));
    }
}
