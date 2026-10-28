package co.xenastudios.mostwanted.player;

import static co.xenastudios.mostwanted.common.db.Tables.LOGIN_CODES;

import co.xenastudios.mostwanted.common.LoginCode;
import co.xenastudios.mostwanted.message.Messages;
import co.xenastudios.mostwanted.storage.Storage;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.bukkit.entity.Player;

/**
 * {@code /website}: a one-time code to sign in on the site, or link this Minecraft
 * account to a website account. Typed rather than clicked, so it works on Bedrock.
 * A new code replaces the last one.
 */
public final class WebsiteCommand {

    private static final long COOLDOWN_MILLIS = 10_000;

    private final Storage storage;
    private final Messages messages;
    private final Executor mainThread;
    private final Clock clock;
    private final Map<UUID, Long> lastCode = new HashMap<>();

    public WebsiteCommand(Storage storage, Messages messages, Executor mainThread, Clock clock) {
        this.storage = storage;
        this.messages = messages;
        this.mainThread = mainThread;
        this.clock = clock;
    }

    public LiteralCommandNode<CommandSourceStack> command() {
        return Commands.literal("website")
                .requires(source -> source.getExecutor() instanceof Player)
                .executes(context -> {
                    code((Player) context.getSource().getExecutor());
                    return Command.SINGLE_SUCCESS;
                })
                .build();
    }

    private void code(Player player) {
        long now = clock.millis();
        Long last = lastCode.get(player.getUniqueId());
        if (last != null && now - last < COOLDOWN_MILLIS) {
            messages.send(player, "website.wait");
            return;
        }
        lastCode.put(player.getUniqueId(), now);
        UUID uuid = player.getUniqueId();
        String code = LoginCode.generate();
        var expires = clock.instant().plus(LoginCode.LIFETIME).atOffset(ZoneOffset.UTC);
        storage.write("a website code", sql -> {
            sql.deleteFrom(LOGIN_CODES).where(LOGIN_CODES.UUID.eq(uuid)).execute();
            sql.insertInto(LOGIN_CODES)
                    .set(LOGIN_CODES.CODE, code)
                    .set(LOGIN_CODES.UUID, uuid)
                    .set(LOGIN_CODES.EXPIRES_AT, expires)
                    .execute();
        }).whenComplete((ok, error) -> mainThread.execute(() -> {
            if (!player.isOnline()) {
                return;
            }
            if (error != null) {
                // A clash with someone else's code (1 in a billion) lands here too: try again.
                lastCode.remove(uuid);
                messages.send(player, "website.failed");
            } else {
                messages.send(player, "website.code", Messages.value("code", code),
                        Messages.value("minutes", LoginCode.LIFETIME.toMinutes()));
            }
        }));
    }
}
