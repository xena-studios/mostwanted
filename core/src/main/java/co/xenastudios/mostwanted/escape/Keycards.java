package co.xenastudios.mostwanted.escape;

import co.xenastudios.mostwanted.config.EscapeSettings;
import co.xenastudios.mostwanted.item.CustomItems;
import co.xenastudios.mostwanted.item.GuardKit;
import co.xenastudios.mostwanted.message.Messages;
import co.xenastudios.mostwanted.player.PlayerStore;
import co.xenastudios.mostwanted.player.Role;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Keycards open gates. Guards carry one on duty and can't hand it over, but it may drop
 * when they die: that's how one gets into the prison. Arrests take them.
 */
public final class Keycards implements Listener, GuardKit {

    private final CustomItems items;
    private final PlayerStore players;
    private final Messages messages;
    private final EscapeSettings settings;

    public Keycards(CustomItems items, PlayerStore players, Messages messages, EscapeSettings settings) {
        this.items = items;
        this.players = players;
        this.messages = messages;
        this.settings = settings;
    }

    public ItemStack create() {
        ItemStack item = ItemStack.of(Material.TRIAL_KEY);
        item.editMeta(meta -> {
            meta.itemName(messages.get("item.keycard.name"));
            meta.lore(List.of(messages.get("item.keycard.lore")));
            items.mark(meta, "keycard");
        });
        return item;
    }

    public boolean is(ItemStack item) {
        return items.is(item, "keycard");
    }

    @Override
    public void give(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (is(item)) {
                return;
            }
        }
        player.getInventory().addItem(create());
    }

    @Override
    public void take(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            if (is(contents[i])) {
                player.getInventory().setItem(i, null);
            }
        }
    }

    /** Guards can't pass theirs to a friend; anyone else who has one can. */
    @EventHandler(ignoreCancelled = true)
    public void drop(PlayerDropItemEvent event) {
        if (is(event.getItemDrop().getItemStack()) && players.get(event.getPlayer().getUniqueId())
                .map(state -> state.role() == Role.GUARD).orElse(false)) {
            event.setCancelled(true);
        }
    }

    /** A guard's keycard drops by chance; no one else's does (arrests take them). Before Combat's handler. */
    @EventHandler(priority = EventPriority.NORMAL)
    public void death(PlayerDeathEvent event) {
        boolean guard = players.get(event.getPlayer().getUniqueId())
                .map(state -> state.role() == Role.GUARD).orElse(false);
        boolean drops = guard && ThreadLocalRandom.current().nextInt(100) < settings.keycardDropPercent();
        if (!drops) {
            event.getDrops().removeIf(this::is);
        } else {
            // One at most, even if they somehow had more.
            boolean kept = false;
            for (var it = event.getDrops().iterator(); it.hasNext(); ) {
                ItemStack item = it.next();
                if (is(item)) {
                    if (kept) {
                        it.remove();
                    } else {
                        item.setAmount(1);
                        kept = true;
                    }
                }
            }
        }
    }
}
