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

    /** Whether {@code attacker} can damage {@code victim}, with no riot on. */
    public static boolean canHurt(Fighter attacker, Fighter victim) {
        return canHurt(attacker, victim, false);
    }

    /**
     * Whether {@code attacker} can damage {@code victim}. Guards and criminals fight; during
     * a riot, guards and inmates also fight inside the prison. Nobody else does.
     */
    public static boolean canHurt(Fighter attacker, Fighter victim, boolean riot) {
        if (attacker.jailed() || victim.jailed() || attacker.protectedHere() || victim.protectedHere()) {
            return false;
        }
        if (riot && rioting(attacker, victim)) {
            return true;
        }
        return attacker.role() == Role.GUARD && victim.role() == Role.CRIMINAL
                || attacker.role() == Role.CRIMINAL && victim.role() == Role.GUARD;
    }

    /** A guard and an inmate, both inside the prison. */
    private static boolean rioting(Fighter a, Fighter b) {
        boolean pair = a.role() == Role.GUARD && b.role() == Role.INMATE || a.role() == Role.INMATE && b.role() == Role.GUARD;
        return pair && a.zones().contains(ZoneKind.PRISON) && b.zones().contains(ZoneKind.PRISON);
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
        return canCuff(guard, target, false);
    }

    /** As above; during a riot, guards can also cuff inmates inside the prison. */
    public static Cuff canCuff(Fighter guard, Fighter target, boolean riot) {
        if (guard.role() != Role.GUARD || guard.jailed()) {
            return Cuff.NOT_A_GUARD;
        }
        boolean rioter = riot && target.role() == Role.INMATE && target.zones().contains(ZoneKind.PRISON);
        if (target.role() != Role.CRIMINAL && !rioter || target.jailed()) {
            return Cuff.NOT_A_CRIMINAL;
        }
        return target.protectedHere() ? Cuff.PROTECTED : Cuff.OK;
    }
}
