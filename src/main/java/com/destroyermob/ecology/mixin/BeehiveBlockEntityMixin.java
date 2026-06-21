package com.destroyermob.ecology.mixin;

import com.destroyermob.ecology.Ecology;
import com.destroyermob.ecology.EcologyConfig;
import com.destroyermob.ecology.bee.EcologyBeeSystem;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BeehiveBlockEntity.class)
public abstract class BeehiveBlockEntityMixin {
    private static final Set<String> DISABLED_HIVE_LOGIC = ConcurrentHashMap.newKeySet();

    @ModifyConstant(method = {"isFull", "addOccupant"}, constant = @Constant(intValue = 3))
    private int ecology$useConfiguredHiveCapacity(int vanillaCapacity) {
        return EcologyConfig.hiveCapacity();
    }

    @Inject(method = "serverTick", at = @At("HEAD"))
    private static void ecology$tickColony(Level level, BlockPos pos, BlockState state, BeehiveBlockEntity beehive, CallbackInfo callback) {
        if (!(level instanceof ServerLevel serverLevel)
                || !EcologyConfig.ENABLE_BEE_SYSTEM.get()
                || !EcologyConfig.ENABLE_HIVE_COLONY_TICKING.get()) {
            return;
        }

        String hiveKey = serverLevel.dimension().location() + ":" + pos.asLong();
        if (DISABLED_HIVE_LOGIC.contains(hiveKey)) {
            return;
        }

        try {
            if (EcologyConfig.DEBUG_BEE_SYSTEM_LOGGING.get()) {
                Ecology.LOGGER.debug("Running Ecology hive colony tick for {} at {}", serverLevel.dimension().location(), pos);
            }
            EcologyBeeSystem.tickOccupiedHiveColony(serverLevel, beehive);
        } catch (RuntimeException exception) {
            DISABLED_HIVE_LOGIC.add(hiveKey);
            Ecology.LOGGER.error("Disabled Ecology hive logic for {} at {} after colony tick failure", serverLevel.dimension().location(), pos, exception);
        }
    }
}
