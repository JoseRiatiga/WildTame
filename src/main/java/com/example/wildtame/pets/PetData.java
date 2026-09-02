package com.example.wildtame.pets;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class PetData {

    /**
     * Nivel máximo que puede alcanzar cualquier mascota. No es final: se sobreescribe una vez
     * al arrancar (y en /wildtame admin reload) desde config.yml — ver PetManager#loadConfigValues.
     */
    public static int MAX_LEVEL = 50;

    /**
     * Identidad fija de la mascota, generada una sola vez al domesticarla. A diferencia de
     * {@link #petUUID} (que apunta a la entidad viva y cambia cada vez que se vuelve a invocar
     * tras guardarla), este valor nunca cambia durante la vida de la mascota.
     */
    public final UUID id;
    public final UUID ownerUUID;
    public UUID petUUID;
    public PetType type;
    public String name;
    public int level;
    public int xp;
    public double hunger;
    public boolean evolved;
    public boolean starving;
    public boolean dead;
    public long reviveAtMillis;
    public String worldName;
    public double x;
    public double y;
    public double z;

    // Roll "de nacimiento" de la mascota (vida/velocidad/salto que Minecraft le asignó al azar
    // al domesticarla). 0 = no capturado (mascotas guardadas antes de este campo), en cuyo caso
    // simplemente no se fuerza el atributo y se deja el valor por defecto del tipo.
    public double baseMaxHealth;
    public double baseMovementSpeed;
    public double baseJumpStrength;

    // Apariencia. Solo se usan los campos que correspondan al tipo (el resto queda null/0).
    public String horseColor;
    public String horseStyle;
    public String llamaColor;
    public int llamaStrength;
    public String wolfVariantKey;
    public String wolfCollarColor;
    public String catTypeKey;
    public String catCollarColor;
    public String parrotVariant;

    // Inventario propio de la mascota (independiente de la entidad viva), cuya capacidad crece
    // con el nivel — ver PetInventoryGUI#capacityForLevel. Se conserva mientras esté guardada.
    public ItemStack[] inventoryContents = new ItemStack[0];

    public PetData(UUID id, UUID ownerUUID, UUID petUUID, PetType type, String name, int level, int xp,
                    double hunger, boolean evolved, boolean starving, boolean dead, long reviveAtMillis,
                    String worldName, double x, double y, double z) {
        this.id = id;
        this.ownerUUID = ownerUUID;
        this.petUUID = petUUID;
        this.type = type;
        this.name = name;
        this.level = level;
        this.xp = xp;
        this.hunger = hunger;
        this.evolved = evolved;
        this.starving = starving;
        this.dead = dead;
        this.reviveAtMillis = reviveAtMillis;
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public int xpToNextLevel() {
        return level * 50;
    }

    public boolean isMaxLevel() {
        return level >= MAX_LEVEL;
    }

    // displayName()/nameplateText() se movieron a PetManager (ver #displayName y
    // #updateDisplayName) porque ahora se arman con plantillas de messages_<idioma>.yml en
    // vez de texto fijo — PetData es un dato plano, no tiene una referencia a Messages.

    public long remainingReviveMillis() {
        return Math.max(0, reviveAtMillis - System.currentTimeMillis());
    }

    public static String formatDuration(long millis) {
        long totalSeconds = Math.max(0, millis / 1000);
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return minutes > 0 ? minutes + "m " + seconds + "s" : seconds + "s";
    }
}
