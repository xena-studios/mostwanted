package co.xenastudios.mostwanted;

import co.xenastudios.mostwanted.common.Database;
import co.xenastudios.mostwanted.config.Settings;
import co.xenastudios.mostwanted.escape.Escapes;
import co.xenastudios.mostwanted.hud.Hud;
import co.xenastudios.mostwanted.message.Messages;
import co.xenastudios.mostwanted.player.PlayerListener;
import co.xenastudios.mostwanted.player.PlayerStore;
import co.xenastudios.mostwanted.storage.GameLog;
import co.xenastudios.mostwanted.storage.Storage;
import co.xenastudios.mostwanted.zone.ZoneCommand;
import co.xenastudios.mostwanted.zone.ZoneNotices;
import co.xenastudios.mostwanted.zone.ZoneStore;
import co.xenastudios.mostwanted.zone.ZoneTracker;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.io.IOException;
import java.time.Clock;
import java.util.List;
import java.util.logging.Level;
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
        try {
            settings = Settings.read(getConfig());
            messages = Messages.load(this);
        } catch (IllegalArgumentException | IOException e) {
            stop("Fix config.yml or messages.yml and restart: " + e.getMessage(), null);
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

        long saveTicks = settings.saveEvery().toSeconds() * 20;
        getServer().getScheduler().runTaskTimer(this, players::saveChanged, saveTicks, saveTicks);
        getServer().getScheduler().runTaskTimer(this, hud::tick, 10, 10);
        getServer().getScheduler().runTaskTimer(this, listener::sweep, 600, 600);

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> event.registrar().register(
                MostWantedCommand.create(this, players, messages, gameLog,
                        new ZoneCommand(zones, tracker, messages, gameLog)),
                "The prison break game's admin command.",
                List.of("mw")));
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

    private void stop(String why, Exception cause) {
        getLogger().log(Level.SEVERE, why, cause);
        getServer().getPluginManager().disablePlugin(this);
    }
}
