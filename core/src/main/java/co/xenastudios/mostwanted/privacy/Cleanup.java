package co.xenastudios.mostwanted.privacy;

import static co.xenastudios.mostwanted.common.db.Tables.GAME_LOG;

import co.xenastudios.mostwanted.config.Retention;
import co.xenastudios.mostwanted.storage.Storage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Deletes what the privacy page says we don't keep: old server log files and old
 * game log entries. Runs at startup and once a day, off the main thread.
 */
public final class Cleanup {

    private final Path logs;
    private final Storage storage;
    private final Retention retention;
    private final Clock clock;
    private final Logger log;

    /** {@code logs} is the server's logs folder. */
    public Cleanup(Path logs, Storage storage, Retention retention, Clock clock, Logger log) {
        this.logs = logs;
        this.storage = storage;
        this.retention = retention;
        this.clock = clock;
        this.log = log;
    }

    public void run() {
        int files = deleteOldLogs(logs, clock.instant().minus(Duration.ofDays(retention.serverLogDays())));
        storage.write("game log cleanup", sql -> {
            int rows = sql.deleteFrom(GAME_LOG)
                    .where(GAME_LOG.AT.lt(clock.instant().atOffset(ZoneOffset.UTC)
                            .minusMonths(retention.gameLogMonths())))
                    .execute();
            if (files > 0 || rows > 0) {
                log.info("Privacy cleanup: deleted " + files + " old log files and " + rows + " old game log entries");
            }
        });
    }

    /**
     * Deletes log files in the folder last changed before {@code cutoff}. Never the live
     * log ({@code latest.log}), and only log files, so nothing else in there is touched.
     *
     * @return how many it deleted
     */
    static int deleteOldLogs(Path folder, Instant cutoff) {
        if (!Files.isDirectory(folder)) {
            return 0;
        }
        List<Path> old = new ArrayList<>();
        try (Stream<Path> files = Files.list(folder)) {
            files.filter(Files::isRegularFile)
                    .filter(Cleanup::isLogFile)
                    .forEach(file -> {
                        try {
                            if (Files.getLastModifiedTime(file).toInstant().isBefore(cutoff)) {
                                old.add(file);
                            }
                        } catch (IOException ignored) {
                            // Gone already, or unreadable: next run tries again.
                        }
                    });
        } catch (IOException e) {
            return 0;
        }
        int deleted = 0;
        for (Path file : old) {
            try {
                Files.delete(file);
                deleted++;
            } catch (IOException e) {
                Logger.getLogger(Cleanup.class.getName()).log(Level.WARNING, "Couldn't delete " + file, e);
            }
        }
        return deleted;
    }

    private static boolean isLogFile(Path file) {
        String name = file.getFileName().toString();
        return !name.equals("latest.log") && (name.endsWith(".log.gz") || name.endsWith(".log"));
    }
}
