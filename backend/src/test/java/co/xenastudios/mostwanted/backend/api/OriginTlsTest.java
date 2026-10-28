package co.xenastudios.mostwanted.backend.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.xenastudios.mostwanted.backend.BackendConfig;
import co.xenastudios.mostwanted.backend.accounts.Accounts;
import co.xenastudios.mostwanted.backend.accounts.LinkedRole;
import co.xenastudios.mostwanted.backend.accounts.Sessions;
import co.xenastudios.mostwanted.backend.stats.Stats;
import java.io.IOException;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedKeyManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * HTTPS with Authenticated Origin Pulls, as on the VPS, with throwaway certificates
 * made here by the JDK's keytool: an "origin" CA for the server's certificate (Cloudflare
 * Origin CA's part), a "pull" CA for the client certificate (Cloudflare's), and a
 * stranger CA that anyone could have. No database: the routes tried don't need one.
 */
class OriginTlsTest {

    static final String PASSWORD = "test-only";

    @TempDir
    static Path dir;
    static Api strict;
    static Api open;

    @BeforeAll
    static void start() throws Exception {
        for (String ca : List.of("origin-ca", "pull-ca", "stranger-ca")) {
            keytool("-genkeypair", "-keystore", ca + ".p12", "-alias", ca, "-keyalg", "EC", "-groupname", "secp256r1",
                    "-dname", "CN=" + ca, "-ext", "bc:c", "-validity", "2");
            keytool("-exportcert", "-rfc", "-keystore", ca + ".p12", "-alias", ca, "-file", ca + ".pem");
        }
        issue("server", "origin-ca", "san=dns:localhost,ip:127.0.0.1");
        issue("cloudflare", "pull-ca", null);
        issue("someone", "stranger-ca", null);
        writeKey("server", dir.resolve("server.key"));

        strict = start(new BackendConfig.Tls(dir.resolve("server.pem"), dir.resolve("server.key"),
                dir.resolve("pull-ca.pem")));
        open = start(new BackendConfig.Tls(dir.resolve("server.pem"), dir.resolve("server.key"), null));
    }

    @AfterAll
    static void stop() {
        strict.stop();
        open.stop();
    }

    @Test
    void cloudflaresCertificateGetsThrough() throws Exception {
        HttpResponse<String> response = get(strict, client("cloudflare"));
        assertEquals(404, response.statusCode());
        assertEquals("{\"error\":\"no such board\"}", response.body(), "the request reached the API");
    }

    @Test
    void noClientCertificateIsRefused() {
        assertRefusedInTheHandshake(assertThrows(IOException.class, () -> get(strict, client(null))));
    }

    @Test
    void aCertificateFromAnyOtherCaIsRefused() {
        assertRefusedInTheHandshake(assertThrows(IOException.class, () -> get(strict, client("someone"))));
    }

