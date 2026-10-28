package co.xenastudios.mostwanted.backend.support;

import static co.xenastudios.mostwanted.common.db.Tables.ACCOUNTS;
import static co.xenastudios.mostwanted.common.db.Tables.APPLICATIONS;
import static co.xenastudios.mostwanted.common.db.Tables.PLAYERS;
import static co.xenastudios.mostwanted.common.db.Tables.RANK_GRANTS;

import co.xenastudios.mostwanted.common.Notifications;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.JSONB;
import org.jooq.Record;
import org.jooq.impl.DSL;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Applications to be a guard, staff or a creator. One waiting at a time of each kind,
 * and a week after a "no" before trying again. Accepting a guard tells core to give
 * them the guard rank (on {@link Notifications#RANK_GRANTS}); staff and creators are
 * given theirs by hand.
 */
public final class Applications {

    /** How long after a "no" before they can apply for the same thing again. */
    public static final Duration RETRY_AFTER = Duration.ofDays(7);

    public record Application(long id, long accountId, String applicant, UUID minecraft, String kind,
            Map<String, String> answers, String status, String note, String decidedBy, Instant decidedAt,
            Instant createdAt) {}

    public enum Sent { SENT, ALREADY_WAITING, TOO_SOON }

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Field<String> APPLICANT = DSL.coalesce(PLAYERS.NAME, ACCOUNTS.DISCORD_NAME, DSL.inline("?"));

    private final DSLContext sql;

    public Applications(DSLContext sql) {
        this.sql = sql;
    }

    public Sent send(long accountId, String kind, Map<String, String> answers) {
        return sql.transactionResult(tx -> {
            DSLContext db = tx.dsl();
            // Their account row, locked: two sends at once can't both get through.
            db.selectFrom(ACCOUNTS).where(ACCOUNTS.ID.eq(accountId)).forUpdate().fetchSingle();
            Condition theirs = APPLICATIONS.ACCOUNT_ID.eq(accountId).and(APPLICATIONS.KIND.eq(kind));
            if (db.fetchExists(APPLICATIONS, theirs.and(APPLICATIONS.STATUS.eq("pending")))) {
                return Sent.ALREADY_WAITING;
            }
            if (db.fetchExists(APPLICATIONS, theirs.and(APPLICATIONS.STATUS.eq("denied"))
                    .and(APPLICATIONS.DECIDED_AT.gt(Instant.now().minus(RETRY_AFTER).atOffset(ZoneOffset.UTC))))) {
                return Sent.TOO_SOON;
            }
            db.insertInto(APPLICATIONS)
                    .set(APPLICATIONS.ACCOUNT_ID, accountId)
                    .set(APPLICATIONS.KIND, kind)
                    .set(APPLICATIONS.ANSWERS, JSONB.valueOf(JSON.writeValueAsString(answers)))
                    .execute();
            return Sent.SENT;
        });
    }

    public List<Application> mine(long accountId) {
        return list(APPLICATIONS.ACCOUNT_ID.eq(accountId), APPLICATIONS.CREATED_AT.desc(), 50);
    }

    /** For staff: waiting ones oldest first; decided ones latest first. */
    public List<Application> queue(String status) {
        return list(APPLICATIONS.STATUS.eq(status),
                status.equals("pending") ? APPLICATIONS.CREATED_AT.asc() : APPLICATIONS.DECIDED_AT.desc(), 100);
    }

    public Optional<Application> get(long id) {
        List<Application> found = list(APPLICATIONS.ID.eq(id), APPLICATIONS.ID.asc(), 1);
        return found.isEmpty() ? Optional.empty() : Optional.of(found.getFirst());
    }

    /**
     * Accepts or turns down one still waiting. Empty if it isn't (someone else decided
     * first). An accepted guard gets the rank in game, given by core.
     */
    public Optional<Application> decide(long id, boolean accepted, String note, String by) {
        boolean decided = sql.transactionResult(tx -> {
            DSLContext db = tx.dsl();
            int changed = db.update(APPLICATIONS)
                    .set(APPLICATIONS.STATUS, accepted ? "accepted" : "denied")
                    .set(APPLICATIONS.NOTE, note)
                    .set(APPLICATIONS.DECIDED_BY, by)
                    .set(APPLICATIONS.DECIDED_AT, DSL.currentOffsetDateTime())
                    .where(APPLICATIONS.ID.eq(id)).and(APPLICATIONS.STATUS.eq("pending"))
                    .execute();
            if (changed == 0) {
                return false;
            }
            if (accepted) {
                UUID minecraft = db.select(ACCOUNTS.MINECRAFT_UUID).from(APPLICATIONS)
                        .join(ACCOUNTS).on(ACCOUNTS.ID.eq(APPLICATIONS.ACCOUNT_ID))
                        .where(APPLICATIONS.ID.eq(id)).and(APPLICATIONS.KIND.eq("guard"))
                        .fetchOne(ACCOUNTS.MINECRAFT_UUID);
                if (minecraft != null) {
                    db.insertInto(RANK_GRANTS).set(RANK_GRANTS.UUID, minecraft).set(RANK_GRANTS.RANK, "guard").execute();
                    Notifications.publish(db, Notifications.RANK_GRANTS, minecraft.toString());
                }
            }
            return true;
        });
        return decided ? get(id) : Optional.empty();
    }

    private List<Application> list(Condition where, org.jooq.SortField<?> order, int limit) {
        return sql.select(APPLICATIONS.fields()).select(APPLICANT, ACCOUNTS.MINECRAFT_UUID)
                .from(APPLICATIONS)
                .join(ACCOUNTS).on(ACCOUNTS.ID.eq(APPLICATIONS.ACCOUNT_ID))
                .leftJoin(PLAYERS).on(PLAYERS.UUID.eq(ACCOUNTS.MINECRAFT_UUID))
                .where(where)
                .orderBy(order)
                .limit(limit)
                .fetch(Applications::application);
    }

    private static Application application(Record r) {
        Map<String, String> answers = r.get(APPLICATIONS.ANSWERS) == null ? Map.of()
                : JSON.readValue(r.get(APPLICATIONS.ANSWERS).data(), new TypeReference<LinkedHashMap<String, String>>() { });
        var decided = r.get(APPLICATIONS.DECIDED_AT);
        return new Application(r.get(APPLICATIONS.ID), r.get(APPLICATIONS.ACCOUNT_ID), r.get(APPLICANT),
                r.get(ACCOUNTS.MINECRAFT_UUID), r.get(APPLICATIONS.KIND), answers, r.get(APPLICATIONS.STATUS),
                r.get(APPLICATIONS.NOTE), r.get(APPLICATIONS.DECIDED_BY), decided == null ? null : decided.toInstant(),
                r.get(APPLICATIONS.CREATED_AT).toInstant());
    }
}
