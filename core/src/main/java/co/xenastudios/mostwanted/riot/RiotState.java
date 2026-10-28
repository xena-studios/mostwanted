package co.xenastudios.mostwanted.riot;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Whether a riot is on, who's in it, and when the next can start. No server needed,
 * so every rule is unit tested; {@link Riots} does the rest.
 */
final class RiotState {

    enum Press {
        /** Counted; not enough inmates yet. */
        COUNTED,
        /** That was the last one needed: the riot is on. */
        STARTED,
        /** A riot is already on. */
        ALREADY,
        /** Too soon after the last. */
        COOLDOWN,
        /** Not enough guards on duty to make it fair. */
        NO_GUARDS
    }

    enum End { NONE, TIME_UP, QUELLED }

    private final Map<UUID, Instant> presses = new HashMap<>();
    private final Set<UUID> rioters = new HashSet<>();
    private Instant endsAt;
    private Instant nextAllowed = Instant.MIN;

    boolean active() {
        return endsAt != null;
    }

    Set<UUID> rioters() {
        return Set.copyOf(rioters);
    }

    Duration left(Instant now) {
        return endsAt == null ? Duration.ZERO : Duration.between(now, endsAt);
    }

    /** An inmate pressed the alarm. Presses older than {@code window} don't count. */
    Press press(UUID inmate, Instant now, int needed, Duration window, Duration length, boolean enoughGuards) {
        if (active()) {
            return Press.ALREADY;
        }
        if (now.isBefore(nextAllowed)) {
            return Press.COOLDOWN;
        }
        if (!enoughGuards) {
            return Press.NO_GUARDS;
        }
        presses.put(inmate, now);
        presses.values().removeIf(at -> at.plus(window).isBefore(now));
        if (presses.size() < needed) {
            return Press.COUNTED;
        }
        rioters.clear();
        rioters.addAll(presses.keySet());
        presses.clear();
        endsAt = now.plus(length);
        return Press.STARTED;
    }

    /** An inmate who joins in during the riot (hits a guard) is a rioter too. */
    void join(UUID inmate) {
        if (active()) {
            rioters.add(inmate);
        }
    }

    /**
     * Once a second: over when time runs out, or when no rioter is still free
     * ({@code free} says whether one is: online and not serving a sentence).
     */
    End tick(Instant now, java.util.function.Predicate<UUID> free, Duration cooldown) {
        if (!active()) {
            return End.NONE;
        }
        End end = !now.isBefore(endsAt) ? End.TIME_UP
                : rioters.stream().noneMatch(free) ? End.QUELLED : End.NONE;
        if (end != End.NONE) {
            endsAt = null;
            rioters.clear();
            nextAllowed = now.plus(cooldown);
        }
        return end;
    }
}
