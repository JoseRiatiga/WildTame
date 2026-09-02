package com.example.wildtame.pets;

import com.example.wildtame.MenuIcons;
import com.example.wildtame.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class PetGUI implements Listener {

    // 3 filas: la de arriba solo tiene el retrato centrado (le da aire); la del medio son las
    // acciones "normales" (renombrar, llamar/invocar, inventario); la de abajo son las acciones
    // menos frecuentes / más delicadas (guardar, liberar) — separadas para que no se sientan
    // todas amontonadas en una sola fila de 9.
    private static final int INVENTORY_SIZE = 27;
    private static final int PORTRAIT_SLOT = 4;
    private static final int RENAME_SLOT = 10;
    private static final int CALL_SLOT = 13;
    private static final int INVENTORY_SLOT = 16;
    private static final int STORE_SLOT = 20;
    private static final int RELEASE_SLOT = 24;

    private static final int CONFIRM_RELEASE_YES_SLOT = 11;
    private static final int CONFIRM_RELEASE_NO_SLOT = 15;

    private final PetManager petManager;
    private final PetRenameGUI petRenameGUI;
    private final PetInventoryGUI petInventoryGUI;
    private final Messages messages;
    private final MenuIcons menuIcons;
    private final Map<UUID, PetData> openDataByPlayer = new HashMap<>();
    private final Set<UUID> confirmingRelease = new HashSet<>();

    public PetGUI(PetManager petManager, PetRenameGUI petRenameGUI, PetInventoryGUI petInventoryGUI,
                  Messages messages, MenuIcons menuIcons) {
        this.petManager = petManager;
        this.petRenameGUI = petRenameGUI;
        this.petInventoryGUI = petInventoryGUI;
        this.messages = messages;
        this.menuIcons = menuIcons;
    }

    private String title() {
        return messages.get("gui.pet.title");
    }

    public void open(Player player, PetData data) {
        confirmingRelease.remove(player.getUniqueId());
        Inventory inv = Bukkit.createInventory(null, INVENTORY_SIZE, title());
        renderNormalView(inv, player, data);
        openDataByPlayer.put(player.getUniqueId(), data);
        player.openInventory(inv);
    }

    private void renderNormalView(Inventory inv, Player player, PetData data) {
        boolean isActive = data == petManager.getDataForOwner(player.getUniqueId());
        ItemStack filler = namedItem(borderMaterialFor(data, isActive), " ", List.of());
        for (int slot = 0; slot < inv.getSize(); slot++) {
            inv.setItem(slot, filler);
        }

        inv.setItem(PORTRAIT_SLOT, buildPortrait(data, isActive));
        inv.setItem(RENAME_SLOT, namedItem(menuIcons.icon("pet-rename", Material.NAME_TAG), messages.get("gui.pet.rename-button-name"),
                messages.getList("gui.pet.rename-button-lore")));
        inv.setItem(CALL_SLOT, buildCallButton(data, isActive));
        int capacity = PetInventoryGUI.capacityForLevel(data.level);
        inv.setItem(INVENTORY_SLOT, namedItem(menuIcons.icon("pet-inventory", Material.CHEST), messages.get("gui.pet.inventory-button-name"),
                messages.getList("gui.pet.inventory-button-lore", Map.of("capacity", String.valueOf(capacity)))));
        if (isActive) {
            inv.setItem(STORE_SLOT, namedItem(menuIcons.icon("pet-store", Material.SHULKER_BOX), messages.get("gui.pet.store-button-name"),
                    messages.getList("gui.pet.store-button-lore")));
            inv.setItem(RELEASE_SLOT, namedItem(menuIcons.icon("pet-release", Material.BARRIER), messages.get("gui.pet.release-button-name"),
                    messages.getList("gui.pet.release-button-lore")));
        }
    }

    /** Le da al borde un color distinto según el estado, para que se note de un vistazo. */
    private Material borderMaterialFor(PetData data, boolean isActive) {
        if (isActive) {
            return Material.LIME_STAINED_GLASS_PANE;
        }
        if (data.dead) {
            return Material.RED_STAINED_GLASS_PANE;
        }
        return Material.LIGHT_BLUE_STAINED_GLASS_PANE;
    }

    private void renderReleaseConfirmView(Inventory inv, PetData data) {
        ItemStack filler = namedItem(Material.RED_STAINED_GLASS_PANE, " ", List.of());
        for (int slot = 0; slot < inv.getSize(); slot++) {
            inv.setItem(slot, filler);
        }
        inv.setItem(13, namedItem(Material.BARRIER,
                messages.get("gui.pet.release-confirm-title", Map.of("name", data.name)),
                messages.getList("gui.pet.release-confirm-lore")));
        inv.setItem(CONFIRM_RELEASE_YES_SLOT, namedItem(menuIcons.icon("release-confirm-yes", Material.LIME_WOOL),
                messages.get("gui.pet.release-confirm-yes-name"), messages.getList("gui.pet.release-confirm-yes-lore")));
        inv.setItem(CONFIRM_RELEASE_NO_SLOT, namedItem(menuIcons.icon("release-confirm-no", Material.RED_WOOL),
                messages.get("gui.pet.release-confirm-no-name"), messages.getList("gui.pet.release-confirm-no-lore")));
    }

    private ItemStack buildPortrait(PetData data, boolean isActive) {
        ItemStack portrait = new ItemStack(data.type.getIconMaterial());
        ItemMeta meta = portrait.getItemMeta();
        meta.setDisplayName("§b" + petManager.displayName(data));

        String statusLine;
        String healthLine;
        if (isActive) {
            statusLine = messages.get("gui.pet.status-active");
            healthLine = healthLineFromEntity(data);
        } else if (data.dead) {
            long remaining = data.remainingReviveMillis();
            boolean ready = remaining <= 0;
            statusLine = ready ? messages.get("gui.pet.status-dead-ready") : messages.get("gui.pet.status-dead-waiting");
            healthLine = ready ? messages.get("gui.pet.health-line-dead-ready")
                    : messages.get("gui.pet.health-line-dead-waiting", Map.of("time", PetData.formatDuration(remaining)));
        } else {
            statusLine = messages.get("gui.pet.status-saved");
            healthLine = messages.get("gui.pet.health-line-unknown");
        }

        String maxSuffix = data.isMaxLevel() ? messages.get("gui.pet.lore-level-max-suffix") : "";
        String xpText = data.isMaxLevel() ? messages.get("gui.pet.lore-xp-max") : data.xp + "/" + data.xpToNextLevel();
        String starvingSuffix = data.starving ? messages.get("gui.pet.lore-hunger-starving-suffix") : "";

        meta.setLore(List.of(
                statusLine,
                messages.get("gui.pet.lore-type", Map.of("type", data.type.getDefaultName())),
                messages.get("gui.pet.lore-level", Map.of("level", String.valueOf(data.level), "max-suffix", maxSuffix)),
                messages.get("gui.pet.lore-xp", Map.of("xp", xpText)),
                healthLine,
                messages.get("gui.pet.lore-hunger", Map.of("hunger", String.valueOf((int) data.hunger), "starving-suffix", starvingSuffix))
        ));
        portrait.setItemMeta(meta);
        return portrait;
    }

    private String healthLineFromEntity(PetData data) {
        Entity petEntity = Bukkit.getEntity(data.petUUID);
        if (petEntity instanceof LivingEntity living) {
            AttributeInstance maxHealthAttr = living.getAttribute(Attribute.MAX_HEALTH);
            if (maxHealthAttr != null) {
                return messages.get("gui.pet.health-line-known", Map.of(
                        "current", String.valueOf((int) living.getHealth()),
                        "max", String.valueOf((int) maxHealthAttr.getValue())));
            }
        }
        return messages.get("gui.pet.health-line-unknown");
    }

    private ItemStack buildCallButton(PetData data, boolean isActive) {
        if (isActive) {
            return namedItem(Material.ENDER_PEARL, messages.get("gui.pet.call-active-name"),
                    messages.getList("gui.pet.call-active-lore"));
        }
        if (data.dead) {
            boolean ready = data.remainingReviveMillis() <= 0;
            return ready
                    ? namedItem(Material.ENDER_PEARL, messages.get("gui.pet.call-revive-ready-name"),
                            messages.getList("gui.pet.call-revive-ready-lore"))
                    : namedItem(Material.COAL, messages.get("gui.pet.call-revive-waiting-name"),
                            messages.getList("gui.pet.call-revive-waiting-lore",
                                    Map.of("time", PetData.formatDuration(data.remainingReviveMillis()))));
        }
        return namedItem(Material.ENDER_PEARL, messages.get("gui.pet.call-summon-name"),
                messages.getList("gui.pet.call-summon-lore"));
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
        if (!title().equals(event.getView().getTitle())) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getCurrentItem() == null) {
            return;
        }

        PetData data = openDataByPlayer.get(player.getUniqueId());
        if (data == null) {
            return;
        }

        UUID uuid = player.getUniqueId();
        int slot = event.getSlot();

        if (confirmingRelease.contains(uuid)) {
            if (slot == CONFIRM_RELEASE_YES_SLOT) {
                confirmingRelease.remove(uuid);
                player.closeInventory();
                petManager.releasePet(player);
            } else if (slot == CONFIRM_RELEASE_NO_SLOT) {
                confirmingRelease.remove(uuid);
                renderNormalView(event.getView().getTopInventory(), player, data);
            }
            return;
        }

        if (slot == INVENTORY_SLOT) {
            player.closeInventory();
            petInventoryGUI.open(player, data);
        } else if (slot == STORE_SLOT) {
            boolean isActive = data == petManager.getDataForOwner(uuid);
            if (isActive) {
                player.closeInventory();
                petManager.storePet(player);
            }
        } else if (slot == RENAME_SLOT) {
            player.closeInventory();
            petRenameGUI.open(player, data);
        } else if (slot == CALL_SLOT) {
            player.closeInventory();
            boolean isActive = data == petManager.getDataForOwner(player.getUniqueId());
            if (isActive) {
                petManager.callPet(player, data);
            } else {
                petManager.summonStoredPet(player, data.name);
            }
        } else if (slot == RELEASE_SLOT) {
            boolean isActive = data == petManager.getDataForOwner(uuid);
            if (isActive) {
                confirmingRelease.add(uuid);
                renderReleaseConfirmView(event.getView().getTopInventory(), data);
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (title().equals(event.getView().getTitle()) && event.getPlayer() instanceof Player player) {
            openDataByPlayer.remove(player.getUniqueId());
            confirmingRelease.remove(player.getUniqueId());
        }
    }
}
