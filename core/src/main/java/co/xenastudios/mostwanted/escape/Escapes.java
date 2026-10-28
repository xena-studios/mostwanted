package co.xenastudios.mostwanted.escape;

import co.xenastudios.mostwanted.message.Messages;
import co.xenastudios.mostwanted.player.PlayerState;
import co.xenastudios.mostwanted.player.Role;
import co.xenastudios.mostwanted.player.Stat;
import co.xenastudios.mostwanted.storage.GameLog;
import co.xenastudios.mostwanted.zone.Zone;
import co.xenastudios.mostwanted.zone.ZoneKind;
import co.xenastudios.mostwanted.zone.ZoneListener;
import co.xenastudios.mostwanted.zone.ZoneTracker;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** An inmate who gets outside every prison zone has escaped, and is now a criminal. */
public final class Escapes implements ZoneListener {

    private final ZoneTracker tracker;
    private final Messages messages;
    private final GameLog gameLog;

    public Escapes(ZoneTracker tracker, Messages messages, GameLog gameLog) {
        this.tracker = tracker;
        this.messages = messages;
        this.gameLog = gameLog;
    }

    @Override
    public void left(Player player, PlayerState state, Zone zone) {
        // A prisoner serving a sentence is put back in their cell instead (Arrests).
        if (zone.kind() != ZoneKind.PRISON || state.role() != Role.INMATE || state.jailedUntil() != null
                || tracker.isIn(player, ZoneKind.PRISON)) {
            return;
        }
        state.role(Role.CRIMINAL);
        state.add(Stat.ESCAPES, 1);
        gameLog.record("escape", player.getUniqueId(), null, Map.of("prison", zone.name()));
        messages.send(player, "escape.you");
        messages.send(Bukkit.getServer(), "escape.everyone", Messages.value("player", player.getName()));
    }
}
