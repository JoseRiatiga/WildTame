package com.example.wildtame;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Todo el texto que ve el jugador (títulos de menú, nombres/lore de ítems, mensajes de chat,
 * el letrero flotante de la mascota) vive en lang/&lt;idioma&gt;.yml en vez de estar
 * escrito directo en el código Java — así se puede traducir o personalizar el plugin sin
 * tocar ni recompilar nada. El idioma activo se elige con "language" en config.yml (vienen
 * "es" y "en" de fábrica, ambos dentro de la carpeta lang/). Los placeholders van entre
 * %porcentajes% y los colores con &amp;.
 */
public class Messages {

    private static final String DEFAULT_LANGUAGE = "es";

    // Idiomas que vienen empaquetados en el jar — se extraen los dos siempre, aunque solo uno
    // esté activo, para que se puedan ver/editar ambos sin tener que cambiar "language" primero.
    private static final List<String> BUNDLED_LANGUAGES = List.of("es", "en");

    private final JavaPlugin plugin;
    private FileConfiguration config;
    private String activeLanguage = DEFAULT_LANGUAGE;

    public Messages(JavaPlugin plugin) {
        this.plugin = plugin;
        load();
    }

    /** Público para que /wildtame admin reload pueda releer idioma + archivo sin reiniciar el servidor. */
    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        String language = plugin.getConfig().getString("language", DEFAULT_LANGUAGE).toLowerCase(Locale.ROOT);

        for (String bundled : BUNDLED_LANGUAGES) {
            ensureLanguageFileUpToDate("lang/" + bundled + ".yml");
        }
        if (!BUNDLED_LANGUAGES.contains(language)) {
            ensureLanguageFileUpToDate("lang/" + language + ".yml");
        }

        File file = resolveLanguageFile(language);
        config = YamlConfiguration.loadConfiguration(file);
        activeLanguage = language;
    }

    /** Código del idioma actualmente cargado (ej. "es"), para mostrar en paneles de admin. */
    public String activeLanguage() {
        return activeLanguage;
    }

    /**
     * Si el archivo no existe todavía, lo extrae del jar. Si ya existe (de una versión
     * anterior del plugin, por ejemplo), en vez de dejarlo como está le agrega las claves de
     * texto nuevas que le falten comparándolo con el que viene empaquetado — sin tocar ni
     * pisar ninguna que el admin ya haya traducido o personalizado. Así un mensaje nuevo que
     * yo agregue no se queda mostrando la clave cruda (ej. "pet.algo-nuevo") en vez de texto.
     */
    private void ensureLanguageFileUpToDate(String resourcePath) {
        File file = new File(plugin.getDataFolder(), resourcePath);
        if (!file.exists()) {
            try {
                plugin.saveResource(resourcePath, false);
            } catch (IllegalArgumentException notBundled) {
                // Idioma personalizado sin archivo por defecto en el jar (ej. "fr" hecho a
                // mano) — no hay nada que extraer, resolveLanguageFile() se encarga después.
            }
            return;
        }
        mergeMissingKeys(file, resourcePath);
    }

    private void mergeMissingKeys(File file, String resourcePath) {
        InputStream defaultStream = plugin.getResource(resourcePath);
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
                plugin.getLogger().info("Se agregaron claves de texto nuevas a " + resourcePath + ".");
            } catch (IOException e) {
                plugin.getLogger().warning("No se pudo actualizar " + resourcePath + ": " + e.getMessage());
            }
        }
    }

    /**
     * Busca lang/&lt;language&gt;.yml en la carpeta del plugin; si no existe, intenta
     * extraerlo del jar (para idiomas empaquetados que por algún motivo no se hayan extraído
     * ya en {@link #load()}). Si el idioma configurado no existe en ningún lado (ej. un
     * código inventado sin archivo propio), se cae a español en vez de romper el plugin por
     * una config inválida.
     */
    private File resolveLanguageFile(String language) {
        String resourcePath = "lang/" + language + ".yml";
        File file = new File(plugin.getDataFolder(), resourcePath);
        if (file.exists()) {
            return file;
        }
        try {
            plugin.saveResource(resourcePath, false);
            return file;
        } catch (IllegalArgumentException notBundled) {
            if (language.equals(DEFAULT_LANGUAGE)) {
                // Ni siquiera el español por defecto está — no hay nada más que intentar.
                return file;
            }
            plugin.getLogger().warning("No se encontró " + resourcePath + ", usando " + DEFAULT_LANGUAGE + " por defecto.");
            return resolveLanguageFile(DEFAULT_LANGUAGE);
        }
    }

    public String get(String path) {
        return colorize(raw(path));
    }

    public String get(String path, Map<String, String> placeholders) {
        return colorize(applyPlaceholders(raw(path), placeholders));
    }

    public List<String> getList(String path) {
        List<String> result = new ArrayList<>();
        for (String line : config.getStringList(path)) {
            result.add(colorize(line));
        }
        return result;
    }

    public List<String> getList(String path, Map<String, String> placeholders) {
        List<String> result = new ArrayList<>();
        for (String line : config.getStringList(path)) {
            result.add(colorize(applyPlaceholders(line, placeholders)));
        }
        return result;
    }

    private String raw(String path) {
        return config.getString(path, path);
    }

    private String applyPlaceholders(String raw, Map<String, String> placeholders) {
        String result = raw;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("%" + entry.getKey() + "%", entry.getValue());
        }
        return result;
    }

    private String colorize(String raw) {
        return ChatColor.translateAlternateColorCodes('&', raw);
    }
}
