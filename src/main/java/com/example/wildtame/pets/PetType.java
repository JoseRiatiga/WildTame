package com.example.wildtame.pets;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.potion.PotionEffectType;

public enum PetType {

    // tamingItem = lo que Minecraft realmente exige para el intento de doma vanilla.
    // foodItem = lo que usamos nosotros para alimentar/curar/dar XP a la mascota YA domesticada.
    // No siempre son el mismo ítem (ej. el lobo se doma con hueso, pero se alimenta con carne).
    //
    // Los 5 parámetros numéricos son el total que gana cada atributo entre nivel 1 y el nivel
    // máximo (ver PetData.MAX_LEVEL) — cada tipo tiene su propio "rol": el lobo pelea, el gato
    // y el loro son ágiles/veloces (el loro por aire), los montables se mueven mejor, y la
    // llama es la más resistente pero no se puede dirigir, así que no gana movilidad real.
    // orden: maxHealthBonus, maxDamageBonus, maxSpeedBonus, maxFlyingSpeedBonus, maxJumpBonus
    //
    // auraEffect es el efecto de poción que le contagia al dueño mientras la mascota está
    // activa y cerca (ver PetManager#applyOwnerAura) — sigue el mismo "rol" que sus atributos.
    WOLF(EntityType.WOLF, "Lobo", Material.BONE, Material.BEEF, Material.WOLF_SPAWN_EGG, true, false,
            30, 6, 0.06, 0, 0, PotionEffectType.STRENGTH),
    CAT(EntityType.CAT, "Gato", Material.COD, Material.COD, Material.CAT_SPAWN_EGG, false, false,
            20, 0, 0.08, 0, 0, PotionEffectType.LUCK),
    PARROT(EntityType.PARROT, "Loro", Material.WHEAT_SEEDS, Material.WHEAT_SEEDS, Material.PARROT_SPAWN_EGG, false, false,
            14, 0, 0, 0.6, 0, PotionEffectType.SLOW_FALLING),
    HORSE(EntityType.HORSE, "Caballo", null, Material.GOLDEN_CARROT, Material.HORSE_SPAWN_EGG, false, true,
            25, 0, 0.20, 0, 0.8, PotionEffectType.SPEED),
    DONKEY(EntityType.DONKEY, "Burro", null, Material.GOLDEN_CARROT, Material.DONKEY_SPAWN_EGG, false, true,
            30, 0, 0.15, 0, 0.6, PotionEffectType.HASTE),
    MULE(EntityType.MULE, "Mula", null, Material.GOLDEN_CARROT, Material.MULE_SPAWN_EGG, false, true,
            30, 0, 0.15, 0, 0.6, PotionEffectType.HASTE),
    LLAMA(EntityType.LLAMA, "Llama", null, Material.GOLDEN_CARROT, Material.LLAMA_SPAWN_EGG, false, true,
            35, 0, 0.03, 0, 0, PotionEffectType.RESISTANCE);

    private final EntityType entityType;
    private final String defaultName;
    private final Material tamingItem;
    private final Material foodItem;
    private final Material iconMaterial;
    private final boolean canFight;
    private final boolean canRide;
    // No son final: se pueden sobreescribir desde config.yml (ver configureBonuses, llamado
    // por PetManager#loadConfigValues), para poder ajustar el balance sin recompilar.
    private double maxHealthBonus;
    private double maxDamageBonus;
    private double maxSpeedBonus;
    private double maxFlyingSpeedBonus;
    private double maxJumpBonus;
    private final PotionEffectType auraEffect;

    PetType(EntityType entityType, String defaultName, Material tamingItem, Material foodItem, Material iconMaterial,
            boolean canFight, boolean canRide, double maxHealthBonus, double maxDamageBonus, double maxSpeedBonus,
            double maxFlyingSpeedBonus, double maxJumpBonus, PotionEffectType auraEffect) {
        this.entityType = entityType;
        this.defaultName = defaultName;
        this.tamingItem = tamingItem;
        this.foodItem = foodItem;
        this.iconMaterial = iconMaterial;
        this.canFight = canFight;
        this.canRide = canRide;
        this.maxHealthBonus = maxHealthBonus;
        this.maxDamageBonus = maxDamageBonus;
        this.maxSpeedBonus = maxSpeedBonus;
        this.maxFlyingSpeedBonus = maxFlyingSpeedBonus;
        this.maxJumpBonus = maxJumpBonus;
        this.auraEffect = auraEffect;
    }

    public EntityType getEntityType() {
        return entityType;
    }

    public String getDefaultName() {
        return defaultName;
    }

    /**
     * Ítem que Minecraft exige sostener para intentar la doma vanilla (null si no aplica,
     * como los de la familia caballo, que se doman montándolos en vez de alimentándolos).
     */
    public Material getTamingItem() {
        return tamingItem;
    }

    /**
     * Ítem para alimentar/curar/dar XP a la mascota una vez ya domesticada.
     */
    public Material getFoodItem() {
        return foodItem;
    }

    public Material getIconMaterial() {
        return iconMaterial;
    }

    public boolean canFight() {
        return canFight;
    }

    /**
     * Nota: la Llama es "canRide" (se puede montar) pero, a diferencia de Caballo/Burro/Mula,
     * el propio juego no permite dirigirla con silla de montar — es una limitación vanilla, no un bug.
     */
    public boolean canRide() {
        return canRide;
    }

    /** Bonificación total de vida máxima entre nivel 1 y {@link PetData#MAX_LEVEL}. */
    public double getMaxHealthBonus() {
        return maxHealthBonus;
    }

    /** Bonificación total de daño de ataque (0 si el tipo no pelea). */
    public double getMaxDamageBonus() {
        return maxDamageBonus;
    }

    /** Bonificación total de velocidad de movimiento propia. */
    public double getMaxSpeedBonus() {
        return maxSpeedBonus;
    }

    /** Bonificación total de velocidad de vuelo (solo el loro). */
    public double getMaxFlyingSpeedBonus() {
        return maxFlyingSpeedBonus;
    }

    /** Bonificación total de fuerza de salto (solo montables que sí se dirigen). */
    public double getMaxJumpBonus() {
        return maxJumpBonus;
    }

    /** Sobreescribe los 5 bonos de atributo con los valores leídos de config.yml. */
    public void configureBonuses(double maxHealthBonus, double maxDamageBonus, double maxSpeedBonus,
            double maxFlyingSpeedBonus, double maxJumpBonus) {
        this.maxHealthBonus = maxHealthBonus;
        this.maxDamageBonus = maxDamageBonus;
        this.maxSpeedBonus = maxSpeedBonus;
        this.maxFlyingSpeedBonus = maxFlyingSpeedBonus;
        this.maxJumpBonus = maxJumpBonus;
    }

    /** Efecto de poción que le contagia al dueño mientras la mascota está activa y cerca. */
    public PotionEffectType getAuraEffect() {
        return auraEffect;
    }

    public static PetType fromArg(String arg) {
        return switch (arg.toLowerCase()) {
            case "lobo", "wolf" -> WOLF;
            case "gato", "cat" -> CAT;
            case "loro", "parrot" -> PARROT;
            case "caballo", "horse" -> HORSE;
            case "burro", "donkey" -> DONKEY;
            case "mula", "mule" -> MULE;
            case "llama" -> LLAMA;
            default -> null;
        };
    }

    public static PetType fromEntityType(EntityType entityType) {
        for (PetType type : values()) {
            if (type.entityType == entityType) {
                return type;
            }
        }
        return null;
    }
}
