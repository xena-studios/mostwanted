package co.xenastudios.mostwanted.economy;

import co.xenastudios.mostwanted.common.style.Money;
import co.xenastudios.mostwanted.message.Messages;
import co.xenastudios.mostwanted.player.PlayerState;
import co.xenastudios.mostwanted.player.Role;
import co.xenastudios.mostwanted.zone.Zone;
import co.xenastudios.mostwanted.zone.ZoneKind;
import co.xenastudios.mostwanted.zone.ZoneListener;
import org.bukkit.entity.Player;

/**
 * A criminal's cash is banked the moment they walk into a hideout: safe from arrest
 * from then on. Automatic, so there's no menu to open (and nothing that differs on Bedrock).
 */
public final class Banking implements ZoneListener {

    private final Messages messages;

    public Banking(Messages messages) {
        this.messages = messages;
    }

    @Override
    public void entered(Player player, PlayerState state, Zone zone) {
        if (zone.kind() != ZoneKind.HIDEOUT || state.role() != Role.CRIMINAL || state.cash() == 0) {
            return;
        }
        long cash = state.cash();
        state.bank(state.bank() + cash);
        state.cash(0);
        messages.send(player, "bank.deposited", Messages.value("amount", Money.format(cash)),
                Messages.value("total", Money.format(state.bank())));
    }
}
