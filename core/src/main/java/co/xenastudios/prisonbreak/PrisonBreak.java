package co.xenastudios.prisonbreak;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.util.List;
import org.bukkit.plugin.java.JavaPlugin;

/** The game's entry point. Each system registers itself here as it's built. */
public final class PrisonBreak extends JavaPlugin {

    @Override
    public void onEnable() {
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> event.registrar().register(
                PrisonBreakCommand.create(this),
                "The prison break game's admin command.",
                List.of("pb")));
    }
}
