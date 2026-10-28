package co.xenastudios.mostwanted.shop;

import co.xenastudios.mostwanted.gun.GunSettings;
import co.xenastudios.mostwanted.player.Role;
import co.xenastudios.mostwanted.zone.ZoneKind;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/** shops.yml: each shop, where it opens, for whom, and what it sells. */
public record ShopSettings(Map<String, Shop> shops) {

    /**
     * A shop. /shop opens it for a player with {@code role} standing in a {@code zone} zone.
     */
    public record Shop(String id, String title, ZoneKind zone, Role role, List<Entry> items) {}

    /**
     * Something for sale: "gun:pistol", "ammo:pistol", or a vanilla item such as
     * "minecraft:iron_chestplate". {@code amount} of it for {@code price}.
     */
    public record Entry(String item, int amount, long price) {

        public boolean gun() {
            return item.startsWith("gun:");
        }

        public boolean ammo() {
            return item.startsWith("ammo:");
        }

        /** The gun or ammo id after the prefix. */
        public String id() {
            return item.substring(item.indexOf(':') + 1);
        }
    }

    /** A menu holds at most five rows of goods; the sixth shows your money. */
    public static final int MAX_ITEMS = 45;

    public static ShopSettings read(YamlConfiguration file, GunSettings guns) {
        Map<String, Shop> shops = new LinkedHashMap<>();
        ConfigurationSection section = file.getConfigurationSection("shops");
        if (section == null) {
            return new ShopSettings(Map.of());
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection s = section.getConfigurationSection(id);
            ZoneKind zone = ZoneKind.fromKey(s.getString("zone", ""));
            if (zone == null) {
                throw new IllegalArgumentException("shop " + id + " needs a zone kind, such as hideout or police");
            }
            Role role;
            try {
                role = Role.fromKey(s.getString("role", ""));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("shop " + id + " needs a role: inmate, criminal or guard");
            }
            List<Entry> entries = new ArrayList<>();
            for (Map<?, ?> raw : s.getMapList("items")) {
                Entry entry = new Entry(String.valueOf(raw.get("item")).toLowerCase(Locale.ROOT),
                        raw.get("amount") instanceof Number n ? n.intValue() : 1,
                        raw.get("price") instanceof Number n ? n.longValue() : -1);
                check(id, entry, guns);
                entries.add(entry);
            }
            if (entries.size() > MAX_ITEMS) {
                throw new IllegalArgumentException("shop " + id + " sells more than " + MAX_ITEMS + " things");
            }
            shops.put(id, new Shop(id, s.getString("title", id), zone, role, List.copyOf(entries)));
        }
        return new ShopSettings(Map.copyOf(shops));
    }

    private static void check(String shop, Entry entry, GunSettings guns) {
        if (entry.price() < 0 || entry.amount() < 1 || entry.amount() > 64) {
            throw new IllegalArgumentException("shop " + shop + ": " + entry.item() + " needs a price, and an amount of 1 to 64");
        }
        if (entry.gun()) {
            GunSettings.Gun gun = guns.guns().get(entry.id());
            if (gun == null) {
                throw new IllegalArgumentException("shop " + shop + " sells gun " + entry.id() + ", which isn't in guns.yml");
            }
            if (gun.guardOnly()) {
                throw new IllegalArgumentException("shop " + shop + " sells " + entry.id() + ", which guards get on duty");
            }
        } else if (entry.ammo()) {
            if (!guns.ammo().containsKey(entry.id())) {
                throw new IllegalArgumentException("shop " + shop + " sells ammo " + entry.id() + ", which isn't in guns.yml");
            }
        } else if (Material.matchMaterial(entry.item()) == null) {
            throw new IllegalArgumentException("shop " + shop + " sells " + entry.item() + ", which isn't an item");
        }
    }

    /** How a price is paid: from cash first, then the bank. Null if they can't afford it. */
    public record Payment(long fromCash, long fromBank) {

        public static Payment of(long cash, long bank, long price) {
            if (price > cash + bank) {
                return null;
            }
            long fromCash = Math.min(cash, price);
            return new Payment(fromCash, price - fromCash);
        }
    }
}
