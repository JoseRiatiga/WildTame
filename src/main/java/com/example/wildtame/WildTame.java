package com.example.wildtame;

import com.example.wildtame.pets.MainMenuGUI;
import com.example.wildtame.pets.PetCommand;
import com.example.wildtame.pets.PetGUI;
import com.example.wildtame.pets.PetInventoryGUI;
import com.example.wildtame.pets.PetListener;
import com.example.wildtame.pets.PetManager;
import com.example.wildtame.pets.PetRenameGUI;
import com.example.wildtame.pets.PetTickTask;
import com.example.wildtame.pets.PetsMenuGUI;
import com.example.wildtame.pets.TamingSessionManager;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class WildTame extends JavaPlugin {

    private static final long AUTO_SAVE_INTERVAL_TICKS = 20L * 60L; // 60 segundos

    private PetManager petManager;
    private TamingSessionManager tamingSessionManager;
    private BukkitTask petTickTask;
    private BukkitTask petAutoSaveTask;
    private BukkitTask tamingEffectsTask;
    private BukkitTask petBackupTask;

    @Override
    public void onEnable() {
        getLogger().info("WildTame activado!");

        ensureConfigUpToDate();
        Messages messages = new Messages(this);
        MenuIcons menuIcons = new MenuIcons(this);
        petManager = new PetManager(this, messages);
        petManager.load();
        registerTreatRecipe();

        PetRenameGUI petRenameGUI = new PetRenameGUI(petManager, messages);
        PetInventoryGUI petInventoryGUI = new PetInventoryGUI(petManager, messages);
        PetGUI petGUI = new PetGUI(petManager, petRenameGUI, petInventoryGUI, messages, menuIcons);
        PetsMenuGUI petsMenuGUI = new PetsMenuGUI(petManager, petGUI, messages, menuIcons);
        tamingSessionManager = new TamingSessionManager(this, petManager, messages);
        MainMenuGUI mainMenuGUI = new MainMenuGUI(petManager, petsMenuGUI, tamingSessionManager, messages, menuIcons);
        petsMenuGUI.setMainMenu(mainMenuGUI);

        PetCommand petCommand = new PetCommand(petManager, mainMenuGUI, petGUI, messages, menuIcons);
        getCommand("wildtame").setExecutor(petCommand);
        getCommand("wildtame").setTabCompleter(petCommand);

        getServer().getPluginManager().registerEvents(new PetListener(petManager, petGUI, messages), this);
        getServer().getPluginManager().registerEvents(petGUI, this);
        getServer().getPluginManager().registerEvents(petsMenuGUI, this);
        getServer().getPluginManager().registerEvents(mainMenuGUI, this);
        getServer().getPluginManager().registerEvents(petRenameGUI, this);
        getServer().getPluginManager().registerEvents(petInventoryGUI, this);
        getServer().getPluginManager().registerEvents(tamingSessionManager, this);
        tamingEffectsTask = tamingSessionManager.start(this);

        petTickTask = new PetTickTask(petManager).runTaskTimer(this, 60L, 60L);

        // La mayoría de las acciones ya guardan al instante, pero esto protege contra
        // caídas o reinicios forzados del proceso, donde onDisable() nunca llega a ejecutarse.
        petAutoSaveTask = getServer().getScheduler().runTaskTimer(this,
                () -> petManager.saveAll(), AUTO_SAVE_INTERVAL_TICKS, AUTO_SAVE_INTERVAL_TICKS);

        // Respaldo periódico de pets.yml (ver backups.* en config.yml). El intervalo solo se
        // lee una vez al arrancar — cambiarlo requiere reiniciar el servidor.
        long backupIntervalTicks = petManager.backupIntervalMinutes() * 60L * 20L;
        petBackupTask = getServer().getScheduler().runTaskTimerAsynchronously(this,
                () -> petManager.backupPetsFile(), backupIntervalTicks, backupIntervalTicks);

        registerPlaceholderApiIfPresent();
    }

    @Override
    public void onDisable() {
        if (petTickTask != null) {
            petTickTask.cancel();
        }
        if (petAutoSaveTask != null) {
            petAutoSaveTask.cancel();
        }
        if (tamingEffectsTask != null) {
            tamingEffectsTask.cancel();
        }
        if (petBackupTask != null) {
            petBackupTask.cancel();
        }
        if (tamingSessionManager != null) {
            tamingSessionManager.stop();
        }
        if (petManager != null) {
            petManager.saveAll();
        }
        getLogger().info("WildTame desactivado.");
    }

    /**
     * config.yml no se pisa si ya existe (saveDefaultConfig solo extrae si falta), así que una
     * actualización del plugin que agregue una clave nueva (como pasó con items/backups/
     * menu-icons durante el desarrollo) se quedaría invisible para siempre en un servidor que
     * ya tenía el archivo generado. Igual que con lang/*.yml en {@link Messages}, esto agrega
     * solo las claves que falten sin tocar ninguna que el admin ya haya personalizado.
     */
    private void ensureConfigUpToDate() {
        saveDefaultConfig();
        File file = new File(getDataFolder(), "config.yml");
        InputStream defaultStream = getResource("config.yml");
        if (defaultStream == null) {
            return;
        }
        FileConfiguration existing = YamlConfiguration.loadConfiguration(file);
        FileConfiguration defaults = YamlConfiguration.loadConfiguration(
                new InputStreamReader(defaultStream, StandardCharsets.UTF_8));

        boolean changed = false;
        for (String key : defaults.getKeys(true)) {
            if (!defaults.isConfigurationSection(key) && !existing.isSet(key)) {
                existing.set(key, defaults.get(key));
                changed = true;
            }
        }
        if (changed) {
            try {
                existing.save(file);
                getLogger().info("Se agregaron claves nuevas a config.yml.");
            } catch (IOException e) {
                getLogger().warning("No se pudo actualizar config.yml: " + e.getMessage());
            }
        }
        reloadConfig();
    }

    /**
     * La Golosina de Mascota (única forma de dar XP directo, igual para las 7 mascotas) se
     * obtiene crafteando en vez de dársela un admin — así cualquier jugador se la puede ganar.
     */
    private void registerTreatRecipe() {
        NamespacedKey recipeKey = new NamespacedKey(this, "pet_treat");
        ShapelessRecipe recipe = new ShapelessRecipe(recipeKey, petManager.createTreatItem());
        recipe.addIngredient(Material.GOLDEN_CARROT);
        recipe.addIngredient(Material.BONE);
        recipe.addIngredient(Material.SWEET_BERRIES);
        getServer().addRecipe(recipe);
    }

    /**
     * Aislado en su propio método a propósito: si PlaceholderAPI no está instalado, esta
     * clase nunca llega a ejecutarse, así que la JVM nunca intenta resolver
     * WildTamePlaceholders (que extiende una clase de PAPI) y el plugin arranca normal.
     */
    private void registerPlaceholderApiIfPresent() {
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new WildTamePlaceholders(this, petManager).register();
            getLogger().info("PlaceholderAPI detectado — placeholders %wildtame_*% registrados.");
        }
    }
}
