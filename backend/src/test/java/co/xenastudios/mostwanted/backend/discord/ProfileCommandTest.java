package co.xenastudios.mostwanted.backend.discord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.xenastudios.mostwanted.backend.stats.Stats;
import co.xenastudios.mostwanted.common.style.Palette;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.junit.jupiter.api.Test;

class ProfileCommandTest {

    private final ProfileCommand command = new ProfileCommand(null, "https://mostwantedmc.com");

    @Test
    void theCardShowsTheirStatsInTheirRolesColour() {
        Map<String, Long> all = Map.of("escapes", 1_234L, "arrests", 0L, "bounty_earned", 50_000L);
        MessageEmbed card = command.card(new Stats.Profile(UUID.randomUUID(), "Ada Lovelace", false, "criminal", 7_500,
                Instant.EPOCH, Instant.EPOCH, Map.of("all", all), null));
        assertEquals("Ada Lovelace", card.getTitle());
        assertEquals("https://mostwantedmc.com/player?name=Ada+Lovelace", card.getUrl());
        assertEquals(Palette.CRIMINAL, card.getColorRaw());
        assertTrue(card.getFields().stream().anyMatch(f -> f.getName().equals("Escapes") && f.getValue().equals("1,234")));
        assertTrue(card.getFields().stream().anyMatch(f -> f.getName().equals("Bounty")));
        assertEquals(null, card.getFooter(), "no branding footer");
    }

    @Test
    void noBountyNoBountyField() {
        MessageEmbed card = command.card(new Stats.Profile(UUID.randomUUID(), "Bo", false, "guard", 0,
                Instant.EPOCH, Instant.EPOCH, Map.of("all", Map.of()), "mvp"));
        assertTrue(card.getFields().stream().noneMatch(f -> f.getName().equals("Bounty")));
        assertEquals(Palette.GUARD, card.getColorRaw());
    }

    @Test
    void typedNamesCantFormatOrPing() {
        assertEquals("\\*\\*bold\\*\\* \\<\\@123\\>", MarkdownSafe.escape("**bold** <@123>"));
    }
}
