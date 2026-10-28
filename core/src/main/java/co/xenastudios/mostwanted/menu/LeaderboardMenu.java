package co.xenastudios.mostwanted.menu;

import co.xenastudios.mostwanted.board.Leaderboards;
import co.xenastudios.mostwanted.board.Leaderboards.Line;
import co.xenastudios.mostwanted.board.Leaderboards.Period;
import co.xenastudios.mostwanted.common.Board;
import co.xenastudios.mostwanted.common.Stat;
import co.xenastudios.mostwanted.common.style.Money;
import co.xenastudios.mostwanted.message.Messages;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * {@code /leaderboards}: every board, then a board's top 10 as heads (first on top,
 * second and third under it), all time, this month or this week. A head opens that
 * player's profile. The same boards, and the same players, as the website's.
 */
public final class LeaderboardMenu {

    private static final Map<Board, Material> ICONS = Map.of(
            Board.MOST_WANTED, Material.GOLDEN_HELMET,
            Board.ESCAPES, Material.LEATHER_BOOTS,
            Board.GUARDS, Material.TRIPWIRE_HOOK,
            Board.ROBBERIES, Material.CHEST,
            Board.BOUNTY_EARNED, Material.GOLD_INGOT,
            Board.MOST_ARRESTED, Material.IRON_BARS);
    private static final int[] BOARD_SLOTS = {11, 13, 15, 20, 22, 24};
    /** First, then second and third, then the rest. */
    private static final int[] PLACES = {13, 21, 23, 28, 29, 30, 31, 32, 33, 34};

    private final Menus menus;
    private final Messages messages;
    private final Leaderboards boards;
    private final ProfileMenu profiles;
    private final Consumer<Player> home;

    public LeaderboardMenu(Menus menus, Leaderboards boards, ProfileMenu profiles, Consumer<Player> home) {
        this.menus = menus;
        this.messages = menus.messages();
        this.boards = boards;
        this.profiles = profiles;
        this.home = home;
    }

    public LiteralCommandNode<CommandSourceStack> command() {
        return Commands.literal("leaderboards")
                .requires(source -> source.getExecutor() instanceof Player)
                .executes(context -> {
                    open((Player) context.getSource().getExecutor());
                    return Command.SINGLE_SUCCESS;
                })
                .build();
    }

    public void open(Player player) {
        Menu menu = menus.create(4, "leaderboards.title");
        Board[] all = Board.values();
        for (int i = 0; i < all.length && i < BOARD_SLOTS.length; i++) {
            Board board = all[i];
            menu.set(BOARD_SLOTS[i], menus.item(ICONS.getOrDefault(board, Material.PAPER),
                    messages.get("board.title." + board.id()),
                    Menus.sections(messages.lines("leaderboards.about." + board.id()), List.of(menus.click("open")))),
                    click -> board(click.player(), board, Period.WEEK));
        }
        menus.close(menu);
        menus.back(menu, home);
        menus.open(player, menu);
    }

    private void board(Player player, Board board, Period period) {
        boards.read(board, period, lines -> {
            if (!player.isOnline()) {
                return;
            }
            if (lines == null) {
                messages.send(player, "leaderboards.failed");
                return;
            }
            show(player, board, period, lines);
        });
    }

    private void show(Player player, Board board, Period period, List<Line> lines) {
        String title = PlainTextComponentSerializer.plainText().serialize(messages.get("board.title." + board.id()));
        Menu menu = menus.create(6, "leaderboards.board-title", Messages.value("board", title));
        boolean money = board == Board.MOST_WANTED || board.stat() == Stat.BOUNTY_EARNED;
        for (int i = 0; i < lines.size() && i < PLACES.length; i++) {
            Line line = lines.get(i);
            String amount = money ? Money.format(line.value()) : String.format(Locale.US, "%,d", line.value());
            ItemStack head = menus.head(line.uuid(), line.name(),
                    messages.get("leaderboards.entry.name", Messages.value("rank", i + 1), Messages.value("player", line.name())),
                    Menus.sections(messages.lines(money ? "leaderboards.entry.lore-money" : "leaderboards.entry.lore",
                            Messages.value("amount", amount)), List.of(menus.click("profile"))));
            menu.set(PLACES[i], head, click -> profiles.open(click.player(), line.uuid(),
                    back -> board(back, board, period)));
        }
        if (lines.isEmpty()) {
            menu.set(PLACES[0], menus.item(Material.PLAYER_HEAD, "leaderboards.empty"));
        }
        menus.back(menu, this::open);
        menus.close(menu);
        // Most wanted is the bounty right now: no periods.
        if (board.stat() != null) {
            Period[] periods = Period.values();
            for (int i = 0; i < periods.length; i++) {
                Period each = periods[i];
                boolean showing = each == period;
                ItemStack clock = menus.item(Material.CLOCK, messages.get("leaderboards.period." + each.key()),
                        List.of(showing ? messages.get("leaderboards.showing") : menus.click("show")));
                clock.editMeta(meta -> meta.setEnchantmentGlintOverride(showing));
                menu.set(menu.bottom() + 6 + i, clock, showing ? null : click -> board(click.player(), board, each));
            }
        }
        menus.open(player, menu);
    }
}
