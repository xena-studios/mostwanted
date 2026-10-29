package co.xenastudios.mostwanted.backend.accounts;

import static co.xenastudios.mostwanted.common.db.Tables.ACCOUNTS;
import static co.xenastudios.mostwanted.common.db.Tables.APPLICATIONS;
import static co.xenastudios.mostwanted.common.db.Tables.LOGIN_CODES;
import static co.xenastudios.mostwanted.common.db.Tables.NOTIFICATIONS;
import static co.xenastudios.mostwanted.common.db.Tables.PLAYERS;
import static co.xenastudios.mostwanted.common.db.Tables.TICKETS;
import static co.xenastudios.mostwanted.common.db.Tables.TICKET_MESSAGES;

import co.xenastudios.mostwanted.common.db.tables.records.AccountsRecord;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;

/**
 * Website accounts: one person, with a Discord account, a Minecraft account, or both.
 * Signing in with either finds or makes their account; linking adds the other. Each
 * change runs in one transaction, so two tabs can't link the same account twice.
 */
public final class Accounts {

    public record Account(long id, String discordId, String discordName, UUID minecraft, String minecraftName) {

        /** Both sides connected: what the Linked role on Discord means. */
        public boolean linked() {
            return discordId != null && minecraft != null;
        }
    }

    public enum Link {
        /** Done. */
        LINKED,
        /** It was already this account's. */
        ALREADY_YOURS,
        /** This account already has a different one: delete the account to start again. */
        ALREADY_LINKED,
        /** Another account has it, and that one is linked to something else too. */
        TAKEN,
        /** The code was wrong, used, or too old. */
        BAD_CODE
    }

    private final DSLContext sql;

    public Accounts(DSLContext sql) {
        this.sql = sql;
    }

    public Optional<Account> get(long id) {
        return sql.select(ACCOUNTS.ID, ACCOUNTS.DISCORD_ID, ACCOUNTS.DISCORD_NAME, ACCOUNTS.MINECRAFT_UUID, PLAYERS.NAME)
                .from(ACCOUNTS)
                .leftJoin(PLAYERS).on(PLAYERS.UUID.eq(ACCOUNTS.MINECRAFT_UUID))
                .where(ACCOUNTS.ID.eq(id))
                .fetchOptional(r -> new Account(r.value1(), r.value2(), r.value3(), r.value4(), r.value5()));
    }

    /** Each account with both sides connected: its Discord user and Minecraft player. */
    public Map<String, UUID> linked() {
        Map<String, UUID> linked = new LinkedHashMap<>();
        sql.select(ACCOUNTS.DISCORD_ID, ACCOUNTS.MINECRAFT_UUID)
                .from(ACCOUNTS)
                .where(ACCOUNTS.DISCORD_ID.isNotNull()).and(ACCOUNTS.MINECRAFT_UUID.isNotNull())
                .forEach(r -> linked.put(r.value1(), r.value2()));
        return linked;
    }

    /** Finds or makes the account for this Discord user, keeping their name up to date. */
    public long signInWithDiscord(String discordId, String name) {
        // One statement, so two sign-ins at once can't both try to make the account.
        return sql.insertInto(ACCOUNTS)
                .set(ACCOUNTS.DISCORD_ID, discordId)
                .set(ACCOUNTS.DISCORD_NAME, name)
                .onConflict(ACCOUNTS.DISCORD_ID).doUpdate().set(ACCOUNTS.DISCORD_NAME, name)
                .returningResult(ACCOUNTS.ID)
                .fetchSingle().value1();
    }

    /** Uses up a /website code and finds or makes that player's account. Empty if the code is bad. */
    public Optional<Long> signInWithCode(String code) {
        return sql.transactionResult(tx -> {
            DSLContext db = tx.dsl();
            UUID uuid = consume(db, code);
            if (uuid == null) {
                return Optional.<Long>empty();
            }
            Long found = db.select(ACCOUNTS.ID).from(ACCOUNTS).where(ACCOUNTS.MINECRAFT_UUID.eq(uuid)).fetchOne(ACCOUNTS.ID);
            if (found != null) {
                return Optional.of(found);
            }
            return Optional.of(db.insertInto(ACCOUNTS).set(ACCOUNTS.MINECRAFT_UUID, uuid)
                    .returningResult(ACCOUNTS.ID).fetchSingle().value1());
        });
    }

