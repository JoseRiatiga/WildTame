package com.example.wildtame.pets;

import com.example.wildtame.MenuIcons;
import com.example.wildtame.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Menú principal de /wildtame menu: un hub con accesos a la lista de mascotas (PetsMenuGUI),
 * el ranking, tus estadísticas, la ayuda de comandos y (para admins) un panel de info/recarga.
 * Todas sus pantallas comparten el mismo tamaño de inventario (27 slots) pero cada una tiene
 * su propio título — se abren como inventarios nuevos, no se mutan en el sitio, así que cada
 * método openX() sigue el mismo orden seguro que el resto del plugin: primero
 * player.openInventory(...), recién después se actualiza el mapa de estado (abrir mientras ya
 * hay otro de los nuestros abierto dispara su cierre synchronous, que si pusiéramos el estado
 * antes lo borraría justo después de ponerlo).
 */
public class MainMenuGUI implements Listener {

    private enum Screen { MAIN, STATS, HELP, ADMIN, RANKING }

    private static final int HUB_SIZE = 27;
    private static final int RANKING_ENTRIES = 18;
    private static final int BACK_SLOT = 22;

    private static final int ADMIN_COLLAR_SLOT = 10;
    private static final int ADMIN_TREAT_SLOT = 12;
    private static final int ADMIN_INFO_SLOT = 14;
    private static final int ADMIN_RELOAD_SLOT = 16;

    private static final String ADMIN_COLLAR_PERMISSION = "wildtame.admin.collar";
    private static final String ADMIN_LEVEL_PERMISSION = "wildtame.admin.nivel";
    private static final String ADMIN_RELOAD_PERMISSION = "wildtame.admin.reload";
    private static final String ADMIN_TREAT_PERMISSION = "wildtame.admin.golosina";
    private static final String ADMIN_CLEANUP_PERMISSION = "wildtame.admin.limpiar";

    private final PetManager petManager;
    private final PetsMenuGUI petsMenuGUI;
    private final TamingSessionManager tamingSessionManager;
    private final Messages messages;
    private final MenuIcons menuIcons;
    private final Map<UUID, Screen> currentScreen = new HashMap<>();

    public MainMenuGUI(PetManager petManager, PetsMenuGUI petsMenuGUI,
                        TamingSessionManager tamingSessionManager, Messages messages, MenuIcons menuIcons) {
        this.petManager = petManager;
        this.petsMenuGUI = petsMenuGUI;
        this.tamingSessionManager = tamingSessionManager;
        this.messages = messages;
        this.menuIcons = menuIcons;
    }

    public void open(Player player) {
        openMain(player);
    }

    private void openMain(Player player) {
        Inventory inv = Bukkit.createInventory(null, HUB_SIZE, messages.get("gui.hub.title"));
        fillWithFiller(inv);
        inv.setItem(10, namedItem(menuIcons.icon("hub-my-pets", Material.BONE), messages.get("gui.hub.my-pets-name"),
                messages.getList("gui.hub.my-pets-lore")));
        inv.setItem(12, namedItem(menuIcons.icon("hub-ranking", Material.NETHER_STAR), messages.get("gui.hub.ranking-name"),
                messages.getList("gui.hub.ranking-lore")));
        inv.setItem(14, namedItem(menuIcons.icon("hub-stats", Material.PAPER), messages.get("gui.hub.stats-name"),
                messages.getList("gui.hub.stats-lore")));
        inv.setItem(16, namedItem(menuIcons.icon("hub-help", Material.WRITABLE_BOOK), messages.get("gui.hub.help-name"),
                messages.getList("gui.hub.help-lore")));
        if (player.hasPermission(ADMIN_RELOAD_PERMISSION)) {
            inv.setItem(22, namedItem(menuIcons.icon("hub-admin", Material.COMMAND_BLOCK), messages.get("gui.hub.admin-name"),
                    messages.getList("gui.hub.admin-lore")));
        }
        player.openInventory(inv);
        currentScreen.put(player.getUniqueId(), Screen.MAIN);
    }

