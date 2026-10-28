package co.xenastudios.mostwanted.combat;

import co.xenastudios.mostwanted.player.Role;
import co.xenastudios.mostwanted.zone.ZoneKind;
import java.util.Set;

/**
 * Who can hurt and cuff whom (docs/02-gameplay.md#combat). No server needed, so
 * every case is unit tested. Riots will open up the inmate rules later.
 */
public final class CombatRules {

    private CombatRules() {}

    /** One side of a fight: their role, whether they're serving a sentence, and the zones they're in. */
    public record Fighter(Role role, boolean jailed, Set<ZoneKind> zones) {

        /** In a zone where their own side can't be touched, and so can't attack from either. */
        boolean protectedHere() {
            return zones.contains(ZoneKind.SAFE)
                    || role == Role.GUARD && zones.contains(ZoneKind.POLICE)
                    || role == Role.CRIMINAL && zones.contains(ZoneKind.HIDEOUT);
        }
    }

    /** Whether {@code attacker} can damage {@code victim}. Guards and criminals fight; nobody else does. */
    public static boolean canHurt(Fighter attacker, Fighter victim) {
        if (attacker.jailed() || victim.jailed() || attacker.protectedHere() || victim.protectedHere()) {
            return false;
        }
        return attacker.role() == Role.GUARD && victim.role() == Role.CRIMINAL
                || attacker.role() == Role.CRIMINAL && victim.role() == Role.GUARD;
    }

    public enum Cuff {
        /** Go ahead. */
        OK,
        /** Only guards on duty carry working cuffs. */
        NOT_A_GUARD,
        /** Only criminals can be arrested. */
        NOT_A_CRIMINAL,
        /** They're in a hideout or a safe zone. */
        PROTECTED
    }

    /**
     * Whether a guard can cuff someone. Unlike fighting, it works from inside the police
     * station: a criminal who walks in can be arrested there.
     */
    public static Cuff canCuff(Fighter guard, Fighter target) {
        if (guard.role() != Role.GUARD || guard.jailed()) {
            return Cuff.NOT_A_GUARD;
        }
        if (target.role() != Role.CRIMINAL || target.jailed()) {
            return Cuff.NOT_A_CRIMINAL;
        }
        return target.protectedHere() ? Cuff.PROTECTED : Cuff.OK;
    }
}