    /**
     * Adds the Minecraft account behind a /website code. If another account has it with
     * nothing else (they signed in with the code before), that one is folded into this.
     */
    public Link linkMinecraft(long accountId, String code) {
        return sql.transactionResult(tx -> {
            DSLContext db = tx.dsl();
            AccountsRecord me = db.selectFrom(ACCOUNTS).where(ACCOUNTS.ID.eq(accountId)).forUpdate().fetchSingle();
            if (me.getMinecraftUuid() != null) {
                // Already linked: say so without using up the code, which may be for another account.
                UUID whose = db.select(LOGIN_CODES.UUID).from(LOGIN_CODES)
                        .where(LOGIN_CODES.CODE.eq(code)).and(LOGIN_CODES.EXPIRES_AT.gt(DSL.currentOffsetDateTime()))
                        .fetchOne(LOGIN_CODES.UUID);
                if (whose == null) {
                    return Link.BAD_CODE;
                }
                return whose.equals(me.getMinecraftUuid()) ? Link.ALREADY_YOURS : Link.ALREADY_LINKED;
            }
            UUID uuid = consume(db, code);
            if (uuid == null) {
                return Link.BAD_CODE;
            }
            AccountsRecord other = db.selectFrom(ACCOUNTS).where(ACCOUNTS.MINECRAFT_UUID.eq(uuid)).forUpdate().fetchOne();
            if (other != null) {
                if (other.getDiscordId() != null) {
                    return Link.TAKEN;
                }
                fold(db, other, me);
            }
            me.setMinecraftUuid(uuid);
            me.store();
            return Link.LINKED;
        });
    }

    /** Adds a Discord account, folding in another account that has only it. */
    public Link linkDiscord(long accountId, String discordId, String name) {
        return sql.transactionResult(tx -> {
            DSLContext db = tx.dsl();
            AccountsRecord me = db.selectFrom(ACCOUNTS).where(ACCOUNTS.ID.eq(accountId)).forUpdate().fetchSingle();
            if (discordId.equals(me.getDiscordId())) {
                return Link.ALREADY_YOURS;
            }
            if (me.getDiscordId() != null) {
                return Link.ALREADY_LINKED;
            }
            AccountsRecord other = db.selectFrom(ACCOUNTS).where(ACCOUNTS.DISCORD_ID.eq(discordId)).forUpdate().fetchOne();
            if (other != null) {
                if (other.getMinecraftUuid() != null) {
                    return Link.TAKEN;
                }
                fold(db, other, me);
            }
            me.setDiscordId(discordId);
            me.setDiscordName(name);
            me.store();
            return Link.LINKED;
        });
    }

    /**
     * Moves another account's tickets, replies, applications and notifications to
     * {@code into}, then deletes it: folding accounts together must not lose them
     * (they'd cascade away with it).
     */
    private static void fold(DSLContext db, AccountsRecord other, AccountsRecord into) {
        long from = other.getId();
        long to = into.getId();
        db.update(TICKETS).set(TICKETS.ACCOUNT_ID, to).where(TICKETS.ACCOUNT_ID.eq(from)).execute();
        db.update(TICKET_MESSAGES).set(TICKET_MESSAGES.ACCOUNT_ID, to).where(TICKET_MESSAGES.ACCOUNT_ID.eq(from)).execute();
        db.update(APPLICATIONS).set(APPLICATIONS.ACCOUNT_ID, to).where(APPLICATIONS.ACCOUNT_ID.eq(from)).execute();
        db.update(NOTIFICATIONS).set(NOTIFICATIONS.ACCOUNT_ID, to).where(NOTIFICATIONS.ACCOUNT_ID.eq(from)).execute();
        other.delete();
    }

    /** Deletes the account, its sessions and links. Game data stays (the privacy page: email us for that). */
    public Optional<Account> delete(long accountId) {
        Optional<Account> account = get(accountId);
        account.ifPresent(a -> sql.deleteFrom(ACCOUNTS).where(ACCOUNTS.ID.eq(accountId)).execute());
        return account;
    }

    /** Deletes a code that's right and in date, and says whose it was. Null otherwise. */
    private static UUID consume(DSLContext db, String code) {
        return db.deleteFrom(LOGIN_CODES)
                .where(LOGIN_CODES.CODE.eq(code))
                .and(LOGIN_CODES.EXPIRES_AT.gt(DSL.currentOffsetDateTime()))
                .returningResult(LOGIN_CODES.UUID)
                .fetchOne(LOGIN_CODES.UUID);
    }
}
