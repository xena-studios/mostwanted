package co.xenastudios.mostwanted.player;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class SettingTest {

    @Test
    void everythingStartsOnAndPublic() {
        PlayerState state = PlayerState.fresh(UUID.randomUUID(), "Alex");
        for (Setting setting : Setting.values()) {
            assertTrue(state.on(setting), setting.key());
        }
        assertFalse(state.statsPrivate());
    }

    @Test
    void publicStatsIsTheOtherWayRoundOfPrivate() {
        PlayerState state = PlayerState.fresh(UUID.randomUUID(), "Alex");
        state.set(Setting.STATS, false);
        assertTrue(state.statsPrivate());
        state.set(Setting.SCOREBOARD, false);
        assertFalse(state.on(Setting.SCOREBOARD));
        assertTrue(state.on(Setting.BROADCASTS));
    }
}
