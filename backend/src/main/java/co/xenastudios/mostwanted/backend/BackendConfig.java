package co.xenastudios.mostwanted.backend;

import co.xenastudios.mostwanted.common.DatabaseConfig;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * The backend's settings, from environment variables: on the VPS, its {@code .env}
 * (from {@code backend/.env.example}), so secrets never touch the repo.
 *
 * @param host and {@code port}: where the API listens
 * @param origins the sites allowed to call the API from a browser
 * @param behindCloudflare whether to trust CF-Connecting-IP for the visitor's IP
 * @param requestsPerMinute per visitor, before 429s
 * @param zone the server's timezone, for the month and week boards; the same as core's clock.timezone
 * @param site the website, for links in Discord
 * @param discordToken the bot's token; null switches the bot off
 * @param apiUrl where the API is reached from outside, for Discord to send people back to
 * @param discordClientId the Discord app's ID (public)
 * @param discordClientSecret its OAuth secret; null switches Discord sign-in off
 * @param cookieDomain such as .mostwantedmc.com, so the site and the API share the session; null for this host only
 * @param cookieSecure HTTPS-only cookies: off only for local testing over plain HTTP
 * @param tls HTTPS with the Cloudflare Origin CA certificate, and Authenticated Origin Pulls; null for plain HTTP
 */
public record BackendConfig(DatabaseConfig database, String host, int port, List<String> origins,
        boolean behindCloudflare, long requestsPerMinute, ZoneId zone, String site, String discordToken,
        String apiUrl, String discordClientId, String discordClientSecret, String cookieDomain, boolean cookieSecure,
        Tls tls) {

    public static BackendConfig fromEnv(Map<String, String> env) {
        String token = env.getOrDefault("DISCORD_TOKEN", "");
        String secret = env.getOrDefault("DISCORD_CLIENT_SECRET", "");
        String domain = env.getOrDefault("COOKIE_DOMAIN", "");
        return new BackendConfig(
                DatabaseConfig.fromEnv(env, Integer.parseInt(env.getOrDefault("DB_POOL_SIZE", "4"))),
                env.getOrDefault("API_HOST", "0.0.0.0"),
                Integer.parseInt(env.getOrDefault("API_PORT", "8080")),
                Arrays.stream(env.getOrDefault("API_ORIGINS", "https://mostwantedmc.com").split(","))
                        .map(String::strip).filter(s -> !s.isEmpty()).toList(),
                Boolean.parseBoolean(env.getOrDefault("API_BEHIND_CLOUDFLARE", "false")),
                Long.parseLong(env.getOrDefault("API_REQUESTS_PER_MINUTE", "120")),
                ZoneId.of(env.getOrDefault("TIMEZONE", "America/New_York")),
                env.getOrDefault("SITE_URL", "https://mostwantedmc.com"),
                token.isBlank() ? null : token,
                env.getOrDefault("API_URL", "https://api.mostwantedmc.com"),
                env.getOrDefault("DISCORD_CLIENT_ID", "1564123260562440303"),
                secret.isBlank() ? null : secret,
                domain.isBlank() ? null : domain,
                Boolean.parseBoolean(env.getOrDefault("COOKIE_SECURE", "true")),
                Tls.fromEnv(env));
    }

    /**
     * HTTPS on the API's port, from PEM files (paths relative to the folder the backend
     * runs in): on the VPS, Cloudflare's Origin CA certificate and its key, and
     * Cloudflare's Authenticated Origin Pulls CA, so only Cloudflare can connect
     * (docs/08-website.md#the-api).
     *
     * @param cert the server's certificate (chain), PEM
     * @param key its private key, PEM (PKCS#8, "BEGIN PRIVATE KEY")
     * @param clientCa the CA a client certificate must be signed by; null accepts clients without one
     */
    public record Tls(Path cert, Path key, Path clientCa) {

        /** TLS_CERT and TLS_KEY switch HTTPS on; TLS_CLIENT_CA also requires a client certificate. */
        static Tls fromEnv(Map<String, String> env) {
            String cert = env.getOrDefault("TLS_CERT", "").strip();
            String key = env.getOrDefault("TLS_KEY", "").strip();
            String clientCa = env.getOrDefault("TLS_CLIENT_CA", "").strip();
            if (cert.isEmpty() && key.isEmpty()) {
                if (!clientCa.isEmpty()) {
                    throw new IllegalArgumentException("TLS_CLIENT_CA needs TLS_CERT and TLS_KEY too");
                }
                return null;
            }
            if (cert.isEmpty() || key.isEmpty()) {
                throw new IllegalArgumentException("set both TLS_CERT and TLS_KEY, or neither");
            }
            return new Tls(Path.of(cert), Path.of(key), clientCa.isEmpty() ? null : Path.of(clientCa));
        }
    }

    /** Never shows the token, the client secret or the password. */
    @Override
    public String toString() {
        return "BackendConfig[" + database + ", api " + host + ":" + port + ", origins " + origins
                + ", cloudflare " + behindCloudflare + ", bot " + (discordToken == null ? "off" : "on")
                + ", discord sign-in " + (discordClientSecret == null ? "off" : "on")
                + ", " + (tls == null ? "http" : tls.clientCa() == null ? "https" : "https with client certificates") + "]";
    }
}
