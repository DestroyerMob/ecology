package com.destroyermob.ecology.mixin;

import com.destroyermob.ecology.village.VillageVocationHolder;
import com.destroyermob.ecology.village.VillageVocations;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Villager.class)
public abstract class VillagerVocationMixin implements VillageVocationHolder {
    @Unique
    private static final String ECOLOGY_FIRST_PARENT_PROFESSION_TAG = "EcologyFirstParentProfession";
    @Unique
    private static final String ECOLOGY_SECOND_PARENT_PROFESSION_TAG = "EcologySecondParentProfession";
    @Unique
    private static final String ECOLOGY_DESIRED_PROFESSION_TAG = "EcologyDesiredProfession";
    @Unique
    private Optional<VillagerProfession> ecology$firstParentProfession = Optional.empty();
    @Unique
    private Optional<VillagerProfession> ecology$secondParentProfession = Optional.empty();
    @Unique
    private Optional<VillagerProfession> ecology$desiredProfession = Optional.empty();

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void ecology$saveVocationData(CompoundTag compound, CallbackInfo callback) {
        ecology$firstParentProfession
                .map(VillageVocations::professionName)
                .ifPresent(name -> compound.putString(ECOLOGY_FIRST_PARENT_PROFESSION_TAG, name));
        ecology$secondParentProfession
                .map(VillageVocations::professionName)
                .ifPresent(name -> compound.putString(ECOLOGY_SECOND_PARENT_PROFESSION_TAG, name));
        ecology$desiredProfession
                .map(VillageVocations::professionName)
                .ifPresent(name -> compound.putString(ECOLOGY_DESIRED_PROFESSION_TAG, name));
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void ecology$readVocationData(CompoundTag compound, CallbackInfo callback) {
        ecology$firstParentProfession = professionFromTag(compound, ECOLOGY_FIRST_PARENT_PROFESSION_TAG);
        ecology$secondParentProfession = professionFromTag(compound, ECOLOGY_SECOND_PARENT_PROFESSION_TAG);
        ecology$desiredProfession = professionFromTag(compound, ECOLOGY_DESIRED_PROFESSION_TAG);
    }

    @Inject(method = "getBreedOffspring", at = @At("RETURN"))
    private void ecology$inheritVillageVocation(ServerLevel level, AgeableMob otherParent, CallbackInfoReturnable<Villager> callback) {
        Villager child = callback.getReturnValue();
        if (child != null) {
            VillageVocations.inheritParentProfessions(child, (Villager)(Object)this, otherParent);
        }
    }

    @Inject(method = "ageBoundaryReached", at = @At("TAIL"))
    private void ecology$assignVocationOnAdulthood(CallbackInfo callback) {
        Villager villager = (Villager)(Object)this;
        if (!villager.isBaby() && villager.level() instanceof ServerLevel level) {
            VillageVocations.assignIfNeeded(level, villager);
        }
    }

    @Override
    public Optional<VillagerProfession> ecology$getFirstParentProfession() {
        return ecology$firstParentProfession;
    }

    @Override
    public Optional<VillagerProfession> ecology$getSecondParentProfession() {
        return ecology$secondParentProfession;
    }

    @Override
    public void ecology$setParentProfessions(Optional<VillagerProfession> first, Optional<VillagerProfession> second) {
        ecology$firstParentProfession = first.filter(VillageVocations::isAssignableProfession);
        ecology$secondParentProfession = second.filter(VillageVocations::isAssignableProfession);
    }

    @Override
    public Optional<VillagerProfession> ecology$getDesiredProfession() {
        return ecology$desiredProfession;
    }

    @Override
    public void ecology$setDesiredProfession(Optional<VillagerProfession> profession) {
        ecology$desiredProfession = profession.filter(VillageVocations::isAssignableProfession);
    }

    @Unique
    private static Optional<VillagerProfession> professionFromTag(CompoundTag compound, String key) {
        if (!compound.contains(key, 8)) {
            return Optional.empty();
        }
        return VillageVocations.professionByName(compound.getString(key))
                .filter(VillageVocations::isAssignableProfession);
    }
}
