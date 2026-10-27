package co.xenastudios.mostwanted.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GameLogTest {

    @Test
    void writesJson() {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("field", "cash");
        details.put("value", 2500);
        details.put("note", "a \"quoted\"\nline");
        assertEquals("{\"field\":\"cash\",\"value\":2500,\"note\":\"a \\\"quoted\\\"\\u000aline\"}", GameLog.json(details));
    }
}
