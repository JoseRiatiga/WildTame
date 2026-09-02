package com.example.wildtame;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Íconos custom-head (opcionales) para los botones de navegación del menú — config.yml
 * (menu-icons.&lt;clave&gt;) acepta el "Value" en base64 que da minecraft-heads.com en su
 * pestaña "For Developers". Si una clave está vacía, {@link #icon} devuelve el material de
 * respaldo de siempre, así nada se rompe mientras el admin va completando las que quiera.
 * No cubre a las mascotas en sí (portrait, botón de llamar): esos íconos representan al
 * animal real y cambian de material según su estado, así que se quedan como estaban.
 */
public class MenuIcons {

    private static final Pattern URL_PATTERN = Pattern.compile("\"url\"\\s*:\\s*\"([^\"]+)\"");

    private final JavaPlugin plugin;
    private final Map<String, String> values = new HashMap<>();
    private boolean enabled = true;

    public MenuIcons(JavaPlugin plugin) {
        this.plugin = plugin;
        load();
    }

    /** Público para que /wildtame admin reload pueda releer config.yml sin reiniciar el servidor. */
    public void load() {
        values.clear();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("menu-icons");
        if (section == null) {
            enabled = true;
            return;
        }
        enabled = section.getBoolean("enabled", true);
        for (String key : section.getKeys(false)) {
            if (key.equals("enabled")) {
                continue;
            }
            String value = section.getString(key, "");
            if (value != null && !value.isBlank()) {
                values.put(key, value.trim());
            }
        }
    }

    /** Con menu-icons.enabled: false en config.yml, todos los botones vuelven a su ítem vanilla de siempre. */
    public ItemStack icon(String key, Material fallback) {
        if (!enabled) {
            return new ItemStack(fallback);
        }
        String value = values.get(key);
        if (value == null) {
            return new ItemStack(fallback);
        }
        ItemStack head = buildHead(value);
        return head != null ? head : new ItemStack(fallback);
    }

    private ItemStack buildHead(String base64Value) {
        String url = extractSkinUrl(base64Value);
        if (url == null) {
            plugin.getLogger().warning("Value de custom-head inválido en config.yml (menu-icons): " + base64Value);
            return null;
        }
        try {
            PlayerProfile profile = Bukkit.createPlayerProfile(UUID.randomUUID());
            PlayerTextures textures = profile.getTextures();
            textures.setSkin(new URL(url));
            profile.setTextures(textures);

            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setOwnerProfile(profile);
            head.setItemMeta(meta);
            return head;
        } catch (MalformedURLException e) {
            plugin.getLogger().warning("URL de textura inválida en menu-icons: " + url);
            return null;
        }
    }

    private static String extractSkinUrl(String base64Value) {
        String decoded;
        try {
            decoded = new String(Base64.getDecoder().decode(base64Value), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException notBase64) {
            return null;
        }
        Matcher matcher = URL_PATTERN.matcher(decoded);
        return matcher.find() ? matcher.group(1) : null;
    }
}
