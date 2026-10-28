package co.xenastudios.mostwanted.message;

import co.xenastudios.mostwanted.common.style.SmallCaps;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Set;

/**
 * Turns the plain text of a MiniMessage string into small caps, the way messages
 * look (docs/09-brand.md#messages). Tags and placeholders are left alone, and so is
 * anything inside a tag that marks a name or value ({@code <value>}, {@code <money>},
 * {@code <white>}) or in bold: headers and titles are bold capitals, written as-is.
 */
public final class SmallCapsText {

    /** Tags whose contents stay as written: names, places, amounts, and bold headers. */
    private static final Set<String> KEEP = Set.of("value", "money", "white", "b", "bold");

    private SmallCapsText() {}

    public static String convert(String miniMessage) {
        StringBuilder out = new StringBuilder(miniMessage.length() + 16);
        Deque<String> open = new ArrayDeque<>();
        int i = 0;
        while (i < miniMessage.length()) {
            int tagStart = miniMessage.indexOf('<', i);
            if (tagStart < 0) {
                text(out, miniMessage.substring(i), open);
                break;
            }
            text(out, miniMessage.substring(i, tagStart), open);
            int tagEnd = tagEnd(miniMessage, tagStart);
            if (tagEnd < 0) {
                // A lone '<' is text, not a tag.
                text(out, miniMessage.substring(tagStart), open);
                break;
            }
            String tag = miniMessage.substring(tagStart, tagEnd + 1);
            track(tag, open);
            out.append(tag);
            i = tagEnd + 1;
        }
        return out.toString();
    }

    /** The '>' closing the tag at {@code start}, skipping over quoted arguments. */
    private static int tagEnd(String s, int start) {
        char quote = 0;
        for (int i = start + 1; i < s.length(); i++) {
            char c = s.charAt(i);
            if (quote != 0) {
                if (c == quote) {
                    quote = 0;
                }
            } else if (c == '\'' || c == '"') {
                quote = c;
            } else if (c == '>') {
                return i;
            }
        }
        return -1;
    }

    private static void track(String tag, Deque<String> open) {
        String body = tag.substring(1, tag.length() - 1);
        if (body.endsWith("/")) {
            return;
        }
        if (body.startsWith("/")) {
            open.removeFirstOccurrence(name(body.substring(1)));
        } else {
            open.push(name(body));
        }
    }

    private static String name(String body) {
        int colon = body.indexOf(':');
        return (colon < 0 ? body : body.substring(0, colon)).toLowerCase();
    }

    private static void text(StringBuilder out, String text, Deque<String> open) {
        boolean keep = open.stream().anyMatch(KEEP::contains);
        out.append(keep ? text : SmallCaps.of(text));
    }
}
