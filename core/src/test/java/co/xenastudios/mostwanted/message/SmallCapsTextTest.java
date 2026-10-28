package co.xenastudios.mostwanted.message;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SmallCapsTextTest {

    @Test
    void convertsPlainTextOnly() {
        assertEquals("<success>✔</success> ᴛᴇʟᴇᴘᴏʀᴛᴇᴅ ᴛᴏ <value>Spawn</value>.",
                SmallCapsText.convert("<success>✔</success> Teleported to <value>Spawn</value>."));
    }

    @Test
    void leavesPlaceholdersAndValuesAlone() {
        assertEquals("ʏᴏᴜʀ ʙᴏᴜɴᴛʏ ɪѕ <money><amount></money>.", SmallCapsText.convert("your bounty is <money><amount></money>."));
        assertEquals("<value><player></value> ɪѕɴ'ᴛ ᴏɴʟɪɴᴇ.", SmallCapsText.convert("<value><player></value> isn't online."));
    }

    @Test
    void boldHeadersStayCapitals() {
        assertEquals("<b><brand>PROFILE</brand></b>", SmallCapsText.convert("<b><brand>PROFILE</brand></b>"));
        assertEquals("<b><white>Most</white><brand>Wanted</brand></b>", SmallCapsText.convert("<b><white>Most</white><brand>Wanted</brand></b>"));
    }

    @Test
    void quotedTagArgumentsArentText() {
        assertEquals("<hover:show_text:'a > b'>ʜᴇʟʟᴏ</hover>", SmallCapsText.convert("<hover:show_text:'a > b'>hello</hover>"));
    }

    @Test
    void aLoneAngleBracketIsText() {
        assertEquals("ᴀ < ʙ", SmallCapsText.convert("a < b"));
    }
}
