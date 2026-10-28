package co.xenastudios.mostwanted.robbery;

import java.time.Duration;
import java.time.Instant;

/**
 * One place to rob, stepped once a second: open, then being robbed while robbers stay
 * inside, then closed for a while whether they got away with it or not. No server
 * needed, so every path is unit tested; {@link Robberies} does the rest.
 */
final class RobberySpot {

    enum State { OPEN, ROBBING, CLOSED }

    /** What a step did, for {@link Robberies} to announce and pay out. */
    enum Change { NONE, OPENED, STARTED, FAILED, FINISHED }

    private State state = State.OPEN;
    /** Seconds robbed so far. */
    private long progress;
    private Instant closedUntil;

    State state() {
        return state;
    }

    /**
     * Moves on a second.
     *
     * @param robbers whether any robber is inside right now
     * @param time how long a robbery takes
     * @param closed how long it stays shut afterwards
     */
    Change step(Instant now, boolean robbers, Duration time, Duration closed) {
        switch (state) {
            case CLOSED -> {
                if (!now.isBefore(closedUntil)) {
                    state = State.OPEN;
                    return Change.OPENED;
                }
            }
            case OPEN -> {
                if (robbers) {
                    state = State.ROBBING;
                    progress = 0;
                    return Change.STARTED;
                }
            }
            case ROBBING -> {
                if (!robbers) {
                    close(now, closed);
                    return Change.FAILED;
                }
                progress++;
                if (progress >= time.toSeconds()) {
                    close(now, closed);
                    return Change.FINISHED;
                }
            }
        }
        return Change.NONE;
    }

    /** How long the robbers still need, while it's being robbed. */
    Duration left(Duration time) {
        return time.minusSeconds(progress);
    }

    /** From 0 to 1, for the progress bar. */
    float done(Duration time) {
        return Math.min(1f, (float) progress / time.toSeconds());
    }

    private void close(Instant now, Duration closed) {
        state = State.CLOSED;
        closedUntil = now.plus(closed);
        progress = 0;
    }
}
