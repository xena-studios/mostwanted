package co.xenastudios.mostwanted;

import co.xenastudios.mostwanted.arrest.Arrests;
import co.xenastudios.mostwanted.arrest.Cuffing;
import co.xenastudios.mostwanted.combat.Combat;
import co.xenastudios.mostwanted.common.Database;
import co.xenastudios.mostwanted.config.Settings;
import co.xenastudios.mostwanted.duty.Duty;
import co.xenastudios.mostwanted.economy.Banking;
import co.xenastudios.mostwanted.escape.Escapes;
import co.xenastudios.mostwanted.gun.GiveCommand;
import co.xenastudios.mostwanted.gun.GunSettings;
import co.xenastudios.mostwanted.gun.Guns;
import co.xenastudios.mostwanted.item.CustomItems;
import co.xenastudios.mostwanted.item.Handcuffs;
import co.xenastudios.mostwanted.hud.Hud;
import co.xenastudios.mostwanted.message.Messages;
import co.xenastudios.mostwanted.player.PlayerListener;
import co.xenastudios.mostwanted.player.PlayerStore;
import co.xenastudios.mostwanted.player.StatsCommand;
import co.xenastudios.mostwanted.player.WebsiteCommand;
import co.xenastudios.mostwanted.privacy.Cleanup;
import co.xenastudios.mostwanted.robbery.Robberies;
import co.xenastudios.mostwanted.shop.ShopSettings;
import co.xenastudios.mostwanted.shop.Shops;
import co.xenastudios.mostwanted.storage.GameLog;
import co.xenastudios.mostwanted.storage.Storage;
import co.xenastudios.mostwanted.zone.ZoneCommand;
import co.xenastudios.mostwanted.zone.Spawns;
import co.xenastudios.mostwanted.zone.ZoneNotices;
import co.xenastudios.mostwanted.zone.ZoneStore;
import co.xenastudios.mostwanted.zone.ZoneTracker;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.logging.Level;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.java.JavaPlugin;

/** The game's entry point. Each system registers itself here as it's built. */
public final class MostWanted extends JavaPlugin {

    private Storage storage;
    private PlayerStore players;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Settings settings;
        Messages messages;
        GunSettings gunSettings;
        ShopSettings shopSettings;
        try {
            settings = Settings.read(getConfig());
            messages = Messages.load(this);
            gunSettings = GunSettings.read(file("guns.yml"));
            shopSettings = ShopSettings.read(file("shops.yml"), gunSettings);
        } catch (IllegalArgumentException | IOException e) {
            stop("Fix config.yml, messages.yml, guns.yml or shops.yml and restart: " + e.getMessage(), null);
            return;
        }
        Database database;
        try {
            database = Database.start(settings.database(), "core");
        } catch (RuntimeException e) {
            stop("Can't use the database at " + settings.database() + ". MostWanted is off until it can", e);
            return;
        }
        storage = new Storage(database, getLogger());
        GameLog gameLog = new GameLog(storage);
        Clock clock = Clock.systemUTC();
        players = new PlayerStore(storage, this::onMainThread, settings.zone(), clock, getLogger());
        Hud hud = new Hud(players, messages, clock, settings.zone(), settings.zoneLabel());
        PlayerListener listener = new PlayerListener(players, messages, hud, getLogger());
        getServer().getPluginManager().registerEvents(listener, this);

        ZoneStore zones = new ZoneStore(storage);
        try {
            zones.load();
        } catch (RuntimeException e) {
            stop("Couldn't load the zones", e);
            return;
        }
        ZoneTracker tracker = new ZoneTracker(zones, players);
        zones.onChange(tracker::refreshAll);
        tracker.listen(new ZoneNotices(messages));
        tracker.listen(new Escapes(tracker, messages, gameLog));
        getServer().getPluginManager().registerEvents(tracker, this);
        getLogger().info("Loaded " + zones.all().size() + " zones");

        Spawns spawns = new Spawns(zones);
        CustomItems items = new CustomItems(this);
        Handcuffs handcuffs = new Handcuffs(items, messages);
        Arrests arrests = new Arrests(this, players, zones, tracker, spawns, messages, gameLog, settings.rules(), clock);
        tracker.listen(arrests);
        Combat combat = new Combat(players, tracker, arrests, handcuffs, messages, gameLog, settings.rules(), clock);
        Guns guns = new Guns(this, gunSettings, items, players, combat, messages);
        arrests.onArrest(guns::confiscate);
        Duty duty = new Duty(players, combat, handcuffs, guns, spawns, messages, gameLog, clock);
        Shops shops = new Shops(shopSettings, guns, players, tracker, messages, gameLog, clock);
        for (var each : List.of(arrests, combat, duty, guns, shops,
                new Cuffing(players, combat, arrests, handcuffs, messages, settings.rules()))) {
            getServer().getPluginManager().registerEvents(each, this);
        }

        long saveTicks = settings.saveEvery().toSeconds() * 20;
        getServer().getScheduler().runTaskTimer(this, players::saveChanged, saveTicks, saveTicks);
        getServer().getScheduler().runTaskTimer(this, hud::tick, 10, 10);
        getServer().getScheduler().runTaskTimer(this, listener::sweep, 600, 600);
        getServer().getScheduler().runTaskTimer(this, arrests::tick, 20, 20);

        tracker.listen(new Banking(messages));
        Robberies robberies = new Robberies(zones, tracker, players, messages, gameLog, settings.robberies(), clock);
        hud.live(robberies::hudLines);
        getServer().getScheduler().runTaskTimer(this, robberies::tick, 20, 20);

        // Log4j writes the server's logs to logs/ in the folder the server runs from.
        Cleanup cleanup = new Cleanup(Path.of("logs").toAbsolutePath(), storage,
                settings.retention(), clock, getLogger());
        getServer().getScheduler().runTaskTimerAsynchronously(this, cleanup::run, 20 * 60, 20 * 60 * 60 * 24);
        StatsCommand stats = new StatsCommand(players, messages);
        WebsiteCommand website = new WebsiteCommand(storage, messages, this::onMainThread, clock);

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            event.registrar().register(
                    MostWantedCommand.create(this, players, messages, gameLog,
                            new ZoneCommand(zones, tracker, messages, gameLog),
                            new GiveCommand(guns, messages, gameLog)),
                    "The prison break game's admin command.",
                    List.of("mw"));
            event.registrar().register(duty.command(), "Go on or off duty as a guard.");
            event.registrar().register(stats.command(), "Show or hide your stats on the website and Discord.");
            event.registrar().register(website.command(), "A code to sign in on mostwantedmc.com.");
            event.registrar().register(shops.command(), "Open the shop where you stand.");
        });
    }

    @Override
    public void onDisable() {
        if (players != null) {
            players.saveAll();
        }
        if (storage != null) {
            storage.close();
        }
    }

    /** Whether the database is connected; for /mostwanted info. */
    boolean databaseReady() {
        return storage != null;
    }

    private void onMainThread(Runnable task) {
        try {
            getServer().getScheduler().runTask(this, task);
        } catch (IllegalPluginAccessException e) {
            // Shutting down: saveAll covers it.
        }
    }

    /** A file in the plugin folder, written from the jar's copy the first time. */
    private YamlConfiguration file(String name) {
        if (!new File(getDataFolder(), name).exists()) {
            saveResource(name, false);
        }
        return YamlConfiguration.loadConfiguration(new File(getDataFolder(), name));
    }

    private void stop(String why, Exception cause) {
        getLogger().log(Level.SEVERE, why, cause);
        getServer().getPluginManager().disablePlugin(this);
    }
}
