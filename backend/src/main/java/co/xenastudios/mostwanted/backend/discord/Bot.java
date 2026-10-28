package co.xenastudios.mostwanted.backend.discord;

import co.xenastudios.mostwanted.backend.stats.Stats;
import java.util.ArrayList;
import java.util.List;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import co.xenastudios.mostwanted.backend.accounts.LinkedRole;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The MostWanted Discord bot (docs/10-discord-bot.md): {@code /profile}, and linked players' roles. Its
 * commands are registered on the one server it's in, so changes show straight away.
 */
public final class Bot extends ListenerAdapter implements LinkedRole {

    private static final Logger LOG = LoggerFactory.getLogger(Bot.class);

    private final JDA jda;
    private final List<CommandData> commands;

    private Bot(JDA jda, List<CommandData> commands) {
        this.jda = jda;
        this.commands = commands;
    }

    /** Connects in the background; the API doesn't wait for Discord. */
    public static Bot start(String token, Stats stats, String site) {
        ProfileCommand profile = new ProfileCommand(stats, site);
        // No privileged intents yet: slash commands need none.
        JDA jda = JDABuilder.createLight(token, List.<GatewayIntent>of())
                .addEventListeners(profile)
                .build();
        Bot bot = new Bot(jda, List.of(ProfileCommand.DATA));
        jda.addEventListener(bot);
        return bot;
    }

    @Override
    public void onReady(ReadyEvent event) {
        for (Guild guild : jda.getGuilds()) {
            guild.updateCommands().addCommands(commands).queue(
                    done -> LOG.info("Discord commands registered on {}", guild.getName()),
                    failed -> LOG.warn("Couldn't register commands on {}", guild.getName(), failed));
        }
        LOG.info("Discord bot connected as {}", jda.getSelfUser().getName());
    }

    /**
     * Gives or takes the Linked role on every server the bot is in. Someone not in the
     * server is skipped. Unlinking takes their store rank's role too.
     */
    @Override
    public void update(String discordId, boolean linked) {
        for (Guild guild : jda.getGuilds()) {
            List<Role> roles = guild.getRolesByName("Linked", true);
            if (roles.isEmpty()) {
                LOG.warn("No Linked role on {}", guild.getName());
                continue;
            }
            UserSnowflake user = UserSnowflake.fromId(discordId);
            (linked ? guild.addRoleToMember(user, roles.getFirst()) : guild.removeRoleFromMember(user, roles.getFirst()))
                    .queue(done -> LOG.info("{} the Linked role for {}", linked ? "Gave" : "Took", discordId),
                            failed -> LOG.info("Couldn't change the Linked role for {}: {}", discordId,
                                    failed.getMessage()));
        }
        if (!linked) {
            rank(discordId, null);
        }
    }

    /** Takes every store rank's role (after unlinking). */
    public void rank(String discordId, String role) {
        roles(discordId, role, false, () -> { });
    }

    /**
     * For a linked player: gives the Linked role and the role named {@code role} (a
     * store rank's, or null for none), and takes the other ranks' roles, on every server
     * the bot is in. {@code done} runs once their roles are right.
     */
    public void sync(String discordId, String role, Runnable done) {
        roles(discordId, role, true, done);
    }

    private void roles(String discordId, String role, boolean linked, Runnable done) {
        for (Guild guild : jda.getGuilds()) {
            List<Role> want = new ArrayList<>();
            List<Role> ranks = new ArrayList<>();
            for (String name : RankRoles.ROLES.values()) {
                for (Role found : guild.getRolesByName(name, false)) {
                    ranks.add(found);
                    if (name.equals(role)) {
                        want.add(found);
                    }
                }
            }
            if (linked) {
                want.addAll(guild.getRolesByName("Linked", true));
            }
            if (ranks.isEmpty() && want.isEmpty()) {
                done.run();
                continue;
            }
            guild.retrieveMemberById(discordId).queue(member -> {
                List<Role> give = want.stream().filter(r -> !member.getRoles().contains(r)).toList();
                List<Role> take = ranks.stream().filter(r -> !want.contains(r) && member.getRoles().contains(r)).toList();
                if (give.isEmpty() && take.isEmpty()) {
                    done.run();
                    return;
                }
                guild.modifyMemberRoles(member, give, take).queue(
                        ok -> {
                            LOG.info("Rank role for {} is now {}", discordId, role == null ? "none" : role);
                            done.run();
                        },
                        failed -> LOG.info("Couldn't change the roles of {}: {}", discordId, failed.getMessage()));
            }, notMember -> { });  // Not in the server (yet): tried again next pass.
        }
    }

    /**
     * A direct message: a staff reply or an application decision. Someone with DMs
     * closed doesn't get it, which is fine: it's on the site and in game too.
     */
    public void dm(String discordId, String text) {
        jda.retrieveUserById(discordId)
                .flatMap(user -> user.openPrivateChannel())
                .flatMap(channel -> channel.sendMessage(text))
                .queue(sent -> { }, failed -> LOG.info("Couldn't DM {}: {}", discordId, failed.getMessage()));
    }

    /** The bot's custom status, and online (green) while the game server's up, idle (yellow) while it's down. */
    public void status(String text, boolean serverUp) {
        jda.getPresence().setPresence(serverUp ? OnlineStatus.ONLINE : OnlineStatus.IDLE, Activity.customStatus(text));
    }

    public boolean connected() {
        return jda.getStatus() == JDA.Status.CONNECTED;
    }

    public void stop() {
        jda.shutdown();
    }
}
