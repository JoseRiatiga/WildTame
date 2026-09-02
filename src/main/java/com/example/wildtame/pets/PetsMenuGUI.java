package com.example.wildtame.pets;

import com.example.wildtame.MenuIcons;
import com.example.wildtame.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class PetsMenuGUI implements Listener {

    private enum StatusFilter { ALL, ACTIVE, STORED, DEAD }

    private enum SortOrder { LEVEL, NAME, XP }

    private static class MenuState {
        int page = 0;
        StatusFilter statusFilter = StatusFilter.ALL;
        PetType typeFilter;
        SortOrder sort = SortOrder.LEVEL;
    }

    // Layout fijo de 54 slots: 45 para íconos de mascotas (paginados) + una fila de controles
    // abajo (filtros, orden, navegación, volver) siempre visible.
    private static final int INVENTORY_SIZE = 54;
    private static final int ITEMS_PER_PAGE = 45;
    private static final int PREV_PAGE_SLOT = 45;
    private static final int STATUS_FILTER_SLOT = 46;
    private static final int TYPE_FILTER_SLOT = 47;
    private static final int SORT_SLOT = 48;
    private static final int INFO_SLOT = 49;
    private static final int BACK_SLOT = 50;
    private static final int NEXT_PAGE_SLOT = 53;

    private final PetManager petManager;
    private final PetGUI petGUI;
    private final Messages messages;
    private final MenuIcons menuIcons;
    private final Map<UUID, MenuState> stateByPlayer = new HashMap<>();
    private MainMenuGUI mainMenuGUI;

    public PetsMenuGUI(PetManager petManager, PetGUI petGUI, Messages messages, MenuIcons menuIcons) {
        this.petManager = petManager;
        this.petGUI = petGUI;
        this.messages = messages;
        this.menuIcons = menuIcons;
    }

    /** Referencia tardía porque MainMenuGUI necesita esta clase primero (evita ciclo en el constructor). */
    public void setMainMenu(MainMenuGUI mainMenuGUI) {
        this.mainMenuGUI = mainMenuGUI;
    }

    private String title() {
        return messages.get("gui.menu.title");
    }

    public void open(Player player) {
        if (petManager.allPetsOf(player.getUniqueId()).isEmpty()) {
            player.sendMessage(messages.get("gui.menu.no-pets"));
            return;
        }
        render(player, new MenuState());
    }

    private void render(Player player, MenuState state) {
        List<PetData> filtered = filteredAndSorted(player, state);
        int totalPages = Math.max(1, (int) Math.ceil(filtered.size() / (double) ITEMS_PER_PAGE));
        state.page = Math.max(0, Math.min(state.page, totalPages - 1));

        Inventory inv = Bukkit.createInventory(null, INVENTORY_SIZE, title());
        PetData active = petManager.getDataForOwner(player.getUniqueId());
        int start = state.page * ITEMS_PER_PAGE;
        for (int i = 0; i < ITEMS_PER_PAGE && start + i < filtered.size(); i++) {
            PetData data = filtered.get(start + i);
            inv.setItem(i, buildIcon(data, data == active));
        }

        ItemStack filler = namedItem(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int slot = ITEMS_PER_PAGE; slot < INVENTORY_SIZE; slot++) {
            inv.setItem(slot, filler);
        }
        if (filtered.isEmpty()) {
            inv.setItem(22, namedItem(menuIcons.icon("no-matches", Material.BARRIER), messages.get("gui.menu.no-matches"), List.of()));
        }
        if (state.page > 0) {
            inv.setItem(PREV_PAGE_SLOT, namedItem(menuIcons.icon("prev-page", Material.ARROW), messages.get("gui.menu.prev-page-name"),
                    messages.getList("gui.menu.prev-page-lore", Map.of("page", String.valueOf(state.page)))));
        }
        inv.setItem(STATUS_FILTER_SLOT, namedItem(menuIcons.icon("filter-status", Material.HOPPER),
                messages.get("gui.menu.filter-status-name", Map.of("value", statusFilterLabel(state.statusFilter))),
                messages.getList("gui.menu.filter-status-lore")));
        inv.setItem(TYPE_FILTER_SLOT, namedItem(menuIcons.icon("filter-type", Material.LEAD),
                messages.get("gui.menu.filter-type-name", Map.of("value", typeFilterLabel(state.typeFilter))),
                messages.getList("gui.menu.filter-type-lore")));
        inv.setItem(SORT_SLOT, namedItem(menuIcons.icon("sort", Material.COMPARATOR),
                messages.get("gui.menu.sort-name", Map.of("value", sortLabel(state.sort))),
                messages.getList("gui.menu.sort-lore")));
        inv.setItem(INFO_SLOT, buildInfoItem(filtered.size(), state.page, totalPages));
        inv.setItem(BACK_SLOT, namedItem(menuIcons.icon("back", Material.ARROW), messages.get("gui.hub.back-name"),
                messages.getList("gui.hub.back-lore")));
        if (state.page < totalPages - 1) {
            inv.setItem(NEXT_PAGE_SLOT, namedItem(menuIcons.icon("next-page", Material.ARROW), messages.get("gui.menu.next-page-name"),
                    messages.getList("gui.menu.next-page-lore", Map.of("page", String.valueOf(state.page + 2)))));
        }

        // Se abre primero y se registra el estado después: abrir mientras ya hay otro
        // inventario nuestro abierto dispara su InventoryCloseEvent de inmediato, y ese
        // handler borra la entrada de stateByPlayer — si la hubiéramos puesto antes, se
        // perdería justo después de ponerla.
        player.openInventory(inv);
        stateByPlayer.put(player.getUniqueId(), state);
    }

    private List<PetData> filteredAndSorted(Player player, MenuState state) {
        PetData active = petManager.getDataForOwner(player.getUniqueId());
        return petManager.allPetsOf(player.getUniqueId()).stream()
                .filter(data -> matchesStatus(data, active, state.statusFilter))
                .filter(data -> state.typeFilter == null || data.type == state.typeFilter)
                .sorted(comparatorFor(state.sort))
                .collect(Collectors.toList());
    }

    private boolean matchesStatus(PetData data, PetData active, StatusFilter filter) {
        return switch (filter) {
            case ALL -> true;
            case ACTIVE -> data == active;
            case STORED -> data != active && !data.dead;
            case DEAD -> data.dead;
        };
    }

    private Comparator<PetData> comparatorFor(SortOrder sort) {
        return switch (sort) {
            case LEVEL -> Comparator.comparingInt((PetData d) -> d.level).thenComparingDouble(d -> d.xp).reversed();
            case NAME -> Comparator.comparing(d -> d.name, String.CASE_INSENSITIVE_ORDER);
            case XP -> Comparator.<PetData>comparingDouble(d -> d.xp).reversed();
        };
    }

    private String statusFilterLabel(StatusFilter filter) {
        String key = switch (filter) {
            case ALL -> "gui.menu.filter-status-all";
            case ACTIVE -> "gui.menu.filter-status-active";
            case STORED -> "gui.menu.filter-status-stored";
            case DEAD -> "gui.menu.filter-status-dead";
        };
        return messages.get(key);
    }

    private String typeFilterLabel(PetType type) {
        return type == null ? messages.get("gui.menu.filter-type-all") : type.getDefaultName();
    }

    private String sortLabel(SortOrder sort) {
        String key = switch (sort) {
            case LEVEL -> "gui.menu.sort-level";
            case NAME -> "gui.menu.sort-alpha";
            case XP -> "gui.menu.sort-xp";
        };
        return messages.get(key);
    }

    private ItemStack buildInfoItem(int total, int page, int totalPages) {
        return namedItem(menuIcons.icon("list-info", Material.BOOK), messages.get("gui.menu.info-name"),
                messages.getList("gui.menu.info-lore", Map.of(
                        "total", String.valueOf(total),
                        "page", String.valueOf(page + 1),
                        "total-pages", String.valueOf(totalPages))));
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

    private ItemStack buildIcon(PetData data, boolean isActive) {
        ItemStack icon = new ItemStack(data.type.getIconMaterial());
        ItemMeta meta = icon.getItemMeta();

        String statusPrefix;
        String statusLine;
        String actionLine;
        if (isActive) {
            statusPrefix = messages.get("gui.menu.status-prefix-active");
            statusLine = messages.get("gui.menu.status-active");
            actionLine = messages.get("gui.menu.action-active");
        } else if (data.dead) {
            long remaining = data.remainingReviveMillis();
            boolean ready = remaining <= 0;
            statusPrefix = ready ? messages.get("gui.menu.status-prefix-dead-ready") : messages.get("gui.menu.status-prefix-dead-waiting");
            statusLine = ready ? messages.get("gui.menu.status-dead-ready")
                    : messages.get("gui.menu.status-dead-waiting", Map.of("time", PetData.formatDuration(remaining)));
            actionLine = ready ? messages.get("gui.menu.action-dead-ready") : messages.get("gui.menu.action-dead-waiting");
        } else {
            statusPrefix = messages.get("gui.menu.status-prefix-saved");
            statusLine = messages.get("gui.menu.status-saved");
            actionLine = messages.get("gui.menu.action-saved");
        }

        String starvingSuffix = data.starving ? messages.get("gui.menu.lore-hunger-starving-suffix") : "";
        String xpText = data.isMaxLevel() ? messages.get("gui.menu.lore-xp-max") : data.xp + "/" + data.xpToNextLevel();

        meta.setDisplayName(statusPrefix + "§b" + petManager.displayName(data));
        meta.setLore(List.of(
                statusLine,
                messages.get("gui.menu.lore-type", Map.of("type", data.type.getDefaultName())),
                messages.get("gui.menu.lore-xp", Map.of("xp", xpText)),
                messages.get("gui.menu.lore-hunger", Map.of("hunger", String.valueOf((int) data.hunger), "starving-suffix", starvingSuffix)),
                "",
                actionLine,
                messages.get("gui.menu.action-info")
        ));
        icon.setItemMeta(meta);
        return icon;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!title().equals(event.getView().getTitle())) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getCurrentItem() == null) {
            return;
        }

        MenuState state = stateByPlayer.get(player.getUniqueId());
        if (state == null) {
            return;
        }

        int slot = event.getSlot();
        if (slot == PREV_PAGE_SLOT) {
            state.page--;
            render(player, state);
            return;
        }
        if (slot == NEXT_PAGE_SLOT) {
            state.page++;
            render(player, state);
            return;
        }
        if (slot == STATUS_FILTER_SLOT) {
            StatusFilter[] values = StatusFilter.values();
            state.statusFilter = values[(state.statusFilter.ordinal() + 1) % values.length];
            state.page = 0;
            render(player, state);
            return;
        }
        if (slot == TYPE_FILTER_SLOT) {
            PetType[] types = PetType.values();
            if (state.typeFilter == null) {
                state.typeFilter = types[0];
            } else {
                int next = state.typeFilter.ordinal() + 1;
                state.typeFilter = next < types.length ? types[next] : null;
            }
            state.page = 0;
            render(player, state);
            return;
        }
        if (slot == SORT_SLOT) {
            SortOrder[] values = SortOrder.values();
            state.sort = values[(state.sort.ordinal() + 1) % values.length];
            render(player, state);
            return;
        }
        if (slot == BACK_SLOT) {
            player.closeInventory();
            if (mainMenuGUI != null) {
                mainMenuGUI.open(player);
            }
            return;
        }
        if (slot >= ITEMS_PER_PAGE) {
            return;
        }

        List<PetData> filtered = filteredAndSorted(player, state);
        int index = state.page * ITEMS_PER_PAGE + slot;
        if (index >= filtered.size()) {
            return;
        }
        handlePetClick(player, filtered.get(index), event.isRightClick(), event.isLeftClick());
    }

    private void handlePetClick(Player player, PetData data, boolean rightClick, boolean leftClick) {
        PetData active = petManager.getDataForOwner(player.getUniqueId());
        boolean isActive = active != null && data == active;

        if (rightClick) {
            player.closeInventory();
            petGUI.open(player, data);
            return;
        }

        if (leftClick) {
            player.closeInventory();
            if (isActive) {
                petManager.storePet(player);
            } else {
                petManager.summonStoredPet(player, data.name);
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (title().equals(event.getView().getTitle()) && event.getPlayer() instanceof Player player) {
            stateByPlayer.remove(player.getUniqueId());
        }
    }
}