    private void openStats(Player player) {
        Inventory inv = Bukkit.createInventory(null, HUB_SIZE, messages.get("gui.stats.title"));
        fillWithFiller(inv, Material.LIGHT_BLUE_STAINED_GLASS_PANE);

        inv.setItem(4, namedItem(menuIcons.icon("stats-header", Material.KNOWLEDGE_BOOK),
                messages.get("gui.stats.header-name", Map.of("player", player.getName())),
                messages.getList("gui.stats.header-lore")));

        List<PetData> all = petManager.allPetsOf(player.getUniqueId());
        if (all.isEmpty()) {
            inv.setItem(13, namedItem(menuIcons.icon("stats-total", Material.BOOK), messages.get("gui.stats.item-name"),
                    messages.getList("gui.stats.no-pets-lore")));
        } else {
            PetData active = petManager.getDataForOwner(player.getUniqueId());
            int storedCount = 0;
            int deadCount = 0;
            int levelSum = 0;
            PetData best = null;
            for (PetData data : all) {
                levelSum += data.level;
                if (data.dead) {
                    deadCount++;
                } else if (data != active) {
                    storedCount++;
                }
                if (best == null || data.level > best.level || (data.level == best.level && data.xp > best.xp)) {
                    best = data;
                }
            }
            String activeName = active != null ? active.name : messages.get("gui.stats.active-none");
            String activeLevelSuffix = active != null
                    ? messages.get("gui.stats.active-level-suffix", Map.of("level", String.valueOf(active.level)))
                    : "";

            // Grid de 2x3 (slot 13 se deja como relleno normal, sin ítem decorativo aparte) —
            // el valor de cada stat va en el propio nombre del ítem, y el lore es una
            // descripción de qué significa, no el número repetido.
            inv.setItem(10, namedItem(menuIcons.icon("stats-total", Material.BOOK),
                    messages.get("gui.stats.total-name", Map.of("total", String.valueOf(all.size()))),
                    messages.getList("gui.stats.total-lore")));
            inv.setItem(11, namedItem(menuIcons.icon("stats-active", Material.NAME_TAG),
                    messages.get("gui.stats.active-name", Map.of("active", activeName, "active-level-suffix", activeLevelSuffix)),
                    messages.getList("gui.stats.active-lore")));
            inv.setItem(12, namedItem(menuIcons.icon("stats-stored", Material.CHEST),
                    messages.get("gui.stats.stored-name", Map.of("stored", String.valueOf(storedCount))),
                    messages.getList("gui.stats.stored-lore")));
            inv.setItem(14, namedItem(menuIcons.icon("stats-dead", Material.SKELETON_SKULL),
                    messages.get("gui.stats.dead-name", Map.of("dead", String.valueOf(deadCount))),
                    messages.getList("gui.stats.dead-lore")));
            inv.setItem(15, namedItem(menuIcons.icon("stats-best", Material.NETHER_STAR),
                    messages.get("gui.stats.best-pet-name"),
                    messages.getList("gui.stats.best-pet-lore", Map.of(
                            "best-name", best.name,
                            "best-level", String.valueOf(best.level)))));
            inv.setItem(16, namedItem(menuIcons.icon("stats-level-sum", Material.EXPERIENCE_BOTTLE),
                    messages.get("gui.stats.level-sum-name", Map.of("level-sum", String.valueOf(levelSum))),
                    messages.getList("gui.stats.level-sum-lore")));
        }
        inv.setItem(BACK_SLOT, backButton());
        player.openInventory(inv);
        currentScreen.put(player.getUniqueId(), Screen.STATS);
    }

    /** Comando de ayuda: clave del texto (gui.help.<key>-name/-lore) + ícono de respaldo. */
    private record HelpEntry(String key, Material fallback) {}

    private static final int[] HELP_USER_SLOTS = {11, 12, 13, 14, 15};
    private static final int[] HELP_ADMIN_SLOTS = {18, 19, 20};

