package co.xenastudios.mostwanted.hud;

import co.xenastudios.mostwanted.common.Stat;
import co.xenastudios.mostwanted.common.style.Money;
import co.xenastudios.mostwanted.message.Messages;
import co.xenastudios.mostwanted.player.PlayerState;
import co.xenastudios.mostwanted.player.PlayerStore;
import co.xenastudios.mostwanted.player.Role;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.object.ObjectContents;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * The scoreboard and tab list (docs/09-brand.md#scoreboard-and-tab-list). Redrawn
 * only for players whose state changed, plus the clock once a minute and running
 * timers once a second.
 */
public final class Hud {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MM/dd/yy");
    /** Bounty for each wanted star. Starting values: tune in playtests. */
    private static final long[] STARS = {1, 1_000, 5_000, 15_000, 50_000};

    private final PlayerStore store;
    private final Messages messages;
    private final Clock clock;
    private final ZoneId zone;
    private final String zoneLabel;
    private final Map<UUID, Sidebar> sidebars = new HashMap<>();
    private String lastMinute = "";
    private long lastSecond;
    private boolean tabChanged = true;
    /** The tab footer and player count last sent, and who has them: only changes are sent. */
    private String lastTab = "";
    private final Set<UUID> tabSent = new HashSet<>();
    /** Live events (a robbery, later a riot): a section everyone sees while they're on. */
    private Supplier<List<Component>> live = List::of;
    private List<Component> lastLive = List.of();
    /** What follows a name in the tab list: a store rank's tag, or nothing. */
    private Function<UUID, Component> tag = uuid -> Component.empty();

    public Hud(PlayerStore store, Messages messages, Clock clock, ZoneId zone, String zoneLabel) {
        this.store = store;
        this.messages = messages;
        this.clock = clock;
        this.zone = zone;
        this.zoneLabel = zoneLabel;
    }

    public void live(Supplier<List<Component>> live) {
        this.live = live;
    }

    public void tags(Function<UUID, Component> tag) {
        this.tag = tag;
    }

    public void show(Player player) {
        Sidebar sidebar = new Sidebar(Bukkit.getScoreboardManager(), messages.get("hud.title"));
        sidebars.put(player.getUniqueId(), sidebar);
        player.setScoreboard(sidebar.board());
        store.get(player.getUniqueId()).ifPresent(PlayerState::markHudDirty);
        tabChanged = true;
    }

    public void hide(Player player) {
        sidebars.remove(player.getUniqueId());
        tabSent.remove(player.getUniqueId());
        tabChanged = true;
    }

    /** Runs on the main thread every half second. */
    public void tick() {
        Instant now = clock.instant();
        String minute = TIME.format(now.atZone(zone));
        boolean clockChanged = !minute.equals(lastMinute);
        lastMinute = minute;
        // Sentences and live timers count in seconds; this runs twice a second.
        long second = now.getEpochSecond();
        boolean newSecond = second != lastSecond;
        lastSecond = second;
        List<Component> events = live.get();
        boolean liveChanged = newSecond && !events.equals(lastLive);
        if (newSecond) {
            lastLive = events;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            Sidebar sidebar = sidebars.get(player.getUniqueId());
            PlayerState state = store.get(player.getUniqueId()).orElse(null);
            if (sidebar == null || state == null) {
                continue;
            }
            boolean changed = state.takeHudDirty();
            if (changed) {
                player.playerListName(listName(state));
                player.setPlayerListOrder(listOrder(state));
                tabChanged = true;
            }
            if (changed || clockChanged || liveChanged || newSecond && serving(state, now)) {
                sidebar.lines(lines(state, now, lastLive));
            }
        }
        if (tabChanged) {
            tabChanged = false;
            drawTab();
        }
    }

    List<Component> lines(PlayerState state, Instant now, List<Component> events) {
        List<Component> lines = new ArrayList<>();
        var at = now.atZone(zone);
        lines.add(messages.get("hud.clock", Messages.value("time", TIME.format(at)),
                Messages.value("zone", zoneLabel), Messages.value("date", DATE.format(at))));
        lines.add(Component.empty());
        lines.add(messages.get("hud.profile"));
        lines.add(messages.get("hud.role", Messages.component("role", role(state.role()))));
        switch (state.role()) {
            case INMATE -> {
                lines.add(money("hud.cash", state.cash()));
                lines.add(money("hud.bank", state.bank()));
                lines.add(Component.empty());
                if (serving(state, now)) {
                    lines.add(messages.get("hud.sentence",
                            Messages.value("time", clockTime(Duration.between(now, state.jailedUntil())))));
                }
                lines.add(count("hud.escapes", state.total(Stat.ESCAPES)));
                lines.add(count("hud.arrested", state.total(Stat.TIMES_ARRESTED)));
            }
            case CRIMINAL -> {
                lines.add(money("hud.cash", state.cash()));
                lines.add(money("hud.bank", state.bank()));
                lines.add(money("hud.bounty", state.bounty()));
                lines.add(messages.get("hud.wanted", Messages.value("stars", "★".repeat(stars(state.bounty()))),
                        Messages.value("empty", "★".repeat(STARS.length - stars(state.bounty())))));
            }
            case GUARD -> {
                lines.add(messages.get("hud.duty"));
                lines.add(money("hud.cash", state.cash()));
                lines.add(Component.empty());
                lines.add(messages.get("hud.patrol"));
                lines.add(count("hud.arrests", state.total(Stat.ARRESTS)));
                lines.add(money("hud.bounty-earned", state.total(Stat.BOUNTY_EARNED)));
            }
        }
        if (!events.isEmpty()) {
            lines.add(Component.empty());
            lines.addAll(events);
        }
        lines.add(Component.empty());
        lines.add(messages.get("hud.footer"));
        return lines;
    }

    private void drawTab() {
        Map<Role, Integer> counts = new EnumMap<>(Role.class);
        for (Role role : Role.values()) {
            counts.put(role, 0);
        }
        store.all().forEach(state -> counts.merge(state.role(), 1, Integer::sum));
        int online = Bukkit.getOnlinePlayers().size();
        // A role change or someone joining changes this; most state changes (cash, bounty) don't.
        String tab = counts + "/" + online;
        boolean everyone = !tab.equals(lastTab);
        lastTab = tab;
        if (everyone) {
            tabSent.clear();
        }
        Component footer = messages.get("tab.footer",
                Messages.value("guards", counts.get(Role.GUARD)),
                Messages.value("criminals", counts.get(Role.CRIMINAL)),
                Messages.value("inmates", counts.get(Role.INMATE)));
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!tabSent.add(player.getUniqueId())) {
                continue;
            }
            Component header = messages.get("tab.header",
                    Messages.component("head", Component.object(ObjectContents.playerHead(player.getUniqueId()))),
                    Messages.value("player", player.getName()),
                    Messages.value("online", online));
            player.sendPlayerListHeaderAndFooter(header, footer);
        }
    }

    private Component listName(PlayerState state) {
        return messages.get("tab.entry." + state.role().key(), Messages.value("player", state.name()))
                .append(tag.apply(state.uuid()));
    }

    /** Higher shows first: guards, then criminals by bounty, then inmates. */
    static int listOrder(PlayerState state) {
        return switch (state.role()) {
            case GUARD -> 2_000_000_000;
            case CRIMINAL -> 1_000_000_000 + (int) Math.min(state.bounty(), 999_999_999);
            case INMATE -> 0;
        };
    }

    private Component role(Role role) {
        return Component.text(role.displayName(), TextColor.color(role.colour()));
    }

    private Component money(String key, long amount) {
        return messages.get(key, Messages.value("amount", Money.format(amount)));
    }

    private Component count(String key, long count) {
        return messages.get(key, Messages.value("count", count));
    }

    private static boolean serving(PlayerState state, Instant now) {
        return state.jailedUntil() != null && state.jailedUntil().isAfter(now);
    }

    static int stars(long bounty) {
        int stars = 0;
        for (long threshold : STARS) {
            if (bounty >= threshold) {
                stars++;
            }
        }
        return stars;
    }

    /** "2:14", "1:02:05". */
    public static String clockTime(Duration left) {
        long seconds = Math.max(0, (left.toMillis() + 999) / 1000);
        long h = seconds / 3600;
        long m = seconds % 3600 / 60;
        long s = seconds % 60;
        return h > 0 ? String.format("%d:%02d:%02d", h, m, s) : String.format("%d:%02d", m, s);
    }
}
