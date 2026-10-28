package co.xenastudios.mostwanted.menu;

import co.xenastudios.mostwanted.message.Messages;
import com.destroystokyo.paper.profile.PlayerProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

/**
 * Every menu's look and handling (docs/09-brand.md#menus): a dark title, a black
 * background, items named in white with grey small-caps lore, what a click does on
 * the last lore line, and the bottom row for getting around: back on the left, close
 * in the middle. Chest menus only, so they're the same on Bedrock.
 */
public final class Menus implements Listener {

    private static final Sound CLICK = Sound.sound(Key.key("minecraft:ui.button.click"), Sound.Source.MASTER, 0.4f, 1f);

    private final Messages messages;
    private final ItemStack background;

    public Menus(Messages messages) {
        this.messages = messages;
        this.background = ItemStack.of(Material.BLACK_STAINED_GLASS_PANE);
        background.editMeta(meta -> meta.setHideTooltip(true));
    }

    public Messages messages() {
        return messages;
    }

    /** A menu with its title from messages.yml. */
    public Menu create(int rows, String titleKey, TagResolver... placeholders) {
        return new Menu(rows, messages.get(titleKey, placeholders), background);
    }

    public void open(Player player, Menu menu) {
        player.openInventory(menu.getInventory());
    }

    // ---------------------------------------------------------------- items

    /** An item named {@code name}, with {@code lore} under it. */
    public ItemStack item(Material material, Component name, List<Component> lore) {
        ItemStack item = ItemStack.of(material);
        item.editMeta(meta -> {
            meta.itemName(name);
            meta.lore(lore);
        });
        return item;
    }

    /** An item from messages.yml: {@code key.name}, and {@code key.lore} if there is one. */
    public ItemStack item(Material material, String key, TagResolver... placeholders) {
        return item(material, messages.get(key + ".name", placeholders), lore(key, placeholders));
    }

    /** {@code key.lore} as lines, or none. */
    public List<Component> lore(String key, TagResolver... placeholders) {
        return messages.has(key + ".lore") ? messages.lines(key + ".lore", placeholders) : List.of();
    }

    /**
     * A player's head, named and described as given. With their skin only for a real
     * Java account (a version 4 UUID): someone online brings theirs, anyone else's the
     * server looks up from Mojang once and keeps. Bedrock players and offline-mode
     * test players have no skin there to find, so they get the plain head, no lookup.
     */
    public ItemStack head(UUID uuid, String name, Component display, List<Component> lore) {
        ItemStack item = ItemStack.of(Material.PLAYER_HEAD);
        item.editMeta(SkullMeta.class, meta -> {
            if (uuid.version() == 4) {
                Player online = Bukkit.getPlayer(uuid);
                PlayerProfile profile = online != null ? online.getPlayerProfile() : Bukkit.createProfile(uuid, name);
                meta.setPlayerProfile(profile);
            }
            meta.itemName(display);
            meta.lore(lore);
        });
        return item;
    }

    /** Lore made of parts, with a blank line between each part that has something in it. */
    @SafeVarargs
    public static List<Component> sections(List<Component>... parts) {
        List<Component> lore = new ArrayList<>();
        for (List<Component> part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (!lore.isEmpty()) {
                lore.add(Component.empty());
            }
            lore.addAll(part);
        }
        return lore;
    }

    /** Splits text into lines of at most {@code width} characters, between words: long lore. */
    public static List<String> wrap(String text, int width) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.strip().split("\\s+")) {
            if (!line.isEmpty() && line.length() + 1 + word.length() > width) {
                lines.add(line.toString());
                line.setLength(0);
            }
            if (!line.isEmpty()) {
                line.append(' ');
            }
            line.append(word);
        }
        if (!line.isEmpty()) {
            lines.add(line.toString());
        }
        return lines;
    }

    /** The "▸ click to …" line: {@code menu.click.<what>}. */
    public Component click(String what, TagResolver... placeholders) {
        return messages.get("menu.click." + what, placeholders);
    }

    // ---------------------------------------------------------------- the bottom row

    /** Back, bottom left: to the menu this one was opened from. */
    public void back(Menu menu, Consumer<Player> to) {
        menu.set(menu.bottom(), item(Material.ARROW, messages.get("menu.back.name"), List.of(click("back"))),
                click -> to.accept(click.player()));
    }

    /** Close, bottom middle. */
    public void close(Menu menu) {
        menu.set(menu.bottom() + 4, item(Material.BARRIER, messages.get("menu.close.name"), List.of()),
                click -> click.player().closeInventory());
    }

    // ---------------------------------------------------------------- clicks

    @EventHandler
    public void click(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof Menu menu)) {
            return;
        }
        // Nothing moves in or out of a menu, whatever the click.
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getClickedInventory() != menu.getInventory()) {
            return;
        }
        Consumer<Click> action = menu.action(event.getSlot());
        if (action != null) {
            player.playSound(CLICK, Sound.Emitter.self());
            action.accept(new Click(player, menu, event.isRightClick()));
        }
    }

    @EventHandler
    public void drag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof Menu) {
            event.setCancelled(true);
        }
    }
}
