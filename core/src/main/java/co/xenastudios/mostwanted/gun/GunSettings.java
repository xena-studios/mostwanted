package co.xenastudios.mostwanted.gun;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * guns.yml: every gun and ammo type. Hitscan guns, so a shot lands the same way on
 * Java and Bedrock, touch controls included (docs/02-gameplay.md#guns).
 *
 * @param headshot damage multiplier for a hit on the head
 * @param confiscate whether an arrest takes the criminal's guns and ammo
 * @param packModels give items their resource-pack model (on once the pack has them)
 */
public record GunSettings(Map<String, Gun> guns, Map<String, Ammo> ammo, double headshot, boolean confiscate,
        boolean packModels) {

    /**
     * One gun.
     *
     * @param ammo the ammo id it loads, or null for none (the taser recharges by itself)
     * @param damage health points per hit (a player has 20), per pellet for a shotgun
     * @param pellets rays per shot; more than one spreads by {@code spread}
     * @param fireTicks the wait between shots, in ticks (20 a second)
     * @param stun for a taser: how long a hit slows them; zero for a real gun
     * @param guardOnly given to guards on duty, never sold or dropped
     */
    public record Gun(String id, String name, Material item, String ammo, double damage, int pellets, double spread,
            double range, int fireTicks, int magazine, Duration reload, Duration stun, boolean guardOnly) {

        public boolean taser() {
            return !stun.isZero();
        }
    }

    public record Ammo(String id, String name, Material item) {}

    public static GunSettings read(YamlConfiguration file) {
        Map<String, Ammo> ammo = new LinkedHashMap<>();
        ConfigurationSection ammoSection = file.getConfigurationSection("ammo");
        if (ammoSection != null) {
            for (String id : ammoSection.getKeys(false)) {
                ConfigurationSection a = ammoSection.getConfigurationSection(id);
                ammo.put(id, new Ammo(id, a.getString("name", id), material(a.getString("item"), id)));
            }
        }
        Map<String, Gun> guns = new LinkedHashMap<>();
        ConfigurationSection gunSection = file.getConfigurationSection("guns");
        if (gunSection != null) {
            for (String id : gunSection.getKeys(false)) {
                ConfigurationSection g = gunSection.getConfigurationSection(id);
                String ammoId = g.getString("ammo", null);
                if (ammoId != null && !ammo.containsKey(ammoId)) {
                    throw new IllegalArgumentException("gun " + id + " uses ammo " + ammoId + ", which isn't in ammo");
                }
                Gun gun = new Gun(id, g.getString("name", id), material(g.getString("item"), id), ammoId,
                        g.getDouble("damage", 4), g.getInt("pellets", 1), g.getDouble("spread", 0),
                        g.getDouble("range", 40), g.getInt("fire-ticks", 10), g.getInt("magazine", 1),
                        Duration.ofMillis((long) (g.getDouble("reload-seconds", 2) * 1000)),
                        Duration.ofMillis((long) (g.getDouble("stun-seconds", 0) * 1000)),
                        g.getBoolean("guard-only", false));
                if (gun.damage < 0 || gun.pellets < 1 || gun.range <= 0 || gun.fireTicks < 1 || gun.magazine < 1) {
                    throw new IllegalArgumentException("gun " + id + " has a value out of range");
                }
                guns.put(id, gun);
            }
        }
        return new GunSettings(Map.copyOf(guns), Map.copyOf(ammo), file.getDouble("headshot-multiplier", 1.5),
                file.getBoolean("confiscate-on-arrest", true), file.getBoolean("pack-models", false));
    }

    private static Material material(String name, String id) {
        Material material = name == null ? null : Material.matchMaterial(name.toLowerCase(Locale.ROOT));
        if (material == null) {
            throw new IllegalArgumentException(id + " needs an item, such as iron_hoe, not " + name);
        }
        return material;
    }

    /** How many rounds a reload puts in: enough to fill it, as far as the ammo goes. */
    static int reloadAmount(int loaded, int magazine, int available) {
        return Math.max(0, Math.min(magazine - loaded, available));
    }

    /** A hit at {@code y} counts as the head if it's within this much of the eyes. */
    static boolean headshot(double y, double eyeY) {
        return y >= eyeY - 0.3;
    }
}
