package com.destroyermob.ecology.mixin;

import com.destroyermob.ecology.EcologyConfig;
import com.destroyermob.ecology.bee.EcologyBeeSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BeehiveBlockEntity.class)
public abstract class BeehiveBlockEntityMixin {
    @Inject(method = "serverTick", at = @At("HEAD"))
    private static void ecology$ensureStarterColony(Level level, BlockPos pos, BlockState state, BeehiveBlockEntity beehive, CallbackInfo callback) {
        if (level instanceof ServerLevel serverLevel) {
            EcologyBeeSystem.ensureStarterColony(serverLevel, beehive);
        }
    }

    @Inject(method = "isFull", at = @At("HEAD"), cancellable = true)
    private void ecology$isFull(CallbackInfoReturnable<Boolean> callback) {
        BeehiveBlockEntity hive = (BeehiveBlockEntity) (Object) this;
        callback.setReturnValue(hive.getOccupantCount() >= EcologyConfig.hiveCapacity());
    }

    @ModifyConstant(method = "addOccupant", constant = @Constant(intValue = 3))
    private int ecology$useConfiguredCapacity(int original) {
        return EcologyConfig.hiveCapacity();
    }
}
