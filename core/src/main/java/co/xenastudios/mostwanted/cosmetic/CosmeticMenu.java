package co.xenastudios.mostwanted.cosmetic;

import co.xenastudios.mostwanted.common.style.Palette;
import co.xenastudios.mostwanted.gun.GunSettings;
import co.xenastudios.mostwanted.gun.Guns;
import co.xenastudios.mostwanted.item.CustomItems;
import co.xenastudios.mostwanted.menu.Click;
import co.xenastudios.mostwanted.menu.Menu;
import co.xenastudios.mostwanted.menu.Menus;
import co.xenastudios.mostwanted.message.Messages;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * {@code /cosmetics}: one row per kind, click one you own to use it (or take it off).
 * Ones you don't own show where to get them. {@code /joinmessage} sets a Legend's join
 * message.
 */
public final class CosmeticMenu {

    private static final List<String> ROWS = List.of("trail", "escape", "arrest", "skin", "chat");
    private static final int MAX_JOIN_MESSAGE = 48;

    private final Cosmetics cosmetics;
    private final CustomItems items;
    private final Messages messages;
    private final Menus menus;
    private Guns guns;
    private Consumer<Player> home = Player::closeInventory;
    private Consumer<Player> store = Player::closeInventory;

    public CosmeticMenu(Cosmetics cosmetics, CustomItems items, Menus menus) {
        this.cosmetics = cosmetics;
        this.items = items;
        this.messages = menus.messages();
        this.menus = menus;
    }

    public void guns(Guns guns) {
        this.guns = guns;
    }

    /** Where Back goes (the main menu), and the store menu, for cosmetics they don't have. */
    public void links(Consumer<Player> home, Consumer<Player> store) {
        this.home = home;
        this.store = store;
    }

    // ---------------------------------------------------------------- commands

    public LiteralCommandNode<CommandSourceStack> command() {
        return Commands.literal("cosmetics")
                .requires(source -> source.getExecutor() instanceof Player)
                .executes(context -> {
                    open((Player) context.getSource().getExecutor());
                    return Command.SINGLE_SUCCESS;
                })
                .build();
    }

    public LiteralCommandNode<CommandSourceStack> joinMessageCommand() {
        return Commands.literal("joinmessage")
                .requires(source -> source.getExecutor() instanceof Player
                        && source.getExecutor().hasPermission("mostwanted.join-message"))
                .executes(context -> {
                    Player player = (Player) context.getSource().getExecutor();
                    String now = cosmetics.store().get(player.getUniqueId(), "join-message");
                    if (now == null) {
                        messages.send(player, "cosmetics.join-message.none");
                    } else {
                        messages.send(player, "cosmetics.join-message.is", Messages.value("message", now));
                    }
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.literal("clear").executes(context -> {
                    Player player = (Player) context.getSource().getExecutor();
                    save(player, "join-message", null);
                    messages.send(player, "cosmetics.join-message.cleared");
                    return Command.SINGLE_SUCCESS;
                }))
                .then(Commands.literal("set").then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(context -> {
                            Player player = (Player) context.getSource().getExecutor();
                            String message = joinMessage(StringArgumentType.getString(context, "message"));
                            if (message == null) {
                                messages.send(player, "cosmetics.join-message.invalid",
                                        Messages.value("max", MAX_JOIN_MESSAGE));
                                return 0;
                            }
                            save(player, "join-message", message);
                            messages.send(player, "cosmetics.join-message.set", Messages.value("message", message));
                            return Command.SINGLE_SUCCESS;
                        })))
                .build();
    }

    /**
     * A join message as it's stored: trimmed, one line, short, printable characters only.
     * It's always shown as plain text, so tags in it do nothing. Null if it can't be used.
     */
    static String joinMessage(String text) {
        String message = text.strip().replaceAll("\\s+", " ");
        if (message.isEmpty() || message.length() > MAX_JOIN_MESSAGE) {
            return null;
        }
        for (int i = 0; i < message.length(); i++) {
            if (Character.isISOControl(message.charAt(i))) {
                return null;
            }
        }
        return message;
    }

    // ---------------------------------------------------------------- the menu

    public void open(Player player) {
        Menu menu = menus.create(6, "cosmetics.title");
        draw(player, menu);
        menus.open(player, menu);
    }

