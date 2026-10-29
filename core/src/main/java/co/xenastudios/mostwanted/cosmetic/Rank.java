package co.xenastudios.mostwanted.cosmetic;

import org.bukkit.permissions.Permissible;

/**
 * The store's ranks (website/src/store/catalog.ts, server/setup/luckperms.txt). A player
 * has one if LuckPerms puts them in its group, which LuckPerms shows as the
 * {@code group.<name>} permission: no LuckPerms API needed.
 */
public enum Rank {
    VIP("vip", 0x99ff33),
    VIP_PLUS("vip_plus", 0x33ffcc),
    MVP("mvp", 0x6633ff),
    LEGEND("legend", 0xff3366);

    private final String group;
    private final int colour;

    Rank(String group, int colour) {
        this.group = group;
        this.colour = colour;
    }

    public String group() {
        return group;
    }

    public int colour() {
        return colour;
    }

    /** "vip-plus": the messages.yml key under rank. */
    public String key() {
        return group.replace('_', '-');
    }

    /**
     * Their highest rank, or null. Only a group they're really in counts: an op has
     * every permission by default, and isn't a Legend for it.
     */
    public static Rank of(Permissible player) {
        Rank[] ranks = values();
        for (int i = ranks.length - 1; i >= 0; i--) {
            String permission = "group." + ranks[i].group;
            if (player.isPermissionSet(permission) && player.hasPermission(permission)) {
                return ranks[i];
            }
        }
        return null;
    }
}
