package com.example.wildtame.pets;

import com.example.wildtame.Messages;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.ChestedHorse;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;

public class PetListener implements Listener {

    private final PetManager petManager;
    private final PetGUI petGUI;
    private final Messages messages;

    public PetListener(PetManager petManager, PetGUI petGUI, Messages messages) {
        this.petManager = petManager;
        this.petGUI = petGUI;
        this.messages = messages;
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Entity clicked = event.getRightClicked();
        if (!petManager.isPetEntity(clicked) || !(clicked instanceof LivingEntity petEntity)) {
            return;
        }

        Player player = event.getPlayer();

        // Ninguna mascota del plugin se puede renombrar con etiqueta vanilla, ni el dueño ni
        // nadie más — el nombre solo se cambia desde /mascota renombrar o el menú.
        if (player.getInventory().getItemInMainHand().getType() == Material.NAME_TAG) {
            event.setCancelled(true);
            return;
        }

        // Burro/mula/llama ya no llevan el cofre nativo de Minecraft — usan el inventario
        // propio del plugin (agachado + clic derecho), que sí sobrevive guardar/liberar.
        if (clicked instanceof ChestedHorse && player.getInventory().getItemInMainHand().getType() == Material.CHEST) {
            event.setCancelled(true);
            player.sendMessage(messages.get("pet.no-chest-allowed"));
            return;
        }

        UUID ownerUUID = petManager.getOwnerOf(clicked);
        if (ownerUUID == null || !ownerUUID.equals(player.getUniqueId())) {
            return;
        }

        PetData data = petManager.getDataForOwner(ownerUUID);
        if (data == null) {
            return;
        }

        ItemStack item = player.getInventory().getItemInMainHand();

        if (player.isSneaking() && item.getType() == Material.AIR) {
            event.setCancelled(true);
            petGUI.open(player, data);
            return;
        }

        if (petManager.isTreatItem(item)) {
            event.setCancelled(true);
            boolean used = petManager.givePetTreat(player, petEntity, data);
            if (used && player.getGameMode() != GameMode.CREATIVE) {
                item.setAmount(item.getAmount() - 1);
            }
            return;
        }

        if (item.getType() == data.type.getFoodItem()) {
            event.setCancelled(true);
            if (player.getGameMode() != GameMode.CREATIVE) {
                item.setAmount(item.getAmount() - 1);
            }
            petManager.feedPet(player, petEntity, data);
            return;
        }

        if (!player.isSneaking() && data.type.canRide() && !petEntity.getPassengers().contains(player)) {
            event.setCancelled(true);
            petEntity.addPassenger(player);
        }
    }

