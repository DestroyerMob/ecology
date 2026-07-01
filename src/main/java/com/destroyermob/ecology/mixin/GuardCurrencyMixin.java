package com.destroyermob.ecology.mixin;

import com.destroyermob.ecology.village.VillageCurrency;
import com.destroyermob.ecology.village.VillageCurrencyHolder;
import com.destroyermob.ecology.village.VillageCurrencySystem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "tallestegg.guardvillagers.common.entities.Guard", remap = false)
public abstract class GuardCurrencyMixin implements VillageCurrencyHolder {
    @Unique
    private static final String ECOLOGY_GUARD_CURRENCY_TAG = "EcologyVillageCurrency";
    @Unique
    private static final EntityDataAccessor<String> ECOLOGY_GUARD_CURRENCY =
            SynchedEntityData.defineId(ecology$guardClass(), EntityDataSerializers.STRING);

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Unique
    private static Class<? extends Entity> ecology$guardClass() {
        try {
            return (Class<? extends Entity>)(Class)Class.forName(
                    "tallestegg.guardvillagers.common.entities.Guard",
                    false,
                    GuardCurrencyMixin.class.getClassLoader());
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("Guard Villagers guard class was unavailable while applying Ecology compat", exception);
        }
    }

    @Inject(method = "defineSynchedData", at = @At("TAIL"))
    private void ecology$defineGuardCurrency(SynchedEntityData.Builder builder, CallbackInfo callback) {
        builder.define(ECOLOGY_GUARD_CURRENCY, VillageCurrency.EMERALD.serializedName());
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void ecology$saveGuardCurrency(CompoundTag compound, CallbackInfo callback) {
        compound.putString(ECOLOGY_GUARD_CURRENCY_TAG, ecology$getVillageCurrency().serializedName());
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void ecology$readGuardCurrency(CompoundTag compound, CallbackInfo callback) {
        if (compound.contains(ECOLOGY_GUARD_CURRENCY_TAG, 8)) {
            ecology$setVillageCurrency(VillageCurrency.byName(compound.getString(ECOLOGY_GUARD_CURRENCY_TAG)));
        }
    }

    @Inject(method = "finalizeSpawn", at = @At("RETURN"))
    private void ecology$assignGuardCurrencyOnSpawn(
            ServerLevelAccessor level,
            DifficultyInstance difficulty,
            MobSpawnType spawnType,
            SpawnGroupData spawnGroupData,
            CallbackInfoReturnable<SpawnGroupData> callback) {
        if (level instanceof ServerLevel serverLevel) {
            VillageCurrencySystem.assignCurrency(serverLevel, (Entity)(Object)this);
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void ecology$refreshGuardCurrency(CallbackInfo callback) {
        Entity entity = (Entity)(Object)this;
        if (entity.level() instanceof ServerLevel serverLevel) {
            VillageCurrencySystem.tickVillageEntity(serverLevel, entity);
        }
    }

    @Override
    public VillageCurrency ecology$getVillageCurrency() {
        return VillageCurrency.byName(((Entity)(Object)this).getEntityData().get(ECOLOGY_GUARD_CURRENCY));
    }

    @Override
    public void ecology$setVillageCurrency(VillageCurrency currency) {
        ((Entity)(Object)this).getEntityData().set(ECOLOGY_GUARD_CURRENCY, currency.serializedName());
    }
}
