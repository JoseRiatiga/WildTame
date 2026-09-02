package com.example.wildtame.pets;

import com.example.wildtame.Messages;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MenuType;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.view.AnvilView;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Yunque virtual usado como cuadro de texto nativo de Minecraft para renombrar una mascota,
 * sin necesidad de pedir el nombre por chat. Se crea con {@link MenuType#ANVIL} en vez de
 * {@code Bukkit.createInventory(null, InventoryType.ANVIL, ...)} (deprecado): en esta versión
 * de Paper, esa forma vieja no conecta el yunque con {@link PrepareAnvilEvent} — el cliente
 * muestra una previsualización de lo escrito, pero el servidor nunca la confirma.
 */
public class PetRenameGUI implements Listener {

    private static final int MAX_NAME_LENGTH = 16;
    private static final int RESULT_SLOT = 2;

    private final PetManager petManager;
    private final Messages messages;
    private final Map<UUID, PetData> pendingRenames = new HashMap<>();

    public PetRenameGUI(PetManager petManager, Messages messages) {
        this.petManager = petManager;
        this.messages = messages;
    }

    private String title() {
        return messages.get("gui.rename.title");
    }

    public void open(Player player, PetData data) {
        AnvilView view = MenuType.ANVIL.create(player, LegacyComponentSerializer.legacySection().deserialize(title()));

        ItemStack nameTag = new ItemStack(Material.NAME_TAG);
        ItemMeta meta = nameTag.getItemMeta();
        meta.setDisplayName(data.name);
        nameTag.setItemMeta(meta);
        view.getTopInventory().setItem(0, nameTag);

        pendingRenames.put(player.getUniqueId(), data);
        player.openInventory(view);
    }

    @EventHandler
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        if (!title().equals(event.getView().getTitle())) {
            return;
        }
        AnvilInventory anvil = event.getInventory();
        anvil.setRepairCostAmount(0);

        ItemStack input = anvil.getFirstItem();
        if (input == null) {
            return;
        }
        ItemStack result = input.clone();
        String renameText = anvil.getRenameText();
        if (renameText != null && !renameText.isEmpty()) {
            ItemMeta resultMeta = result.getItemMeta();
            resultMeta.setDisplayName(renameText);
            result.setItemMeta(resultMeta);
        }
        event.setResult(result);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!title().equals(event.getView().getTitle())) {
            return;
        }
        event.setCancelled(true);
        // Usamos el número de slot crudo en vez de getSlotType(): el slot 2 de un yunque
        // SIEMPRE es el resultado, sin depender de cómo Paper clasifique el tipo de slot.
        if (event.getRawSlot() != RESULT_SLOT || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        PetData data = pendingRenames.remove(player.getUniqueId());
        if (data == null) {
            return;
        }

        // Leemos el nombre del propio ítem resultado (el mismo que armamos en onPrepareAnvil)
        // en vez de volver a preguntarle al yunque con getRenameText() en este momento.
        ItemStack result = event.getCurrentItem();
        String newName = (result != null && result.hasItemMeta() && result.getItemMeta().hasDisplayName())
                ? result.getItemMeta().getDisplayName()
                : null;
        player.closeInventory();

        if (newName == null || newName.isBlank()) {
            player.sendMessage(messages.get("gui.rename.invalid-name"));
            return;
        }
        newName = newName.trim();
        if (newName.length() > MAX_NAME_LENGTH) {
            newName = newName.substring(0, MAX_NAME_LENGTH);
        }
        petManager.renamePet(player, data, newName);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (title().equals(event.getView().getTitle()) && event.getPlayer() instanceof Player player) {
            pendingRenames.remove(player.getUniqueId());
        }
    }
}
