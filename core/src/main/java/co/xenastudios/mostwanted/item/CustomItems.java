package co.xenastudios.mostwanted.item;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * Marks our items, so a renamed vanilla item can't pass for one: each carries an id
 * such as "handcuffs", "gun:pistol" or "ammo:pistol" (docs/04-tech.md, custom items).
 * Each is a vanilla item underneath; the resource pack gives them models later.
 */
public final class CustomItems {

    private final NamespacedKey key;

    public CustomItems(Plugin plugin) {
        this.key = new NamespacedKey(plugin, "item");
    }

    public void mark(ItemMeta meta, String id) {
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, id);
    }

    /** The item's id, or null if it isn't one of ours. */
    public String id(ItemStack item) {
        if (item == null || item.isEmpty() || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }

    public boolean is(ItemStack item, String id) {
        return id.equals(id(item));
    }
}
