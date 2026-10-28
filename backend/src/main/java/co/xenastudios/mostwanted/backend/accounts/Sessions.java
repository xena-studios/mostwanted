package co.xenastudios.mostwanted.backend.accounts;

import static co.xenastudios.mostwanted.common.db.Tables.LOGIN_CODES;
import static co.xenastudios.mostwanted.common.db.Tables.SESSIONS;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;

/**
 * Signed-in browsers. The token goes in a cookie scripts can't read; the database keeps
 * only its SHA-256, so a leaked copy of the table can't sign anyone in. 30 days each.
 */
public final class Sessions {

    public static final Duration LIFETIME = Duration.ofDays(30);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final DSLContext sql;
    private final Clock clock;

    public Sessions(DSLContext sql, Clock clock) {
        this.sql = sql;
        this.clock = clock;
    }

    /** A new session for the account; returns the token for the cookie. */
    public String create(long accountId) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        sql.insertInto(SESSIONS)
                .set(SESSIONS.TOKEN_HASH, hash(token))
                .set(SESSIONS.ACCOUNT_ID, accountId)
                .set(SESSIONS.EXPIRES_AT, clock.instant().plus(LIFETIME).atOffset(ZoneOffset.UTC))
                .execute();
        return token;
    }

    /** Whose session this is, if it's real and in date. */
    public Optional<Long> account(String token) {
        if (token == null || token.isEmpty() || token.length() > 64) {
            return Optional.empty();
        }
        return sql.select(SESSIONS.ACCOUNT_ID).from(SESSIONS)
                .where(SESSIONS.TOKEN_HASH.eq(hash(token)))
                .and(SESSIONS.EXPIRES_AT.gt(clock.instant().atOffset(ZoneOffset.UTC)))
                .fetchOptional(SESSIONS.ACCOUNT_ID);
    }

    public void delete(String token) {
        if (token != null) {
            sql.deleteFrom(SESSIONS).where(SESSIONS.TOKEN_HASH.eq(hash(token))).execute();
        }
    }

    /** Clears out expired sessions and /website codes. Runs daily. */
    public int cleanup() {
        var now = clock.instant().atOffset(ZoneOffset.UTC);
        return sql.deleteFrom(SESSIONS).where(SESSIONS.EXPIRES_AT.le(now)).execute()
                + sql.deleteFrom(LOGIN_CODES).where(LOGIN_CODES.EXPIRES_AT.le(DSL.currentOffsetDateTime())).execute();
    }

    static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("every JVM has SHA-256", e);
        }
    }
}
