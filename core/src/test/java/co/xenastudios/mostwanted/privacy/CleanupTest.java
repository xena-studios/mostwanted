package co.xenastudios.mostwanted.privacy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CleanupTest {

    @TempDir
    Path logs;

    private Path file(String name, Duration age, Instant now) throws Exception {
        Path file = Files.writeString(logs.resolve(name), "x");
        Files.setLastModifiedTime(file, FileTime.from(now.minus(age)));
        return file;
    }

    @Test
    void deletesOnlyLogFilesOlderThanTheCutoff() throws Exception {
        Instant now = Instant.parse("2026-10-27T12:00:00Z");
        Path old = file("2026-07-01-1.log.gz", Duration.ofDays(118), now);
        Path recent = file("2026-10-20-1.log.gz", Duration.ofDays(7), now);
        Path latest = file("latest.log", Duration.ofDays(200), now);
        Path other = file("notes.txt", Duration.ofDays(200), now);

        assertEquals(1, Cleanup.deleteOldLogs(logs, now.minus(Duration.ofDays(90))));
        assertFalse(Files.exists(old));
        assertTrue(Files.exists(recent));
        assertTrue(Files.exists(latest), "the live log is never deleted");
        assertTrue(Files.exists(other), "only log files are touched");
    }

    @Test
    void aMissingFolderIsFine() {
        assertEquals(0, Cleanup.deleteOldLogs(logs.resolve("nope"), Instant.now()));
    }
}
