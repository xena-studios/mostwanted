package co.xenastudios.mostwanted.cosmetic;

import co.xenastudios.mostwanted.common.style.Palette;
import co.xenastudios.mostwanted.gun.GunSettings;
import co.xenastudios.mostwanted.gun.Guns;
import co.xenastudios.mostwanted.item.CustomItems;
import co.xenastudios.mostwanted.message.Messages;
import co.xenastudios.mostwanted.player.PlayerStore;
import co.xenastudios.mostwanted.storage.GameLog;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * What the store's ranks and cosmetics do in game: trails as you move, an effect when
 * you escape or make an arrest, chat colours, gun skins (through Guns), rank tags and
 * join announcements, and the Server Boost. Everything is looks only: no cosmetic
 * changes how anyone plays, and their fireworks never hurt anyone.
 */
public final class Cosmetics implements Listener {

    /** The chat colours on offer: the palette's hues, 30° apart. */
    public static final List<Integer> CHAT_HUES = List.of(0, 30, 60, 90, 120, 150, 180, 210, 240, 270, 300, 330);
    private static final Sound ESCAPE_THUNDER = Sound.sound(Key.key("minecraft:entity.lightning_bolt.thunder"), Sound.Source.PLAYER, 0.6f, 1.2f);
    private static final Sound SPOTLIGHT = Sound.sound(Key.key("minecraft:block.beacon.activate"), Sound.Source.PLAYER, 1f, 1.4f);
    private static final Sound SIREN = Sound.sound(Key.key("minecraft:block.note_block.bell"), Sound.Source.PLAYER, 1f, 1.8f);

    private final Plugin plugin;
    private final CosmeticStore store;
    private final PlayerStore players;
    private final Messages messages;
    private final GameLog gameLog;
    private final CustomItems items;
    private Guns guns;
    private final NamespacedKey harmless;
    private final ItemStack cash = ItemStack.of(org.bukkit.Material.PAPER);
    /** Blocks moved since the last trail puff, so trails stay light. */
    private final Map<UUID, Integer> steps = new HashMap<>();
    /** Each online player's rank, refreshed now and then: a purchase changes it. */
    private final Map<UUID, Rank> ranks = new HashMap<>();

    public Cosmetics(Plugin plugin, CosmeticStore store, PlayerStore players, Messages messages, GameLog gameLog,
            CustomItems items) {
        this.plugin = plugin;
        this.store = store;
        this.players = players;
        this.messages = messages;
        this.gameLog = gameLog;
        this.items = items;
        this.harmless = new NamespacedKey(plugin, "harmless");
    }

    // ---------------------------------------------------------------- what they own and use

    public boolean owns(Player player, Cosmetic cosmetic) {
        return player.hasPermission(cosmetic.permission());
    }

    /** What they use in a slot, if they still own it. */
    public Cosmetic using(Player player, String slot) {
        Cosmetic cosmetic = Cosmetic.byId(String.valueOf(store.get(player.getUniqueId(), slot)));
        return cosmetic != null && owns(player, cosmetic) ? cosmetic : null;
    }

    public CosmeticStore store() {
        return store;
    }

    /** For Guns: the pack model of the skin they use on this gun, or null for the plain one. */
    public String gunSkin(Player player, String gun) {
        Cosmetic skin = using(player, "skin:" + gun);
        return skin == null ? null : skin.model();
    }

    /** Guns are made after this, so they're handed over once they exist. */
    public void guns(Guns guns) {
        this.guns = guns;
    }

