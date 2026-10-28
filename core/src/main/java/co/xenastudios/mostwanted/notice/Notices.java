package co.xenastudios.mostwanted.notice;

import static co.xenastudios.mostwanted.common.db.Tables.ACCOUNTS;
import static co.xenastudios.mostwanted.common.db.Tables.NOTIFICATIONS;
import static co.xenastudios.mostwanted.common.db.Tables.RANK_GRANTS;

import co.xenastudios.mostwanted.common.Notifications;
import co.xenastudios.mostwanted.message.Messages;
import co.xenastudios.mostwanted.storage.Storage;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import org.jooq.Record3;
import org.jooq.impl.DSL;

/**
 * What the website tells players, in game: a staff reply, an application decided
 * (docs/08-website.md#accounts-and-the-api). Shown at once to someone online, or
 * as they join. Also gives an accepted guard their rank. The backend nudges us over
 * LISTEN/NOTIFY; what to do is always read from the tables, so nothing is missed
 * while we were away.
 */
public final class Notices implements Listener {

    private final Plugin plugin;
    private final Storage storage;
    private final Messages messages;
    private final String website;
    private final Logger log;
    private Notifications.Listener listener;

    public Notices(Plugin plugin, Storage storage, Messages messages, String website, Logger log) {
        this.plugin = plugin;
        this.storage = storage;
        this.messages = messages;
        this.website = website;
        this.log = log;
    }

    public void start() {
        listener = Notifications.listen(storage.database(), List.of(Notifications.INBOX, Notifications.RANK_GRANTS),
                this::catchUp, (channel, payload) -> {
                    if (channel.equals(Notifications.RANK_GRANTS)) {
                        grants();
                    } else {
                        Bukkit.getScheduler().runTask(plugin, () -> Bukkit.getOnlinePlayers().forEach(this::deliver));
                    }
                });
    }

    public void stop() {
        if (listener != null) {
            listener.close();
        }
    }

    /** After every (re)connect: whatever came while we weren't listening. */
    private void catchUp() {
        grants();
        Bukkit.getScheduler().runTask(plugin, () -> Bukkit.getOnlinePlayers().forEach(this::deliver));
    }

    @EventHandler
    public void join(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // A moment after the welcome, so it isn't lost in it.
        Bukkit.getScheduler().runTaskLater(plugin, () -> deliver(player), 60);
    }

    /** Shows them anything not yet shown in game, marking it shown. Reads off the main thread. */
    private void deliver(Player player) {
        if (!player.isOnline()) {
            return;
        }
        UUID uuid = player.getUniqueId();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<Record3<Long, String, String>> rows;
            try {
                rows = storage.sql().transactionResult(tx -> {
                    var found = tx.dsl().select(NOTIFICATIONS.ID, NOTIFICATIONS.TEXT, NOTIFICATIONS.LINK)
                            .from(NOTIFICATIONS).join(ACCOUNTS).on(ACCOUNTS.ID.eq(NOTIFICATIONS.ACCOUNT_ID))
                            .where(ACCOUNTS.MINECRAFT_UUID.eq(uuid)).and(NOTIFICATIONS.SHOWN_IN_GAME_AT.isNull())
                            .orderBy(NOTIFICATIONS.ID)
                            .limit(10)
                            .forUpdate().of(NOTIFICATIONS).skipLocked()
                            .fetch();
                    tx.dsl().update(NOTIFICATIONS).set(NOTIFICATIONS.SHOWN_IN_GAME_AT, DSL.currentOffsetDateTime())
                            .where(NOTIFICATIONS.ID.in(found.getValues(NOTIFICATIONS.ID)))
                            .execute();
                    return found;
                });
            } catch (RuntimeException e) {
                log.warning("Couldn't read notifications: " + e.getMessage());
                return;
            }
            if (rows.isEmpty()) {
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> rows.forEach(row -> show(player, row.value2(), row.value3())));
        });
    }

    private void show(Player player, String text, String link) {
        if (link == null) {
            messages.send(player, "notice.message", Messages.value("text", text));
            return;
        }
        String url = website + link;
        player.sendMessage(messages.get("notice.linked", Messages.value("text", text),
                Messages.value("url", url.replaceFirst("^https?://", ""))).clickEvent(ClickEvent.openUrl(url)));
    }

    /** Gives every rank waiting to be given. Only guard: the table allows nothing else. */
    private void grants() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<Record3<Long, UUID, String>> pending;
            try {
                pending = storage.sql().select(RANK_GRANTS.ID, RANK_GRANTS.UUID, RANK_GRANTS.RANK).from(RANK_GRANTS)
                        .where(RANK_GRANTS.DONE_AT.isNull()).orderBy(RANK_GRANTS.ID).limit(50).fetch();
            } catch (RuntimeException e) {
                log.warning("Couldn't read ranks to give: " + e.getMessage());
                return;
            }
            if (pending.isEmpty()) {
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (Bukkit.getPluginManager().getPlugin("LuckPerms") == null) {
                    log.warning(pending.size() + " rank(s) to give, but LuckPerms isn't here: they wait for it");
                    return;
                }
                for (var grant : pending) {
                    if (!grant.value3().equals("guard")) {
                        continue;
                    }
                    log.info("Website: giving " + grant.value2() + " the guard rank (application accepted)");
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "lp user " + grant.value2() + " parent add guard");
                }
                List<Long> done = pending.stream().map(Record3::value1).toList();
                storage.write("rank grants", sql -> sql.update(RANK_GRANTS)
                        .set(RANK_GRANTS.DONE_AT, DSL.currentOffsetDateTime())
                        .where(RANK_GRANTS.ID.in(done)).execute());
            });
        });
    }
}
