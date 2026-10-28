package co.xenastudios.mostwanted.gun;

import co.xenastudios.mostwanted.combat.Combat;
import co.xenastudios.mostwanted.item.CustomItems;
import co.xenastudios.mostwanted.item.GuardKit;
import co.xenastudios.mostwanted.message.Messages;
import co.xenastudios.mostwanted.player.PlayerState;
import co.xenastudios.mostwanted.player.PlayerStore;
import co.xenastudios.mostwanted.player.Role;
import com.destroystokyo.paper.event.player.PlayerPostRespawnEvent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/**
 * Right-click to shoot: hitscan rays from the eyes, damage through the combat rules (a
 * hit counts as the shooter's attack, so kills are credited and safe zones hold). Each
 * gun keeps its loaded rounds on the item and reloads by itself from ammo in the
 * inventory. The taser slows instead of hurting. Main thread only.
 */
public final class Guns implements Listener {

    private static final Sound SHOT = Sound.sound(Key.key("minecraft:entity.firework_rocket.blast"), Sound.Source.PLAYER, 1.5f, 1.4f);
    private static final Sound TASE = Sound.sound(Key.key("minecraft:entity.bee.sting"), Sound.Source.PLAYER, 1.5f, 0.6f);
    private static final Sound EMPTY = Sound.sound(Key.key("minecraft:block.dispenser.fail"), Sound.Source.PLAYER, 1f, 1.6f);
    private static final Sound RELOADED = Sound.sound(Key.key("minecraft:item.crossbow.loading_end"), Sound.Source.PLAYER, 1f, 1f);

    private final Plugin plugin;
    private final GunSettings settings;
    private final CustomItems items;
    private final PlayerStore players;
    private final Combat combat;
    private final Messages messages;
    private final NamespacedKey loadedKey;
    /** Players mid-reload, with the task that finishes it. */
    private final Map<UUID, Integer> reloading = new HashMap<>();

    public Guns(Plugin plugin, GunSettings settings, CustomItems items, PlayerStore players, Combat combat,
            Messages messages) {
        this.plugin = plugin;
        this.settings = settings;
        this.items = items;
        this.players = players;
        this.combat = combat;
        this.messages = messages;
        this.loadedKey = new NamespacedKey(plugin, "loaded");
    }

    // ---------------------------------------------------------------- items

    /** A gun, fully loaded. */
    public ItemStack gun(GunSettings.Gun gun) {
        ItemStack item = ItemStack.of(gun.item());
        item.editMeta(meta -> {
            meta.itemName(messages.get(gun.guardOnly() ? "item.gun.guard-name" : "item.gun.name",
                    Messages.value("name", gun.name())));
            meta.lore(List.of(messages.get(gun.taser() ? "item.gun.taser-lore" : "item.gun.lore",
                    Messages.value("damage", format(gun.damage() * gun.pellets())),
                    Messages.value("magazine", gun.magazine()))));
            items.mark(meta, "gun:" + gun.id());
            meta.getPersistentDataContainer().set(loadedKey, PersistentDataType.INTEGER, gun.magazine());
        });
        return item;
    }

    public ItemStack ammo(GunSettings.Ammo ammo, int amount) {
        ItemStack item = ItemStack.of(ammo.item(), amount);
        item.editMeta(meta -> {
            meta.itemName(messages.get("item.ammo.name", Messages.value("name", ammo.name())));
            items.mark(meta, "ammo:" + ammo.id());
        });
        return item;
    }

    /** The gun this item is, or null. */
    public GunSettings.Gun gunOf(ItemStack item) {
        String id = items.id(item);
        return id != null && id.startsWith("gun:") ? settings.guns().get(id.substring(4)) : null;
    }

    public GunSettings settings() {
        return settings;
    }

    // ---------------------------------------------------------------- shooting

    @EventHandler(priority = EventPriority.HIGH)
    public void click(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        GunSettings.Gun gun = gunOf(event.getItem());
        if (gun != null) {
            // No tilling with a hoe-shaped gun.
            event.setCancelled(true);
            fire(event.getPlayer(), gun);
        }
    }