    /**
     * Every couple of seconds: each gun in their inventory shows the skin they chose, or
     * the plain look if they took it off (or lost it). A gun dropped and picked up by
     * someone else changes to theirs.
     */
    public void applySkins() {
        if (guns == null) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            ItemStack[] contents = player.getInventory().getContents();
            for (int slot = 0; slot < contents.length; slot++) {
                ItemStack item = contents[slot];
                GunSettings.Gun gun = item == null ? null : guns.gunOf(item);
                if (gun == null) {
                    continue;
                }
                ItemMeta meta = item.getItemMeta();
                if (items.skin(meta, gunSkin(player, gun.id()))) {
                    item.setItemMeta(meta);
                    player.getInventory().setItem(slot, item);
                }
            }
        }
    }

    public Rank rank(Player player) {
        return ranks.get(player.getUniqueId());
    }

    // ---------------------------------------------------------------- joining

    @EventHandler(priority = EventPriority.MONITOR)
    public void join(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        Rank rank = Rank.of(player);
        ranks.put(uuid, rank);
        name(player, rank);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            store.load(uuid);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    announce(player, rank);
                } else {
                    store.forget(uuid);
                }
            });
        });
    }

    /** MVP and up are announced; Legend with their own message if they set one. */
    private void announce(Player player, Rank rank) {
        if (rank == null || !player.hasPermission("mostwanted.join-announce")) {
            return;
        }
        Component tag = messages.get("rank." + rank.key());
        String custom = player.hasPermission("mostwanted.join-message")
                ? store.get(player.getUniqueId(), "join-message") : null;
        if (custom != null) {
            messages.send(Bukkit.getServer(), "rank.joined-message", Messages.component("rank", tag),
                    Messages.value("player", player.getName()), Messages.value("message", custom));
        } else {
            messages.send(Bukkit.getServer(), "rank.joined", Messages.component("rank", tag),
                    Messages.value("player", player.getName()));
        }
    }

    @EventHandler
    public void quit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        store.forget(uuid);
        steps.remove(uuid);
        ranks.remove(uuid);
    }

    /** Every 30 s: a rank bought (or taken away) while online shows up without a rejoin. */
    public void refreshRanks() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Rank now = Rank.of(player);
            if (now != ranks.put(player.getUniqueId(), now)) {
                name(player, now);
                players.get(player.getUniqueId()).ifPresent(state -> state.markHudDirty());
            }
        }
    }

    /** The tag after their name in the tab list. */
    public Component tag(UUID uuid) {
        Rank rank = ranks.get(uuid);
        return rank == null ? Component.empty() : Component.space().append(messages.get("rank." + rank.key()));
    }

    /**
     * MVP and up: their name in their rank's colour wherever the server shows their
     * display name (chat, through the chat plugin's format). The tab list keeps the role
     * colours, which mean something in the game.
     */
    private void name(Player player, Rank rank) {
        player.displayName(rank != null && player.hasPermission("mostwanted.name-colour")
                ? Component.text(player.getName(), TextColor.color(rank.colour()))
                : null);
    }

    // ---------------------------------------------------------------- trails

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void move(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getBlockX() == to.getBlockX() && from.getBlockZ() == to.getBlockZ()) {
            return;
        }
        Player player = event.getPlayer();
        Cosmetic trail = using(player, "trail");
        if (trail == null || player.isInvisible() || steps.merge(player.getUniqueId(), 1, Integer::sum) % 2 != 0) {
            return;
        }
        World world = from.getWorld();
        Location at = from.clone().add(0, 0.15, 0);
        switch (trail) {
            case TRAIL_CASH -> world.spawnParticle(Particle.ITEM, at, 4, 0.2, 0.05, 0.2, 0.02, cash);
            case TRAIL_EMBER -> {
                world.spawnParticle(Particle.FLAME, at, 3, 0.15, 0.05, 0.15, 0.01);
                world.spawnParticle(Particle.SMALL_FLAME, at, 2, 0.15, 0.05, 0.15, 0.01);
            }
            case TRAIL_SMOKE -> world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, at, 1, 0.1, 0, 0.1, 0.005);
            case TRAIL_SIREN -> {
                boolean red = steps.get(player.getUniqueId()) % 4 == 0;
                world.spawnParticle(Particle.DUST, at, 6, 0.25, 0.1, 0.25, 0,
                        new Particle.DustOptions(red ? Color.fromRGB(Palette.hue(0)) : Color.fromRGB(Palette.hue(210)), 1.2f));
            }
            default -> { }
        }
    }

    // ---------------------------------------------------------------- escape and arrest effects

    /** Called by Escapes when a player breaks out. */
    public void escaped(Player player) {
        Cosmetic effect = using(player, "escape");
        if (effect == null) {
            return;
        }
        Location at = player.getLocation();
        switch (effect) {
            case ESCAPE_FIREWORKS -> {
                for (int i = 0; i < 3; i++) {
                    firework(at.clone().add(ThreadLocalRandom.current().nextDouble(-2, 2), 0.5,
                            ThreadLocalRandom.current().nextDouble(-2, 2)), 1);
                }
            }
            case ESCAPE_CONFETTI -> {
                for (int hue = 0; hue < 360; hue += 30) {
                    at.getWorld().spawnParticle(Particle.DUST, at.clone().add(0, 1.8, 0), 12, 0.8, 0.6, 0.8, 0,
                            new Particle.DustOptions(Color.fromRGB(Palette.hue(hue)), 1.3f));
                }
            }
            case ESCAPE_THUNDER -> {
                at.getWorld().strikeLightningEffect(at);
                at.getWorld().playSound(ESCAPE_THUNDER, at.getX(), at.getY(), at.getZ());
            }
            default -> { }
        }
    }

    /** Called by Arrests when a guard arrests someone: the guard's effect, on the prisoner. */
    public void arrested(Player criminal, Player guard) {
        Cosmetic effect = using(guard, "arrest");
        if (effect == null) {
            return;
        }
        Location at = criminal.getLocation();
        switch (effect) {
            case ARREST_SPOTLIGHT -> {
                for (double y = 0; y < 6; y += 0.4) {
                    at.getWorld().spawnParticle(Particle.END_ROD, at.clone().add(0, y, 0), 2, 0.15, 0, 0.15, 0);
                }
                at.getWorld().playSound(SPOTLIGHT, at.getX(), at.getY(), at.getZ());
            }
            case ARREST_SIREN -> {
                for (int i = 0; i < 16; i++) {
                    double angle = Math.PI * 2 * i / 16;
                    at.getWorld().spawnParticle(Particle.DUST, at.clone().add(Math.cos(angle), 2.2, Math.sin(angle)), 2,
                            0, 0, 0, 0, new Particle.DustOptions(Color.fromRGB(Palette.hue(i % 2 == 0 ? 0 : 210)), 1.4f));
                }
                at.getWorld().playSound(SIREN, at.getX(), at.getY(), at.getZ());
            }
            default -> { }
        }
    }

    // ---------------------------------------------------------------- chat colour

    /** Before the chat plugin formats it: their words in their colour. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void chat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (!owns(player, Cosmetic.CHAT_COLOURS)) {
            return;
        }
        Integer hue = CosmeticMenu.hue(store.get(player.getUniqueId(), "chat"));
        if (hue != null) {
            event.message(event.message().color(TextColor.color(Palette.hue(hue))));
        }
    }

    // ---------------------------------------------------------------- the Server Boost

    /** {@code /mostwanted boost <player>}, run by Tebex: fireworks over everyone, and a thank-you. */
    public void boost(String buyer) {
        gameLog.record("store.boost", null, null, Map.of("player", buyer));
        Title title = Title.title(messages.get("boost.title"), messages.get("boost.subtitle",
                Messages.value("player", buyer)));
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.showTitle(title);
            for (int i = 0; i < 2; i++) {
                firework(player.getLocation().add(ThreadLocalRandom.current().nextDouble(-6, 6), 2,
                        ThreadLocalRandom.current().nextDouble(-6, 6)), 2);
            }
        }
        messages.send(Bukkit.getServer(), "boost.chat", Messages.value("player", buyer));
    }

    // ---------------------------------------------------------------- fireworks that never hurt

    private void firework(Location at, int power) {
        at.getWorld().spawn(at, Firework.class, firework -> {
            FireworkMeta meta = firework.getFireworkMeta();
            ThreadLocalRandom random = ThreadLocalRandom.current();
            meta.addEffect(FireworkEffect.builder()
                    .with(FireworkEffect.Type.values()[random.nextInt(FireworkEffect.Type.values().length)])
                    .withColor(Color.fromRGB(Palette.hue(random.nextInt(24) * 15)),
                            Color.fromRGB(Palette.hue(random.nextInt(24) * 15)))
                    .withFade(Color.fromRGB(Palette.WHITE))
                    .trail(true).flicker(random.nextBoolean())
                    .build());
            meta.setPower(power);
            firework.setFireworkMeta(meta);
            firework.getPersistentDataContainer().set(harmless, PersistentDataType.BYTE, (byte) 1);
        });
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void fireworkDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Firework firework
                && firework.getPersistentDataContainer().has(harmless, PersistentDataType.BYTE)) {
            event.setCancelled(true);
        }
    }
}
