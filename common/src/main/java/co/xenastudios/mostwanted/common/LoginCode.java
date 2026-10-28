package co.xenastudios.mostwanted.common;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Locale;
import java.util.random.RandomGenerator;

/**
 * The 6-character codes /website shows in game, typed on the site to sign in or link.
 * No 0/O or 1/I, so they're easy to read off a screen and type on a phone.
 */
public final class LoginCode {

    public static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    public static final int LENGTH = 6;
    public static final Duration LIFETIME = Duration.ofMinutes(10);
    private static final RandomGenerator RANDOM = new SecureRandom();

    private LoginCode() {}

    public static String generate() {
        StringBuilder code = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            code.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }

    /** As typed, made comparable: upper case, without spaces or dashes. Null if it can't be a code. */
    public static String normalize(String typed) {
        if (typed == null) {
            return null;
        }
        String code = typed.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
        if (code.length() != LENGTH) {
            return null;
        }
        for (char c : code.toCharArray()) {
            if (ALPHABET.indexOf(c) < 0) {
                return null;
            }
        }
        return code;
    }
}
