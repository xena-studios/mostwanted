package co.xenastudios.mostwanted.zone;

import co.xenastudios.mostwanted.player.PlayerState;
import org.bukkit.entity.Player;

/** Told when a player crosses a zone's edge, on the main thread. Leaving comes before entering. */
public interface ZoneListener {

    default void entered(Player player, PlayerState state, Zone zone) {}

    default void left(Player player, PlayerState state, Zone zone) {}
}
