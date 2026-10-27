package co.xenastudios.mostwanted.common;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import org.jooq.DSLContext;
import org.postgresql.PGConnection;
import org.postgresql.PGNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PostgreSQL's LISTEN/NOTIFY: how core and backend tell each other something changed,
 * instantly, without polling. A staff reply on the website reaches the player in game
 * and on Discord this way.
 *
 * <p>A notification is only a nudge. What happened is always written to a table, and
 * a listener reads it from there. Notifications sent while a listener is reconnecting
 * are lost, so {@link #listen} calls {@code onConnect} after every (re)connect for the
 * listener to catch up from the tables.
 */
public final class Notifications {

    private static final Logger LOG = LoggerFactory.getLogger(Notifications.class);

    /** Lower case, so names never need quoting. PostgreSQL's limit is 63 bytes. */
    private static final Pattern CHANNEL = Pattern.compile("[a-z][a-z0-9_]{0,62}");

    /** PostgreSQL's limit is just under 8000 bytes. Send an ID, not the data. */
    static final int MAX_PAYLOAD_BYTES = 7999;

    private Notifications() {}

    /**
     * Notifies every listener on {@code channel}. Inside a transaction, it's sent only
     * when the transaction commits, so a listener never reads the tables too early:
     * write the change and publish in the same transaction.
     */
    public static void publish(DSLContext sql, String channel, String payload) {
        checkChannel(channel);
        Objects.requireNonNull(payload, "payload");
        if (payload.getBytes(StandardCharsets.UTF_8).length > MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("Payload over " + MAX_PAYLOAD_BYTES + " bytes; send an ID instead");
        }
        sql.execute("SELECT pg_notify(?, ?)", channel, payload);
    }

    /** Receives notifications on the listener's own thread; hop to the main thread before touching the game. */
    @FunctionalInterface
    public interface Handler {
        void receive(String channel, String payload);
    }

    /**
     * Starts listening on {@code channels} with a connection of its own, reconnecting
     * on its own if the database restarts.
     *
     * @param onConnect runs after each (re)connect, before any notification: catch up from the tables here
     */
    public static Listener listen(Database database, List<String> channels, Runnable onConnect, Handler handler) {
        channels.forEach(Notifications::checkChannel);
        if (channels.isEmpty()) {
            throw new IllegalArgumentException("No channels to listen on");
        }
        return new Listener(database, List.copyOf(channels), onConnect, handler);
    }

    private static void checkChannel(String channel) {
        if (channel == null || !CHANNEL.matcher(channel).matches()) {
            throw new IllegalArgumentException("Bad channel name: " + channel);
        }
    }

    /** A running listener. Close it on shutdown. */
    public static final class Listener implements AutoCloseable {

        /** How long each wait for notifications lasts, and so the longest {@link #close} waits. */
        private static final int POLL_MILLIS = 1_000;
        /** After this long without a notification, check the connection is still alive. */
        private static final Duration HEARTBEAT = Duration.ofSeconds(30);
        private static final Duration MIN_BACKOFF = Duration.ofSeconds(1);
        private static final Duration MAX_BACKOFF = Duration.ofSeconds(30);

        private final Database database;
        private final List<String> channels;
        private final Runnable onConnect;
        private final Handler handler;
        private final Thread thread;
        private volatile boolean running = true;
        private volatile boolean connected;

        private Listener(Database database, List<String> channels, Runnable onConnect, Handler handler) {
            this.database = database;
            this.channels = channels;
            this.onConnect = Objects.requireNonNull(onConnect, "onConnect");
            this.handler = Objects.requireNonNull(handler, "handler");
            this.thread = Thread.ofPlatform().daemon().name("mostwanted-listen").start(this::run);
        }

        /** Whether it's connected and listening right now. */
        public boolean connected() {
            return connected;
        }

        private void run() {
            Duration backoff = MIN_BACKOFF;
            while (running) {
                try (Connection connection = database.openDedicated()) {
                    try (Statement statement = connection.createStatement()) {
                        for (String channel : channels) {
                            statement.execute("LISTEN " + channel);
                        }
                    }
                    connected = true;
                    backoff = MIN_BACKOFF;
                    safely("catching up after connecting", onConnect);
                    receive(connection);
                } catch (SQLException | RuntimeException e) {
                    if (!running) {
                        break;
                    }
                    LOG.warn("Lost the database notification connection, retrying in {} s: {}",
                            backoff.toSeconds(), e.getMessage());
                } finally {
                    connected = false;
                }
                if (!sleep(backoff)) {
                    break;
                }
                Duration doubled = backoff.multipliedBy(2);
                backoff = doubled.compareTo(MAX_BACKOFF) < 0 ? doubled : MAX_BACKOFF;
            }
        }

        private void receive(Connection connection) throws SQLException {
            PGConnection postgres = connection.unwrap(PGConnection.class);
            long lastHeard = System.nanoTime();
            while (running) {
                PGNotification[] batch = postgres.getNotifications(POLL_MILLIS);
                if (batch != null && batch.length > 0) {
                    lastHeard = System.nanoTime();
                    for (PGNotification notification : batch) {
                        safely("handling a notification on " + notification.getName(),
                                () -> handler.receive(notification.getName(), notification.getParameter()));
                    }
                } else if (System.nanoTime() - lastHeard > HEARTBEAT.toNanos()) {
                    // A dead connection can wait silently forever; a query finds out.
                    if (!connection.isValid(5)) {
                        throw new SQLException("The connection stopped answering");
                    }
                    lastHeard = System.nanoTime();
                }
            }
        }

        /** A failing handler is logged, never allowed to stop the listener. */
        private static void safely(String what, Runnable task) {
            try {
                task.run();
            } catch (RuntimeException e) {
                LOG.error("Failed {}", what, e);
            }
        }

        /** Returns false if closed while waiting. */
        private boolean sleep(Duration duration) {
            try {
                Thread.sleep(duration);
                return running;
            } catch (InterruptedException e) {
                return false;
            }
        }

        /** Stops listening, waiting up to a few seconds for the connection to close. */
        @Override
        public void close() {
            running = false;
            thread.interrupt();
            try {
                thread.join(Duration.ofSeconds(5));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
