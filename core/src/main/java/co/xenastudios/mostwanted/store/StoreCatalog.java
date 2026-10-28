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
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

/**
 * What the store sells, for the store menu: the website's own catalogue
 * (website/src/store/catalog.ts, served as /store.json), so the game and the website
 * never disagree. Read off the main thread now and then, and kept in a file for when
 * the website can't be reached at startup.
 */
public final class StoreCatalog {

    /** One package, as the website describes it. */
    public record Item(String id, String name, String category, String kind, double price, String blurb,
            List<String> perks, List<String> includes) {

        /** "$4.99". */
        public String priceText() {
            return String.format(Locale.US, "$%.2f", price);
        }
    }

    private final StoreSettings settings;
    private final File cache;
    private final Logger log;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL).build();
    private volatile List<Item> items = List.of();

    public StoreCatalog(StoreSettings settings, File cache, Logger log) {
        this.settings = settings;
        this.cache = cache;
        this.log = log;
    }

    public List<Item> items() {
        return items;
    }

    /** The /buy link for a package: opens the store with it in the cart. */
    public String link(Item item) {
        return settings.website() + "/buy/" + item.id();
    }

    /** The website's store page. */
    public String storeLink() {
        return settings.website() + "/store";
    }

    /** At startup: the last catalogue read, if there is one. Small, so fine on the main thread. */
    public void loadCached() {
        if (!cache.exists()) {
            return;
        }
        try {
            items = parse(Files.readString(cache.toPath()));
        } catch (IOException | RuntimeException e) {
            log.warning("Couldn't read " + cache.getName() + ": " + e.getMessage());
        }
    }

    /** Off the main thread: the website's catalogue now. Keeps the last one if it can't be read. */
    public void refresh() {
        try {
            HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(settings.website() + "/store.json"))
                    .timeout(Duration.ofSeconds(15)).header("User-Agent", "MostWanted").GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IOException("the website answered " + response.statusCode());
            }
            List<Item> read = parse(response.body());
            if (!read.equals(items)) {
                items = read;
                Files.writeString(cache.toPath(), response.body());
            }
        } catch (IOException | RuntimeException e) {
            log.warning("Couldn't read the store's catalogue: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    static List<Item> parse(String json) {
        List<Item> items = new ArrayList<>();
        for (JsonElement each : JsonParser.parseString(json).getAsJsonObject().getAsJsonArray("packages")) {
            JsonObject item = each.getAsJsonObject();
            items.add(new Item(text(item, "id"), text(item, "name"), text(item, "category"), text(item, "kind"),
                    item.get("price").getAsDouble(), text(item, "blurb"), list(item, "perks"), list(item, "includes")));
        }
        return List.copyOf(items);
    }

    private static String text(JsonObject object, String name) {
        JsonElement value = object.get(name);
        return value == null || value.isJsonNull() ? "" : value.getAsString();
    }

    private static List<String> list(JsonObject object, String name) {
        JsonElement value = object.get(name);
        if (value == null || !value.isJsonArray()) {
            return List.of();
        }
        List<String> list = new ArrayList<>();
        for (JsonElement each : (JsonArray) value) {
            list.add(each.getAsString());
        }
        return List.copyOf(list);
    }
}
