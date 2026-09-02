package com.example.wildtame.pets;

import com.example.wildtame.Messages;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Inventario propio de cada mascota (independiente del cofre nativo de burro/mula/llama, que no
 * todos los tipos tienen). Su capacidad crece con el nivel, para que subir de nivel también sirva
 * para algo práctico y no solo para stats. A diferencia de los demás menús del plugin, este deja
 * que Minecraft maneje los clics normalmente (mover/sacar/meter ítems como en un cofre), en vez de
 * cancelarlos.
 */
public class PetInventoryGUI implements Listener {

    private static final int SLOTS_PER_TIER = 9;
    private static final int LEVELS_PER_TIER = 5;
    private static final int MAX_SLOTS = 54;

    private final PetManager petManager;
    private final Messages messages;
    private final Map<UUID, PetData> openDataByPlayer = new HashMap<>();

    public PetInventoryGUI(PetManager petManager, Messages messages) {
        this.petManager = petManager;
        this.messages = messages;
    }

    public static int capacityForLevel(int level) {
        int tiers = 1 + (level - 1) / LEVELS_PER_TIER;
        return Math.min(MAX_SLOTS, tiers * SLOTS_PER_TIER);
    }

    public void open(Player player, PetData data) {
        int size = capacityForLevel(data.level);
        String title = messages.get("gui.inventory.title", Map.of("name", data.name));
        Inventory inv = Bukkit.createInventory(null, size, title);
        for (int i = 0; i < size && i < data.inventoryContents.length; i++) {
            inv.setItem(i, data.inventoryContents[i]);
        }
        openDataByPlayer.put(player.getUniqueId(), data);
        player.openInventory(inv);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        PetData data = openDataByPlayer.remove(player.getUniqueId());
        if (data == null) {
            return;
        }
        data.inventoryContents = event.getInventory().getContents().clone();
        petManager.saveAll();
    }
}
