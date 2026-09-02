package com.example.wildtame;

import com.example.wildtame.pets.PetData;
import com.example.wildtame.pets.PetManager;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Placeholders %wildtame_*% para PlaceholderAPI (scoreboard, tablist, hologramas, etc.),
 * sobre la mascota ACTIVA del jugador. Solo se carga si el server tiene PlaceholderAPI
 * instalado — ver WildTame#registerPlaceholderApiIfPresent, que aísla esta clase para que su
 * ausencia no rompa el arranque del plugin en servers sin PAPI.
 */
public class WildTamePlaceholders extends PlaceholderExpansion {

    private final WildTame plugin;
    private final PetManager petManager;

    public WildTamePlaceholders(WildTame plugin, PetManager petManager) {
        this.plugin = plugin;
        this.petManager = petManager;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "wildtame";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Lord_Shadow_HF";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer offlinePlayer, @NotNull String params) {
        if (offlinePlayer == null) {
            return "";
        }
        PetData data = petManager.getDataForOwner(offlinePlayer.getUniqueId());
        if (data == null) {
            return params.equals("pet_active") ? "no" : "";
        }
        return switch (params) {
            case "pet_active" -> "yes";
            case "pet_name" -> data.name;
            case "pet_type" -> data.type.getDefaultName();
            case "pet_level" -> String.valueOf(data.level);
            case "pet_xp" -> String.valueOf(data.xp);
            case "pet_xp_to_next" -> String.valueOf(data.xpToNextLevel());
            case "pet_hunger" -> String.valueOf((int) data.hunger);
            case "pet_health" -> petManager.healthPercentText(data);
            case "pet_max_level" -> data.isMaxLevel() ? "yes" : "no";
            case "pet_evolved" -> data.evolved ? "yes" : "no";
            default -> null;
        };
    }
}