    private void openHelp(Player player) {
        Inventory inv = Bukkit.createInventory(null, HUB_SIZE, messages.get("gui.hub.help-name"));
        fillWithFiller(inv, Material.YELLOW_STAINED_GLASS_PANE);

        inv.setItem(4, namedItem(menuIcons.icon("help-header", Material.WRITTEN_BOOK),
                messages.get("gui.help.header-name"), messages.getList("gui.help.header-lore")));

        // Fila de comandos normales y, debajo, la de admin — separadas visualmente en vez de
        // una lista plana, y cada una con nombre en negrita + lore en vez de un solo renglón.
        List<HelpEntry> userEntries = List.of(
                new HelpEntry("info", Material.PAPER),
                new HelpEntry("llamar", Material.ENDER_PEARL),
                new HelpEntry("renombrar", Material.NAME_TAG),
                new HelpEntry("menu", Material.CHEST),
                new HelpEntry("top", Material.NETHER_STAR));
        for (int i = 0; i < userEntries.size(); i++) {
            inv.setItem(HELP_USER_SLOTS[i], helpItem(userEntries.get(i)));
        }

        List<HelpEntry> adminEntries = new ArrayList<>();
        if (player.hasPermission(ADMIN_LEVEL_PERMISSION)) {
            adminEntries.add(new HelpEntry("nivel", Material.EXPERIENCE_BOTTLE));
        }
        if (player.hasPermission(ADMIN_RELOAD_PERMISSION)) {
            adminEntries.add(new HelpEntry("reload", Material.EMERALD));
        }
        if (player.hasPermission(ADMIN_CLEANUP_PERMISSION)) {
            adminEntries.add(new HelpEntry("limpiar", Material.BARRIER));
        }
        for (int i = 0; i < adminEntries.size(); i++) {
            inv.setItem(HELP_ADMIN_SLOTS[i], helpItem(adminEntries.get(i)));
        }

        inv.setItem(BACK_SLOT, backButton());
        player.openInventory(inv);
        currentScreen.put(player.getUniqueId(), Screen.HELP);
    }

    private ItemStack helpItem(HelpEntry entry) {
        return namedItem(menuIcons.icon("help-" + entry.key(), entry.fallback()),
                messages.get("gui.help." + entry.key() + "-name"),
                messages.getList("gui.help." + entry.key() + "-lore"));
    }

    private void openAdmin(Player player) {
        Inventory inv = Bukkit.createInventory(null, HUB_SIZE, messages.get("gui.admin-panel.title"));
        fillWithFiller(inv);
        if (player.hasPermission(ADMIN_COLLAR_PERMISSION)) {
            inv.setItem(ADMIN_COLLAR_SLOT, namedItem(menuIcons.icon("admin-collar", petManager.collarMaterial()),
                    messages.get("gui.admin-panel.collar-name"), messages.getList("gui.admin-panel.collar-lore")));
        }
        if (player.hasPermission(ADMIN_TREAT_PERMISSION)) {
            inv.setItem(ADMIN_TREAT_SLOT, namedItem(menuIcons.icon("admin-treat", petManager.treatMaterial()),
                    messages.get("gui.admin-panel.treat-name"), messages.getList("gui.admin-panel.treat-lore")));
        }
        inv.setItem(ADMIN_INFO_SLOT, namedItem(menuIcons.icon("admin-info", Material.BOOK), messages.get("gui.admin-panel.item-name"),
                messages.getList("gui.admin-panel.lore", Map.of(
                        "language", messages.activeLanguage(),
                        "total", String.valueOf(petManager.totalRegisteredPets())))));
        if (player.hasPermission(ADMIN_RELOAD_PERMISSION)) {
            inv.setItem(ADMIN_RELOAD_SLOT, namedItem(menuIcons.icon("admin-reload", Material.EMERALD), messages.get("gui.admin-panel.reload-name"),
                    messages.getList("gui.admin-panel.reload-lore")));
        }
        inv.setItem(BACK_SLOT, backButton());
        player.openInventory(inv);
        currentScreen.put(player.getUniqueId(), Screen.ADMIN);
    }

