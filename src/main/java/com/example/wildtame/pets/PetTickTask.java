package com.example.wildtame.pets;

import org.bukkit.entity.LivingEntity;
import org.bukkit.scheduler.BukkitRunnable;

public class PetTickTask extends BukkitRunnable {

    private final PetManager petManager;

    public PetTickTask(PetManager petManager) {
        this.petManager = petManager;
    }

    @Override
    public void run() {
        for (PetData data : petManager.getAllData()) {
            LivingEntity petEntity = petManager.getLivingPet(data);
            if (petEntity == null) {
                continue;
            }
            petManager.decayHunger(data, petEntity);
            petManager.applyStarvingEffects(data, petEntity);
            petManager.updateDisplayName(data, petEntity);
            petManager.collectNearbyItems(data, petEntity);
            petManager.growlAbility(data, petEntity);
            petManager.applyOwnerAura(data, petEntity);
            petManager.llamaTaunt(data, petEntity);
            petManager.parrotAlert(data, petEntity);
        }
    }
}
