package co.xenastudios.mostwanted.menu;

import co.xenastudios.mostwanted.hud.Hud;
import co.xenastudios.mostwanted.player.PlayerState;
import co.xenastudios.mostwanted.player.PlayerStore;
import co.xenastudios.mostwanted.player.Setting;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.List;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * {@code /settings}: each setting with what it does, and a green or red pane under it
 * saying whether it's on. Clicking either turns it the other way, straight away.
 */
public final class SettingsMenu {

    private static final int[] SLOTS = {10, 12, 14, 16};
    private static final Material[] ICONS = {Material.SPYGLASS, Material.MAP, Material.BELL, Material.NAME_TAG};

    private final Menus menus;
    private final PlayerStore players;
    private final Hud hud;
    private final Consumer<Player> back;

    public SettingsMenu(Menus menus, PlayerStore players, Hud hud, Consumer<Player> back) {
        this.menus = menus;
        this.players = players;
        this.hud = hud;
        this.back = back;
    }

    public LiteralCommandNode<CommandSourceStack> command() {
        return Commands.literal("settings")
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
        Menu menu = menus.create(4, "settings.title");
        draw(menu, state);
        menus.open(player, menu);
    }

    private void draw(Menu menu, PlayerState state) {
        menu.clear();
        Setting[] settings = Setting.values();
        for (int i = 0; i < settings.length; i++) {
            Setting setting = settings[i];
            boolean on = state.on(setting);
            List<Component> lore = Menus.sections(
                    menus.lore("settings." + setting.key()),
                    List.of(menus.messages().get(on ? "settings.on" : "settings.off")),
                    List.of(menus.click(on ? "turn-off" : "turn-on")));
            ItemStack icon = menus.item(ICONS[i], menus.messages().get("settings." + setting.key() + ".name"), lore);
            icon.editMeta(meta -> meta.setEnchantmentGlintOverride(on));
            ItemStack pane = menus.item(on ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE,
                    menus.messages().get(on ? "settings.on" : "settings.off"), List.of(menus.click(on ? "turn-off" : "turn-on")));
            menu.set(SLOTS[i], icon, click -> flip(click, setting));
            menu.set(SLOTS[i] + 9, pane, click -> flip(click, setting));
        }
        menus.back(menu, back);
        menus.close(menu);
    }

    private void flip(Click click, Setting setting) {
        PlayerState state = players.get(click.player().getUniqueId()).orElse(null);
        if (state == null) {
            return;
        }
        state.set(setting, !state.on(setting));
        if (setting == Setting.SCOREBOARD) {
            hud.scoreboard(click.player());
        }
        draw(click.menu(), state);
    }
}
