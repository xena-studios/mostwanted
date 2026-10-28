package co.xenastudios.mostwanted.backend;

import co.xenastudios.mostwanted.common.DatabaseConfig;
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
 */
public record BackendConfig(DatabaseConfig database, String host, int port, List<String> origins,
        boolean behindCloudflare, long requestsPerMinute, ZoneId zone, String site, String discordToken) {

    public static BackendConfig fromEnv(Map<String, String> env) {
        String token = env.getOrDefault("DISCORD_TOKEN", "");
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
                token.isBlank() ? null : token);
    }

    /** Never shows the token or the password. */
    @Override
    public String toString() {
        return "BackendConfig[" + database + ", api " + host + ":" + port + ", origins " + origins
                + ", cloudflare " + behindCloudflare + ", discord " + (discordToken == null ? "off" : "on") + "]";
    }
}
