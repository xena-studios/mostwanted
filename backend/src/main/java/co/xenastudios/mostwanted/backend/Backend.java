package co.xenastudios.mostwanted.backend;

import co.xenastudios.mostwanted.backend.accounts.Accounts;
import co.xenastudios.mostwanted.backend.accounts.DiscordOAuth;
import co.xenastudios.mostwanted.backend.accounts.LinkedRole;
import co.xenastudios.mostwanted.backend.discord.RankRoles;
import co.xenastudios.mostwanted.backend.discord.ServerStatus;
import co.xenastudios.mostwanted.backend.accounts.Sessions;
import co.xenastudios.mostwanted.backend.api.AccountRoutes;
import co.xenastudios.mostwanted.backend.api.Api;
import co.xenastudios.mostwanted.backend.discord.Bot;
import co.xenastudios.mostwanted.backend.stats.Stats;
import co.xenastudios.mostwanted.backend.support.Applications;
import co.xenastudios.mostwanted.backend.support.Inbox;
import co.xenastudios.mostwanted.backend.support.Staff;
import co.xenastudios.mostwanted.backend.support.Tickets;
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
        try {
            // Before anything starts: a wrong certificate path or key stops it here, with the reason.
            Api.checkTls(config);
        } catch (IllegalArgumentException e) {
            LOG.error("Bad TLS settings: {}", e.getMessage());
            System.exit(2);
            return;
        }
        LOG.info("Starting with {}", config);
        Database database = connect(config);
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
        AccountRoutes accountRoutes = Api.accounts(config, accounts, sessions, discord, bot == null ? LinkedRole.NONE : bot);
        Inbox inbox = new Inbox(database.sql(), config.site());
        if (bot != null) {
            inbox.dm(bot::dm);
        }
        Api.support(accountRoutes, accounts, new Tickets(database.sql()), new Applications(database.sql()), inbox,
                new Staff(database.sql(), Clock.systemUTC()));
        Api api = Api.start(config, stats, accountRoutes, bot == null ? null : bot::connected);
        Thread.ofVirtual().name("daily-cleanup").start(() -> daily(sessions));
        if (bot != null) {
            RankRoles roles = new RankRoles(accounts, stats, bot);
            Thread.ofVirtual().name("rank-roles").start(() -> every(Duration.ofMinutes(10), roles::sync));
            ServerStatus status = new ServerStatus(database.sql(), bot, Clock.systemUTC());
            Thread.ofVirtual().name("bot-status").start(() -> every(Duration.ofMinutes(1), status::update));
        }
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

    /**
     * Starts the pool, waiting up to 2 minutes for the database: after a reboot the
     * backend can come up before PostgreSQL does.
     */
    private static Database connect(BackendConfig config) {
        for (int attempt = 1; ; attempt++) {
            try {
                return Database.start(config.database(), "backend");
            } catch (RuntimeException e) {
                if (attempt == 12) {
                    throw e;
                }
                LOG.warn("Database not reachable yet ({}); trying again in 10 s", e.getMessage());
                try {
                    Thread.sleep(Duration.ofSeconds(10));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
    }

    /** Runs {@code task} now and then, a minute after starting (the bot needs to connect first). */
    private static void every(Duration gap, Runnable task) {
        try {
            Thread.sleep(Duration.ofMinutes(1));
            while (true) {
                try {
                    task.run();
                } catch (RuntimeException e) {
                    LOG.warn("A Discord task failed", e);
                }
                Thread.sleep(gap);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
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
