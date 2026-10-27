package co.xenastudios.mostwanted.common.style;

/**
 * Small capitals (ᴍᴏѕᴛᴡᴀɴᴛᴇᴅ), the look of labels and system text. Message files keep
 * plain text; it's converted once when they load. Names and values stay as they are.
 * See docs/09-brand.md#messages.
 */
public final class SmallCaps {

    // a-z. Letters without a small capital of their own use the closest that renders
    // everywhere: Cyrillic ѕ for s, ǫ for q, and plain x.
    private static final String LETTERS = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀѕᴛᴜᴠᴡxʏᴢ";

    private SmallCaps() {}

    /** Letters become small capitals, in either case; everything else is kept. */
    public static String of(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = Character.toLowerCase(text.charAt(i));
            out.append(c >= 'a' && c <= 'z' ? LETTERS.charAt(c - 'a') : text.charAt(i));
        }
        return out.toString();
    }
}
