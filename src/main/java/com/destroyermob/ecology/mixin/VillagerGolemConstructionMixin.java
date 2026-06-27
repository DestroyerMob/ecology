package com.destroyermob.ecology.mixin;

import com.destroyermob.ecology.Ecology;
import com.destroyermob.ecology.village.VillageGolemConstruction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Villager.class)
public abstract class VillagerGolemConstructionMixin {
    @Inject(method = "spawnGolemIfNeeded", at = @At("HEAD"), cancellable = true)
    private void ecology$buildGolemStructure(ServerLevel serverLevel, long gameTime, int minVillagerAmount, CallbackInfo callback) {
        Villager villager = (Villager) (Object) this;
        try {
            if (VillageGolemConstruction.handleSpawnAttempt(serverLevel, villager, gameTime, minVillagerAmount)) {
                callback.cancel();
            }
        } catch (RuntimeException exception) {
            Ecology.LOGGER.error("Ecology villager golem construction failed; falling back to vanilla spawning", exception);
        }
    }
}
