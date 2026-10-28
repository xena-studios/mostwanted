package co.xenastudios.mostwanted.shop;

import co.xenastudios.mostwanted.common.style.Money;
import co.xenastudios.mostwanted.gun.Guns;
import co.xenastudios.mostwanted.menu.Menu;
import co.xenastudios.mostwanted.menu.Menus;
import co.xenastudios.mostwanted.message.Messages;
import co.xenastudios.mostwanted.player.PlayerState;
import co.xenastudios.mostwanted.player.PlayerStore;
import co.xenastudios.mostwanted.storage.GameLog;
import co.xenastudios.mostwanted.zone.ZoneTracker;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * The hideout shop, the armory, and any other in shops.yml: a chest menu, so it's the
 * same on Java and Bedrock (no Floodgate forms: Minehut's Bedrock players don't have
 * Floodgate on our side). {@code /shop} opens the one for where you stand.
 */
public final class Shops {

    private static final Sound BOUGHT = Sound.sound(Key.key("minecraft:entity.experience_orb.pickup"), Sound.Source.PLAYER, 1f, 1.2f);
    private static final Sound REFUSED = Sound.sound(Key.key("minecraft:block.note_block.bass"), Sound.Source.PLAYER, 1f, 0.7f);

    private final ShopSettings settings;
    private final Guns guns;
    private final PlayerStore players;
    private final ZoneTracker tracker;
    private final Messages messages;
    private final GameLog gameLog;
    private final Clock clock;
    private final Menus menus;
    private Consumer<Player> home = Player::closeInventory;

    public Shops(ShopSettings settings, Guns guns, PlayerStore players, ZoneTracker tracker, Menus menus,
            GameLog gameLog, Clock clock) {
        this.settings = settings;
        this.guns = guns;
        this.players = players;
        this.tracker = tracker;
        this.messages = menus.messages();
        this.menus = menus;
        this.gameLog = gameLog;
        this.clock = clock;
    }

    /** Where Back goes: the main menu. */
    public void home(Consumer<Player> home) {
        this.home = home;
    }

    public LiteralCommandNode<CommandSourceStack> command() {
        return Commands.literal("shop")
                .requires(source -> source.getExecutor() instanceof Player)
                .executes(context -> {
                    open((Player) context.getSource().getExecutor());
                    return Command.SINGLE_SUCCESS;
                })
                .build();
    }

    /** The shop for where they stand and who they are, if any. */
    public ShopSettings.Shop shopFor(Player player) {
        PlayerState state = players.get(player.getUniqueId()).orElse(null);
        return state == null ? null : shopFor(player, state);
    }

    ShopSettings.Shop shopFor(Player player, PlayerState state) {
        if (state.jailed(clock.instant())) {
            return null;
        }
        for (ShopSettings.Shop shop : settings.shops().values()) {
            if (shop.role() == state.role() && tracker.isIn(player, shop.zone())) {
                return shop;
            }
        }
        return null;
    }

    public void open(Player player) {
        PlayerState state = players.get(player.getUniqueId()).orElse(null);
        ShopSettings.Shop shop = state == null ? null : shopFor(player, state);
        if (shop == null) {
            messages.send(player, "shop.none");
            return;
        }
        int rows = Math.min(5, (shop.items().size() + 8) / 9) + 1;
        Menu menu = menus.create(rows, "shop.title", Messages.value("name", shop.title().toUpperCase(java.util.Locale.ROOT)));
        for (int i = 0; i < shop.items().size(); i++) {
            ShopSettings.Entry entry = shop.items().get(i);
            menu.set(i, display(entry), click -> buy(click.player(), click.menu(), shop, entry));
        }
        menus.back(menu, home);
        menus.close(menu);
        drawBalance(menu, state);
        menus.open(player, menu);
    }

    /** What's for sale, with its price underneath. */
    private ItemStack display(ShopSettings.Entry entry) {
        ItemStack item = goods(entry);
        item.editMeta(meta -> meta.lore(Menus.sections(meta.hasLore() ? meta.lore() : List.of(),
                List.of(messages.get("shop.price", Messages.value("price", Money.format(entry.price())))),
                List.of(menus.click("buy")))));
        return item;
    }

    /** The real thing, as it goes into their inventory. */
    private ItemStack goods(ShopSettings.Entry entry) {
        if (entry.gun()) {
            return guns.gun(guns.settings().guns().get(entry.id()));
        }
        if (entry.ammo()) {
            return guns.ammo(guns.settings().ammo().get(entry.id()), entry.amount());
        }
        return ItemStack.of(Material.matchMaterial(entry.item()), entry.amount());
    }

    private void drawBalance(Menu menu, PlayerState state) {
        menu.set(menu.size() - 1, menus.item(Material.GOLD_INGOT, messages.get("shop.balance"), List.of(
                messages.get("hud.cash", Messages.value("amount", Money.format(state.cash()))),
                messages.get("hud.bank", Messages.value("amount", Money.format(state.bank()))))));
    }

    private void buy(Player player, Menu menu, ShopSettings.Shop shop, ShopSettings.Entry entry) {
        PlayerState state = players.get(player.getUniqueId()).orElse(null);
        // Checked again: they may have walked out, or been arrested, with the menu open.
        if (state == null || shopFor(player, state) != shop) {
            player.closeInventory();
            messages.send(player, "shop.none");
            return;
        }
        ShopSettings.Payment payment = ShopSettings.Payment.of(state.cash(), state.bank(), entry.price());
        if (payment == null) {
            refuse(player, "shop.cant-afford", Messages.value("price", Money.format(entry.price())));
            return;
        }
        ItemStack goods = goods(entry);
        int left = player.getInventory().addItem(goods.clone()).values().stream().mapToInt(ItemStack::getAmount).sum();
        if (left > 0) {
            // Didn't all fit: take back exactly what went in (not what they already had), and keep their money.
            if (left < goods.getAmount()) {
                ItemStack added = goods.clone();
                added.setAmount(goods.getAmount() - left);
                player.getInventory().removeItem(added);
            }
            refuse(player, "shop.full");
            return;
        }
        state.cash(state.cash() - payment.fromCash());
        state.bank(state.bank() - payment.fromBank());
        gameLog.record("shop.buy", player.getUniqueId(), null, Map.of("shop", shop.id(), "item", entry.item(),
                "amount", entry.amount(), "price", entry.price()));
        player.playSound(BOUGHT, Sound.Emitter.self());
        messages.send(player, "shop.bought", Messages.value("item", plain(goods)),
                Messages.value("amount", entry.amount()), Messages.value("price", Money.format(entry.price())));
        drawBalance(menu, state);
    }

    private void refuse(Player player, String key, TagResolver... values) {
        player.playSound(REFUSED, Sound.Emitter.self());
        messages.send(player, key, values);
    }

    private static String plain(ItemStack item) {
        return PlainTextComponentSerializer.plainText().serialize(item.effectiveName());
    }
}
