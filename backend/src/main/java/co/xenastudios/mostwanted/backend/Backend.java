package co.xenastudios.mostwanted.backend;

import co.xenastudios.mostwanted.backend.accounts.Accounts;
import co.xenastudios.mostwanted.backend.accounts.DiscordOAuth;
import co.xenastudios.mostwanted.backend.accounts.LinkedRole;
import co.xenastudios.mostwanted.backend.accounts.Sessions;
import co.xenastudios.mostwanted.backend.api.Api;
import co.xenastudios.mostwanted.backend.discord.Bot;
import co.xenastudios.mostwanted.backend.stats.Stats;
import co.xenastudios.mostwanted.common.Database;
import java.time.Clock;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The backend: the website's API and the Discord bot, sharing one database pool.
 * Settings come from environment variables ({@link BackendConfig}).
 */
public final class Backend {

    private static final Logger LOG = LoggerFactory.getLogger(Backend.class);

    private Backend() {}

    public static void main(String[] args) {
        BackendConfig config;
        try {
            config = BackendConfig.fromEnv(System.getenv());
        } catch (RuntimeException e) {
            LOG.error("Bad settings: {}", e.getMessage());
            System.exit(2);
            return;
        }
        LOG.info("Starting with {}", config);
        Database database = Database.start(config.database(), "backend");
        Stats stats = new Stats(database.sql(), config.zone(), Clock.systemUTC());
        Bot bot = config.discordToken() == null ? null : Bot.start(config.discordToken(), stats, config.site());
        if (bot == null) {
            LOG.info("No DISCORD_TOKEN: the bot is off");
        }
        Accounts accounts = new Accounts(database.sql());
        Sessions sessions = new Sessions(database.sql(), Clock.systemUTC());
        DiscordOAuth discord = config.discordClientSecret() == null ? null : new DiscordOAuth(config.discordClientId(),
                config.discordClientSecret(), config.apiUrl() + "/v1/auth/discord/callback", "https://discord.com/api/v10");
        if (discord == null) {
            LOG.info("No DISCORD_CLIENT_SECRET: signing in with Discord is off");
        }
        Api api = Api.start(config, stats,
                Api.accounts(config, accounts, sessions, discord, bot == null ? LinkedRole.NONE : bot),
                bot == null ? null : bot::connected);
        Thread.ofVirtual().name("daily-cleanup").start(() -> daily(sessions));
        LOG.info("API listening on {}:{}", config.host(), api.port());

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOG.info("Stopping");
            api.stop();
            if (bot != null) {
                bot.stop();
            }
            database.close();
        }, "shutdown"));
    }

    /** Expired sessions and codes go once a day: the privacy page promises 30-day sessions. */
    private static void daily(Sessions sessions) {
        while (true) {
            try {
                int deleted = sessions.cleanup();
                if (deleted > 0) {
                    LOG.info("Deleted {} expired sessions and codes", deleted);
                }
            } catch (RuntimeException e) {
                LOG.warn("Cleanup failed: {}", e.getMessage());
            }
            try {
                Thread.sleep(Duration.ofDays(1));
            } catch (InterruptedException e) {
                return;
            }
        }
    }
}
