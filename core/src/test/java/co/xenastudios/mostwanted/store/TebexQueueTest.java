package co.xenastudios.mostwanted.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.xenastudios.mostwanted.config.StoreSettings;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TebexQueueTest {

    private static JsonObject json(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }

    @Test
    void placeholdersAreFilledIn() {
        JsonObject command = json("""
                {"id": 7, "command": "lp user {username} parent add mvp", "conditions": {"delay": 3},
                 "player": {"id": 1, "name": "Notch", "uuid": "069a79f444e94726a5befca90e38aaf5"}}""");
        TebexQueue.Command parsed = TebexQueue.Command.of(command, command.getAsJsonObject("player"));
        assertEquals(new TebexQueue.Command(7, "lp user Notch parent add mvp", 3), parsed);

        JsonObject byUuid = json("""
                {"id": 8, "command": "say {id} {uuid} {name}"}""");
        assertEquals("say 069a79f444e94726a5befca90e38aaf5 069a79f4-44e9-4726-a5be-fca90e38aaf5 Notch",
                TebexQueue.Command.of(byUuid, command.getAsJsonObject("player")).line());
        assertEquals(UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5"),
                TebexQueue.Command.uuid("069a79f444e94726a5befca90e38aaf5"));
        assertNull(TebexQueue.Command.uuid("2535428650000000"), "a Bedrock XUID");
    }

    @Test
    void onlyTheStoresOwnCommandsRun() {
        StoreSettings store = new StoreSettings("secret", StoreSettings.DEFAULT_ALLOWED);
        assertTrue(store.allows("lp user Notch parent add legend"));
        assertTrue(store.allows("/lp user .Bedrock_Name parent add vip_plus"));
        assertTrue(store.allows("lp user Notch permission set mostwanted.cosmetic.trail-cash true"));
        assertTrue(store.allows("mostwanted boost Notch"));
        assertFalse(store.allows("lp user Notch parent add admin"));
        assertFalse(store.allows("lp user Notch permission set * true"));
        assertFalse(store.allows("lp user Notch parent add vip; op Notch"));
        assertFalse(store.allows("op Notch"));
        assertFalse(store.allows("mostwanted boost Notch extra"));
    }

    @Test
    void deliveryIsOffWithoutAKey() {
        assertFalse(new StoreSettings("", StoreSettings.DEFAULT_ALLOWED).enabled());
        assertFalse(new StoreSettings("none", StoreSettings.DEFAULT_ALLOWED).enabled());
        assertTrue(new StoreSettings("abc", StoreSettings.DEFAULT_ALLOWED).enabled());
        assertFalse(new StoreSettings("abc", StoreSettings.DEFAULT_ALLOWED).toString().contains("abc"));
    }
}
