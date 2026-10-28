package co.xenastudios.mostwanted.zone;

import co.xenastudios.mostwanted.message.Messages;
import co.xenastudios.mostwanted.player.PlayerState;
import org.bukkit.entity.Player;

/** Says where you are, above the hotbar, as you walk into a zone. */
public final class ZoneNotices implements ZoneListener {

    private final Messages messages;

    public ZoneNotices(Messages messages) {
        this.messages = messages;
    }

    @Override
    public void entered(Player player, PlayerState state, Zone zone) {
        player.sendActionBar(messages.get("zone.entered." + zone.kind().key(), Messages.value("zone", zone.name())));
    }
}
