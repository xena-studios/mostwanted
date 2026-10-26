package co.xenastudios.mostwanted.common.style;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void dollarsWithCommas() {
        assertEquals("$0", Money.format(0));
        assertEquals("$950", Money.format(950));
        assertEquals("$1,200", Money.format(1_200));
        assertEquals("$2,500,000", Money.format(2_500_000));
        assertEquals("-$1,200", Money.format(-1_200));
    }
}
