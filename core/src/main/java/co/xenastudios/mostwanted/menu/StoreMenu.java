package co.xenastudios.mostwanted.menu;

import co.xenastudios.mostwanted.cosmetic.Cosmetic;
import co.xenastudios.mostwanted.cosmetic.Cosmetics;
import co.xenastudios.mostwanted.cosmetic.Rank;
import co.xenastudios.mostwanted.message.Messages;
import co.xenastudios.mostwanted.store.StoreCatalog;
import co.xenastudios.mostwanted.store.StoreCatalog.Item;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * {@code /store}: the website's store, in game. Ranks on top, cosmetics under them,
 * then support. Nothing is paid for here: a click gives a link that opens the website
 * with it in the cart (a short one to type, for Bedrock, where links can't be clicked).
 */
public final class StoreMenu {

    private static final int[] RANK_SLOTS = {10, 12, 14, 16};
    private static final int[] COSMETIC_SLOTS = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
    private static final int[] SUPPORT_SLOTS = {39, 41};
    static final Map<String, Material> ICONS = Map.of(
            "vip", Material.IRON_INGOT,
            "vip-plus", Material.GOLD_INGOT,
            "mvp", Material.DIAMOND,
            "legend", Material.NETHERITE_INGOT,
            "server-boost", Material.FIREWORK_ROCKET,
            "tip-jar", Material.DECORATED_POT);
    private static final int WIDTH = 30;

    private final Menus menus;
    private final Messages messages;
    private final StoreCatalog catalog;
    private final Cosmetics cosmetics;
    private final Consumer<Player> home;

    public StoreMenu(Menus menus, StoreCatalog catalog, Cosmetics cosmetics, Consumer<Player> home) {
        this.menus = menus;
        this.messages = menus.messages();
        this.catalog = catalog;
        this.cosmetics = cosmetics;
        this.home = home;
    }

    public LiteralCommandNode<CommandSourceStack> command() {
        return Commands.literal("store")
                .requires(source -> source.getExecutor() instanceof Player)
                .executes(context -> {
                    open((Player) context.getSource().getExecutor());
                    return Command.SINGLE_SUCCESS;
                })
                .build();
    }

    public void open(Player player) {
        Menu menu = menus.create(6, "store-menu.title");
        List<Item> items = catalog.items();
        if (items.isEmpty()) {
            menu.set(22, menus.item(Material.CLOCK, "store-menu.loading"));
        }
        place(menu, player, items, "ranks", RANK_SLOTS);
        place(menu, player, items, "cosmetics", COSMETIC_SLOTS);
        place(menu, player, items, "support", SUPPORT_SLOTS);
        menus.back(menu, home);
        menus.close(menu);
        menu.set(menu.bottom() + 8, menus.item(Material.EMERALD, messages.get("store-menu.website.name"),
                Menus.sections(menus.lore("store-menu.website"), List.of(menus.click("link")))),
                click -> link(click.player(), messages.get("store-menu.website.name"), catalog.storeLink()));
        menus.open(player, menu);
    }

    private void place(Menu menu, Player player, List<Item> items, String category, int[] slots) {
        int next = 0;
        for (Item item : items) {
            if (item.category().equals(category) && next < slots.length) {
                menu.set(slots[next++], display(player, item), click -> link(click.player(), name(item),
                        catalog.link(item)));
            }
        }
    }

    private ItemStack display(Player player, Item item) {
        boolean owned = owned(player, item);
        List<Component> about = new ArrayList<>();
        for (String line : Menus.wrap(item.blurb(), WIDTH)) {
            about.add(messages.get("store-menu.blurb", Messages.value("line", line)));
        }
        List<Component> perks = new ArrayList<>();
        for (String perk : item.perks()) {
            perks.add(messages.get("store-menu.perk", Messages.value("perk", perk)));
        }
        List<Component> buy = owned ? List.of(messages.get("store-menu.owned"))
                : List.of(messages.get("store-menu.price", Messages.value("price", item.priceText())), menus.click("link"));
        ItemStack stack = menus.item(icon(item), name(item), Menus.sections(about, perks, buy));
        stack.editMeta(meta -> meta.setEnchantmentGlintOverride(owned ? Boolean.TRUE : null));
        return stack;
    }

    private Component name(Item item) {
        Rank rank = rank(item.id());
        return rank != null ? messages.get("rank." + rank.key())
                : messages.get("store-menu.name", Messages.value("name", item.name()));
    }

    private static Material icon(Item item) {
        Cosmetic cosmetic = Cosmetic.byId(item.id());
        return cosmetic != null ? cosmetic.icon() : ICONS.getOrDefault(item.id(), Material.PAPER);
    }

    private boolean owned(Player player, Item item) {
        Rank rank = rank(item.id());
        if (rank != null) {
            Rank has = cosmetics.rank(player);
            return has != null && has.ordinal() >= rank.ordinal();
        }
        Cosmetic cosmetic = Cosmetic.byId(item.id());
        return cosmetic != null && cosmetics.owns(player, cosmetic);
    }

    private static Rank rank(String id) {
        for (Rank rank : Rank.values()) {
            if (rank.key().equals(id)) {
                return rank;
            }
        }
        return null;
    }

    /** The link in chat: clickable on Java, and short enough to type on Bedrock. */
    private void link(Player player, Component name, String url) {
        player.closeInventory();
        String shown = url.replaceFirst("^https?://", "");
        player.sendMessage(messages.get("store-menu.link", Messages.component("name", name), Messages.value("url", shown))
                .clickEvent(ClickEvent.openUrl(url)));
    }
}
