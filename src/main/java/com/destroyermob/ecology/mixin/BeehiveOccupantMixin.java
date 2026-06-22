package com.destroyermob.ecology.mixin;

import com.destroyermob.ecology.EcologyConfig;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(BeehiveBlockEntity.Occupant.class)
public abstract class BeehiveOccupantMixin {
    @ModifyConstant(method = {"of", "create"}, constant = @Constant(intValue = 600))
    private static int ecology$useConfiguredFreshReleaseTicks(int vanillaTicks) {
        return EcologyConfig.FRESH_HIVE_RELEASE_TICKS.get();
    }
}
