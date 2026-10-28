package co.xenastudios.mostwanted.store;

import co.xenastudios.mostwanted.config.StoreSettings;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Delivers store purchases: what the Tebex plugin does, without the plugin. Asks Tebex's
 * command queue (https://docs.tebex.io/plugin/endpoints/command-queue) for the commands
 * each purchase runs, runs them from the console, and tells Tebex they're done.
 *
 * <p>Only commands {@link StoreSettings#allows} are run. The rest stay queued (Tebex
 * shows them as not delivered) and are logged once each.
 *
 * <p>A command that ran is written down until Tebex has been told, so a failed call or
 * a restart in between can't run it twice (a second boost, say).
 */
public final class TebexQueue {

    private static final URI BASE = URI.create("https://plugin.tebex.io/");
    /** How long to wait when Tebex doesn't say, or can't be reached. */
    private static final int DEFAULT_WAIT = 120;

    private final Plugin plugin;
    private final StoreSettings settings;
    private final Logger log;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    /** Commands run or turned down this session, so neither happens twice if Tebex is slow to forget them. */
    private final Set<Integer> handled = new HashSet<>();
    /** Ran, but Tebex hasn't been told yet; kept in {@link #delivered} across restarts. */
    private final Set<Integer> unacknowledged = new HashSet<>();
    private final File delivered;
    private volatile BukkitTask next;
    private volatile boolean stopped;

    public TebexQueue(Plugin plugin, StoreSettings settings, File delivered, Logger log) {
        this.plugin = plugin;
        this.settings = settings;
        this.delivered = delivered;
        this.log = log;
    }

    public void start() {
        if (!settings.enabled()) {
            log.info("Store delivery is off (store.tebex-secret)");
            return;
        }
        log.info("Store delivery is on");
        if (delivered.exists()) {
            try {
                for (String line : Files.readAllLines(delivered.toPath())) {
                    if (!line.isBlank()) {
                        unacknowledged.add(Integer.parseInt(line.strip()));
                    }
                }
            } catch (IOException | NumberFormatException e) {
                log.warning("Couldn't read " + delivered.getName() + ": " + e.getMessage());
            }
            handled.addAll(unacknowledged);
        }
        schedule(5);
    }

    public void stop() {
        stopped = true;
        if (next != null) {
            next.cancel();
        }
    }

    private void schedule(int seconds) {
        if (!stopped && plugin.isEnabled()) {
            next = Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, this::check, seconds * 20L);
        }
    }

    /** Off the main thread: one look at the queue, then the next as Tebex asks. */
    private void check() {
        int wait = DEFAULT_WAIT;
        try {
            acknowledge();
            JsonObject queue = get("queue");
            JsonObject meta = queue.getAsJsonObject("meta");
            if (meta != null && meta.has("next_check")) {
                // Tebex asks clients to keep to this strictly; never faster than every 30 s.
                wait = Math.max(30, meta.get("next_check").getAsInt());
            }
            List<Command> due = new ArrayList<>();
            for (JsonElement each : array(get("queue/offline-commands"), "commands")) {
                JsonObject command = each.getAsJsonObject();
                due.add(Command.of(command, command.getAsJsonObject("player")));
            }
            for (JsonElement each : array(queue, "players")) {
                JsonObject player = each.getAsJsonObject();
                UUID uuid = Command.uuid(text(player, "uuid"));
                if (uuid == null || !online(uuid)) {
                    continue;
                }
                for (JsonElement command : array(get("queue/online-commands/" + player.get("id").getAsString()), "commands")) {
                    due.add(Command.of(command.getAsJsonObject(), player));
                }
            }
            run(due);
        } catch (IOException | RuntimeException e) {
            log.log(Level.WARNING, "Couldn't check the store's queue: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        schedule(wait);
    }

    private void run(List<Command> due) {
        List<Command> todo = new ArrayList<>();
        synchronized (handled) {
            for (Command command : due) {
                if (!handled.add(command.id())) {
                    continue;
                }
                if (settings.allows(command.line())) {
                    todo.add(command);
                } else {
                    log.warning("Store command " + command.id() + " isn't allowed by store.allowed-commands, so it"
                            + " wasn't run: " + command.line());
                }
            }
        }
        if (todo.isEmpty()) {
            return;
        }
        List<CompletableFuture<Void>> runs = new ArrayList<>();
        for (Command command : todo) {
            CompletableFuture<Void> ran = new CompletableFuture<>();
            runs.add(ran);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                try {
                    log.info("Store: " + command.line());
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.line());
                    synchronized (unacknowledged) {
                        unacknowledged.add(command.id());
                        writeDelivered();
                    }
                } finally {
                    ran.complete(null);
                }
            }, Math.max(1, command.delaySeconds() * 20L));
        }
        CompletableFuture.allOf(runs.toArray(CompletableFuture[]::new)).thenRunAsync(() -> {
            try {
                acknowledge();
            } catch (IOException | RuntimeException e) {
                log.log(Level.WARNING, "Ran store commands but couldn't tell Tebex yet (will try again): "
                        + e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    /** Tells Tebex about every command that ran, and forgets them once it knows. */
    private void acknowledge() throws IOException, InterruptedException {
        List<Integer> ids;
        synchronized (unacknowledged) {
            ids = List.copyOf(unacknowledged);
        }
        if (ids.isEmpty()) {
            return;
        }
        delete(ids);
        synchronized (unacknowledged) {
            ids.forEach(unacknowledged::remove);
            writeDelivered();
        }
    }

    /** Holding the lock on {@link #unacknowledged}. Small, and only after a purchase. */
    private void writeDelivered() {
        try {
            if (unacknowledged.isEmpty()) {
                Files.deleteIfExists(delivered.toPath());
            } else {
                Files.write(delivered.toPath(), unacknowledged.stream().map(String::valueOf).toList());
            }
        } catch (IOException e) {
            log.warning("Couldn't write " + delivered.getName() + ": " + e.getMessage());
        }
    }

    private boolean online(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        return player != null && player.isOnline();
    }

    // ---------------------------------------------------------------- Tebex

    private JsonObject get(String path) throws IOException, InterruptedException {
        HttpResponse<String> response = http.send(request(path).GET().build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Tebex answered " + response.statusCode() + " for " + path);
        }
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    private void delete(List<Integer> ids) throws IOException, InterruptedException {
        if (ids.isEmpty()) {
            return;
        }
        JsonArray array = new JsonArray();
        ids.forEach(array::add);
        JsonObject body = new JsonObject();
        body.add("ids", array);
        HttpResponse<Void> response = http.send(request("queue")
                .header("Content-Type", "application/json")
                .method("DELETE", HttpRequest.BodyPublishers.ofString(body.toString()))
                .build(), HttpResponse.BodyHandlers.discarding());
        if (response.statusCode() / 100 != 2) {
            throw new IOException("Tebex answered " + response.statusCode());
        }
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(BASE.resolve(path))
                .timeout(Duration.ofSeconds(15))
                .header("X-Tebex-Secret", settings.tebexSecret())
                .header("User-Agent", "MostWanted");
    }

    private static JsonArray array(JsonObject object, String name) {
        JsonElement value = object.get(name);
        return value != null && value.isJsonArray() ? value.getAsJsonArray() : new JsonArray();
    }

    private static String text(JsonObject object, String name) {
        JsonElement value = object == null ? null : object.get(name);
        return value == null || value.isJsonNull() ? null : value.getAsString();
    }

    /** A queued command, with Tebex's placeholders filled in. */
    record Command(int id, String line, int delaySeconds) {

        static Command of(JsonObject command, JsonObject player) {
            String name = text(player, "name");
            String uuid = text(player, "uuid");
            String line = text(command, "command");
            if (line == null) {
                line = "";
            }
            if (name != null) {
                line = line.replace("{username}", name).replace("{name}", name);
            }
            if (uuid != null) {
                UUID dashed = uuid(uuid);
                line = line.replace("{id}", uuid).replace("{uuid}", dashed == null ? uuid : dashed.toString());
            }
            JsonObject conditions = command.getAsJsonObject("conditions");
            int delay = conditions != null && conditions.has("delay") ? conditions.get("delay").getAsInt() : 0;
            return new Command(command.get("id").getAsInt(), line.strip(), Math.max(0, delay));
        }

        /** Tebex sends UUIDs without dashes. Null for anything else (a Bedrock XUID). */
        static UUID uuid(String text) {
            if (text == null) {
                return null;
            }
            String hex = text.replace("-", "");
            if (!hex.matches("[0-9a-fA-F]{32}")) {
                return null;
            }
            return UUID.fromString(hex.replaceFirst("(.{8})(.{4})(.{4})(.{4})(.{12})", "$1-$2-$3-$4-$5"));
        }
    }
}
