package co.xenastudios.mostwanted.storage;

import static co.xenastudios.mostwanted.common.db.Tables.GAME_LOG;

import java.util.Map;
import java.util.UUID;
import org.jooq.JSONB;

/**
 * Writes to the game log: arrests, robberies, staff actions. For staff and the
 * website; nothing in the game reads it back.
 */
public final class GameLog {

    private final Storage storage;

    public GameLog(Storage storage) {
        this.storage = storage;
    }

    /** Queues an entry. {@code details} values are strings or numbers. */
    public void record(String kind, UUID actor, UUID target, Map<String, ?> details) {
        JSONB json = JSONB.valueOf(json(details));
        storage.write("a " + kind + " log entry", sql -> sql.insertInto(GAME_LOG)
                .set(GAME_LOG.KIND, kind)
                .set(GAME_LOG.ACTOR, actor)
                .set(GAME_LOG.TARGET, target)
                .set(GAME_LOG.DETAILS, json)
                .execute());
    }

    static String json(Map<String, ?> details) {
        StringBuilder out = new StringBuilder("{");
        details.forEach((key, value) -> {
            if (out.length() > 1) {
                out.append(',');
            }
            out.append(quote(key)).append(':');
            out.append(value instanceof Number ? value.toString() : quote(String.valueOf(value)));
        });
        return out.append('}').toString();
    }

    private static String quote(String s) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }
}
