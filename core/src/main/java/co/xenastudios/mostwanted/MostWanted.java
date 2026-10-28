package co.xenastudios.mostwanted;

import co.xenastudios.mostwanted.arrest.Arrests;
import co.xenastudios.mostwanted.arrest.Cuffing;
import co.xenastudios.mostwanted.board.BoardCommand;
import co.xenastudios.mostwanted.board.Leaderboards;
import co.xenastudios.mostwanted.combat.Combat;
import co.xenastudios.mostwanted.common.Database;
import co.xenastudios.mostwanted.config.Settings;
import co.xenastudios.mostwanted.cosmetic.CosmeticMenu;
import co.xenastudios.mostwanted.cosmetic.CosmeticStore;
import co.xenastudios.mostwanted.cosmetic.Cosmetics;
import co.xenastudios.mostwanted.duty.Duty;
import co.xenastudios.mostwanted.economy.Banking;
import co.xenastudios.mostwanted.escape.Escapes;
import co.xenastudios.mostwanted.escape.Gates;
import co.xenastudios.mostwanted.escape.Keycards;
import co.xenastudios.mostwanted.escape.Walls;
import co.xenastudios.mostwanted.gun.GiveCommand;
import co.xenastudios.mostwanted.gun.GunSettings;
import co.xenastudios.mostwanted.gun.Guns;
import co.xenastudios.mostwanted.hud.Hud;
import co.xenastudios.mostwanted.item.CustomItems;
import co.xenastudios.mostwanted.item.Handcuffs;
import co.xenastudios.mostwanted.menu.LeaderboardMenu;
import co.xenastudios.mostwanted.menu.MainMenu;
import co.xenastudios.mostwanted.menu.MenuItem;
import co.xenastudios.mostwanted.menu.Menus;
import co.xenastudios.mostwanted.menu.ProfileMenu;
import co.xenastudios.mostwanted.menu.SettingsMenu;
import co.xenastudios.mostwanted.menu.StoreMenu;
import co.xenastudios.mostwanted.message.Messages;
import co.xenastudios.mostwanted.notice.Notices;
import co.xenastudios.mostwanted.pack.ResourcePack;
import co.xenastudios.mostwanted.player.PlayerListener;
import co.xenastudios.mostwanted.player.PlayerStore;
import co.xenastudios.mostwanted.player.Profiles;
import co.xenastudios.mostwanted.player.StatsCommand;
import co.xenastudios.mostwanted.player.WebsiteCommand;
import co.xenastudios.mostwanted.privacy.Cleanup;
import co.xenastudios.mostwanted.riot.Riots;
import co.xenastudios.mostwanted.robbery.Robberies;
import co.xenastudios.mostwanted.shop.ShopSettings;
import co.xenastudios.mostwanted.shop.Shops;
import co.xenastudios.mostwanted.storage.GameLog;
import co.xenastudios.mostwanted.storage.Storage;
import co.xenastudios.mostwanted.store.StoreCatalog;
import co.xenastudios.mostwanted.store.TebexQueue;
import co.xenastudios.mostwanted.zone.Spawns;
import co.xenastudios.mostwanted.zone.ZoneCommand;
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
import org.bukkit.event.Listener;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.java.JavaPlugin;

/** The game's entry point. Each system registers itself here as it's built. */
public final class MostWanted extends JavaPlugin {

    private Storage storage;
    private PlayerStore players;
    private Walls walls;
    private Gates gates;
    private Robberies robberies;
    private TebexQueue store;
    private Notices notices;

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
        Escapes escapes = new Escapes(tracker, players, messages, gameLog);
        tracker.listen(escapes);
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
        Keycards keycards = new Keycards(items, players, messages, settings.escapes());
        arrests.onArrest(keycards::take);
        Duty duty = new Duty(players, combat, List.of(handcuffs, guns.guardKit(), keycards), spawns, messages,
                gameLog, clock);
        walls = new Walls(zones, players, messages, gameLog, settings.escapes(), clock,
                new File(getDataFolder(), "broken-walls.yml"), getLogger());
        walls.restoreSaved();
        gates = new Gates(zones, players, keycards, messages, gameLog, settings.escapes(), clock,
                new File(getDataFolder(), "open-gates.yml"), getLogger());
        gates.restoreSaved();
        Menus menus = new Menus(messages);
        CosmeticStore cosmeticStore = new CosmeticStore(storage);
        Cosmetics cosmetics = new Cosmetics(this, cosmeticStore, players, messages, gameLog, items);
        cosmetics.guns(guns);
        CosmeticMenu cosmeticMenu = new CosmeticMenu(cosmetics, items, menus);
        cosmeticMenu.guns(guns);
        escapes.onEscape(cosmetics::escaped);
        arrests.onArrestBy(cosmetics::arrested);
        hud.tags(cosmetics::tag);
        getServer().getScheduler().runTaskTimer(this, cosmetics::refreshRanks, 20 * 30, 20 * 30);
        getServer().getScheduler().runTaskTimer(this, cosmetics::applySkins, 40, 40);
        Shops shops = new Shops(shopSettings, guns, players, tracker, menus, gameLog, clock);
        for (Listener each : List.of(arrests, combat, duty, guns, menus, keycards, walls, gates, cosmetics,
                new Cuffing(players, combat, arrests, handcuffs, messages, settings.rules()))) {
            getServer().getPluginManager().registerEvents(each, this);
        }

