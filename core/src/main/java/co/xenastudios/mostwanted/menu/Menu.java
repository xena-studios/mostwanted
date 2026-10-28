package co.xenastudios.mostwanted.menu;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * A chest menu: what's in each slot, and what clicking it does. Made by
 * {@link Menus#create}; nothing in it can be taken or moved ({@link Menus} sees to that).
 */
public final class Menu implements InventoryHolder {

    private final Inventory inventory;
    private final ItemStack background;
    private final Map<Integer, Consumer<Click>> actions = new HashMap<>();

    Menu(int rows, Component title, ItemStack background) {
        this.inventory = Bukkit.createInventory(this, rows * 9, title);
        this.background = background;
        clear();
    }

    public int size() {
        return inventory.getSize();
    }

    /** The first slot of the bottom row, where the back button goes. */
    public int bottom() {
        return size() - 9;
    }

    /** Something to look at, that does nothing when clicked. */
    public void set(int slot, ItemStack item) {
        set(slot, item, null);
    }

    public void set(int slot, ItemStack item, Consumer<Click> action) {
        inventory.setItem(slot, item);
        if (action == null) {
            actions.remove(slot);
        } else {
            actions.put(slot, action);
        }
    }

    /** Every slot back to the background, for drawing again. */
    public void clear() {
        actions.clear();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, background);
        }
    }

    Consumer<Click> action(int slot) {
        return actions.get(slot);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
