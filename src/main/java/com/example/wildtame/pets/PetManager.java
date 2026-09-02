package com.example.wildtame.pets;

import com.example.wildtame.Messages;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.AnimalTamer;
import org.bukkit.entity.Cat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Item;
import org.bukkit.entity.Llama;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Parrot;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Wolf;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class PetManager {

    // Constantes de "sensación" (radios, partículas, duraciones) — no valen tanto la pena
    // exponer en config.yml como las de balance de abajo, que sí se cargan desde ahí.
    private static final double AOE_DAMAGE = 2.0;
    private static final double AOE_RADIUS = 3.0;
    private static final double GROWL_RADIUS = 6.0;
    private static final double ITEM_COLLECT_RADIUS = 4.0;
    private static final double EVOLUTION_SCALE_BONUS = 0.15;
    private static final int AURA_DURATION_TICKS = 100;
    private static final double LLAMA_TAUNT_RADIUS = 8.0;
    private static final double PARROT_ALERT_RADIUS = 12.0;
    private static final int STARVING_SLOWNESS_AMPLIFIER = 1;
    private static final int STARVING_EFFECT_DURATION_TICKS = 80;
    private static final int STARVING_PARTICLE_COUNT = 6;

    // Números de balance, cargados desde config.yml en loadConfigValues() — así un admin
    // puede ajustarlos (y recargarlos con /wildtame admin reload) sin recompilar el plugin.
    private long callCooldownMs;
    private double xpPerTreat;
    private double xpPerKillAssist;
    private double combatAssistRange;
    private int evolutionLevel;
    private int aoeLevel;
    private int growlLevel;
    private int auraLevel;
    private int auraTier2Level;
    private double hungerDecayPerTick;
    private double starvingPenaltyFactor;
    private long reviveCooldownMs;
    private int catDodgeLevel;
    private double catDodgeMinChance;
    private double catDodgeMaxChance;
    private int llamaTauntLevel;
    private int parrotAlertLevel;
    private boolean backupsEnabled;
    private int backupsKeep;
    private long backupIntervalMinutes;
    private Material collarMaterial;
    private Material treatMaterial;

    private final JavaPlugin plugin;
    private final Messages messages;
    private final NamespacedKey isPetKey;
    private final NamespacedKey starvingHealthKey;
    private final NamespacedKey starvingDamageKey;
    private final NamespacedKey treatKey;
    private final File dataFile;
    private final Map<UUID, PetData> activeByOwner = new HashMap<>();
    private final Map<UUID, List<PetData>> storedByOwner = new HashMap<>();
    private final Map<UUID, Long> lastCallTime = new HashMap<>();

    public PetManager(JavaPlugin plugin, Messages messages) {
        this.plugin = plugin;
        this.messages = messages;
        this.isPetKey = new NamespacedKey(plugin, "is_rpg_pet");
        this.starvingHealthKey = new NamespacedKey(plugin, "pet_starving_health");
        this.starvingDamageKey = new NamespacedKey(plugin, "pet_starving_damage");
        this.treatKey = new NamespacedKey(plugin, "pet_treat");
        this.dataFile = new File(plugin.getDataFolder(), "pets.yml");
        loadConfigValues();
    }

    /**
     * Lee config.yml (creándolo con los valores por defecto si no existe) y aplica todos los
     * números de balance. Público para que /wildtame admin reload pueda llamarlo de nuevo sin
     * reiniciar el servidor.
     */
    public void loadConfigValues() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();

        PetData.MAX_LEVEL = config.getInt("max-level", 50);

        xpPerTreat = config.getDouble("xp.per-treat", 10);
        xpPerKillAssist = config.getDouble("xp.per-kill-assist", 8);
        combatAssistRange = config.getDouble("xp.combat-assist-range", 10);

        hungerDecayPerTick = config.getDouble("hunger.decay-per-tick", 0.5);
        starvingPenaltyFactor = config.getDouble("hunger.starving-penalty-factor", -0.25);

        callCooldownMs = config.getLong("cooldowns.call-seconds", 60) * 1000L;
        reviveCooldownMs = config.getLong("cooldowns.revive-minutes", 10) * 60_000L;

        evolutionLevel = config.getInt("abilities.evolution-level", 10);
        aoeLevel = config.getInt("abilities.aoe-level", 10);
        growlLevel = config.getInt("abilities.growl-level", 15);
        auraLevel = config.getInt("abilities.aura-level", 5);
        auraTier2Level = config.getInt("abilities.aura-tier2-level", 30);
        catDodgeLevel = config.getInt("abilities.cat-dodge-level", 10);
        catDodgeMinChance = config.getDouble("abilities.cat-dodge-min-chance", 0.10);
        catDodgeMaxChance = config.getDouble("abilities.cat-dodge-max-chance", 0.30);
        llamaTauntLevel = config.getInt("abilities.llama-taunt-level", 20);
        parrotAlertLevel = config.getInt("abilities.parrot-alert-level", 10);

        backupsEnabled = config.getBoolean("backups.enabled", true);
        backupsKeep = config.getInt("backups.keep", 24);
        backupIntervalMinutes = config.getLong("backups.interval-minutes", 60);

        collarMaterial = parseMaterial(config.getString("items.collar-material", "LEAD"), Material.LEAD);
        treatMaterial = parseMaterial(config.getString("items.treat-material", "SWEET_BERRIES"), Material.SWEET_BERRIES);

        for (PetType type : PetType.values()) {
            String path = "attribute-bonuses." + type.name() + ".";
            type.configureBonuses(
                    config.getDouble(path + "health", type.getMaxHealthBonus()),
                    config.getDouble(path + "damage", type.getMaxDamageBonus()),
                    config.getDouble(path + "speed", type.getMaxSpeedBonus()),
                    config.getDouble(path + "flying-speed", type.getMaxFlyingSpeedBonus()),
                    config.getDouble(path + "jump", type.getMaxJumpBonus()));
        }
    }

    private Material parseMaterial(String name, Material fallback) {
        Material material = Material.matchMaterial(name);
        if (material == null) {
            plugin.getLogger().warning("Material inválido \"" + name + "\" en config.yml, usando " + fallback.name() + ".");
            return fallback;
        }
        return material;
    }

    /** Ítem base del Collar de Domesticación (config: items.collar-material). */
    public Material collarMaterial() {
        return collarMaterial;
    }

    /** Ítem base de la Golosina de Mascota (config: items.treat-material). */
    public Material treatMaterial() {
        return treatMaterial;
    }

    public void load() {
        activeByOwner.clear();
        storedByOwner.clear();
        if (!dataFile.exists()) {
            return;
        }
        FileConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection playersSection = config.getConfigurationSection("players");
        if (playersSection == null) {
            return;
        }
        for (String ownerKey : playersSection.getKeys(false)) {
            ConfigurationSection section = playersSection.getConfigurationSection(ownerKey);
            if (section == null) {
                continue;
            }
            UUID ownerUUID = UUID.fromString(ownerKey);

            ConfigurationSection activeSection = section.getConfigurationSection("active");
            if (activeSection == null && section.contains("petUUID")) {
                // Formato antiguo: la sección del dueño ERA directamente los datos de la mascota activa.
                activeSection = section;
            }
            if (activeSection != null) {
                PetData active = readPetData(ownerUUID, activeSection);
                if (active != null) {
                    activeByOwner.put(ownerUUID, active);
                }
            }

            ConfigurationSection storedSection = section.getConfigurationSection("stored");
            if (storedSection != null) {
                List<PetData> stored = new ArrayList<>();
                for (String key : storedSection.getKeys(false)) {
                    ConfigurationSection s = storedSection.getConfigurationSection(key);
                    if (s == null) {
                        continue;
                    }
                    PetData data = readPetData(ownerUUID, s);
                    if (data != null) {
                        stored.add(data);
                    }
                }
                if (!stored.isEmpty()) {
                    storedByOwner.put(ownerUUID, stored);
                }
            }
        }
    }

    private PetData readPetData(UUID ownerUUID, ConfigurationSection section) {
        String petUUIDStr = section.getString("petUUID");
        if (petUUIDStr == null) {
            return null;
        }
        UUID petUUID = UUID.fromString(petUUIDStr);
        // Los guardados de antes de que existiera este campo no lo tienen: se les asigna
        // un id nuevo la primera vez que se cargan, ya que el original nunca se guardó.
        String idStr = section.getString("id");
        UUID id = idStr != null ? UUID.fromString(idStr) : UUID.randomUUID();
        PetType type = PetType.valueOf(section.getString("type", "WOLF"));
        String name = section.getString("name", type.getDefaultName());
        int level = section.getInt("level", 1);
        int xp = section.getInt("xp", 0);
        double hunger = section.getDouble("hunger", 100.0);
        boolean evolved = section.getBoolean("evolved", false);
        boolean starving = section.getBoolean("starving", false);
        boolean dead = section.getBoolean("dead", false);
        long reviveAt = section.getLong("reviveAt", 0L);
        String world = section.getString("world", "world");
        double x = section.getDouble("x");
        double y = section.getDouble("y");
        double z = section.getDouble("z");
        PetData data = new PetData(id, ownerUUID, petUUID, type, name, level, xp, hunger, evolved, starving, dead,
                reviveAt, world, x, y, z);
        // Guardados de antes de este campo no tienen nada de esto: quedan en sus valores por
        // defecto (0/null), y simplemente no se fuerzan al re-invocar (ver restoreBaseAttributes
        // y restoreAppearance).
        data.baseMaxHealth = section.getDouble("baseMaxHealth", 0);
        data.baseMovementSpeed = section.getDouble("baseMovementSpeed", 0);
        data.baseJumpStrength = section.getDouble("baseJumpStrength", 0);
        data.horseColor = section.getString("horseColor");
        data.horseStyle = section.getString("horseStyle");
        data.llamaColor = section.getString("llamaColor");
        data.llamaStrength = section.getInt("llamaStrength", 0);
        data.wolfVariantKey = section.getString("wolfVariantKey");
        data.wolfCollarColor = section.getString("wolfCollarColor");
        data.catTypeKey = section.getString("catTypeKey");
        data.catCollarColor = section.getString("catCollarColor");
        data.parrotVariant = section.getString("parrotVariant");
        data.inventoryContents = readInventoryContents(section);
        return data;
    }

    private ItemStack[] readInventoryContents(ConfigurationSection section) {
        int size = section.getInt("inventorySize", 0);
        ItemStack[] contents = new ItemStack[size];
        ConfigurationSection invSection = section.getConfigurationSection("inventory");
        if (invSection != null) {
            for (String key : invSection.getKeys(false)) {
                int index = Integer.parseInt(key);
                if (index >= 0 && index < contents.length) {
                    contents[index] = invSection.getItemStack(key);
                }
            }
        }
        return contents;
    }

    public void saveAll() {
        for (PetData data : activeByOwner.values()) {
            Entity petEntity = Bukkit.getEntity(data.petUUID);
            if (petEntity != null) {
                data.worldName = petEntity.getWorld().getName();
                data.x = petEntity.getLocation().getX();
                data.y = petEntity.getLocation().getY();
                data.z = petEntity.getLocation().getZ();
            }
            // No se recapturan los atributos base aquí: ya llevan encima el bonus por nivel,
            // así que sobreescribirlos "congelaría" ese bonus como si fuera el roll original.
            // La apariencia (ej. collar recoloreado a mano) sí puede cambiar en cualquier momento.
            if (petEntity instanceof LivingEntity livingPetEntity) {
                captureAppearance(data, livingPetEntity);
            }
        }

        FileConfiguration config = new YamlConfiguration();
        Set<UUID> owners = new HashSet<>();
        owners.addAll(activeByOwner.keySet());
        owners.addAll(storedByOwner.keySet());

        for (UUID ownerUUID : owners) {
            String base = "players." + ownerUUID;
            PetData active = activeByOwner.get(ownerUUID);
            if (active != null) {
                writePetData(config, base + ".active", active);
            }
            List<PetData> stored = storedByOwner.getOrDefault(ownerUUID, List.of());
            for (int i = 0; i < stored.size(); i++) {
                writePetData(config, base + ".stored." + i, stored.get(i));
            }
        }

        try {
            plugin.getDataFolder().mkdirs();
            File tempFile = new File(plugin.getDataFolder(), "pets.yml.tmp");
            config.save(tempFile);
            // Escritura atómica: si el proceso muere a mitad de guardar, el archivo real
            // (pets.yml) nunca queda a medio escribir/corrupto, en el peor caso se pierde
            // solo este guardado puntual y se conserva el anterior.
            try {
                Files.move(tempFile.toPath(), dataFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tempFile.toPath(), dataFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            plugin.getLogger().warning("No se pudo guardar pets.yml: " + e.getMessage());
        }
    }

    public long backupIntervalMinutes() {
        return backupIntervalMinutes;
    }

    /**
     * Copia pets.yml a backups/pets-&lt;fecha&gt;.yml y borra las copias más viejas que
     * superen backups.keep. Es una red de seguridad aparte de la escritura atómica de
     * saveAll(): esa protege contra caídas a mitad de guardado, esto protege contra errores
     * lógicos (un bug, un borrado accidental) dando algo a lo que volver.
     */
    public void backupPetsFile() {
        if (!backupsEnabled || !dataFile.exists()) {
            return;
        }
        try {
            File backupsDir = new File(plugin.getDataFolder(), "backups");
            backupsDir.mkdirs();
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            File backupFile = new File(backupsDir, "pets-" + timestamp + ".yml");
            Files.copy(dataFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

            File[] backups = backupsDir.listFiles((dir, name) -> name.startsWith("pets-") && name.endsWith(".yml"));
            if (backups != null && backups.length > backupsKeep) {
                Arrays.sort(backups, Comparator.comparing(File::getName));
                int excess = backups.length - backupsKeep;
                for (int i = 0; i < excess; i++) {
                    backups[i].delete();
                }
            }
        } catch (IOException e) {
            plugin.getLogger().warning("No se pudo hacer el respaldo de pets.yml: " + e.getMessage());
        }
    }

    private void writePetData(FileConfiguration config, String path, PetData data) {
        config.set(path + ".id", data.id.toString());
        config.set(path + ".petUUID", data.petUUID.toString());
        config.set(path + ".type", data.type.name());
        config.set(path + ".name", data.name);
        config.set(path + ".level", data.level);
        config.set(path + ".xp", data.xp);
        config.set(path + ".hunger", data.hunger);
        config.set(path + ".evolved", data.evolved);
        config.set(path + ".starving", data.starving);
        config.set(path + ".dead", data.dead);
        config.set(path + ".reviveAt", data.reviveAtMillis);
        config.set(path + ".world", data.worldName);
        config.set(path + ".x", data.x);
        config.set(path + ".y", data.y);
        config.set(path + ".z", data.z);
        config.set(path + ".baseMaxHealth", data.baseMaxHealth);
        config.set(path + ".baseMovementSpeed", data.baseMovementSpeed);
        config.set(path + ".baseJumpStrength", data.baseJumpStrength);
        config.set(path + ".horseColor", data.horseColor);
        config.set(path + ".horseStyle", data.horseStyle);
        config.set(path + ".llamaColor", data.llamaColor);
        config.set(path + ".llamaStrength", data.llamaStrength);
        config.set(path + ".wolfVariantKey", data.wolfVariantKey);
        config.set(path + ".wolfCollarColor", data.wolfCollarColor);
        config.set(path + ".catTypeKey", data.catTypeKey);
        config.set(path + ".catCollarColor", data.catCollarColor);
        config.set(path + ".parrotVariant", data.parrotVariant);
        config.set(path + ".inventorySize", data.inventoryContents.length);
        for (int i = 0; i < data.inventoryContents.length; i++) {
            ItemStack stack = data.inventoryContents[i];
            if (stack != null) {
                config.set(path + ".inventory." + i, stack);
            }
        }
    }

    /**
     * Crea y domestica una entidad nueva. Usado al sacar una mascota guardada
     * ({@link #summonStoredPet}); las mascotas nuevas se consiguen domesticando mobs
     * salvajes de forma vanilla, ver {@link TamingSessionManager}.
     */
    private LivingEntity spawnTamedEntity(Player owner, PetType type, Location loc) {
        Entity spawned = loc.getWorld().spawnEntity(loc, type.getEntityType());
        if (!(spawned instanceof Tameable tameable) || !(spawned instanceof LivingEntity petEntity)) {
            spawned.remove();
            return null;
        }
        tagAsPet(tameable, petEntity, owner);
        return petEntity;
    }

    /**
     * Registra como mascota RPG un mob que el jugador ya domesticó de forma vanilla
     * (alimentándolo o montándolo hasta que el juego confirma {@code EntityTameEvent}),
     * en vez de invocar uno nuevo. Conserva su posición y variante actuales.
     */
    public boolean tameWildEntity(Player owner, LivingEntity entity, PetType type) {
        if (activeByOwner.containsKey(owner.getUniqueId())) {
            owner.sendMessage(messages.get("pet.already-have-active"));
            return false;
        }
        if (!(entity instanceof Tameable tameable)) {
            return false;
        }
        tagAsPet(tameable, entity, owner);

        Location loc = entity.getLocation();
        PetData data = new PetData(UUID.randomUUID(), owner.getUniqueId(), entity.getUniqueId(), type,
                type.getDefaultName(),
                1, 0, 100.0, false, false, false, 0L, loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ());
        captureBaseAttributes(data, entity, type);
        captureAppearance(data, entity);
        activeByOwner.put(owner.getUniqueId(), data);
        updateDisplayName(data, entity);
        saveAll();
        owner.sendMessage(messages.get("pet.tamed", Map.of("name", data.name.toLowerCase())));
        return true;
    }

    private void tagAsPet(Tameable tameable, LivingEntity petEntity, Player owner) {
        tameable.setTamed(true);
        tameable.setOwner(owner);
        petEntity.setPersistent(true);
        petEntity.setCustomNameVisible(true);
        petEntity.getPersistentDataContainer().set(isPetKey, PersistentDataType.BYTE, (byte) 1);
        // Silla automática para que el dueño pueda dirigirla de inmediato. La llama no soporta
        // silla en absoluto (limitación vanilla), así que se excluye a propósito.
        if (petEntity instanceof AbstractHorse horse && !(horse instanceof Llama)) {
            horse.getInventory().setSaddle(new ItemStack(Material.SADDLE));
        }
    }

    public boolean isPetEntity(Entity entity) {
        return entity.getPersistentDataContainer().has(isPetKey, PersistentDataType.BYTE);
    }

    /**
     * Recorre las entidades cargadas de todos los mundos y elimina las que tienen la etiqueta
     * interna de mascota pero ya no corresponden a ninguna mascota activa registrada — restos
     * "fantasma" de una desincronización entre el mundo y pets.yml (ej. un kill forzado del
     * proceso a mitad de guardar). Solo mira entidades cargadas; una fantasma en un chunk
     * descargado no causa problemas hasta que se carga, momento en el que sí se detecta.
     */
    public int cleanupGhostPets() {
        Set<UUID> trackedUUIDs = new HashSet<>();
        for (PetData data : activeByOwner.values()) {
            trackedUUIDs.add(data.petUUID);
        }
        int removed = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (isPetEntity(entity) && !trackedUUIDs.contains(entity.getUniqueId())) {
                    entity.remove();
                    removed++;
                }
            }
        }
        return removed;
    }

    public UUID getOwnerOf(Entity entity) {
        if (entity instanceof Tameable tameable) {
            AnimalTamer tamer = tameable.getOwner();
            if (tamer instanceof OfflinePlayer offlinePlayer) {
                return offlinePlayer.getUniqueId();
            }
        }
        return null;
    }

    public PetData getDataForOwner(UUID ownerUUID) {
        return activeByOwner.get(ownerUUID);
    }

    public List<PetData> getStoredPets(UUID ownerUUID) {
        return storedByOwner.getOrDefault(ownerUUID, List.of());
    }

    /** La activa (si tiene) seguida de todas las guardadas de ese dueño. */
    public List<PetData> allPetsOf(UUID ownerUUID) {
        List<PetData> all = new ArrayList<>();
        PetData active = activeByOwner.get(ownerUUID);
        if (active != null) {
            all.add(active);
        }
        all.addAll(getStoredPets(ownerUUID));
        return all;
    }

    public Collection<PetData> getAllData() {
        return activeByOwner.values();
    }

    /** Cuenta todas las mascotas registradas de todos los jugadores (activas + guardadas). */
    public int totalRegisteredPets() {
        int total = activeByOwner.size();
        for (List<PetData> stored : storedByOwner.values()) {
            total += stored.size();
        }
        return total;
    }

    public List<PetData> topPets(int limit) {
        List<PetData> all = new ArrayList<>(activeByOwner.values());
        for (List<PetData> stored : storedByOwner.values()) {
            all.addAll(stored);
        }
        return all.stream()
                .sorted(Comparator.comparingInt((PetData d) -> d.level)
                        .thenComparingDouble(d -> d.xp)
                        .reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    public void sendTopPets(CommandSender sender, int limit) {
        List<PetData> top = topPets(limit);
        if (top.isEmpty()) {
            sender.sendMessage(messages.get("pet.no-pets-registered"));
            return;
        }
        String divider = "§8§m                                        ";
        sender.sendMessage(divider);
        sender.sendMessage(messages.get("pet.top-header", Map.of("limit", String.valueOf(limit))));
        int rank = 1;
        for (PetData data : top) {
            OfflinePlayer owner = Bukkit.getOfflinePlayer(data.ownerUUID);
            String ownerName = owner.getName() != null ? owner.getName() : "???";
            String rankLabel = switch (rank) {
                case 1 -> "§6★";
                case 2 -> "§7★";
                case 3 -> "§c★";
                default -> "§7#" + rank;
            };
            String evolvedMark = data.evolved ? messages.get("pet.top-evolved-mark") : "";
            String levelSuffix = data.isMaxLevel() ? messages.get("pet.top-max-suffix") : "";
            sender.sendMessage(messages.get("pet.top-entry", Map.of(
                    "rank", rankLabel,
                    "name", data.name,
                    "evolved-mark", evolvedMark,
                    "type", data.type.getDefaultName(),
                    "owner", ownerName,
                    "level", String.valueOf(data.level),
                    "max-suffix", levelSuffix)));
            rank++;
        }
        sender.sendMessage(divider);
    }

    public LivingEntity getLivingPet(PetData data) {
        Entity entity = Bukkit.getEntity(data.petUUID);
        return entity instanceof LivingEntity livingEntity ? livingEntity : null;
    }

    /** Nombre + nivel para mostrar en los menús (sin colores propios, el menú los pone). */
    public String displayName(PetData data) {
        String evolvedPrefix = data.evolved ? messages.get("nameplate.evolved-prefix") : "";
        return messages.get("nameplate.display-name-format", Map.of(
                "evolved", evolvedPrefix, "name", data.name, "level", String.valueOf(data.level)));
    }

    /**
     * Arma el letrero flotante sobre la cabeza de la mascota a partir de la plantilla de
     * messages_&lt;idioma&gt;.yml — Minecraft no soporta una segunda línea real bajo el
     * nombre, así que va todo en una sola línea armada por nameplate.format.
     */
    public void updateDisplayName(PetData data, LivingEntity petEntity) {
        int healthPercent = healthPercentOf(petEntity);
        String evolvedPrefix = data.evolved ? messages.get("nameplate.evolved-prefix") : "";
        String text = messages.get("nameplate.format", Map.of(
                "evolved", evolvedPrefix,
                "level", String.valueOf(data.level),
                "name", data.name,
                "health-icon", healthIcon(healthPercent),
                "hunger-icon", hungerIcon(data)));
        petEntity.setCustomName(text);
    }

    private String healthIcon(int healthPercent) {
        String key = healthPercent > 50 ? "nameplate.health-icon-high"
                : healthPercent > 20 ? "nameplate.health-icon-mid" : "nameplate.health-icon-low";
        return messages.get(key, Map.of("percent", String.valueOf(healthPercent)));
    }

    private String hungerIcon(PetData data) {
        String key = data.hunger > 50 ? "nameplate.hunger-icon-high"
                : data.hunger > 20 ? "nameplate.hunger-icon-mid" : "nameplate.hunger-icon-low";
        return messages.get(key, Map.of("percent", String.valueOf((int) data.hunger)));
    }

    private int healthPercentOf(LivingEntity petEntity) {
        AttributeInstance maxHealthAttr = petEntity.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealthAttr == null || maxHealthAttr.getValue() <= 0) {
            return 100;
        }
        return (int) Math.round(petEntity.getHealth() / maxHealthAttr.getValue() * 100);
    }

    /** Vida actual en % como texto, o "?" si la mascota no está activa/cargada (para PlaceholderAPI). */
    public String healthPercentText(PetData data) {
        LivingEntity petEntity = getLivingPet(data);
        return petEntity != null ? String.valueOf(healthPercentOf(petEntity)) : "?";
    }

    public void addXp(Player owner, LivingEntity petEntity, PetData data, double amount) {
        if (data.isMaxLevel()) {
            return;
        }
        data.xp += amount;
        while (!data.isMaxLevel() && data.xp >= data.xpToNextLevel()) {
            data.xp -= data.xpToNextLevel();
            data.level++;
            applyStatBonus(data, petEntity, 1);
            applyEvolutionIfNeeded(data, petEntity);
            updateDisplayName(data, petEntity);
            if (data.isMaxLevel()) {
                // Nivel máximo: fanfarria propia, distinta de una subida de nivel normal.
                owner.sendMessage(messages.get("pet.max-level-reached",
                        Map.of("name", data.name, "max-level", String.valueOf(PetData.MAX_LEVEL))));
                owner.playSound(owner.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                petEntity.getWorld().spawnParticle(Particle.FIREWORK, petEntity.getLocation().add(0, 1, 0), 40, 0.5, 0.5, 0.5, 0.05);
                petEntity.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, petEntity.getLocation().add(0, 1, 0), 20);
            } else {
                owner.sendMessage(messages.get("pet.leveled-up",
                        Map.of("name", data.name, "level", String.valueOf(data.level))));
                owner.playSound(owner.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                petEntity.getWorld().spawnParticle(Particle.HEART, petEntity.getLocation().add(0, 1, 0), 8);
            }
        }
        if (data.isMaxLevel()) {
            data.xp = 0;
        }
    }

    /**
     * Alimentar solo cura y resetea el hambre — ya no da XP (ver {@link #givePetTreat} para eso).
     */
    public void feedPet(Player owner, LivingEntity petEntity, PetData data) {
        AttributeInstance maxHealthAttr = petEntity.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealthAttr != null) {
            petEntity.setHealth(Math.min(maxHealthAttr.getValue(), petEntity.getHealth() + 4));
        }
        data.hunger = 100.0;
        if (data.starving) {
            data.starving = false;
            clearStarvingPenalty(petEntity);
        }
        saveAll();
        petEntity.getWorld().spawnParticle(Particle.HEART, petEntity.getLocation().add(0, 1, 0), 5);
        owner.sendMessage(messages.get("pet.fed", Map.of("name", data.name)));
    }

    /**
     * Crea la Golosina de Mascota: el único ítem que da XP directamente, igual para las 7
     * mascotas (a diferencia de la comida, que es distinta por tipo y solo cura/sacia). Se
     * obtiene crafteando, ver la receta registrada en {@link com.example.wildtame.WildTame}.
     */
    public ItemStack createTreatItem() {
        ItemStack item = new ItemStack(treatMaterial);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(messages.get("treat.name"));
        meta.setLore(messages.getList("treat.lore", Map.of("amount", String.valueOf((int) xpPerTreat))));
        meta.getPersistentDataContainer().set(treatKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isTreatItem(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(treatKey, PersistentDataType.BYTE);
    }

    /**
     * @return true si la golosina se usó de verdad (para que quien la dio sepa si debe
     * gastarla) — false si la mascota ya estaba en el nivel máximo.
     */
    public boolean givePetTreat(Player owner, LivingEntity petEntity, PetData data) {
        if (data.isMaxLevel()) {
            owner.sendMessage(messages.get("pet.already-max-level",
                    Map.of("name", data.name, "max-level", String.valueOf(PetData.MAX_LEVEL))));
            return false;
        }
        addXp(owner, petEntity, data, xpPerTreat);
        updateDisplayName(data, petEntity);
        saveAll();
        petEntity.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, petEntity.getLocation().add(0, 1, 0), 8);
        owner.playSound(owner.getLocation(), Sound.ENTITY_VILLAGER_YES, 1f, 1f);
        owner.sendMessage(messages.get("pet.treat-given", Map.of("name", data.name, "amount", String.valueOf((int) xpPerTreat))));
        return true;
    }

    public void grantCombatAssistXp(Player owner, LivingEntity petEntity, PetData data) {
        addXp(owner, petEntity, data, xpPerKillAssist);
        saveAll();
    }

    public double combatAssistRange() {
        return combatAssistRange;
    }

    public void applyAoeIfEligible(PetData data, LivingEntity petEntity, LivingEntity primaryVictim) {
        if (!data.type.canFight() || data.level < aoeLevel) {
            return;
        }
        for (Entity nearby : primaryVictim.getNearbyEntities(AOE_RADIUS, AOE_RADIUS, AOE_RADIUS)) {
            if (nearby instanceof Monster monster && !monster.getUniqueId().equals(primaryVictim.getUniqueId())) {
                monster.damage(AOE_DAMAGE, petEntity);
            }
        }
        primaryVictim.getWorld().spawnParticle(Particle.SWEEP_ATTACK, primaryVictim.getLocation().add(0, 1, 0), 3);
    }

    public void growlAbility(PetData data, LivingEntity petEntity) {
        if (!data.type.canFight() || data.level < growlLevel) {
            return;
        }
        boolean affectedAny = false;
        for (Entity nearby : petEntity.getNearbyEntities(GROWL_RADIUS, GROWL_RADIUS, GROWL_RADIUS)) {
            if (nearby instanceof Monster monster) {
                Vector direction = monster.getLocation().toVector()
                        .subtract(petEntity.getLocation().toVector())
                        .normalize()
                        .multiply(0.6)
                        .setY(0.2);
                monster.setVelocity(direction);
                monster.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1));
                affectedAny = true;
            }
        }
        if (affectedAny) {
            petEntity.getWorld().playSound(petEntity.getLocation(), Sound.ENTITY_WOLF_GROWL, 1f, 1f);
            petEntity.getWorld().spawnParticle(Particle.ANGRY_VILLAGER, petEntity.getLocation().add(0, 1, 0), 6);
        }
    }

    /**
     * Le contagia al dueño el efecto de poción propio del tipo de mascota (ver
     * {@link PetType#getAuraEffect()}) mientras esté activa y cerca — se desbloquea en
     * {@link #auraLevel} y sube de amplificador en {@link #auraTier2Level}. Se reaplica
     * cada tick de {@link PetTickTask} con una duración más larga que el intervalo del tick,
     * así que se corta solo apenas el jugador se aleja o la mascota deja de estar activa.
     */
    public void applyOwnerAura(PetData data, LivingEntity petEntity) {
        if (data.level < auraLevel || data.starving) {
            return;
        }
        Player owner = Bukkit.getPlayer(data.ownerUUID);
        if (owner == null || !owner.getWorld().equals(petEntity.getWorld())) {
            return;
        }
        double range = combatAssistRange();
        if (petEntity.getLocation().distanceSquared(owner.getLocation()) > range * range) {
            return;
        }
        int amplifier = data.level >= auraTier2Level ? 1 : 0;
        owner.addPotionEffect(new PotionEffect(data.type.getAuraEffect(), AURA_DURATION_TICKS, amplifier, true, false, true));
    }

    /**
     * Probabilidad de esquivar por completo un golpe de un monstruo cerca del dueño — sube
     * en línea recta de {@link #catDodgeMinChance} en {@link #catDodgeLevel} hasta
     * {@link #catDodgeMaxChance} en el nivel máximo. La detecta y aplica {@link PetListener}.
     */
    public boolean rollCatDodge(PetData data) {
        if (data.type != PetType.CAT || data.level < catDodgeLevel) {
            return false;
        }
        double progress = (double) (data.level - catDodgeLevel) / Math.max(1, PetData.MAX_LEVEL - catDodgeLevel);
        double chance = catDodgeMinChance + (catDodgeMaxChance - catDodgeMinChance) * Math.min(1, progress);
        return ThreadLocalRandom.current().nextDouble() < chance;
    }

    /**
     * A nivel alto, la llama atrae hacia ella la agresividad de los monstruos cercanos —
     * encaja con que ya escupe a las amenazas en vanilla.
     */
    public void llamaTaunt(PetData data, LivingEntity petEntity) {
        if (data.type != PetType.LLAMA || data.level < llamaTauntLevel) {
            return;
        }
        boolean affectedAny = false;
        for (Entity nearby : petEntity.getNearbyEntities(LLAMA_TAUNT_RADIUS, LLAMA_TAUNT_RADIUS, LLAMA_TAUNT_RADIUS)) {
            if (nearby instanceof Monster monster) {
                monster.setTarget(petEntity);
                affectedAny = true;
            }
        }
        if (affectedAny) {
            petEntity.getWorld().playSound(petEntity.getLocation(), Sound.ENTITY_LLAMA_ANGRY, 1f, 1f);
            petEntity.getWorld().spawnParticle(Particle.ANGRY_VILLAGER, petEntity.getLocation().add(0, 1, 0), 4);
        }
    }

    /** El loro avisa al dueño en la action bar si hay un monstruo cerca — un radar temprano. */
    public void parrotAlert(PetData data, LivingEntity petEntity) {
        if (data.type != PetType.PARROT || data.level < parrotAlertLevel) {
            return;
        }
        Player owner = Bukkit.getPlayer(data.ownerUUID);
        if (owner == null) {
            return;
        }
        boolean dangerNearby = false;
        for (Entity nearby : petEntity.getNearbyEntities(PARROT_ALERT_RADIUS, PARROT_ALERT_RADIUS, PARROT_ALERT_RADIUS)) {
            if (nearby instanceof Monster) {
                dangerNearby = true;
                break;
            }
        }
        if (dangerNearby) {
            owner.sendActionBar(LegacyComponentSerializer.legacySection()
                    .deserialize(messages.get("pet.parrot-alert", Map.of("name", data.name))));
        }
    }

    public void decayHunger(PetData data, LivingEntity petEntity) {
        if (data.hunger <= 0) {
            return;
        }
        data.hunger = Math.max(0, data.hunger - hungerDecayPerTick);
        if (data.hunger <= 0 && !data.starving) {
            data.starving = true;
            applyStarvingPenalty(petEntity);
        }
    }

    /**
     * Debuff visible de hambre: la penalización de {@link #applyStarvingPenalty} solo toca
     * atributos (invisible a simple vista), así que mientras siga hambrienta se le reaplica
     * Lentitud + partículas cada tick — igual que el aura del dueño, se corta solo apenas deja
     * de estar hambrienta porque dejamos de refrescarlo y el efecto expira por su cuenta.
     */
    public void applyStarvingEffects(PetData data, LivingEntity petEntity) {
        if (!data.starving) {
            return;
        }
        petEntity.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, STARVING_EFFECT_DURATION_TICKS,
                STARVING_SLOWNESS_AMPLIFIER, true, false, true));
        petEntity.getWorld().spawnParticle(Particle.SMOKE, petEntity.getLocation().add(0, 1, 0),
                STARVING_PARTICLE_COUNT, 0.3, 0.3, 0.3, 0.01);
    }

    public void collectNearbyItems(PetData data, LivingEntity petEntity) {
        Player owner = Bukkit.getPlayer(data.ownerUUID);
        if (owner == null || !owner.getWorld().equals(petEntity.getWorld())) {
            return;
        }
        for (Entity nearby : petEntity.getNearbyEntities(ITEM_COLLECT_RADIUS, ITEM_COLLECT_RADIUS, ITEM_COLLECT_RADIUS)) {
            if (!(nearby instanceof Item item)) {
                continue;
            }
            ItemStack stack = item.getItemStack();
            Map<Integer, ItemStack> leftover = owner.getInventory().addItem(stack);
            if (leftover.isEmpty()) {
                item.remove();
                owner.getWorld().playSound(owner.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.7f, 1.2f);
            }
        }
    }

    private void applyEvolutionIfNeeded(PetData data, LivingEntity petEntity) {
        if (data.evolved || data.level < evolutionLevel) {
            return;
        }
        data.evolved = true;
        AttributeInstance scaleAttr = petEntity.getAttribute(Attribute.SCALE);
        if (scaleAttr != null) {
            scaleAttr.setBaseValue(scaleAttr.getBaseValue() + EVOLUTION_SCALE_BONUS);
        }
        petEntity.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, petEntity.getLocation().add(0, 1, 0), 25);
    }

    /**
     * Aplica bonificaciones de stats acumuladas por {@code levels} niveles, repartiendo en
     * partes iguales el total de cada tipo (ver {@link PetType}) a lo largo de
     * {@link PetData#MAX_LEVEL} niveles. Cada tipo tiene su propio rol: el lobo pelea,
     * gato/loro son ágiles (el loro por aire), los montables se mueven mejor, y la llama es
     * la más resistente pero apenas gana movilidad porque no se puede dirigir.
     */
    private void applyStatBonus(PetData data, LivingEntity petEntity, int levels) {
        if (levels == 0) {
            return;
        }
        PetType type = data.type;
        applyAttributeIncrement(petEntity, Attribute.MAX_HEALTH, type.getMaxHealthBonus(), levels);
        applyAttributeIncrement(petEntity, Attribute.ATTACK_DAMAGE, type.getMaxDamageBonus(), levels);
        applyAttributeIncrement(petEntity, Attribute.MOVEMENT_SPEED, type.getMaxSpeedBonus(), levels);
        applyAttributeIncrement(petEntity, Attribute.FLYING_SPEED, type.getMaxFlyingSpeedBonus(), levels);
        applyAttributeIncrement(petEntity, Attribute.JUMP_STRENGTH, type.getMaxJumpBonus(), levels);

        AttributeInstance healthAttr = petEntity.getAttribute(Attribute.MAX_HEALTH);
        if (healthAttr != null) {
            petEntity.setHealth(healthAttr.getValue());
        }
    }

    /** Reparte {@code totalBonus} en partes iguales a lo largo de {@code MAX_LEVEL - 1} subidas. */
    private void applyAttributeIncrement(LivingEntity petEntity, Attribute attribute, double totalBonus, int levels) {
        if (totalBonus == 0) {
            return;
        }
        AttributeInstance attr = petEntity.getAttribute(attribute);
        if (attr == null) {
            return;
        }
        double perLevel = totalBonus / (PetData.MAX_LEVEL - 1);
        attr.setBaseValue(attr.getBaseValue() + perLevel * levels);
    }

    /**
     * Al sacar una mascota guardada se crea una entidad nueva (la anterior se eliminó del mundo
     * al guardarla), así que hay que reconstruir sus bonificaciones acumuladas por nivel/evolución
     * en vez de partir de una entidad vanilla sin modificar.
     */
    private void reapplyPersistedStats(PetData data, LivingEntity petEntity) {
        restoreBaseAttributes(data, petEntity);
        restoreAppearance(data, petEntity);
        applyStatBonus(data, petEntity, data.level - 1);
        if (data.evolved) {
            AttributeInstance scaleAttr = petEntity.getAttribute(Attribute.SCALE);
            if (scaleAttr != null) {
                scaleAttr.setBaseValue(scaleAttr.getBaseValue() + EVOLUTION_SCALE_BONUS);
            }
        }
        if (data.starving) {
            applyStarvingPenalty(petEntity);
        }
    }

    /**
     * Guarda el roll "de nacimiento" (vida/velocidad/salto) que Minecraft le asignó al azar a
     * esta mascota en concreto, para poder restaurarlo exacto si más adelante se guarda y se
     * vuelve a sacar (que crea una entidad nueva con sus propios valores aleatorios).
     */
    private void captureBaseAttributes(PetData data, LivingEntity entity, PetType type) {
        data.baseMaxHealth = attributeBaseValue(entity, Attribute.MAX_HEALTH);
        if (type.canRide()) {
            data.baseMovementSpeed = attributeBaseValue(entity, Attribute.MOVEMENT_SPEED);
            data.baseJumpStrength = attributeBaseValue(entity, Attribute.JUMP_STRENGTH);
        }
    }

    private double attributeBaseValue(LivingEntity entity, Attribute attribute) {
        AttributeInstance attr = entity.getAttribute(attribute);
        return attr != null ? attr.getBaseValue() : 0;
    }

    private void restoreBaseAttributes(PetData data, LivingEntity petEntity) {
        setBaseValueIfCaptured(petEntity, Attribute.MAX_HEALTH, data.baseMaxHealth);
        setBaseValueIfCaptured(petEntity, Attribute.MOVEMENT_SPEED, data.baseMovementSpeed);
        setBaseValueIfCaptured(petEntity, Attribute.JUMP_STRENGTH, data.baseJumpStrength);
    }

    private void setBaseValueIfCaptured(LivingEntity petEntity, Attribute attribute, double capturedValue) {
        if (capturedValue <= 0) {
            return;
        }
        AttributeInstance attr = petEntity.getAttribute(attribute);
        if (attr != null) {
            attr.setBaseValue(capturedValue);
        }
    }

    /**
     * Guarda color/variante/etc. según el tipo concreto de entidad, para que una mascota
     * guardada mantenga su apariencia exacta al volver a invocarla (si no, Minecraft le da una
     * apariencia aleatoria nueva a la entidad recién creada).
     */
    private void captureAppearance(PetData data, LivingEntity entity) {
        if (entity instanceof Horse horse) {
            data.horseColor = horse.getColor().name();
            data.horseStyle = horse.getStyle().name();
        } else if (entity instanceof Llama llama) {
            data.llamaColor = llama.getColor().name();
            data.llamaStrength = llama.getStrength();
        } else if (entity instanceof Wolf wolf) {
            data.wolfVariantKey = wolf.getVariant().getKey().toString();
            data.wolfCollarColor = wolf.getCollarColor().name();
        } else if (entity instanceof Cat cat) {
            data.catTypeKey = cat.getCatType().getKey().toString();
            data.catCollarColor = cat.getCollarColor().name();
        } else if (entity instanceof Parrot parrot) {
            data.parrotVariant = parrot.getVariant().name();
        }
    }

    private void restoreAppearance(PetData data, LivingEntity petEntity) {
        if (petEntity instanceof Horse horse) {
            if (data.horseColor != null) {
                horse.setColor(Horse.Color.valueOf(data.horseColor));
            }
            if (data.horseStyle != null) {
                horse.setStyle(Horse.Style.valueOf(data.horseStyle));
            }
        } else if (petEntity instanceof Llama llama) {
            if (data.llamaColor != null) {
                llama.setColor(Llama.Color.valueOf(data.llamaColor));
            }
            if (data.llamaStrength > 0) {
                llama.setStrength(data.llamaStrength);
            }
        } else if (petEntity instanceof Wolf wolf) {
            if (data.wolfVariantKey != null) {
                Wolf.Variant variant = RegistryAccess.registryAccess().getRegistry(RegistryKey.WOLF_VARIANT)
                        .get(NamespacedKey.fromString(data.wolfVariantKey));
                if (variant != null) {
                    wolf.setVariant(variant);
                }
            }
            if (data.wolfCollarColor != null) {
                wolf.setCollarColor(DyeColor.valueOf(data.wolfCollarColor));
            }
        } else if (petEntity instanceof Cat cat) {
            if (data.catTypeKey != null) {
                Cat.Type type = RegistryAccess.registryAccess().getRegistry(RegistryKey.CAT_VARIANT)
                        .get(NamespacedKey.fromString(data.catTypeKey));
                if (type != null) {
                    cat.setCatType(type);
                }
            }
            if (data.catCollarColor != null) {
                cat.setCollarColor(DyeColor.valueOf(data.catCollarColor));
            }
        } else if (petEntity instanceof Parrot parrot && data.parrotVariant != null) {
            parrot.setVariant(Parrot.Variant.valueOf(data.parrotVariant));
        }
    }

    private void applyStarvingPenalty(LivingEntity petEntity) {
        addModifierIfAbsent(petEntity, Attribute.MAX_HEALTH, starvingHealthKey);
        addModifierIfAbsent(petEntity, Attribute.ATTACK_DAMAGE, starvingDamageKey);
    }

    private void clearStarvingPenalty(LivingEntity petEntity) {
        removeModifier(petEntity, Attribute.MAX_HEALTH, starvingHealthKey);
        removeModifier(petEntity, Attribute.ATTACK_DAMAGE, starvingDamageKey);
    }

    private void addModifierIfAbsent(LivingEntity petEntity, Attribute attribute, NamespacedKey key) {
        AttributeInstance attr = petEntity.getAttribute(attribute);
        if (attr == null || attr.getModifier(key) != null) {
            return;
        }
        attr.addModifier(new AttributeModifier(key, starvingPenaltyFactor, AttributeModifier.Operation.ADD_SCALAR));
    }

    private void removeModifier(LivingEntity petEntity, Attribute attribute, NamespacedKey key) {
        AttributeInstance attr = petEntity.getAttribute(attribute);
        if (attr != null) {
            attr.removeModifier(key);
        }
    }

    /**
     * Guarda la mascota activa (la quita del mundo pero conserva nivel/XP/nombre) en vez de borrarla,
     * para poder recuperarla luego con {@link #summonStoredPet}.
     */
    public boolean storePet(Player owner) {
        PetData data = activeByOwner.remove(owner.getUniqueId());
        if (data == null) {
            owner.sendMessage(messages.get("pet.no-active-pet-to-store"));
            return false;
        }
        LivingEntity petEntity = getLivingPet(data);
        if (petEntity != null) {
            petEntity.remove();
        }
        storedByOwner.computeIfAbsent(owner.getUniqueId(), k -> new ArrayList<>()).add(data);
        saveAll();
        owner.sendMessage(messages.get("pet.stored", Map.of("name", data.name)));
        return true;
    }

    public boolean summonStoredPet(Player owner, String name) {
        if (activeByOwner.containsKey(owner.getUniqueId())) {
            owner.sendMessage(messages.get("pet.already-have-active"));
            return false;
        }
        List<PetData> stored = storedByOwner.get(owner.getUniqueId());
        if (stored == null || stored.isEmpty()) {
            owner.sendMessage(messages.get("pet.no-pets-stored"));
            return false;
        }
        PetData found = stored.stream()
                .filter(candidate -> candidate.name.equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
        if (found == null) {
            owner.sendMessage(messages.get("pet.not-found-by-name"));
            return false;
        }
        if (found.dead) {
            long remaining = found.remainingReviveMillis();
            if (remaining > 0) {
                owner.sendMessage(messages.get("pet.still-reviving", Map.of("time", PetData.formatDuration(remaining))));
                return false;
            }
        }

        Location loc = owner.getLocation();
        LivingEntity petEntity = spawnTamedEntity(owner, found.type, loc);
        if (petEntity == null) {
            owner.sendMessage(messages.get("pet.could-not-summon"));
            return false;
        }

        stored.remove(found);
        if (stored.isEmpty()) {
            storedByOwner.remove(owner.getUniqueId());
        }

        boolean wasDead = found.dead;
        found.dead = false;
        found.reviveAtMillis = 0L;
        found.petUUID = petEntity.getUniqueId();
        found.worldName = loc.getWorld().getName();
        found.x = loc.getX();
        found.y = loc.getY();
        found.z = loc.getZ();

        reapplyPersistedStats(found, petEntity);
        updateDisplayName(found, petEntity);

        activeByOwner.put(owner.getUniqueId(), found);
        saveAll();
        if (wasDead) {
            owner.sendMessage(messages.get("pet.revived", Map.of("name", found.name)));
            return true;
        }
        owner.sendMessage(messages.get("pet.summoned", Map.of("name", found.name)));
        return true;
    }

    /**
     * Libera para siempre a la mascota activa: pierde el tameo y el dueño (vuelve a ser un mob
     * salvaje normal) y desaparece del registro. A diferencia de {@link #storePet}, no se puede
     * recuperar después con {@link #summonStoredPet}.
     */
    public boolean releasePet(Player owner) {
        PetData data = activeByOwner.get(owner.getUniqueId());
        if (data == null) {
            owner.sendMessage(messages.get("pet.no-active-pet-to-release"));
            return false;
        }

        LivingEntity petEntity = getLivingPet(data);
        if (petEntity != null) {
            // El inventario de la mascota es solo datos nuestros, no del mundo — si no lo
            // tiramos aquí, se pierde en silencio junto con el PetData al liberarla.
            for (ItemStack stack : data.inventoryContents) {
                if (stack != null) {
                    petEntity.getWorld().dropItemNaturally(petEntity.getLocation(), stack);
                }
            }
            clearStarvingPenalty(petEntity);
            petEntity.getPersistentDataContainer().remove(isPetKey);
            petEntity.setCustomName(null);
            petEntity.setCustomNameVisible(false);
            petEntity.setPersistent(false);
            if (petEntity instanceof Tameable tameable) {
                tameable.setOwner(null);
                tameable.setTamed(false);
            }
            if (petEntity instanceof AbstractHorse horse) {
                horse.getInventory().setSaddle(null);
                horse.setDomestication(0);
            }
        }

        activeByOwner.remove(owner.getUniqueId());
        saveAll();
        owner.sendMessage(messages.get("pet.released", Map.of("name", data.name)));
        return true;
    }

    /**
     * En vez de descartar la mascota, la mueve a la colección guardada marcada como "muerta"
     * con un cooldown de revivido — se puede recuperar con {@link #summonStoredPet} una vez
     * pasado ese tiempo, conservando su nivel y XP.
     */
    public void handlePetDeath(UUID ownerUUID, String petName) {
        PetData data = activeByOwner.remove(ownerUUID);
        if (data == null) {
            return;
        }
        data.dead = true;
        data.reviveAtMillis = System.currentTimeMillis() + reviveCooldownMs;
        storedByOwner.computeIfAbsent(ownerUUID, k -> new ArrayList<>()).add(data);
        saveAll();
        Player owner = Bukkit.getPlayer(ownerUUID);
        if (owner != null) {
            owner.sendMessage(messages.get("pet.died", Map.of(
                    "name", petName, "time", PetData.formatDuration(reviveCooldownMs))));
        }
    }

    public void renamePet(Player owner, PetData data, String newName) {
        PetData collision = findPetByName(owner.getUniqueId(), newName);
        if (collision != null && collision != data) {
            owner.sendMessage(messages.get("pet.name-taken", Map.of("name", newName)));
            return;
        }
        data.name = newName;
        LivingEntity petEntity = getLivingPet(data);
        if (petEntity != null) {
            updateDisplayName(data, petEntity);
        }
        saveAll();
        owner.sendMessage(messages.get("pet.renamed", Map.of("name", newName)));
    }

    /**
     * Busca por nombre entre la mascota activa y las guardadas de un dueño (case-insensitive).
     */
    public PetData findPetByName(UUID ownerUUID, String name) {
        PetData active = activeByOwner.get(ownerUUID);
        if (active != null && active.name.equalsIgnoreCase(name)) {
            return active;
        }
        for (PetData stored : getStoredPets(ownerUUID)) {
            if (stored.name.equalsIgnoreCase(name)) {
                return stored;
            }
        }
        return null;
    }

    /**
     * Herramienta de administrador: fija el nivel de una mascota concreta de un jugador
     * (activa o guardada, buscada por nombre), recalculando sus bonificaciones de stats
     * para que queden consistentes con ese nivel. Devuelve false si no se encontró la mascota.
     */
    public boolean setPetLevel(UUID ownerUUID, String petName, int newLevel) {
        PetData data = findPetByName(ownerUUID, petName);
        if (data == null) {
            return false;
        }
        newLevel = Math.min(PetData.MAX_LEVEL, Math.max(1, newLevel));
        int delta = newLevel - data.level;
        LivingEntity petEntity = getLivingPet(data);
        if (petEntity != null && delta != 0) {
            applyStatBonus(data, petEntity, delta);
        }
        data.level = newLevel;
        data.xp = 0;
        if (petEntity != null) {
            applyEvolutionIfNeeded(data, petEntity);
            updateDisplayName(data, petEntity);
        }
        saveAll();
        return true;
    }

    public void callPet(Player owner, PetData data) {
        long now = System.currentTimeMillis();
        Long last = lastCallTime.get(owner.getUniqueId());
        if (last != null && now - last < callCooldownMs) {
            long remainingSeconds = (callCooldownMs - (now - last)) / 1000;
            owner.sendMessage(messages.get("pet.call-cooldown", Map.of("seconds", String.valueOf(remainingSeconds))));
            return;
        }
        lastCallTime.put(owner.getUniqueId(), now);

        World world = Bukkit.getWorld(data.worldName);
        if (world == null) {
            owner.sendMessage(messages.get("pet.world-not-found"));
            return;
        }
        Location petLoc = new Location(world, data.x, data.y, data.z);
        world.getChunkAtAsync(petLoc).thenAccept(chunk -> {
            Entity found = Bukkit.getEntity(data.petUUID);
            if (found != null) {
                found.teleport(owner.getLocation());
                owner.sendMessage(messages.get("pet.call-success"));
            } else {
                owner.sendMessage(messages.get("pet.call-not-found"));
            }
        });
    }
}
