package co.xenastudios.mostwanted.backend;

import co.xenastudios.mostwanted.backend.api.Api;
import co.xenastudios.mostwanted.backend.discord.Bot;
import co.xenastudios.mostwanted.backend.stats.Stats;
import co.xenastudios.mostwanted.common.Database;
import java.time.Clock;
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
        Api api = Api.start(config, stats, bot == null ? null : bot::connected);
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
}
