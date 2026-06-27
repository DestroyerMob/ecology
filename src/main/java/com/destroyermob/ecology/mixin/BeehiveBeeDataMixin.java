package com.destroyermob.ecology.mixin;

import com.destroyermob.ecology.bee.EcologyBeeSystem;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.world.level.block.entity.BeehiveBlockEntity$BeeData")
public abstract class BeehiveBeeDataMixin {
    @Shadow
    @Final
    private BeehiveBlockEntity.Occupant occupant;

    @Shadow
    private int ticksInHive;

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void ecology$releaseEmptyCompletedWorkers(CallbackInfoReturnable<Boolean> callback) {
        if (EcologyBeeSystem.shouldForceReleaseStoredWorker(occupant, ticksInHive)) {
            callback.setReturnValue(true);
        }
    }
}
