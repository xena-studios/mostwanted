package co.xenastudios.mostwanted.item;

import co.xenastudios.mostwanted.message.Messages;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * Guards' handcuffs: a tripwire hook marked as ours, so a renamed hook can't pass for
 * them. Given on duty, taken off duty, never dropped. The resource pack can model it later.
 */
public final class Handcuffs {

    private final NamespacedKey key;
    private final Messages messages;

    public Handcuffs(Plugin plugin, Messages messages) {
        this.key = new NamespacedKey(plugin, "item");
        this.messages = messages;
    }

    public ItemStack create() {
        ItemStack item = ItemStack.of(Material.TRIPWIRE_HOOK);
        item.editMeta(meta -> {
            meta.itemName(messages.get("item.handcuffs.name"));
            meta.lore(java.util.List.of(messages.get("item.handcuffs.lore")));
            meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, "handcuffs");
        });
        return item;
    }

    public boolean is(ItemStack item) {
        return item != null && item.getType() == Material.TRIPWIRE_HOOK && item.hasItemMeta()
                && "handcuffs".equals(item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING));
    }

    /** Gives a pair unless they already have one. */
    public void give(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (is(item)) {
                return;
            }
        }
        player.getInventory().addItem(create());
    }

    public void take(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            if (is(contents[i])) {
                player.getInventory().setItem(i, null);
            }
        }
        if (is(player.getItemOnCursor())) {
            player.setItemOnCursor(null);
        }
    }
}
