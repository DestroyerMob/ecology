package com.destroyermob.ecology.mixin;

import com.destroyermob.ecology.bee.EcologyBeeSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BeehiveBlockEntity.class)
public abstract class BeehiveBlockEntityMixin {
    @Inject(method = "serverTick", at = @At("HEAD"))
    private static void ecology$tickColony(Level level, BlockPos pos, BlockState state, BeehiveBlockEntity beehive, CallbackInfo callback) {
        if (level instanceof ServerLevel serverLevel) {
            EcologyBeeSystem.tickOccupiedHiveColony(serverLevel, beehive);
        }
    }
}
