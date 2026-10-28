package co.xenastudios.mostwanted.menu;

import org.bukkit.entity.Player;

/** A click on a menu button: who, in which menu, and whether it was a right-click. */
public record Click(Player player, Menu menu, boolean right) {}
