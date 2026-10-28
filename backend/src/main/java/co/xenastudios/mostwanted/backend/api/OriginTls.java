package co.xenastudios.mostwanted.backend.api;

import co.xenastudios.mostwanted.backend.BackendConfig;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.SignatureException;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.util.ssl.SslContextFactory;

/**
 * HTTPS for api.mostwantedmc.com, served by Jetty itself (no nginx): the Cloudflare
 * Origin CA certificate for Cloudflare's "Full (strict)" mode, and Authenticated Origin
 * Pulls, where Cloudflare shows its own client certificate and any connection without
 * one signed by {@code TLS_CLIENT_CA} is refused during the handshake, before a request
 * is read (docs/08-website.md#the-api).
 *
 * <p>Everything is read from PEM files with the JDK, so the keys never have to be
 * converted into a Java keystore by hand.
 */
final class OriginTls {

    private static final Pattern PEM = Pattern.compile(
            "-----BEGIN ([A-Z ]+)-----([A-Za-z0-9+/=\\s]+)-----END \\1-----");

    private OriginTls() {}

    /** Reads and checks the files now, so a bad path or key stops the start with a clear reason. */
    static SslContextFactory.Server sslContext(BackendConfig.Tls tls) {
        try {
            List<X509Certificate> chain = certificates(tls.cert(), "TLS_CERT");
            PrivateKey key = privateKey(tls.key());
            if (!matches(key, chain.getFirst())) {
                throw new IllegalArgumentException("TLS_KEY (" + tls.key() + ") isn't the key for TLS_CERT (" + tls.cert() + ")");
            }
            // The keystore only lives in memory; its password just has to be the same twice.
            String password = HexFormat.of().formatHex(randomBytes());
            KeyStore keys = KeyStore.getInstance("PKCS12");
            keys.load(null, null);
            keys.setKeyEntry("origin", key, password.toCharArray(), chain.toArray(Certificate[]::new));

            SslContextFactory.Server ssl = new SslContextFactory.Server();
            ssl.setKeyStore(keys);
            ssl.setKeyStorePassword(password);
            if (tls.clientCa() != null) {
                KeyStore trusted = KeyStore.getInstance("PKCS12");
                trusted.load(null, null);
                int i = 0;
                for (X509Certificate ca : certificates(tls.clientCa(), "TLS_CLIENT_CA")) {
                    trusted.setCertificateEntry("client-ca-" + i++, ca);
                }
                ssl.setTrustStore(trusted);
                ssl.setNeedClientAuth(true);
            }
            return ssl;
        } catch (IOException e) {
            throw new IllegalArgumentException("can't read the TLS files: " + e, e);
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("bad TLS certificate or key: " + e.getMessage(), e);
        }
    }

    /** An HTTPS connector on host:port, used instead of Javalin's plain HTTP one. */
    static ServerConnector connector(Server server, HttpConfiguration base, SslContextFactory.Server ssl,
            String host, int port) {
        HttpConfiguration https = new HttpConfiguration(base);
        https.addCustomizer(new SecureRequestCustomizer());
        ServerConnector connector = new ServerConnector(server,
                new SslConnectionFactory(ssl, HttpVersion.HTTP_1_1.asString()), new HttpConnectionFactory(https));
        connector.setHost(host);
        connector.setPort(port);
        return connector;
    }

    static List<X509Certificate> certificates(Path file, String setting) throws IOException, GeneralSecurityException {
        byte[] pem = Files.readAllBytes(file);
        List<X509Certificate> certificates = CertificateFactory.getInstance("X.509")
                .generateCertificates(new ByteArrayInputStream(pem)).stream()
                .map(X509Certificate.class::cast).toList();
        if (certificates.isEmpty()) {
            throw new IllegalArgumentException(setting + " (" + file + ") has no certificate in it");
        }
        return certificates;
    }

    /** A PKCS#8 key ("BEGIN PRIVATE KEY"), as Cloudflare's dashboard gives it: RSA or ECDSA. */
    static PrivateKey privateKey(Path file) throws IOException, GeneralSecurityException {
        String text = Files.readString(file, StandardCharsets.US_ASCII);
        Matcher block = PEM.matcher(text);
        while (block.find()) {
            String type = block.group(1);
            switch (type) {
                case "PRIVATE KEY" -> {
                    byte[] der = Base64.getMimeDecoder().decode(block.group(2));
                    for (String algorithm : List.of("RSA", "EC", "Ed25519")) {
                        try {
                            return KeyFactory.getInstance(algorithm).generatePrivate(new PKCS8EncodedKeySpec(der));
                        } catch (InvalidKeySpecException notThisOne) {
                            // Try the next algorithm.
                        }
                    }
                    throw new IllegalArgumentException("TLS_KEY (" + file + ") is not an RSA, EC or Ed25519 key");
                }
                case "RSA PRIVATE KEY", "EC PRIVATE KEY" -> throw new IllegalArgumentException("TLS_KEY (" + file
                        + ") is in the old " + type + " format; convert it with: "
                        + "openssl pkcs8 -topk8 -nocrypt -in old.pem -out key.pem");
                case "ENCRYPTED PRIVATE KEY" -> throw new IllegalArgumentException("TLS_KEY (" + file
                        + ") has a passphrase; save it without one (and chmod 600 it)");
                default -> {
                    // A certificate in the same file: skip it.
                }
            }
        }
        throw new IllegalArgumentException("TLS_KEY (" + file + ") has no \"BEGIN PRIVATE KEY\" block");
    }

    private static byte[] randomBytes() {
        byte[] bytes = new byte[16];
        new SecureRandom().nextBytes(bytes);
        return bytes;
    }

    /** Whether {@code key} is the private half of the certificate's public key: sign, then verify. */
    private static boolean matches(PrivateKey key, X509Certificate certificate) throws GeneralSecurityException {
        String algorithm = switch (key.getAlgorithm()) {
            case "RSA" -> "SHA256withRSA";
            case "EC" -> "SHA256withECDSA";
            default -> key.getAlgorithm();
        };
        byte[] probe = "mostwanted".getBytes(StandardCharsets.US_ASCII);
        Signature sign = Signature.getInstance(algorithm);
        sign.initSign(key);
        sign.update(probe);
        byte[] signature = sign.sign();
        Signature verify = Signature.getInstance(algorithm);
        try {
            verify.initVerify(certificate.getPublicKey());
            verify.update(probe);
            return verify.verify(signature);
        } catch (InvalidKeyException | SignatureException differentKey) {
            return false;
        }
    }
}
