package co.xenastudios.mostwanted.backend.discord;

import co.xenastudios.mostwanted.backend.stats.Stats;
import co.xenastudios.mostwanted.common.Stat;
import co.xenastudios.mostwanted.common.style.Money;
import co.xenastudios.mostwanted.common.style.Palette;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** {@code /profile <player>}: a player's stats card, linking to their page on the site. */
final class ProfileCommand extends ListenerAdapter {

    static final CommandData DATA = Commands.slash("profile", "A player's MostWanted stats")
            .addOption(OptionType.STRING, "player", "Their Minecraft name", true);

    private static final Logger LOG = LoggerFactory.getLogger(ProfileCommand.class);

    private final Stats stats;
    private final String site;

    ProfileCommand(Stats stats, String site) {
        this.stats = stats;
        this.site = site;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("profile")) {
            return;
        }
        String name = event.getOption("player", "", option -> option.getAsString().strip());
        event.deferReply().queue();
        // Off JDA's event thread: it's shared by every event.
        Thread.ofVirtual().start(() -> {
            try {
                stats.player(name).ifPresentOrElse(
                        profile -> event.getHook().sendMessageEmbeds(card(profile)).queue(),
                        () -> event.getHook().sendMessage("Nobody called **" + MarkdownSafe.escape(name)
                                + "** has played yet, or their stats are private.").queue());
            } catch (RuntimeException e) {
                LOG.error("/profile {} failed", name, e);
                event.getHook().sendMessage("Couldn't load stats right now. Try again in a minute.").queue();
            }
        });
    }

    MessageEmbed card(Stats.Profile profile) {
        Map<String, Long> all = profile.stats().get(Stats.Period.ALL.key());
        String role = profile.role();
        EmbedBuilder card = new EmbedBuilder()
                .setTitle(profile.name(), site + "/player?name=" + URLEncoder.encode(profile.name(), StandardCharsets.UTF_8))
                .setColor(colour(role))
                .setThumbnail("https://mc-heads.net/avatar/" + profile.uuid() + "/64")
                .addField("Role", Character.toUpperCase(role.charAt(0)) + role.substring(1), true);
        if (profile.bounty() > 0) {
            card.addField("Bounty", Money.format(profile.bounty()), true);
        }
        card.addField("Escapes", count(all, Stat.ESCAPES), true)
                .addField("Robberies", count(all, Stat.ROBBERIES), true)
                .addField("Times arrested", count(all, Stat.TIMES_ARRESTED), true)
                .addField("Arrests", count(all, Stat.ARRESTS), true)
                .addField("Bounty earned", Money.format(all.getOrDefault(Stat.BOUNTY_EARNED.key(), 0L)), true)
                .setTimestamp(profile.lastSeen());
        return card.build();
    }

    private static String count(Map<String, Long> stats, Stat stat) {
        return String.format("%,d", stats.getOrDefault(stat.key(), 0L));
    }

    private static int colour(String role) {
        return switch (role) {
            case "criminal" -> Palette.CRIMINAL;
            case "guard" -> Palette.GUARD;
            default -> Palette.INMATE;
        };
    }
}
