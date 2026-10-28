package co.xenastudios.mostwanted.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BackendConfigTest {

    static BackendConfig config(String... settings) {
        Map<String, String> env = new HashMap<>(Map.of("DB_PASSWORD", "x"));
        for (int i = 0; i < settings.length; i += 2) {
            env.put(settings[i], settings[i + 1]);
        }
        return BackendConfig.fromEnv(env);
    }

    @Test
    void plainHttpWithoutTlsSettings() {
        assertNull(config().tls());
        assertNull(config("TLS_CERT", "", "TLS_KEY", " ").tls());
        assertTrue(config().toString().endsWith(", http]"));
    }

    @Test
    void httpsWithAClientCa() {
        BackendConfig config = config("TLS_CERT", "tls/origin.pem", "TLS_KEY", "tls/origin.key",
                "TLS_CLIENT_CA", "tls/cloudflare-pull.pem");
        assertEquals(new BackendConfig.Tls(Path.of("tls/origin.pem"), Path.of("tls/origin.key"),
                Path.of("tls/cloudflare-pull.pem")), config.tls());
        assertTrue(config.toString().endsWith(", https with client certificates]"));
        assertNull(config("TLS_CERT", "a", "TLS_KEY", "b").tls().clientCa());
    }

    @Test
    void halfATlsSetupIsAnError() {
        assertThrows(IllegalArgumentException.class, () -> config("TLS_CERT", "a"));
        assertThrows(IllegalArgumentException.class, () -> config("TLS_KEY", "b"));
        assertThrows(IllegalArgumentException.class, () -> config("TLS_CLIENT_CA", "c"));
    }
}