    @EventHandler
    public void onOwnerAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player) || !(event.getEntity() instanceof LivingEntity victim)) {
            return;
        }

        PetData data = petManager.getDataForOwner(player.getUniqueId());
        if (data == null) {
            return;
        }

        LivingEntity petEntity = petManager.getLivingPet(data);
        if (!(petEntity instanceof Mob pet) || petEntity.getWorld() != victim.getWorld()) {
            return;
        }

        double range = petManager.combatAssistRange();
        if (petEntity.getLocation().distanceSquared(victim.getLocation()) > range * range) {
            return;
        }

        // Fuerza el objetivo manualmente: la IA vanilla de lobos ignora creepers/ghasts por defecto.
        pet.setTarget(victim);
    }

    @EventHandler
    public void onOwnerDamaged(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player player) || !(event.getDamager() instanceof LivingEntity attacker)) {
            return;
        }

        PetData data = petManager.getDataForOwner(player.getUniqueId());
        if (data == null) {
            return;
        }

        LivingEntity petEntity = petManager.getLivingPet(data);
        if (!(petEntity instanceof Mob pet) || petEntity.getWorld() != attacker.getWorld()) {
            return;
        }

        double range = petManager.combatAssistRange();
        if (petEntity.getLocation().distanceSquared(attacker.getLocation()) > range * range) {
            return;
        }

        pet.setTarget(attacker);
    }

    /**
     * El dueño no puede dañar a su propia mascota (ni cuerpo a cuerpo ni con proyectiles),
     * para evitar bajarle vida sin querer en medio de un combate. Otros jugadores sí pueden.
     */
    @EventHandler
    public void onOwnerDamagesOwnPet(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity victim) || !petManager.isPetEntity(victim)) {
            return;
        }
        Player damager = resolvePlayerDamager(event.getDamager());
        if (damager == null) {
            return;
        }
        UUID ownerUUID = petManager.getOwnerOf(victim);
        if (ownerUUID != null && ownerUUID.equals(damager.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    /**
     * El gato le da al dueño una chance de esquivar por completo un golpe de un monstruo
     * cercano — ver {@link PetManager#rollCatDodge}.
     */
    @EventHandler
    public void onCatDodge(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player player) || !(event.getDamager() instanceof Monster)) {
            return;
        }
        PetData data = petManager.getDataForOwner(player.getUniqueId());
        if (data == null || data.type != PetType.CAT) {
            return;
        }
        LivingEntity petEntity = petManager.getLivingPet(data);
        if (petEntity == null || petEntity.getWorld() != player.getWorld()) {
            return;
        }
        double range = petManager.combatAssistRange();
        if (petEntity.getLocation().distanceSquared(player.getLocation()) > range * range) {
            return;
        }
        if (petManager.rollCatDodge(data)) {
            event.setCancelled(true);
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_CAT_HISS, 1f, 1.4f);
            player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation().add(0, 1, 0), 8);
        }
    }

    private Player resolvePlayerDamager(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }

    @EventHandler
    public void onPetAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof LivingEntity petEntity) || !petManager.isPetEntity(petEntity)) {
            return;
        }
        if (!(event.getEntity() instanceof Monster victim)) {
            return;
        }

        UUID ownerUUID = petManager.getOwnerOf(petEntity);
        if (ownerUUID == null) {
            return;
        }
        PetData data = petManager.getDataForOwner(ownerUUID);
        if (data == null) {
            return;
        }

        petManager.applyAoeIfEligible(data, petEntity, victim);
    }

    @EventHandler
    public void onPetDeath(EntityDeathEvent event) {
        Entity dead = event.getEntity();
        if (!petManager.isPetEntity(dead)) {
            return;
        }
        UUID ownerUUID = petManager.getOwnerOf(dead);
        if (ownerUUID == null) {
            return;
        }
        PetData data = petManager.getDataForOwner(ownerUUID);
        String petName = data != null ? data.name : messages.get("pet.fallback-name");
        petManager.handlePetDeath(ownerUUID, petName);
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity dead = event.getEntity();
        Player killer = dead.getKiller();
        if (killer == null) {
            return;
        }

        PetData data = petManager.getDataForOwner(killer.getUniqueId());
        if (data == null) {
            return;
        }

        LivingEntity petEntity = petManager.getLivingPet(data);
        if (petEntity == null || petEntity.getWorld() != dead.getWorld()) {
            return;
        }

        double range = petManager.combatAssistRange();
        if (petEntity.getLocation().distanceSquared(dead.getLocation()) > range * range) {
            return;
        }

        petManager.grantCombatAssistXp(killer, petEntity, data);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PetData data = petManager.getDataForOwner(player.getUniqueId());
        if (data != null) {
            World world = Bukkit.getWorld(data.worldName);
            if (world != null) {
                // Igual que callPet(): hay que forzar la carga del chunk antes de buscar la
                // entidad, si no, un jugador que se conecta lejos de donde dejó a su mascota
                // nunca la encuentra (el chunk todavía no está cargado) y el teletransporte
                // automático falla en silencio.
                Location petLoc = new Location(world, data.x, data.y, data.z);
                world.getChunkAtAsync(petLoc).thenAccept(chunk -> {
                    Entity petEntity = Bukkit.getEntity(data.petUUID);
                    if (petEntity != null) {
                        petEntity.teleport(player.getLocation());
                    }
                });
            }
        }

        // Aviso aparte, independiente de si tiene mascota activa: si alguna guardada ya
        // terminó su cooldown de revivido mientras estaba desconectado, avisarle — si no, se
        // entera recién cuando abre el menú por su cuenta.
        for (PetData stored : petManager.getStoredPets(player.getUniqueId())) {
            if (stored.dead && stored.remainingReviveMillis() <= 0) {
                player.sendMessage(messages.get("pet.ready-to-revive-on-join", Map.of("name", stored.name)));
            }
        }
    }

    /**
     * La mascota activa te sigue automáticamente al cambiar de mundo (portal al Nether/End,
     * u otro teletransporte). Sin esto, se queda del otro lado y hay que acordarse de usar
     * /wildtame llamar cada vez.
     */
    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        PetData data = petManager.getDataForOwner(player.getUniqueId());
        if (data == null) {
            return;
        }
        LivingEntity petEntity = petManager.getLivingPet(data);
        if (petEntity == null || petEntity.getWorld() == player.getWorld()) {
            return;
        }
        petEntity.teleport(player.getLocation());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        petManager.saveAll();
    }
}
