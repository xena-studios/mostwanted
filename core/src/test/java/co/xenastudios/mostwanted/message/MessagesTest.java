package co.xenastudios.mostwanted.message;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

class MessagesTest {

    private final Messages messages = new Messages(Map.of(
            "plain", SmallCapsText.convert("<success>✔</success> done."),
            "named", SmallCapsText.convert("<value><player></value> is here.")));

    @Test
    void fillsPlaceholdersAsWritten() {
        Component message = messages.get("named", Messages.value("player", "<red>Alex"));
        assertEquals("<red>Alex ɪѕ ʜᴇʀᴇ.", PlainTextComponentSerializer.plainText().serialize(message));
    }

    @Test
    void textIsGreyByDefault() {
        assertEquals(NamedTextColor.GRAY, messages.get("plain").color());
    }

    @Test
    void messagesWithoutPlaceholdersAreParsedOnce() {
        assertSame(messages.get("plain"), messages.get("plain"));
    }

    @Test
    void aMissingKeyShowsTheKey() {
        assertEquals("nope", PlainTextComponentSerializer.plainText().serialize(messages.get("nope")));
    }
}
