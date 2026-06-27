package com.destroyermob.ecology.mixin;

import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.world.level.block.entity.BeehiveBlockEntity$BeeData")
public interface BeehiveBeeDataAccessor {
    @Accessor("occupant")
    BeehiveBlockEntity.Occupant ecology$getOccupant();

    @Mutable
    @Accessor("occupant")
    void ecology$setOccupant(BeehiveBlockEntity.Occupant occupant);

    @Accessor("ticksInHive")
    int ecology$getTicksInHive();

    @Accessor("ticksInHive")
    void ecology$setTicksInHive(int ticksInHive);
}
