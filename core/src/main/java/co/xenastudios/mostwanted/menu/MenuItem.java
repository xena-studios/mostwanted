package co.xenastudios.mostwanted.menu;

import co.xenastudios.mostwanted.item.CustomItems;
import co.xenastudios.mostwanted.message.Messages;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/**
 * The menu item: always in the last hotbar slot, opening the main menu on a right-click
 * (or a click in the inventory, as Bedrock players tap it). It can't be dropped, moved,
 * stored or lost on death, and comes back on joining and respawning.
 */
public final class MenuItem implements Listener {

    static final int SLOT = 8;
    private static final String ID = "menu";

    private final Plugin plugin;
    private final CustomItems items;
    private final MainMenu main;
    private final ItemStack item;

    public MenuItem(Plugin plugin, CustomItems items, Messages messages, MainMenu main) {
        this.plugin = plugin;
        this.items = items;
        this.main = main;
        this.item = ItemStack.of(Material.NETHER_STAR);
        item.editMeta(meta -> {
            items.mark(meta, ID);
            meta.itemName(messages.get("menu.item.name"));
            meta.lore(messages.lines("menu.item.lore"));
        });
    }

    boolean is(ItemStack stack) {
        return items.is(stack, ID);
    }

    /** Puts it in the last hotbar slot, moving whatever was there. */
    public void give(Player player) {
        var inventory = player.getInventory();
        if (is(inventory.getItem(SLOT))) {
            return;
        }
        // Any stray copy goes: there's one, in its place.
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (is(inventory.getItem(slot))) {
                inventory.setItem(slot, null);
            }
        }
        ItemStack there = inventory.getItem(SLOT);
        inventory.setItem(SLOT, item.clone());
        if (there != null && !there.isEmpty()) {
            Map<Integer, ItemStack> left = inventory.addItem(there);
            left.values().forEach(stack -> player.getWorld().dropItemNaturally(player.getLocation(), stack));
        }
    }

    private void openSoon(Player player) {
        // Not from inside an inventory event: a tick later, as Bukkit asks.
        Bukkit.getScheduler().runTask(plugin, () -> main.open(player));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void join(PlayerJoinEvent event) {
        give(event.getPlayer());
    }

    @EventHandler
    public void respawn(PlayerRespawnEvent event) {
        give(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOW)
    public void use(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !is(event.getItem())) {
            return;
        }
        event.setCancelled(true);
        if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            main.open(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void useOn(PlayerInteractEntityEvent event) {
        if (event.getHand() == EquipmentSlot.HAND && is(event.getPlayer().getInventory().getItemInMainHand())) {
            event.setCancelled(true);
            main.open(event.getPlayer());
        }
    }

    @EventHandler
    public void drop(PlayerDropItemEvent event) {
        if (is(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void swap(PlayerSwapHandItemsEvent event) {
        if (is(event.getMainHandItem()) || is(event.getOffHandItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void click(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        boolean swapped = event.getAction() == InventoryAction.HOTBAR_SWAP && event.getHotbarButton() >= 0
                && is(player.getInventory().getItem(event.getHotbarButton()));
        if (is(event.getCurrentItem()) || is(event.getCursor()) || swapped) {
            event.setCancelled(true);
            if (is(event.getCurrentItem()) && !(event.getView().getTopInventory().getHolder(false) instanceof Menu)) {
                player.closeInventory();
                openSoon(player);
            }
        }
    }

    @EventHandler
    public void drag(InventoryDragEvent event) {
        if (is(event.getOldCursor())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void death(PlayerDeathEvent event) {
        event.getDrops().removeIf(this::is);
    }
}
