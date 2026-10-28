package co.xenastudios.mostwanted.menu;

import co.xenastudios.mostwanted.common.Stat;
import co.xenastudios.mostwanted.common.style.Money;
import co.xenastudios.mostwanted.hud.Hud;
import co.xenastudios.mostwanted.message.Messages;
import co.xenastudios.mostwanted.player.PlayerState;
import co.xenastudios.mostwanted.player.PlayerStore;
import co.xenastudios.mostwanted.player.Profiles;
import co.xenastudios.mostwanted.player.Profiles.Period;
import co.xenastudios.mostwanted.player.Profiles.Profile;
import co.xenastudios.mostwanted.player.Role;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * {@code /profile [name]}: a player's head, role and bounty, and every stat all time,
 * this month and this week. Anyone public can be looked at, online or not; your own
 * also shows your money (only to you) and a way to your settings.
 */
public final class ProfileMenu {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);
    /** Each stat's icon, in the order they're shown. */
    private static final Map<Stat, Material> ICONS = new EnumMap<>(Map.of(
            Stat.ESCAPES, Material.LEATHER_BOOTS,
            Stat.ROBBERIES, Material.CHEST,
            Stat.CASH_STOLEN, Material.GOLD_NUGGET,
            Stat.RIOTS, Material.BELL,
            Stat.ARRESTS, Material.TRIPWIRE_HOOK,
            Stat.BOUNTY_EARNED, Material.GOLD_INGOT,
            Stat.GUARDS_KILLED, Material.IRON_SWORD,
            Stat.TIMES_ARRESTED, Material.IRON_BARS));
    private static final List<Stat> ORDER = List.of(Stat.ESCAPES, Stat.ROBBERIES, Stat.CASH_STOLEN, Stat.RIOTS,
            Stat.ARRESTS, Stat.BOUNTY_EARNED, Stat.GUARDS_KILLED, Stat.TIMES_ARRESTED);
    private static final int[] STAT_SLOTS = {19, 21, 23, 25, 28, 30, 32, 34};

    private final Menus menus;
    private final Messages messages;
    private final Profiles profiles;
    private final PlayerStore players;
    private final ZoneId zone;
    private final Consumer<Player> home;
    private final Consumer<Player> settings;

    public ProfileMenu(Menus menus, Profiles profiles, PlayerStore players, ZoneId zone, Consumer<Player> home,
            Consumer<Player> settings) {
        this.menus = menus;
        this.messages = menus.messages();
        this.profiles = profiles;
        this.players = players;
        this.zone = zone;
        this.home = home;
        this.settings = settings;
    }

    public LiteralCommandNode<CommandSourceStack> command() {
        return Commands.literal("profile")
                .requires(source -> source.getExecutor() instanceof Player)
                .executes(context -> {
                    Player player = (Player) context.getSource().getExecutor();
                    open(player, player.getUniqueId(), home);
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            Bukkit.getOnlinePlayers().forEach(p -> builder.suggest(p.getName()));
                            return builder.buildFuture();
                        })
                        .executes(context -> {
                            Player player = (Player) context.getSource().getExecutor();
                            String name = StringArgumentType.getString(context, "player");
                            profiles.byName(name, profile -> show(player, profile, name, home), () -> failed(player));
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
    }

    /** Opens a player's profile once it's read; {@code back} is where Back goes. */
    public void open(Player viewer, UUID who, Consumer<Player> back) {
        profiles.byUuid(who, profile -> show(viewer, profile, "?", back), () -> failed(viewer));
    }

    private void failed(Player viewer) {
        if (viewer.isOnline()) {
            messages.send(viewer, "profile.failed");
        }
    }

    private void show(Player viewer, Profile profile, String asked, Consumer<Player> back) {
        if (!viewer.isOnline()) {
            return;
        }
        if (profile == null) {
            messages.send(viewer, "profile.not-found", Messages.value("player", asked));
            return;
        }
        boolean self = profile.uuid().equals(viewer.getUniqueId());
        if (profile.statsPrivate() && !self && !viewer.hasPermission("mostwanted.admin")) {
            messages.send(viewer, "profile.private", Messages.value("player", profile.name()));
            return;
        }
        Menu menu = menus.create(6, "profile.title", Messages.value("player", profile.name()));
        menu.set(4, head(profile));
        long bounty = profile.bounty();
        int stars = Hud.stars(bounty);
        menu.set(self ? 11 : 13, menus.item(Material.GOLDEN_HELMET, "profile.bounty",
                Messages.value("amount", Money.format(bounty)), Messages.value("stars", "★".repeat(stars)),
                Messages.value("empty", "★".repeat(Hud.MAX_STARS - stars))));
        if (self) {
            PlayerState state = players.get(viewer.getUniqueId()).orElse(null);
            if (state != null) {
                menu.set(15, menus.item(Material.EMERALD, "profile.money",
                        Messages.value("cash", Money.format(state.cash())),
                        Messages.value("bank", Money.format(state.bank()))));
            }
        }
        for (int i = 0; i < ORDER.size(); i++) {
            menu.set(STAT_SLOTS[i], stat(profile, ORDER.get(i)));
        }
        menus.back(menu, back);
        menus.close(menu);
        if (self) {
            menu.set(menu.bottom() + 8, Menus.icon(menus.item(Material.COMPARATOR, messages.get("menu.settings.name"),
                    Menus.sections(menus.lore("menu.settings"), List.of(menus.click("open")))), "settings"),
                    click -> settings.accept(click.player()));
        }
        menus.open(viewer, menu);
    }

    private ItemStack head(Profile profile) {
        boolean online = Bukkit.getPlayer(profile.uuid()) != null;
        Component name = Component.text(profile.name(), TextColor.color(profile.role().colour()));
        List<Component> lore = new ArrayList<>(messages.lines("profile.head.lore",
                Messages.component("role", role(profile.role())),
                Messages.value("joined", DAY.format(profile.firstJoined().atZone(zone))),
                Messages.value("seen", online ? "now" : ago(profile.lastSeen()))));
        if (online) {
            lore.add(messages.get("profile.online"));
        }
        return menus.head(profile.uuid(), profile.name(), name, lore);
    }

    private ItemStack stat(Profile profile, Stat stat) {
        boolean money = stat == Stat.BOUNTY_EARNED || stat == Stat.CASH_STOLEN;
        return menus.item(ICONS.get(stat), messages.get("profile.stat." + stat.key()),
                messages.lines(money ? "profile.stat.lore-money" : "profile.stat.lore",
                        Messages.value("all", format(profile.stat(Period.ALL, stat), money)),
                        Messages.value("month", format(profile.stat(Period.MONTH, stat), money)),
                        Messages.value("week", format(profile.stat(Period.WEEK, stat), money))));
    }

    private static String format(long value, boolean money) {
        return money ? Money.format(value) : String.format(Locale.US, "%,d", value);
    }

    private static Component role(Role role) {
        return Component.text(role.displayName(), TextColor.color(role.colour()));
    }

    /** "5 minutes ago", "3 days ago". */
    static String ago(Instant then) {
        Duration since = Duration.between(then, Instant.now());
        if (since.toMinutes() < 1) {
            return "just now";
        }
        if (since.toHours() < 1) {
            return plural(since.toMinutes(), "minute");
        }
        if (since.toDays() < 1) {
            return plural(since.toHours(), "hour");
        }
        return plural(since.toDays(), "day");
    }

    private static String plural(long n, String unit) {
        return n + " " + unit + (n == 1 ? "" : "s") + " ago";
    }
}
