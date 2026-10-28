package co.xenastudios.mostwanted.backend.discord;

/** Shows user input in a Discord message as typed, without it turning into formatting or pings. */
final class MarkdownSafe {

    private MarkdownSafe() {}

    static String escape(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            if ("\\*_~`|>#-[]()<@:".indexOf(c) >= 0) {
                out.append('\\');
            }
            out.append(c);
        }
        return out.toString();
    }
}
