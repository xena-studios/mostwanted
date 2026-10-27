package co.xenastudios.mostwanted.storage;

import co.xenastudios.mostwanted.common.Database;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jooq.DSLContext;

/**
 * The database, and the one thread that writes to it. Every write goes through
 * {@link #write}, in order, so an older save can never land after a newer one.
 * Nothing here runs on the main thread.
 */
public final class Storage implements AutoCloseable {

    private final Database database;
    private final Logger log;
    private final ExecutorService writer = Executors.newSingleThreadExecutor(
            Thread.ofPlatform().name("mostwanted-db-write").daemon().factory());

    public Storage(Database database, Logger log) {
        this.database = database;
        this.log = log;
    }

    /** For reads, which callers run off the main thread themselves. */
    public DSLContext sql() {
        return database.sql();
    }

    /** Queues a write, in a transaction. The future fails if the write did. */
    public CompletableFuture<Void> write(String what, Consumer<DSLContext> work) {
        return CompletableFuture.runAsync(() -> {
            try {
                database.sql().transaction(tx -> work.accept(tx.dsl()));
            } catch (RuntimeException e) {
                log.log(Level.WARNING, "Couldn't save " + what + ": " + e.getMessage());
                throw e;
            }
        }, writer);
    }

    /** Waits for queued writes to finish, then closes the pool. For shutdown only. */
    @Override
    public void close() {
        writer.shutdown();
        try {
            if (!writer.awaitTermination(Duration.ofSeconds(20).toSeconds(), TimeUnit.SECONDS)) {
                log.severe("Database writes didn't finish within 20 s; some changes may be lost");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        database.close();
    }
}
