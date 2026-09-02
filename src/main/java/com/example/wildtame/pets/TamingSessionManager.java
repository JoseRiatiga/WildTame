package com.example.wildtame.pets;

import com.example.wildtame.Messages;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTameEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Da feedback visual (barra de progreso + anillo de partículas) mientras un jugador domestica
 * un mob a la manera vanilla (alimentándolo o montándolo), y adopta el mob al sistema RPG en
 * cuanto el propio juego confirma la domesticación ({@link EntityTameEvent}) — pero SOLO si el
 * jugador llevaba el Collar de Domesticación en la mano secundaria. Sin el collar, toda
 * interacción con mobs domesticables es 100% vanilla, sin que el plugin la intercepte.
 */
public class TamingSessionManager implements Listener {

    private static final long TICK_INTERVAL = 4L;
    private static final long SESSION_TIMEOUT_MS = 15_000;
    private static final int EXPECTED_FEED_ATTEMPTS = 5;
    private static final double RING_RADIUS = 1.0;
    private static final double RING_ANGLE_STEP = 0.3;
    private static final int RING_POINTS = 10;

    private final PetManager petManager;
    private final Messages messages;
    private final NamespacedKey collarKey;
    private final Map<UUID, TamingSession> sessionsByEntity = new HashMap<>();

    public TamingSessionManager(JavaPlugin plugin, PetManager petManager, Messages messages) {
        this.petManager = petManager;
        this.messages = messages;
        this.collarKey = new NamespacedKey(plugin, "taming_collar");
    }

    public BukkitTask start(JavaPlugin plugin) {
        return Bukkit.getScheduler().runTaskTimer(plugin, this::tick, TICK_INTERVAL, TICK_INTERVAL);
    }

    public void stop() {
        for (TamingSession session : sessionsByEntity.values()) {
            session.bossBar.removeAll();
        }
        sessionsByEntity.clear();
    }

    public ItemStack createCollarItem() {
        ItemStack item = new ItemStack(petManager.collarMaterial());
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(messages.get("taming.collar-name"));
        meta.setLore(messages.getList("taming.collar-lore"));
        meta.getPersistentDataContainer().set(collarKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    private boolean isHoldingCollar(Player player) {
        return isCollar(player.getInventory().getItemInOffHand());
    }

    private boolean isCollar(ItemStack item) {
        return item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(collarKey, PersistentDataType.BYTE);
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Entity clicked = event.getRightClicked();
        if (!(clicked instanceof LivingEntity target) || !(clicked instanceof Tameable tameable)) {
            return;
        }
        if (tameable.isTamed() || petManager.isPetEntity(clicked)) {
            return;
        }
        PetType type = PetType.fromEntityType(clicked.getType());
        if (type == null) {
            return;
        }
        Player player = event.getPlayer();
        if (!isHoldingCollar(player) || petManager.getDataForOwner(player.getUniqueId()) != null) {
            return;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        boolean relevantInteraction = type.canRide() || item.getType() == type.getTamingItem();
        if (!relevantInteraction) {
            return;
        }

        String bossBarTitle = messages.get("taming.progress-bar-title", Map.of("type", type.getDefaultName()));
        TamingSession session = sessionsByEntity.computeIfAbsent(target.getUniqueId(),
                id -> new TamingSession(player, target, type, bossBarTitle));
        session.lastInteractionMillis = System.currentTimeMillis();
        if (!type.canRide()) {
            session.attempts++;
        }
    }

    @EventHandler
    public void onTame(EntityTameEvent event) {
        TamingSession session = sessionsByEntity.remove(event.getEntity().getUniqueId());
        if (session == null) {
            // El jugador domesticó este mob sin el collar puesto: es un tame vanilla normal,
            // no debe convertirse en una mascota RPG.
            return;
        }
        session.bossBar.removeAll();

        LivingEntity target = event.getEntity();
        if (!(event.getOwner() instanceof Player player) || petManager.isPetEntity(target)) {
            return;
        }
        PetType type = PetType.fromEntityType(target.getType());
        if (type == null) {
            return;
        }

        boolean registered = petManager.tameWildEntity(player, target, type);
        if (registered) {
            target.getWorld().spawnParticle(Particle.HAPPY_VILLAGER,
                    target.getLocation().add(0, 1, 0), 20, 0.5, 0.5, 0.5);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.4f);
            breakCollar(player);
        }
    }

    private void breakCollar(Player player) {
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (!isCollar(offHand)) {
            return;
        }
        offHand.setAmount(offHand.getAmount() - 1);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);
        player.sendMessage(messages.get("taming.collar-broke"));
    }

    @EventHandler
    public void onTargetDeath(EntityDeathEvent event) {
        endSession(event.getEntity().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Iterator<TamingSession> it = sessionsByEntity.values().iterator();
        while (it.hasNext()) {
            TamingSession session = it.next();
            if (session.player.getUniqueId().equals(event.getPlayer().getUniqueId())) {
                session.bossBar.removeAll();
                it.remove();
            }
        }
    }

    private void endSession(UUID entityUUID) {
        TamingSession session = sessionsByEntity.remove(entityUUID);
        if (session != null) {
            session.bossBar.removeAll();
        }
    }

    private void tick() {
        long now = System.currentTimeMillis();
        Iterator<TamingSession> it = sessionsByEntity.values().iterator();
        while (it.hasNext()) {
            TamingSession session = it.next();
            if (!session.target.isValid() || !session.player.isOnline()
                    || now - session.lastInteractionMillis > SESSION_TIMEOUT_MS) {
                session.bossBar.removeAll();
                it.remove();
                continue;
            }

            session.bossBar.setProgress(progressOf(session));
            spawnRing(session);
            session.angle += RING_ANGLE_STEP;
        }
    }

    private double progressOf(TamingSession session) {
        if (session.type.canRide() && session.target instanceof AbstractHorse horse) {
            int max = Math.max(1, horse.getMaxDomestication());
            return Math.min(1.0, horse.getDomestication() / (double) max);
        }
        return Math.min(0.9, session.attempts / (double) EXPECTED_FEED_ATTEMPTS);
    }

    private void spawnRing(TamingSession session) {
        Location center = session.target.getLocation().add(0, 0.1, 0);
        Particle particle = session.type.canRide() ? Particle.POOF : Particle.HAPPY_VILLAGER;
        for (int i = 0; i < RING_POINTS; i++) {
            double angle = session.angle + (2 * Math.PI * i / RING_POINTS);
            double x = center.getX() + RING_RADIUS * Math.cos(angle);
            double z = center.getZ() + RING_RADIUS * Math.sin(angle);
            center.getWorld().spawnParticle(particle, x, center.getY(), z, 0, 0, 0, 0, 0);
        }
    }

    private static class TamingSession {
        final Player player;
        final LivingEntity target;
        final PetType type;
        final BossBar bossBar;
        int attempts;
        double angle;
        long lastInteractionMillis;

        TamingSession(Player player, LivingEntity target, PetType type, String bossBarTitle) {
            this.player = player;
            this.target = target;
            this.type = type;
            this.lastInteractionMillis = System.currentTimeMillis();
            this.bossBar = Bukkit.createBossBar(bossBarTitle, BarColor.YELLOW, BarStyle.SEGMENTED_10);
            this.bossBar.addPlayer(player);
        }
    }
}