    private void draw(Player player, Menu menu) {
        menu.clear();
        for (int row = 0; row < ROWS.size(); row++) {
            String kind = ROWS.get(row);
            menu.set(row * 9, label(kind));
            int column = 2;
            for (Cosmetic cosmetic : Cosmetic.values()) {
                if (cosmetic.slot().equals(kind) || cosmetic.slot().equals(kind + ":" + cosmetic.gun())) {
                    menu.set(row * 9 + column++, display(player, cosmetic), click -> click(click, cosmetic));
                }
            }
        }
        menus.back(menu, home);
        menus.close(menu);
        menu.set(menu.bottom() + 8, menus.item(Material.EMERALD, messages.get("menu.store.name"),
                Menus.sections(menus.lore("menu.store"), List.of(menus.click("open")))),
                click -> store.accept(click.player()));
    }

    private ItemStack label(String kind) {
        return menus.item(Material.GRAY_STAINED_GLASS_PANE, messages.get("cosmetics.kind." + kind), List.of());
    }

    private ItemStack display(Player player, Cosmetic cosmetic) {
        ItemStack item = icon(cosmetic);
        boolean owned = cosmetics.owns(player, cosmetic);
        String slot = cosmetic.slot();
        List<Component> lore = new ArrayList<>();
        boolean inUse;
        if (cosmetic == Cosmetic.CHAT_COLOURS) {
            String choice = cosmetics.store().get(player.getUniqueId(), slot);
            Integer hue = hue(choice);
            inUse = owned && hue != null;
            lore.add(hue == null
                    ? messages.get("cosmetics.chat-off")
                    : messages.get("cosmetics.chat-on").color(TextColor.color(Palette.hue(hue))));
            if (owned) {
                lore.add(Component.empty());
                lore.addAll(messages.lines("cosmetics.chat-click"));
            }
        } else {
            inUse = cosmetics.using(player, slot) == cosmetic;
            if (inUse) {
                lore.add(messages.get("cosmetics.using"));
            }
            if (owned) {
                lore.add(Component.empty());
                lore.add(menus.click(inUse ? "take-off" : "use"));
            }
        }
        if (!owned) {
            lore.add(messages.get("cosmetics.locked"));
            lore.add(Component.empty());
            lore.add(menus.click("link"));
        }
        item.editMeta(meta -> {
            meta.itemName(messages.get(owned ? "cosmetics.name" : "cosmetics.name-locked",
                    Messages.value("name", cosmetic.displayName())));
            meta.lore(lore);
            meta.setEnchantmentGlintOverride(inUse);
        });
        return item;
    }

    /** A gun skin shows as the skinned gun itself (with the pack on); the rest as their icon. */
    private ItemStack icon(Cosmetic cosmetic) {
        GunSettings.Gun gun = cosmetic.gun() == null || guns == null ? null
                : guns.settings().guns().get(cosmetic.gun());
        if (gun == null) {
            return ItemStack.of(cosmetic.icon());
        }
        ItemStack item = ItemStack.of(gun.item());
        item.editMeta(meta -> {
            items.mark(meta, "gun:" + gun.id());
            items.skin(meta, cosmetic.model());
            // A picture, not a gun: drop the id so it can never be used as one.
            new ArrayList<>(meta.getPersistentDataContainer().getKeys()).forEach(meta.getPersistentDataContainer()::remove);
        });
        return item;
    }

    private void click(Click click, Cosmetic cosmetic) {
        Player player = click.player();
        if (!cosmetics.owns(player, cosmetic)) {
            store.accept(player);
            return;
        }
        if (cosmetic == Cosmetic.CHAT_COLOURS) {
            Integer hue = hue(cosmetics.store().get(player.getUniqueId(), "chat"));
            Integer next = nextHue(hue, click.right());
            save(player, "chat", next == null ? null : "hue:" + next);
        } else if (cosmetics.using(player, cosmetic.slot()) == cosmetic) {
            save(player, cosmetic.slot(), null);
        } else {
            save(player, cosmetic.slot(), cosmetic.id());
        }
        draw(player, click.menu());
    }

    private void save(Player player, String slot, String choice) {
        // The cache changes now; the database write goes off the main thread (CosmeticStore).
        cosmetics.store().set(player.getUniqueId(), slot, choice);
    }

    // ---------------------------------------------------------------- chat colours

    static Integer hue(String choice) {
        if (choice == null || !choice.startsWith("hue:")) {
            return null;
        }
        try {
            int hue = Integer.parseInt(choice.substring(4));
            return Cosmetics.CHAT_HUES.contains(hue) ? hue : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Through every colour, then off, then round again; backwards on a right-click. */
    static Integer nextHue(Integer hue, boolean back) {
        List<Integer> hues = Cosmetics.CHAT_HUES;
        int at = hue == null ? -1 : hues.indexOf(hue);
        int next = back ? (at == -1 ? hues.size() - 1 : at - 1) : at + 1;
        return next < 0 || next >= hues.size() ? null : hues.get(next);
    }
}
