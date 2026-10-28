package co.xenastudios.mostwanted.backend.accounts;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * "Sign in with Discord": the two calls the docs describe, with the JDK's HTTP client.
 * Scope {@code identify} only: their ID, username and avatar, not email or servers.
 */
public final class DiscordOAuth {

    /** Who signed in. {@code name} is their display name if they have one. */
    public record User(String id, String name) {}

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final String api;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    /** {@code api} is Discord's API base, such as https://discord.com/api/v10 (tests point it elsewhere). */
    public DiscordOAuth(String clientId, String clientSecret, String redirectUri, String api) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
        this.api = api;
    }

    /** Where to send the browser. {@code state} comes back unchanged, to check it's the same browser. */
    public String authorizeUrl(String state) {
        return "https://discord.com/oauth2/authorize?" + form(Map.of(
                "response_type", "code",
                "client_id", clientId,
                "scope", "identify",
                "redirect_uri", redirectUri,
                "state", state,
                "prompt", "none"));
    }

    /** Trades the code Discord sent back for who the user is. */
    public User user(String code) throws IOException, InterruptedException {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("grant_type", "authorization_code");
        body.put("code", code);
        body.put("redirect_uri", redirectUri);
        String basic = Base64.getEncoder().encodeToString((clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));
        JsonNode token = send(HttpRequest.newBuilder(URI.create(api + "/oauth2/token"))
                .header("Authorization", "Basic " + basic)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form(body))));
        JsonNode me = send(HttpRequest.newBuilder(URI.create(api + "/users/@me"))
                .header("Authorization", "Bearer " + token.path("access_token").asString()).GET());
        String id = me.path("id").asString();
        String name = me.path("global_name").isString() ? me.path("global_name").asString() : me.path("username").asString();
        if (id.isEmpty()) {
            throw new IOException("Discord didn't say who signed in");
        }
        return new User(id, name.length() > 64 ? name.substring(0, 64) : name);
    }

    private JsonNode send(HttpRequest.Builder request) throws IOException, InterruptedException {
        HttpResponse<String> response = http.send(request.timeout(Duration.ofSeconds(10)).build(),
                HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            // Never log the body: a token response could be in it.
            throw new IOException("Discord answered " + response.statusCode());
        }
        return JSON.readTree(response.body());
    }

    private static String form(Map<String, String> values) {
        return values.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "="
                        + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }
}
