package co.xenastudios.mostwanted.item;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import java.util.List;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
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

    /**
     * Marks the item as ours, and tags it for the resource pack: "gun:pistol" shows the
     * pack's "mostwanted:pistol" model, "ammo:pistol" its "mostwanted:ammo_pistol". The
     * pack falls back to the vanilla model for anything else (pack/build.py).
     */
    public void mark(ItemMeta meta, String id) {
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, id);
        CustomModelDataComponent model = meta.getCustomModelDataComponent();
        model.setStrings(List.of(model(id)));
        meta.setCustomModelDataComponent(model);
    }

    /** The pack's model name for an item id: "gun:pistol" is "mostwanted:pistol". */
    static String model(String id) {
        String name = id.startsWith("gun:") ? id.substring(4) : id.replace(':', '_');
        return "mostwanted:" + name;
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
