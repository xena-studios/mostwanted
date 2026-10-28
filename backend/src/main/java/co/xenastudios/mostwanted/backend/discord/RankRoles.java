package co.xenastudios.mostwanted.backend.discord;

import co.xenastudios.mostwanted.backend.accounts.Accounts;
import co.xenastudios.mostwanted.backend.stats.Stats;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Keeps store ranks' Discord roles in step: each linked player gets the role named
 * after their rank (VIP, VIP+, MVP or Legend) and loses the others. Checks every
 * linked account now and then; only changes are sent to Discord.
 */
public final class RankRoles {

    private static final Logger LOG = LoggerFactory.getLogger(RankRoles.class);

    /** The rank keys the API uses, and the Discord role each is. */
    public static final Map<String, String> ROLES = Map.of("vip", "VIP", "vip-plus", "VIP+", "mvp", "MVP",
            "legend", "Legend");

    private final Accounts accounts;
    private final Stats stats;
    private final Bot bot;
    /** What each Discord user was last given; "" for no rank. Empty after a restart, so everyone's checked once. */
    private final Map<String, String> given = new HashMap<>();

    public RankRoles(Accounts accounts, Stats stats, Bot bot) {
        this.accounts = accounts;
        this.stats = stats;
        this.bot = bot;
    }

    /** One pass over every linked account. Blocks on the database; Discord calls are queued. */
    public void sync() {
        if (!bot.connected()) {
            return;
        }
        Map<String, UUID> linked;
        try {
            linked = accounts.linked();
        } catch (RuntimeException e) {
            LOG.warn("Couldn't read linked accounts for rank roles: {}", e.getMessage());
            return;
        }
        for (Map.Entry<String, UUID> each : linked.entrySet()) {
            String rank = Objects.requireNonNullElse(stats.supporter(each.getValue()), "");
            if (!rank.equals(given.get(each.getKey()))) {
                bot.rank(each.getKey(), ROLES.get(rank));
                given.put(each.getKey(), rank);
            }
        }
        // Unlinked since: forget them, so linking again checks them again.
        given.keySet().retainAll(linked.keySet());
    }
}
