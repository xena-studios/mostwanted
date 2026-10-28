package co.xenastudios.mostwanted.item;

import org.bukkit.entity.Player;

/** A piece of guard gear: given on going on duty, taken on going off. */
public interface GuardKit {

    /** Gives it unless they already have it. */
    void give(Player player);

    void take(Player player);
}
