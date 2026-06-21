package com.destroyermob.ecology.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.animal.Bee;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Bee.class)
public interface BeeAccessor {
    @Invoker("setHasNectar")
    void ecology$setHasNectar(boolean hasNectar);

    @Invoker("setHasStung")
    void ecology$setHasStung(boolean hasStung);

    @Invoker("pathfindRandomlyTowards")
    void ecology$pathfindRandomlyTowards(BlockPos pos);
}
