package co.xenastudios.mostwanted.combat;

import static co.xenastudios.mostwanted.combat.CombatRules.canCuff;
import static co.xenastudios.mostwanted.combat.CombatRules.canHurt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.xenastudios.mostwanted.combat.CombatRules.Cuff;
import co.xenastudios.mostwanted.combat.CombatRules.Fighter;
import co.xenastudios.mostwanted.player.Role;
import co.xenastudios.mostwanted.zone.ZoneKind;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CombatRulesTest {

    static Fighter out(Role role) {
        return new Fighter(role, false, Set.of());
    }

    static Fighter in(Role role, ZoneKind zone) {
        return new Fighter(role, false, Set.of(zone));
    }

    @Test
    void theTableInTheGameplayDoc() {
        // Rows attack columns: guard, criminal, inmate.
        boolean[][] expected = {
                {false, true, false},
                {true, false, false},
                {false, false, false},
        };
        Role[] roles = {Role.GUARD, Role.CRIMINAL, Role.INMATE};
        for (int a = 0; a < 3; a++) {
            for (int v = 0; v < 3; v++) {
                assertEquals(expected[a][v], canHurt(out(roles[a]), out(roles[v])), roles[a] + " hurts " + roles[v]);
            }
        }
    }

    @Test
    void safeZonesStopFightingBothWays() {
        assertFalse(canHurt(out(Role.GUARD), in(Role.CRIMINAL, ZoneKind.SAFE)));
        assertFalse(canHurt(in(Role.GUARD, ZoneKind.SAFE), out(Role.CRIMINAL)));
    }

    @Test
    void eachSideIsSafeAtItsOwnBase() {
        assertFalse(canHurt(out(Role.GUARD), in(Role.CRIMINAL, ZoneKind.HIDEOUT)));
        assertFalse(canHurt(in(Role.CRIMINAL, ZoneKind.HIDEOUT), out(Role.GUARD)), "no shooting out of a hideout");
        assertFalse(canHurt(out(Role.CRIMINAL), in(Role.GUARD, ZoneKind.POLICE)));
        assertFalse(canHurt(in(Role.GUARD, ZoneKind.POLICE), out(Role.CRIMINAL)));
        // The other side's base isn't safe for you.
        assertTrue(canHurt(out(Role.CRIMINAL), in(Role.GUARD, ZoneKind.HIDEOUT)));
        assertTrue(canHurt(out(Role.GUARD), in(Role.CRIMINAL, ZoneKind.POLICE)));
    }

    @Test
    void nobodyFightsWhileServingASentence() {
        assertFalse(canHurt(out(Role.GUARD), new Fighter(Role.CRIMINAL, true, Set.of())));
        assertFalse(canHurt(new Fighter(Role.CRIMINAL, true, Set.of()), out(Role.GUARD)));
    }

    @Test
    void cuffing() {
        assertEquals(Cuff.OK, canCuff(out(Role.GUARD), out(Role.CRIMINAL)));
        assertEquals(Cuff.OK, canCuff(in(Role.GUARD, ZoneKind.POLICE), in(Role.CRIMINAL, ZoneKind.POLICE)),
                "a criminal who walks into the station can be arrested");
        assertEquals(Cuff.PROTECTED, canCuff(out(Role.GUARD), in(Role.CRIMINAL, ZoneKind.HIDEOUT)));
        assertEquals(Cuff.PROTECTED, canCuff(out(Role.GUARD), in(Role.CRIMINAL, ZoneKind.SAFE)));
        assertEquals(Cuff.NOT_A_CRIMINAL, canCuff(out(Role.GUARD), out(Role.INMATE)));
        assertEquals(Cuff.NOT_A_CRIMINAL, canCuff(out(Role.GUARD), out(Role.GUARD)));
        assertEquals(Cuff.NOT_A_GUARD, canCuff(out(Role.CRIMINAL), out(Role.CRIMINAL)));
    }

    @Test
    void duringARiotGuardsAndInmatesFightInsideThePrison() {
        Fighter guard = in(Role.GUARD, ZoneKind.PRISON);
        Fighter inmate = in(Role.INMATE, ZoneKind.PRISON);
        assertFalse(canHurt(guard, inmate, false), "no riot, no fighting");
        assertTrue(canHurt(guard, inmate, true));
        assertTrue(canHurt(inmate, guard, true));
        assertFalse(canHurt(inmate, in(Role.INMATE, ZoneKind.PRISON), true), "inmates still can't hurt each other");
        assertFalse(canHurt(out(Role.GUARD), inmate, true), "a guard outside the prison stays out of it");
        assertFalse(canHurt(guard, new Fighter(Role.INMATE, false, Set.of(ZoneKind.PRISON, ZoneKind.SAFE)), true),
                "the prison's safe spawn still holds");
        assertTrue(canHurt(guard, in(Role.CRIMINAL, ZoneKind.PRISON), true), "criminals as ever");
    }

    @Test
    void duringARiotGuardsCuffInmatesInThePrison() {
        Fighter guard = in(Role.GUARD, ZoneKind.PRISON);
        assertEquals(Cuff.NOT_A_CRIMINAL, canCuff(guard, in(Role.INMATE, ZoneKind.PRISON), false));
        assertEquals(Cuff.OK, canCuff(guard, in(Role.INMATE, ZoneKind.PRISON), true));
        assertEquals(Cuff.NOT_A_CRIMINAL, canCuff(guard, out(Role.INMATE), true));
        assertEquals(Cuff.NOT_A_CRIMINAL, canCuff(guard, new Fighter(Role.INMATE, true, Set.of(ZoneKind.PRISON)), true),
                "already serving a sentence");
    }
}
