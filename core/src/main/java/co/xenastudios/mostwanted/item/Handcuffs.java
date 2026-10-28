package co.xenastudios.mostwanted.item;

import co.xenastudios.mostwanted.message.Messages;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Guards' handcuffs: a tripwire hook marked as ours, so a renamed hook can't pass for
 * them. Given on duty, taken off duty, never dropped. The resource pack can model it later.
 */
public final class Handcuffs {

    private final CustomItems items;
    private final Messages messages;

    public Handcuffs(CustomItems items, Messages messages) {
        this.items = items;
        this.messages = messages;
    }

    public ItemStack create() {
        ItemStack item = ItemStack.of(Material.TRIPWIRE_HOOK);
        item.editMeta(meta -> {
            meta.itemName(messages.get("item.handcuffs.name"));
            meta.lore(java.util.List.of(messages.get("item.handcuffs.lore")));
            items.mark(meta, "handcuffs");
        });
        return item;
    }

    public boolean is(ItemStack item) {
        return item != null && item.getType() == Material.TRIPWIRE_HOOK && items.is(item, "handcuffs");
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