    /** Refused by TLS itself (Jetty never read a request), not some other failure. */
    static void assertRefusedInTheHandshake(IOException e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof SSLException) {
                return;
            }
        }
        throw new AssertionError("not a TLS failure", e);
    }

    @Test
    void plainHttpGetsNoAnswerFromTheApi() {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + strict.port()
                + "/v1/leaderboards/nope")).timeout(Duration.ofSeconds(10)).build();
        try {
            HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            assertTrue(!response.body().contains("no such board"), response.body());
        } catch (IOException | InterruptedException refused) {
            // Also fine: Jetty closed the connection.
        }
    }

    @Test
    void withoutAClientCaAnyoneMayConnect() throws Exception {
        assertEquals(404, get(open, client(null)).statusCode());
    }

    @Test
    void aKeyThatDoesntMatchTheCertificateStopsTheStart() throws Exception {
        Path wrong = dir.resolve("cloudflare.key");
        writeKey("cloudflare", wrong);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> OriginTls.sslContext(
                new BackendConfig.Tls(dir.resolve("server.pem"), wrong, null)));
        assertTrue(e.getMessage().contains("isn't the key"), e.getMessage());
    }

    @Test
    void anOldStyleKeySaysHowToConvertIt() throws Exception {
        Path old = dir.resolve("old.key");
        Files.writeString(old, "-----BEGIN RSA PRIVATE KEY-----\nAAAA\n-----END RSA PRIVATE KEY-----\n");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> OriginTls.privateKey(old));
        assertTrue(e.getMessage().contains("openssl pkcs8"), e.getMessage());
    }

    static Api start(BackendConfig.Tls tls) {
        BackendConfig config = new BackendConfig(null, "127.0.0.1", 0, List.of("https://mostwantedmc.com"), true,
                1000, ZoneId.of("America/New_York"), "https://mostwantedmc.com", null, "https://api.mostwantedmc.com",
                "1", null, null, true, tls);
        return Api.start(config, new Stats(null, config.zone(), Clock.systemUTC()),
                Api.accounts(config, new Accounts(null), new Sessions(null, Clock.systemUTC()), null, LinkedRole.NONE),
                null);
    }

    static HttpResponse<String> get(Api api, HttpClient client) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://localhost:" + api.port() + "/v1/leaderboards/nope"))
                .timeout(Duration.ofSeconds(10)).build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /** A client that trusts the origin CA, showing {@code name}'s certificate, or none if null. */
    static HttpClient client(String name) throws Exception {
        KeyStore trusted = KeyStore.getInstance("PKCS12");
        trusted.load(null, null);
        trusted.setCertificateEntry("origin-ca", OriginTls.certificates(dir.resolve("origin-ca.pem"), "test").getFirst());
        TrustManagerFactory trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trust.init(trusted);

        KeyManagerFactory keys = null;
        if (name != null) {
            KeyStore store = load(name);
            keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keys.init(store, PASSWORD.toCharArray());
        }
        SSLContext ssl = SSLContext.getInstance("TLS");
        ssl.init(keys == null ? null : new KeyManager[] {insistOn(name, (X509ExtendedKeyManager) keys.getKeyManagers()[0])},
                trust.getTrustManagers(), null);
        return HttpClient.newBuilder().sslContext(ssl).connectTimeout(Duration.ofSeconds(10)).build();
    }

    /**
     * Shows the certificate even when the server's list of CAs it accepts doesn't include
     * its issuer (the JDK would otherwise quietly send none), like an attacker would.
     */
    static X509ExtendedKeyManager insistOn(String alias, X509ExtendedKeyManager keys) {
        return new X509ExtendedKeyManager() {
            @Override
            public String chooseEngineClientAlias(String[] types, Principal[] issuers, SSLEngine engine) {
                return alias;
            }

            @Override
            public String chooseClientAlias(String[] types, Principal[] issuers, Socket socket) {
                return alias;
            }

            @Override
            public String[] getClientAliases(String type, Principal[] issuers) {
                return new String[] {alias};
            }

            @Override
            public String[] getServerAliases(String type, Principal[] issuers) {
                return null;
            }

            @Override
            public String chooseServerAlias(String type, Principal[] issuers, Socket socket) {
                return null;
            }

            @Override
            public X509Certificate[] getCertificateChain(String name) {
                return keys.getCertificateChain(name);
            }

            @Override
            public PrivateKey getPrivateKey(String name) {
                return keys.getPrivateKey(name);
            }
        };
    }

    /** {@code name}'s key with its certificate signed by {@code ca}, as the server or client would hold it. */
    static KeyStore load(String name) throws Exception {
        KeyStore store = KeyStore.getInstance("PKCS12");
        try (var in = Files.newInputStream(dir.resolve(name + ".p12"))) {
            store.load(in, PASSWORD.toCharArray());
        }
        List<Certificate> chain = new ArrayList<>(OriginTls.certificates(dir.resolve(name + ".pem"), "test"));
        KeyStore withChain = KeyStore.getInstance("PKCS12");
        withChain.load(null, null);
        withChain.setKeyEntry(name, store.getKey(name, PASSWORD.toCharArray()), PASSWORD.toCharArray(),
                chain.toArray(Certificate[]::new));
        return withChain;
    }

    /** Makes {@code name}'s key pair and has {@code ca} sign its certificate into {@code name}.pem. */
    static void issue(String name, String ca, String extension) throws Exception {
        keytool("-genkeypair", "-keystore", name + ".p12", "-alias", name, "-keyalg", "EC", "-groupname", "secp256r1",
                "-dname", "CN=" + name, "-validity", "2");
        keytool("-certreq", "-keystore", name + ".p12", "-alias", name, "-file", name + ".csr");
        List<String> sign = new ArrayList<>(List.of("-gencert", "-rfc", "-keystore", ca + ".p12", "-alias", ca,
                "-infile", name + ".csr", "-outfile", name + ".pem", "-validity", "2"));
        if (extension != null) {
            sign.addAll(List.of("-ext", extension));
        }
        keytool(sign.toArray(String[]::new));
    }

    /** The private key as a PKCS#8 PEM file, the format Cloudflare's dashboard gives. */
    static void writeKey(String name, Path file) throws Exception {
        PrivateKey key = (PrivateKey) load(name).getKey(name, PASSWORD.toCharArray());
        String base64 = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                .encodeToString(key.getEncoded());
        Files.writeString(file, "-----BEGIN PRIVATE KEY-----\n" + base64 + "\n-----END PRIVATE KEY-----\n");
    }

    static void keytool(String... args) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "keytool").toString());
        command.addAll(List.of(args));
        command.addAll(List.of("-storetype", "PKCS12", "-storepass", PASSWORD, "-noprompt"));
        Process process = new ProcessBuilder(command).directory(dir.toFile()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (process.waitFor() != 0) {
            throw new IllegalStateException("keytool " + String.join(" ", args) + " failed:\n" + output);
        }
    }
}
