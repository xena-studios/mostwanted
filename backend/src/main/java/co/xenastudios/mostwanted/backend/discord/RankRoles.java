package co.xenastudios.mostwanted.backend.discord;

import co.xenastudios.mostwanted.backend.accounts.Accounts;
import co.xenastudios.mostwanted.backend.stats.Stats;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Keeps linked players' Discord roles in step: the Linked role, and the role named
 * after their store rank (VIP, VIP+, MVP or Legend) without the others. Checks every
 * linked account now and then; only changes are sent to Discord.
 */
public final class RankRoles {

    private static final Logger LOG = LoggerFactory.getLogger(RankRoles.class);

    /** The rank keys the API uses, and the Discord role each is. */
    public static final Map<String, String> ROLES = Map.of("vip", "VIP", "vip-plus", "VIP+", "mvp", "MVP",
            "legend", "Legend");

    /** Every this many passes, everyone's checked again: someone may have left and rejoined. */
    private static final int FULL_CHECK = 6;

    private final Accounts accounts;
    private final Stats stats;
    private final Bot bot;
    /**
     * The rank ("" for none) each Discord user's roles are known to match; set once
     * Discord confirms, so a failed change is tried again on the next pass.
     */
    private final Map<String, String> synced = new ConcurrentHashMap<>();
    private int passes;

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
        Map<UUID, String> ranks;
        try {
            linked = accounts.linked();
            ranks = stats.supporters(linked.values());
        } catch (RuntimeException e) {
            // Not "nobody has a rank": leave everyone's roles as they are until it can be read.
            LOG.warn("Couldn't read linked accounts' ranks for Discord roles: {}", e.getMessage());
            return;
        }
        if (passes++ % FULL_CHECK == 0) {
            synced.clear();
        }
        for (Map.Entry<String, UUID> each : linked.entrySet()) {
            String discordId = each.getKey();
            String rank = ranks.getOrDefault(each.getValue(), "");
            if (!rank.equals(synced.get(discordId))) {
                bot.sync(discordId, ROLES.get(rank), () -> synced.put(discordId, rank));
            }
        }
        // Unlinked since: forget them, so linking again checks them again.
        synced.keySet().retainAll(linked.keySet());
    }
}