        long saveTicks = settings.saveEvery().toSeconds() * 20;
        getServer().getScheduler().runTaskTimer(this, players::saveChanged, saveTicks, saveTicks);
        getServer().getScheduler().runTaskTimer(this, hud::tick, 10, 10);
        getServer().getScheduler().runTaskTimer(this, listener::sweep, 600, 600);
        getServer().getScheduler().runTaskTimer(this, arrests::tick, 20, 20);
        getServer().getScheduler().runTaskTimer(this, walls::tick, 20, 20);
        getServer().getScheduler().runTaskTimer(this, gates::tick, 20, 20);

        tracker.listen(new Banking(messages));
        robberies = new Robberies(zones, tracker, players, messages, gameLog, settings.robberies(), clock);
        Riots riots = new Riots(settings.riots(), players, tracker, messages, gameLog, clock);
        combat.riot(riots::active);
        getServer().getPluginManager().registerEvents(riots, this);
        getServer().getScheduler().runTaskTimer(this, riots::tick, 20, 20);
        // One live section fits the scoreboard: a riot comes before a robbery.
        hud.live(() -> riots.active() ? riots.hudLines() : robberies.hudLines());
        getServer().getScheduler().runTaskTimer(this, robberies::tick, 20, 20);

        // Log4j writes the server's logs to logs/ in the folder the server runs from.
        Cleanup cleanup = new Cleanup(Path.of("logs").toAbsolutePath(), storage,
                settings.retention(), clock, getLogger());
        getServer().getScheduler().runTaskTimerAsynchronously(this, cleanup::run, 20 * 60, 20 * 60 * 60 * 24);
        ResourcePack pack = new ResourcePack(settings.pack(), messages, getLogger());
        getServer().getPluginManager().registerEvents(pack, this);
        getServer().getScheduler().runTaskTimerAsynchronously(this, pack::refresh, 0, 20 * 60 * 5);
        store = new TebexQueue(this, settings.store(), new File(getDataFolder(), "store-delivered.txt"), getLogger());
        store.start();
        Leaderboards boards = new Leaderboards(this, storage, messages, settings.zone(), clock,
                new File(getDataFolder(), "boards.yml"), getLogger());
        boards.load();
        getServer().getPluginManager().registerEvents(boards, this);
        getServer().getScheduler().runTaskTimer(this, boards::refresh, 40, 20 * 60);
        StatsCommand stats = new StatsCommand(players, messages);
        WebsiteCommand website = new WebsiteCommand(storage, messages, this::onMainThread, clock);

        // The menus (docs/09-brand.md#menus): the main menu, and everything it opens.
        StoreCatalog catalog = new StoreCatalog(settings.store(), new File(getDataFolder(), "store-catalog.json"),
                getLogger());
        catalog.loadCached();
        getServer().getScheduler().runTaskTimerAsynchronously(this, catalog::refresh, 20, 20 * 60 * 30);
        Profiles profiles = new Profiles(this, storage, players, settings.zone(), clock);
        MainMenu mainMenu = new MainMenu(menus, players);
        SettingsMenu settingsMenu = new SettingsMenu(menus, players, hud, mainMenu::open);
        ProfileMenu profileMenu = new ProfileMenu(menus, profiles, players, settings.zone(), mainMenu::open,
                settingsMenu::open);
        LeaderboardMenu leaderboardMenu = new LeaderboardMenu(menus, boards, profileMenu, mainMenu::open);
        StoreMenu storeMenu = new StoreMenu(menus, catalog, cosmetics, mainMenu::open);
        mainMenu.links(profileMenu, settingsMenu, shops, cosmeticMenu, storeMenu, leaderboardMenu, website);
        shops.home(mainMenu::open);
        cosmeticMenu.links(mainMenu::open, storeMenu::open);
        MenuItem menuItem = new MenuItem(this, items, messages, mainMenu);
        getServer().getPluginManager().registerEvents(menuItem, this);
        notices = new Notices(this, storage, messages, settings.store().website(), getLogger());
        getServer().getPluginManager().registerEvents(notices, this);
        notices.start();
        // After a reload, those already online get theirs now.
        getServer().getOnlinePlayers().forEach(menuItem::give);

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            event.registrar().register(
                    MostWantedCommand.create(this, players, messages, gameLog,
                            new ZoneCommand(zones, tracker, messages, gameLog),
                            new GiveCommand(guns, messages, gameLog), cosmetics,
                            new BoardCommand(boards, messages, gameLog)),
                    "The prison break game's admin command.",
                    List.of("mw"));
            event.registrar().register(duty.command(), "Go on or off duty as a guard.");
            event.registrar().register(stats.command(), "Show or hide your stats on the website and Discord.");
            event.registrar().register(website.command(), "A code to sign in on mostwantedmc.com.");
            event.registrar().register(shops.command(), "Open the shop where you stand.");
            event.registrar().register(cosmeticMenu.command(), "Choose your trail, effects, gun skins and chat colour.");
            event.registrar().register(cosmeticMenu.joinMessageCommand(), "Set the message shown when you join.");
            event.registrar().register(mainMenu.command(), "Open the menu: everything in one place.");
            event.registrar().register(profileMenu.command(), "See your stats, or another player's.");
            event.registrar().register(settingsMenu.command(), "Your privacy, scoreboard and chat settings.");
            event.registrar().register(leaderboardMenu.command(), "The leaderboards.", List.of("lb"));
            event.registrar().register(storeMenu.command(), "Ranks and cosmetics, on the website.", List.of("buy"));
        });
    }

    @Override
    public void onDisable() {
        if (store != null) {
            store.stop();
        }
        if (notices != null) {
            notices.stop();
        }
        // Leave the map as it was built: no holes in walls, no gates open.
        if (walls != null) {
            walls.restoreAll();
        }
        if (gates != null) {
            gates.closeAll();
        }
        if (robberies != null) {
            robberies.hideBars();
        }
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
