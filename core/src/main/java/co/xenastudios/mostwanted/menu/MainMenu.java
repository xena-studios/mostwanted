package co.xenastudios.mostwanted.menu;

import co.xenastudios.mostwanted.common.style.Money;
import co.xenastudios.mostwanted.cosmetic.CosmeticMenu;
import co.xenastudios.mostwanted.message.Messages;
import co.xenastudios.mostwanted.player.PlayerState;
import co.xenastudios.mostwanted.player.PlayerStore;
import co.xenastudios.mostwanted.player.WebsiteCommand;
import co.xenastudios.mostwanted.shop.ShopSettings;
import co.xenastudios.mostwanted.shop.Shops;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * {@code /menu}, and the menu item: the way into everything, so nobody needs to know a
 * command (Bedrock players on a phone least of all). Your profile, settings, the shop,
 * cosmetics, the store, leaderboards, your account and the wiki.
 */
public final class MainMenu {

    private final Menus menus;
    private final Messages messages;
    private final PlayerStore players;
    private ProfileMenu profile;
    private SettingsMenu settings;
    private Shops shops;
    private CosmeticMenu cosmetics;
    private StoreMenu store;
    private LeaderboardMenu leaderboards;
    private WebsiteCommand website;

    public MainMenu(Menus menus, PlayerStore players) {
        this.menus = menus;
        this.messages = menus.messages();
        this.players = players;
    }

    /** The menus it opens, which need this one for their Back button: so set after. */
    public void links(ProfileMenu profile, SettingsMenu settings, Shops shops, CosmeticMenu cosmetics, StoreMenu store,
            LeaderboardMenu leaderboards, WebsiteCommand website) {
        this.profile = profile;
        this.settings = settings;
        this.shops = shops;
        this.cosmetics = cosmetics;
        this.store = store;
        this.leaderboards = leaderboards;
        this.website = website;
    }

    public LiteralCommandNode<CommandSourceStack> command() {
        return Commands.literal("menu")
                .requires(source -> source.getExecutor() instanceof Player)
                .executes(context -> {
                    open((Player) context.getSource().getExecutor());
                    return Command.SINGLE_SUCCESS;
                })
                .build();
    }

    public void open(Player player) {
        PlayerState state = players.get(player.getUniqueId()).orElse(null);
        if (state == null) {
            return;
        }
        Menu menu = menus.create(4, "menu.title");
        Component role = Component.text(state.role().displayName(), TextColor.color(state.role().colour()));
        menu.set(10, menus.head(player.getUniqueId(), player.getName(), messages.get("menu.profile.name"),
                Menus.sections(messages.lines("menu.profile.lore", Messages.component("role", role),
                        Messages.value("cash", Money.format(state.cash())), Messages.value("bank", Money.format(state.bank())),
                        Messages.value("bounty", Money.format(state.bounty()))), List.of(menus.click("stats")))),
                click -> profile.open(click.player(), click.player().getUniqueId(), this::open));
        ShopSettings.Shop shop = shops.shopFor(player);
        menu.set(12, menus.item(Material.CHEST, messages.get("menu.shop.name"), Menus.sections(menus.lore("menu.shop"),
                shop == null ? messages.lines("menu.shop.none")
                        : List.of(messages.get("menu.shop.here", Messages.value("shop", shop.title())), menus.click("open")))),
                shop == null ? null : click -> shops.open(click.player()));
        button(menu, 14, Material.FIREWORK_STAR, "menu.cosmetics", "open", click -> cosmetics.open(click.player()));
        button(menu, 16, Material.EMERALD, "menu.store", "open", click -> store.open(click.player()));
        button(menu, 19, Material.GOLDEN_HELMET, "menu.leaderboards", "open", click -> leaderboards.open(click.player()));
        button(menu, 21, Material.COMPARATOR, "menu.settings", "open", click -> settings.open(click.player()));
        button(menu, 23, Material.NAME_TAG, "menu.account", "code", click -> {
            click.player().closeInventory();
            website.code(click.player());
        });
        button(menu, 25, Material.BOOK, "menu.help", "link", click -> {
            click.player().closeInventory();
            messages.send(click.player(), "menu.help-link");
        });
        menus.close(menu);
        menus.open(player, menu);
    }

    private void button(Menu menu, int slot, Material icon, String key, String click, java.util.function.Consumer<Click> action) {
        menu.set(slot, menus.item(icon, messages.get(key + ".name"),
                Menus.sections(menus.lore(key), List.of(menus.click(click)))), action);
    }
}