    private void openRanking(Player player) {
        Inventory inv = Bukkit.createInventory(null, HUB_SIZE, messages.get("gui.ranking.title"));
        fillWithFiller(inv);

        List<PetData> top = petManager.topPets(RANKING_ENTRIES);
        if (top.isEmpty()) {
            inv.setItem(13, namedItem(menuIcons.icon("ranking-empty", Material.BARRIER), messages.get("gui.ranking.empty-name"),
                    messages.getList("gui.ranking.empty-lore")));
        } else {
            int slot = 0;
            for (PetData data : top) {
                OfflinePlayer owner = Bukkit.getOfflinePlayer(data.ownerUUID);
                String ownerName = owner.getName() != null ? owner.getName() : "???";
                String maxSuffix = data.isMaxLevel() ? messages.get("gui.pet.lore-level-max-suffix") : "";

                ItemStack item = new ItemStack(data.type.getIconMaterial());
                ItemMeta meta = item.getItemMeta();
                meta.setDisplayName("§6#" + (slot + 1) + " §b" + petManager.displayName(data));
                meta.setLore(messages.getList("gui.ranking.entry-lore", Map.of(
                        "owner", ownerName,
                        "type", data.type.getDefaultName(),
                        "level", String.valueOf(data.level),
                        "max-suffix", maxSuffix)));
                item.setItemMeta(meta);
                inv.setItem(slot, item);
                slot++;
            }
        }
        inv.setItem(BACK_SLOT, backButton());
        player.openInventory(inv);
        currentScreen.put(player.getUniqueId(), Screen.RANKING);
    }

    private ItemStack backButton() {
        return namedItem(menuIcons.icon("back", Material.ARROW), messages.get("gui.hub.back-name"), messages.getList("gui.hub.back-lore"));
    }

    private void fillWithFiller(Inventory inv) {
        fillWithFiller(inv, Material.GRAY_STAINED_GLASS_PANE);
    }

    private void fillWithFiller(Inventory inv, Material paneMaterial) {
        ItemStack filler = namedItem(paneMaterial, " ", List.of());
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, filler);
        }
    }

    private ItemStack namedItem(Material material, String name, List<String> lore) {
        return namedItem(new ItemStack(material), name, lore);
    }

    private ItemStack namedItem(ItemStack base, String name, List<String> lore) {
        ItemStack item = base.clone();
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        if (!lore.isEmpty()) {
            meta.setLore(lore);
        }
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Screen screen = currentScreen.get(player.getUniqueId());
        if (screen == null) {
            return;
        }
        event.setCancelled(true);
        if (event.getCurrentItem() == null) {
            return;
        }
        int slot = event.getSlot();
        switch (screen) {
            case MAIN -> handleMainClick(player, slot);
            case STATS, RANKING, HELP -> {
                if (slot == BACK_SLOT) {
                    openMain(player);
                }
            }
            case ADMIN -> handleAdminClick(player, slot);
        }
    }

    private void handleMainClick(Player player, int slot) {
        if (slot == 10) {
            player.closeInventory();
            petsMenuGUI.open(player);
        } else if (slot == 12) {
            openRanking(player);
        } else if (slot == 14) {
            openStats(player);
        } else if (slot == 16) {
            openHelp(player);
        } else if (slot == 22 && player.hasPermission(ADMIN_RELOAD_PERMISSION)) {
            openAdmin(player);
        }
    }

    private void handleAdminClick(Player player, int slot) {
        if (slot == ADMIN_COLLAR_SLOT && player.hasPermission(ADMIN_COLLAR_PERMISSION)) {
            giveItemToSelf(player, tamingSessionManager.createCollarItem(), "command.admin-received-collar");
        } else if (slot == ADMIN_TREAT_SLOT && player.hasPermission(ADMIN_TREAT_PERMISSION)) {
            giveItemToSelf(player, petManager.createTreatItem(), "command.admin-received-treat");
        } else if (slot == ADMIN_RELOAD_SLOT && player.hasPermission(ADMIN_RELOAD_PERMISSION)) {
            petManager.loadConfigValues();
            messages.load();
            menuIcons.load();
            player.sendMessage(messages.get("command.reload-done"));
            openAdmin(player);
        } else if (slot == BACK_SLOT) {
            openMain(player);
        }
    }

    private void giveItemToSelf(Player player, ItemStack item, String receivedMessageKey) {
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
        if (!leftover.isEmpty()) {
            player.sendMessage(messages.get("command.admin-no-space"));
            return;
        }
        player.sendMessage(messages.get(receivedMessageKey));
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            currentScreen.remove(player.getUniqueId());
        }
    }
}
