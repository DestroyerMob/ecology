package com.destroyermob.ecology.mixin;

import com.destroyermob.ecology.item.VillageLedgerItem;
import com.destroyermob.ecology.registry.EcologyItems;
import com.destroyermob.ecology.village.VillageCurrency;
import com.destroyermob.ecology.village.VillageCurrencyHolder;
import com.destroyermob.ecology.village.VillageCurrencySystem;
import com.destroyermob.ecology.village.VillageVocations;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Villager.class)
public abstract class VillagerCurrencyMixin implements VillageCurrencyHolder {
    @Unique
    private static final String ECOLOGY_VILLAGE_CURRENCY_TAG = "EcologyVillageCurrency";
    @Unique
    private static final EntityDataAccessor<String> ECOLOGY_VILLAGE_CURRENCY =
            SynchedEntityData.defineId(Villager.class, EntityDataSerializers.STRING);

    @Inject(method = "defineSynchedData", at = @At("TAIL"))
    private void ecology$defineVillageCurrency(SynchedEntityData.Builder builder, CallbackInfo callback) {
        builder.define(ECOLOGY_VILLAGE_CURRENCY, VillageCurrency.EMERALD.serializedName());
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void ecology$saveVillageCurrency(CompoundTag compound, CallbackInfo callback) {
        compound.putString(ECOLOGY_VILLAGE_CURRENCY_TAG, ecology$getVillageCurrency().serializedName());
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void ecology$readVillageCurrency(CompoundTag compound, CallbackInfo callback) {
        if (compound.contains(ECOLOGY_VILLAGE_CURRENCY_TAG, 8)) {
            ecology$setVillageCurrency(VillageCurrency.byName(compound.getString(ECOLOGY_VILLAGE_CURRENCY_TAG)));
        }
    }

    @Inject(method = "updateTrades", at = @At("TAIL"))
    private void ecology$convertVillageCurrencyTrades(CallbackInfo callback) {
        Villager villager = (Villager)(Object)this;
        VillageCurrencySystem.convertOffers(villager);
    }

    @Inject(method = "mobInteract", at = @At("HEAD"), cancellable = true)
    private void ecology$prepareVillageCurrencyTrade(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> callback) {
        Villager villager = (Villager)(Object)this;
        ItemStack heldStack = player.getItemInHand(hand);
        if (heldStack.is(Items.VILLAGER_SPAWN_EGG)) {
            return;
        }
        if (heldStack.is(EcologyItems.VILLAGE_LEDGER.get()) && VillageLedgerItem.hasVillagerAction(heldStack, player)) {
            if (villager.level().isClientSide) {
                callback.setReturnValue(InteractionResult.SUCCESS);
                return;
            }
            InteractionResult result = VillageLedgerItem.interactWithVillager(heldStack, player, villager);
            if (result.consumesAction()) {
                callback.setReturnValue(result);
                return;
            }
        }
        if (!villager.level().isClientSide && !villager.isBaby() && villager.level() instanceof ServerLevel level) {
            VillageVocations.prepareForTrading(level, villager);
        }
    }

    @Inject(method = "getBreedOffspring", at = @At("RETURN"))
    private void ecology$inheritVillageCurrency(ServerLevel level, AgeableMob otherParent, CallbackInfoReturnable<Villager> callback) {
        Villager child = callback.getReturnValue();
        if (child != null) {
            VillageCurrencySystem.inheritCurrency(child, (Villager)(Object)this, otherParent);
        }
    }

    @Inject(method = "finalizeSpawn", at = @At("RETURN"))
    private void ecology$assignVillageCurrencyOnSpawn(
            ServerLevelAccessor level,
            DifficultyInstance difficulty,
            MobSpawnType spawnType,
            SpawnGroupData spawnGroupData,
            CallbackInfoReturnable<SpawnGroupData> callback) {
        if (level instanceof ServerLevel serverLevel) {
            VillageCurrencySystem.assignCurrency(serverLevel, (Villager)(Object)this);
        }
    }

    @Override
    public VillageCurrency ecology$getVillageCurrency() {
        return VillageCurrency.byName(((Villager)(Object)this).getEntityData().get(ECOLOGY_VILLAGE_CURRENCY));
    }

    @Override
    public void ecology$setVillageCurrency(VillageCurrency currency) {
        ((Villager)(Object)this).getEntityData().set(ECOLOGY_VILLAGE_CURRENCY, currency.serializedName());
    }
}
