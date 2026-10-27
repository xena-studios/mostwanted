package co.xenastudios.mostwanted.common.style;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class PaletteTest {

    /** The table in docs/09-brand.md#in-game-colours, hue 0 to 345. */
    private static final String[] DOCUMENTED = {
        "#ff3333", "#ff6633", "#ff9933", "#ffcc33", "#ffff33", "#ccff33", "#99ff33", "#66ff33",
        "#33ff33", "#33ff66", "#33ff99", "#33ffcc", "#33ffff", "#33ccff", "#3399ff", "#3366ff",
        "#3333ff", "#6633ff", "#9933ff", "#cc33ff", "#ff33ff", "#ff33cc", "#ff3399", "#ff3366",
    };

    @Test
    void everyHueMatchesTheBrandDoc() {
        for (int i = 0; i < DOCUMENTED.length; i++) {
            assertEquals(DOCUMENTED[i], Palette.hex(Palette.hue(i * 15)), "hue " + i * 15);
        }
    }

    @Test
    void rolesUseTheirHues() {
        assertEquals("#ff9933", Palette.hex(Palette.BRAND));
        assertEquals("#3399ff", Palette.hex(Palette.GUARD));
        assertEquals("#ff3333", Palette.hex(Palette.CRIMINAL));
    }

    @Test
    void refusesColoursOffThePalette() {
        assertThrows(IllegalArgumentException.class, () -> Palette.hue(10));
        assertThrows(IllegalArgumentException.class, () -> Palette.hue(360));
        assertThrows(IllegalArgumentException.class, () -> Palette.hue(-15));
    }
}