    /** Right-clicking right at someone is an entity click, not an air click: still a shot. */
    @EventHandler(priority = EventPriority.HIGH)
    public void clickEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        GunSettings.Gun gun = gunOf(event.getPlayer().getInventory().getItemInMainHand());
        if (gun != null) {
            event.setCancelled(true);
            fire(event.getPlayer(), gun);
        }
    }

    private void fire(Player shooter, GunSettings.Gun gun) {
        ItemStack held = shooter.getInventory().getItemInMainHand();
        if (shooter.hasCooldown(held) || reloading.containsKey(shooter.getUniqueId())) {
            return;
        }
        if (gun.ammo() != null) {
            int loaded = loaded(held);
            if (loaded <= 0) {
                reload(shooter, gun);
                return;
            }
            setLoaded(shooter, loaded - 1);
        }
        shooter.setCooldown(held, gun.fireTicks());

        Location eye = shooter.getEyeLocation();
        Map<LivingEntity, Double> hits = new HashMap<>();
        for (int i = 0; i < gun.pellets(); i++) {
            Vector direction = eye.getDirection();
            if (gun.pellets() > 1 || gun.spread() > 0) {
                ThreadLocalRandom random = ThreadLocalRandom.current();
                direction.add(new Vector(random.nextGaussian(), random.nextGaussian(), random.nextGaussian())
                        .multiply(gun.spread())).normalize();
            }
            RayTraceResult result = shooter.getWorld().rayTrace(eye, direction, gun.range(),
                    FluidCollisionMode.NEVER, true, 0.2, entity -> isTarget(entity, shooter));
            Vector end = result == null ? eye.toVector().add(direction.clone().multiply(gun.range()))
                    : result.getHitPosition();
            trail(eye, end, gun.taser() ? Particle.ELECTRIC_SPARK : Particle.CRIT);
            if (result != null && result.getHitEntity() instanceof LivingEntity target) {
                double damage = gun.damage();
                if (GunSettings.headshot(end.getY(), target.getEyeLocation().getY())) {
                    damage *= settings.headshot();
                }
                hits.merge(target, damage, Double::sum);
            }
        }
        shooter.getWorld().playSound(gun.taser() ? TASE : SHOT, eye.getX(), eye.getY(), eye.getZ());
        hits.forEach((target, damage) -> hit(shooter, gun, target, damage));
        showAmmo(shooter);
        if (gun.ammo() != null && loaded(shooter.getInventory().getItemInMainHand()) == 0) {
            reload(shooter, gun);
        }
    }

    private void hit(Player shooter, GunSettings.Gun gun, LivingEntity target, double damage) {
        if (gun.taser()) {
            if (!(target instanceof Player victim)) {
                return;
            }
            PlayerState shooterState = players.get(shooter.getUniqueId()).orElse(null);
            PlayerState victimState = players.get(victim.getUniqueId()).orElse(null);
            if (shooterState == null || victimState == null
                    || !combat.canHurt(shooter, shooterState, victim, victimState)) {
                return;
            }
            int ticks = (int) (gun.stun().toMillis() / 50);
            victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ticks, 4, false, false, true));
            victim.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, ticks, 2, false, false, true));
            messages.send(victim, "gun.tased-you", Messages.value("player", shooter.getName()));
            messages.send(shooter, "gun.tased", Messages.value("player", victim.getName()));
            return;
        }
        // Each shot counts: without this, a hit within half a second of the last does nothing.
        target.setNoDamageTicks(0);
        target.damage(damage, DamageSource.builder(DamageType.PLAYER_ATTACK)
                .withCausingEntity(shooter).withDirectEntity(shooter).build());
    }

    private static void trail(Location from, Vector to, Particle particle) {
        Vector start = from.toVector();
        Vector step = to.clone().subtract(start);
        double length = step.length();
        if (length < 0.01) {
            return;
        }
        step.normalize().multiply(0.6);
        int points = (int) Math.min(length / 0.6, 60);
        Vector at = start.clone().add(step.clone().multiply(2));
        for (int i = 2; i < points; i++) {
            from.getWorld().spawnParticle(particle, at.getX(), at.getY(), at.getZ(), 1, 0, 0, 0, 0);
            at.add(step);
        }
    }

    // ---------------------------------------------------------------- reloading

    private void reload(Player player, GunSettings.Gun gun) {
        if (gun.ammo() == null || reloading.containsKey(player.getUniqueId())) {
            return;
        }
        GunSettings.Ammo ammo = settings.ammo().get(gun.ammo());
        if (count(player, ammo) == 0) {
            player.playSound(EMPTY, Sound.Emitter.self());
            player.sendActionBar(messages.get("gun.no-ammo", Messages.value("ammo", ammo.name())));
            return;
        }
        int slot = player.getInventory().getHeldItemSlot();
        player.sendActionBar(messages.get("gun.reloading", Messages.value("name", gun.name())));
        int task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            reloading.remove(player.getUniqueId());
            ItemStack held = player.getInventory().getItem(slot);
            if (!player.isOnline() || player.getInventory().getHeldItemSlot() != slot || gunOf(held) != gun) {
                return;
            }
            int amount = GunSettings.reloadAmount(loaded(held), gun.magazine(), count(player, ammo));
            take(player, ammo, amount);
            setLoaded(player, loaded(held) + amount);
            player.playSound(RELOADED, Sound.Emitter.self());
            showAmmo(player);
        }, Math.max(1, gun.reload().toMillis() / 50)).getTaskId();
        reloading.put(player.getUniqueId(), task);
    }

    private void cancelReload(Player player) {
        Integer task = reloading.remove(player.getUniqueId());
        if (task != null) {
            Bukkit.getScheduler().cancelTask(task);
        }
    }

    @EventHandler
    public void switchItem(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        cancelReload(player);
        Bukkit.getScheduler().runTask(plugin, () -> showAmmo(player));
    }

    /** "Pistol 7/12 · 36 spare", while holding a gun. */
    private void showAmmo(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        GunSettings.Gun gun = gunOf(held);
        if (gun == null || gun.ammo() == null) {
            return;
        }
        player.sendActionBar(messages.get("gun.hud", Messages.value("name", gun.name()),
                Messages.value("loaded", loaded(held)), Messages.value("magazine", gun.magazine()),
                Messages.value("spare", count(player, settings.ammo().get(gun.ammo())))));
    }

    private int loaded(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return 0;
        }
        Integer loaded = item.getPersistentDataContainer().get(loadedKey, PersistentDataType.INTEGER);
        return loaded == null ? 0 : loaded;
    }

    private void setLoaded(Player player, int loaded) {
        ItemStack held = player.getInventory().getItemInMainHand();
        held.editMeta(meta -> meta.getPersistentDataContainer().set(loadedKey, PersistentDataType.INTEGER, loaded));
        player.getInventory().setItemInMainHand(held);
    }

    private int count(Player player, GunSettings.Ammo ammo) {
        int count = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (items.is(item, "ammo:" + ammo.id())) {
                count += item.getAmount();
            }
        }
        return count;
    }

    private void take(Player player, GunSettings.Ammo ammo, int amount) {
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int i = 0; i < contents.length && amount > 0; i++) {
            if (items.is(contents[i], "ammo:" + ammo.id())) {
                int used = Math.min(amount, contents[i].getAmount());
                contents[i].setAmount(contents[i].getAmount() - used);
                amount -= used;
            }
        }
        player.getInventory().setStorageContents(contents);
    }

    // ---------------------------------------------------------------- guard kit and arrests

    /** The guard-only guns as a piece of guard gear. */
    public GuardKit guardKit() {
        return new GuardKit() {
            @Override
            public void give(Player player) {
                giveGuardGuns(player);
            }

            @Override
            public void take(Player player) {
                takeGuardGuns(player);
            }
        };
    }

    /** The guard-only guns (the taser), unless they already have them. */
    public void giveGuardGuns(Player player) {
        for (GunSettings.Gun gun : settings.guns().values()) {
            if (gun.guardOnly() && !has(player, "gun:" + gun.id())) {
                player.getInventory().addItem(gun(gun));
            }
        }
    }

    public void takeGuardGuns(Player player) {
        removeIf(player, id -> settings.guns().containsKey(id.substring(4)) && settings.guns().get(id.substring(4)).guardOnly(),
                "gun:");
    }

    /** An arrest takes every gun and all ammo, if confiscate-on-arrest is on. */
    public void confiscate(Player player) {
        if (settings.confiscate()) {
            removeIf(player, id -> true, "gun:");
            removeIf(player, id -> true, "ammo:");
        }
    }

    private boolean has(Player player, String id) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (items.is(item, id)) {
                return true;
            }
        }
        return false;
    }

    private void removeIf(Player player, Predicate<String> which, String prefix) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            String id = items.id(contents[i]);
            if (id != null && id.startsWith(prefix) && which.test(id)) {
                player.getInventory().setItem(i, null);
            }
        }
    }

    private boolean guardOnly(ItemStack item) {
        GunSettings.Gun gun = gunOf(item);
        return gun != null && gun.guardOnly();
    }

    @EventHandler(ignoreCancelled = true)
    public void drop(PlayerDropItemEvent event) {
        if (guardOnly(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    /**
     * Guard gear never drops. A criminal's guns and ammo don't either when arrests
     * confiscate them: dying is an arrest. Before Combat's handler, which makes them an inmate.
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void death(PlayerDeathEvent event) {
        cancelReload(event.getPlayer());
        boolean criminal = players.get(event.getPlayer().getUniqueId())
                .map(state -> state.role() == Role.CRIMINAL).orElse(false);
        event.getDrops().removeIf(item -> {
            String id = items.id(item);
            return guardOnly(item) || criminal && settings.confiscate() && id != null
                    && (id.startsWith("gun:") || id.startsWith("ammo:"));
        });
    }

    /** Guards keep their kit; anyone else who has guard gear loses it. After Duty's join check. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void join(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        players.get(player.getUniqueId()).ifPresent(state -> {
            if (state.role() == Role.GUARD) {
                giveGuardGuns(player);
            } else {
                takeGuardGuns(player);
            }
        });
    }

    @EventHandler
    public void respawn(PlayerPostRespawnEvent event) {
        Player player = event.getPlayer();
        players.get(player.getUniqueId()).filter(state -> state.role() == Role.GUARD)
                .ifPresent(state -> giveGuardGuns(player));
    }

    @EventHandler
    public void quit(PlayerQuitEvent event) {
        cancelReload(event.getPlayer());
    }

    /** Hits a player can see as damage, for the item's lore: "5", "18". */
    private static String format(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.format("%.1f", value);
    }

    /** What a shot can hit: anything alive but the shooter. Combat's rules decide if it hurts. */
    private static boolean isTarget(Entity entity, Player shooter) {
        return entity instanceof LivingEntity && !entity.equals(shooter);
    }
}
