package co.xenastudios.mostwanted.pack;

import co.xenastudios.mostwanted.config.PackSettings;
import co.xenastudios.mostwanted.message.Messages;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

/**
 * Sends the resource pack (pack/, published as a GitHub release) to players as they
 * join. Its SHA-1 is read from the file published next to it, at startup and every few
 * minutes, so publishing a new pack needs no config change or restart. The game works
 * without the pack: our items fall back to the vanilla items they're made from.
 */
public final class ResourcePack implements Listener {

    private static final Pattern SHA1 = Pattern.compile("[0-9a-f]{40}");
    /** The same for every version, so a new pack replaces the old one on the client. */
    private static final UUID ID = UUID.nameUUIDFromBytes("mostwanted-pack".getBytes(StandardCharsets.UTF_8));

    private final PackSettings settings;
    private final Messages messages;
    private final Logger log;
    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(10)).build();
    private volatile String sha1;

    public ResourcePack(PackSettings settings, Messages messages, Logger log) {
        this.settings = settings;
        this.messages = messages;
        this.log = log;
    }

    /** Reads the current SHA-1. Off the main thread: it's a web request. */
    public void refresh() {
        if (!settings.enabled()) {
            return;
        }
        try {
            HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(settings.sha1Url()))
                    .timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.ofString());
            String read = response.body().strip().toLowerCase(Locale.ROOT);
            if (response.statusCode() != 200 || !SHA1.matcher(read).matches()) {
                log.warning("Couldn't read the resource pack's SHA-1 (" + response.statusCode() + "); not sending it yet");
                return;
            }
            if (!read.equals(sha1)) {
                log.info("Resource pack " + read);
            }
            sha1 = read;
        } catch (Exception e) {
            log.warning("Couldn't read the resource pack's SHA-1: " + e.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void join(PlayerJoinEvent event) {
        String hash = sha1;
        if (!settings.enabled() || hash == null) {
            return;
        }
        event.getPlayer().sendResourcePacks(ResourcePackRequest.resourcePackRequest()
                .packs(ResourcePackInfo.resourcePackInfo(ID, URI.create(settings.url()), hash))
                .required(settings.required())
                .prompt(messages.get("pack.prompt"))
                .replace(true)
                .build());
    }

    /** A download that fails isn't the player's fault: they play on with vanilla-looking items. */
    @EventHandler
    public void status(PlayerResourcePackStatusEvent event) {
        Player player = event.getPlayer();
        switch (event.getStatus()) {
            case FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD -> messages.send(player, "pack.failed");
            default -> { }
        }
    }
}
