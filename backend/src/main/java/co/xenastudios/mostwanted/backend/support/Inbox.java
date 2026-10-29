package co.xenastudios.mostwanted.backend.support;

import static co.xenastudios.mostwanted.common.db.Tables.ACCOUNTS;
import static co.xenastudios.mostwanted.common.db.Tables.NOTIFICATIONS;

import co.xenastudios.mostwanted.common.Notifications;
import java.time.Instant;
import java.util.List;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * What players are told: a staff reply, an application decided. Kept here for the
 * site, sent by Discord DM if they've linked Discord, and shown in game by core,
 * which hears of it on {@link Notifications#INBOX} (at once if they're online,
 * otherwise as they join).
 */
public final class Inbox {

    private static final Logger LOG = LoggerFactory.getLogger(Inbox.class);

    /** Sends a Discord DM. */
    @FunctionalInterface
    public interface Dm {
        Dm NONE = (discordId, text) -> { };

        void send(String discordId, String text);
    }

    public record Notice(long id, String text, String link, Instant createdAt, boolean read) {}

    private final DSLContext sql;
    private final String site;
    private Dm dm = Dm.NONE;

    public Inbox(DSLContext sql, String site) {
        this.sql = sql;
        this.site = site;
    }

    public void dm(Dm dm) {
        this.dm = dm;
    }

    /**
     * Tells a player something, with a path on the site to see it. Never throws: it
     * runs after what it reports is saved, and a failure here mustn't make that look
     * failed (the player would retry and post twice).
     */
    public void tell(long accountId, String text, String link) {
        try {
            sql.transaction(tx -> {
                tx.dsl().insertInto(NOTIFICATIONS)
                        .set(NOTIFICATIONS.ACCOUNT_ID, accountId)
                        .set(NOTIFICATIONS.TEXT, text)
                        .set(NOTIFICATIONS.LINK, link)
                        .execute();
                Notifications.publish(tx.dsl(), Notifications.INBOX, Long.toString(accountId));
            });
            String discord = sql.select(ACCOUNTS.DISCORD_ID).from(ACCOUNTS).where(ACCOUNTS.ID.eq(accountId))
                    .fetchOne(ACCOUNTS.DISCORD_ID);
            if (discord != null) {
                dm.send(discord, text + (link == null ? "" : "\n" + site + link));
            }
        } catch (RuntimeException e) {
            LOG.warn("Couldn't notify account {}: {}", accountId, text, e);
        }
    }

    /** Their latest 30, newest first. */
    public List<Notice> recent(long accountId) {
        return sql.selectFrom(NOTIFICATIONS)
                .where(NOTIFICATIONS.ACCOUNT_ID.eq(accountId))
                .orderBy(NOTIFICATIONS.ID.desc())
                .limit(30)
                .fetch(r -> new Notice(r.getId(), r.getText(), r.getLink(), r.getCreatedAt().toInstant(),
                        r.getReadAt() != null));
    }

    public int unread(long accountId) {
        return sql.fetchCount(NOTIFICATIONS, NOTIFICATIONS.ACCOUNT_ID.eq(accountId).and(NOTIFICATIONS.READ_AT.isNull()));
    }

    public void readAll(long accountId) {
        sql.update(NOTIFICATIONS).set(NOTIFICATIONS.READ_AT, DSL.currentOffsetDateTime())
                .where(NOTIFICATIONS.ACCOUNT_ID.eq(accountId)).and(NOTIFICATIONS.READ_AT.isNull())
                .execute();
    }
}
