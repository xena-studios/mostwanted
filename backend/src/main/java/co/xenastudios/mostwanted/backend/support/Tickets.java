package co.xenastudios.mostwanted.backend.support;

import static co.xenastudios.mostwanted.common.db.Tables.ACCOUNTS;
import static co.xenastudios.mostwanted.common.db.Tables.PLAYERS;
import static co.xenastudios.mostwanted.common.db.Tables.TICKETS;
import static co.xenastudios.mostwanted.common.db.Tables.TICKET_MESSAGES;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.impl.DSL;

/** Support tickets: a conversation between a player and staff, from the website's form. */
public final class Tickets {

    public record Ticket(long id, long accountId, String owner, String category, String subject, String status,
            Instant createdAt, Instant updatedAt) {}

    public record Message(long id, String author, boolean staff, String body, Instant createdAt) {}

    /** Who opened it: their Minecraft name, or their Discord name if they haven't linked one. */
    private static final Field<String> OWNER = DSL.coalesce(PLAYERS.NAME, ACCOUNTS.DISCORD_NAME, DSL.inline("?"));

    private final DSLContext sql;

    public Tickets(DSLContext sql) {
        this.sql = sql;
    }

    public long open(long accountId, String author, String category, String subject, String body) {
        return sql.transactionResult(tx -> {
            long id = tx.dsl().insertInto(TICKETS)
                    .set(TICKETS.ACCOUNT_ID, accountId)
                    .set(TICKETS.CATEGORY, category)
                    .set(TICKETS.SUBJECT, subject)
                    .returningResult(TICKETS.ID).fetchSingle().value1();
            tx.dsl().insertInto(TICKET_MESSAGES)
                    .set(TICKET_MESSAGES.TICKET_ID, id)
                    .set(TICKET_MESSAGES.ACCOUNT_ID, accountId)
                    .set(TICKET_MESSAGES.AUTHOR, author)
                    .set(TICKET_MESSAGES.STAFF, false)
                    .set(TICKET_MESSAGES.BODY, body)
                    .execute();
            return id;
        });
    }

    /** How many they've opened since then: for the limit on new tickets. */
    public int openedSince(long accountId, Duration ago) {
        return sql.fetchCount(TICKETS, TICKETS.ACCOUNT_ID.eq(accountId)
                .and(TICKETS.CREATED_AT.gt(Instant.now().minus(ago).atOffset(ZoneOffset.UTC))));
    }

    public List<Ticket> mine(long accountId) {
        return list(TICKETS.ACCOUNT_ID.eq(accountId), 50);
    }

    /** For staff: tickets in a state, the longest waiting first (closed: the latest first). */
    public List<Ticket> queue(String status) {
        return sql.select(TICKETS.fields()).select(OWNER)
                .from(TICKETS)
                .join(ACCOUNTS).on(ACCOUNTS.ID.eq(TICKETS.ACCOUNT_ID))
                .leftJoin(PLAYERS).on(PLAYERS.UUID.eq(ACCOUNTS.MINECRAFT_UUID))
                .where(TICKETS.STATUS.eq(status))
                .orderBy(status.equals("closed") ? TICKETS.UPDATED_AT.desc() : TICKETS.UPDATED_AT.asc())
                .limit(100)
                .fetch(Tickets::ticket);
    }

    public Optional<Ticket> get(long id) {
        List<Ticket> found = list(TICKETS.ID.eq(id), 1);
        return found.isEmpty() ? Optional.empty() : Optional.of(found.getFirst());
    }

    public List<Message> messages(long ticketId) {
        return sql.selectFrom(TICKET_MESSAGES)
                .where(TICKET_MESSAGES.TICKET_ID.eq(ticketId))
                .orderBy(TICKET_MESSAGES.ID)
                .fetch(r -> new Message(r.getId(), r.getAuthor(), r.getStaff(), r.getBody(), r.getCreatedAt().toInstant()));
    }

    /** A reply: from staff it waits for the player ("answered"); from the player, for staff ("open"). */
    public void reply(long ticketId, long accountId, String author, boolean staff, String body) {
        sql.transaction(tx -> {
            tx.dsl().insertInto(TICKET_MESSAGES)
                    .set(TICKET_MESSAGES.TICKET_ID, ticketId)
                    .set(TICKET_MESSAGES.ACCOUNT_ID, accountId)
                    .set(TICKET_MESSAGES.AUTHOR, author)
                    .set(TICKET_MESSAGES.STAFF, staff)
                    .set(TICKET_MESSAGES.BODY, body)
                    .execute();
            tx.dsl().update(TICKETS)
                    .set(TICKETS.STATUS, staff ? "answered" : "open")
                    .set(TICKETS.UPDATED_AT, DSL.currentOffsetDateTime())
                    .where(TICKETS.ID.eq(ticketId))
                    .execute();
        });
    }

    public void close(long ticketId) {
        sql.update(TICKETS)
                .set(TICKETS.STATUS, "closed")
                .set(TICKETS.UPDATED_AT, DSL.currentOffsetDateTime())
                .where(TICKETS.ID.eq(ticketId))
                .execute();
    }

    private List<Ticket> list(Condition where, int limit) {
        return sql.select(TICKETS.fields()).select(OWNER)
                .from(TICKETS)
                .join(ACCOUNTS).on(ACCOUNTS.ID.eq(TICKETS.ACCOUNT_ID))
                .leftJoin(PLAYERS).on(PLAYERS.UUID.eq(ACCOUNTS.MINECRAFT_UUID))
                .where(where)
                .orderBy(TICKETS.UPDATED_AT.desc())
                .limit(limit)
                .fetch(Tickets::ticket);
    }

    private static Ticket ticket(Record r) {
        return new Ticket(r.get(TICKETS.ID), r.get(TICKETS.ACCOUNT_ID), r.get(OWNER), r.get(TICKETS.CATEGORY),
                r.get(TICKETS.SUBJECT), r.get(TICKETS.STATUS), r.get(TICKETS.CREATED_AT).toInstant(),
                r.get(TICKETS.UPDATED_AT).toInstant());
    }
}
