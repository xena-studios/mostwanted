package co.xenastudios.mostwanted.backend.discord;

import static co.xenastudios.mostwanted.common.db.Tables.SERVER_STATUS;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The bot's custom status: how many are playing, from the count the game server
 * writes every 30 seconds (core, server_status), or that the server is down when
 * it hasn't written for two minutes.
 */
public final class ServerStatus {

    private static final Logger LOG = LoggerFactory.getLogger(ServerStatus.class);
    private static final Duration STALE = Duration.ofMinutes(2);

    private final DSLContext sql;
    private final Bot bot;
    private final Clock clock;
    private String last;

    public ServerStatus(DSLContext sql, Bot bot, Clock clock) {
        this.sql = sql;
        this.bot = bot;
        this.clock = clock;
    }

    /** Once a minute: the status now, sent only when it changed. */
    public void update() {
        if (!bot.connected()) {
            return;
        }
        String text;
        boolean up;
        try {
            var row = sql.selectFrom(SERVER_STATUS).where(SERVER_STATUS.ID.eq((short) 1)).fetchOne();
            up = row != null && row.getUpdatedAt().toInstant().plus(STALE).isAfter(Instant.now(clock));
            text = text(up, row == null ? 0 : row.getOnline());
        } catch (RuntimeException e) {
            LOG.warn("Couldn't read the server's status: {}", e.getMessage());
            return;
        }
        if (!text.equals(last)) {
            bot.status(text, up);
            last = text;
        }
    }

    /** "12 players on MostWanted · mostwantedmc.com". */
    static String text(boolean up, int online) {
        if (!up) {
            return "Server offline · mostwantedmc.com";
        }
        return (online == 1 ? "1 player" : online + " players") + " on MostWanted · mostwantedmc.com";
    }
}
