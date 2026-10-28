package co.xenastudios.mostwanted.cosmetic;

import org.bukkit.Material;

/**
 * Every cosmetic the store sells (website/src/store/catalog.ts: the ids must match).
 * Owning one is the {@code mostwanted.cosmetic.<id>} permission; each sits in a slot,
 * and a player uses one per slot.
 */
public enum Cosmetic {
    TRAIL_CASH("trail-cash", "trail", "Cash Trail", Material.PAPER),
    TRAIL_EMBER("trail-ember", "trail", "Ember Trail", Material.BLAZE_POWDER),
    TRAIL_SMOKE("trail-smoke", "trail", "Smoke Trail", Material.GUNPOWDER),
    TRAIL_SIREN("trail-siren", "trail", "Siren Trail", Material.REDSTONE),
    ESCAPE_FIREWORKS("escape-fireworks", "escape", "Fireworks Escape", Material.FIREWORK_ROCKET),
    ESCAPE_CONFETTI("escape-confetti", "escape", "Confetti Escape", Material.PINK_DYE),
    ESCAPE_THUNDER("escape-thunder", "escape", "Thunder Escape", Material.LIGHTNING_ROD),
    ARREST_SPOTLIGHT("arrest-spotlight", "arrest", "Spotlight Arrest", Material.END_ROD),
    ARREST_SIREN("arrest-siren", "arrest", "Siren Arrest", Material.BELL),
    SKIN_GILDED_PISTOL("skin-gilded-pistol", "skin:pistol", "Gilded Pistol", Material.IRON_HOE),
    SKIN_COPPER_SHOTGUN("skin-copper-shotgun", "skin:shotgun", "Copper Shotgun", Material.DIAMOND_HOE),
    SKIN_MIDNIGHT_RIFLE("skin-midnight-rifle", "skin:rifle", "Midnight Rifle", Material.NETHERITE_HOE),
    SKIN_DIAMOND_TASER("skin-diamond-taser", "skin:taser", "Diamond Taser", Material.BREEZE_ROD),
    CHAT_COLOURS("chat-colours", "chat", "Chat Colours", Material.WRITABLE_BOOK);

    private final String id;
    private final String slot;
    private final String displayName;
    private final Material icon;

    Cosmetic(String id, String slot, String displayName, Material icon) {
        this.id = id;
        this.slot = slot;
        this.displayName = displayName;
        this.icon = icon;
    }

    public String id() {
        return id;
    }

    public String slot() {
        return slot;
    }

    public String displayName() {
        return displayName;
    }

    public Material icon() {
        return icon;
    }

    public String permission() {
        return "mostwanted.cosmetic." + id;
    }

    /** For a gun skin, the gun it's for ("pistol"); otherwise null. */
    public String gun() {
        return slot.startsWith("skin:") ? slot.substring(5) : null;
    }

    /** For a gun skin, the pack model it shows ("pistol_gilded"). */
    public String model() {
        return gun() + "_" + id.substring(5, id.lastIndexOf('-'));
    }

    public static Cosmetic byId(String id) {
        for (Cosmetic cosmetic : values()) {
            if (cosmetic.id.equals(id)) {
                return cosmetic;
            }
        }
        return null;
    }
}
