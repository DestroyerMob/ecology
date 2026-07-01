package com.destroyermob.ecology.mixin;

import com.destroyermob.ecology.village.VillageHouseholdHolder;
import com.destroyermob.ecology.village.VillageMarketStallHolder;
import com.destroyermob.ecology.village.VillageRelocationHolder;
import com.destroyermob.ecology.village.VillageTradeboardHolder;
import com.destroyermob.ecology.village.VillageWelfareHolder;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Villager.class)
public abstract class VillagerWelfareMixin implements VillageWelfareHolder, VillageMarketStallHolder, VillageRelocationHolder, VillageTradeboardHolder, VillageHouseholdHolder {
    @Unique
    private static final String ECOLOGY_CONFINEMENT_PRESSURE_TAG = "EcologyConfinementPressure";
    @Unique
    private static final String ECOLOGY_MARKET_STALL_DIMENSION_TAG = "EcologyMarketStallDimension";
    @Unique
    private static final String ECOLOGY_MARKET_STALL_POS_TAG = "EcologyMarketStallPos";
    @Unique
    private static final String ECOLOGY_RELOCATION_GUIDE_TAG = "EcologyRelocationGuide";
    @Unique
    private static final String ECOLOGY_RELOCATION_TARGET_DIMENSION_TAG = "EcologyRelocationTargetDimension";
    @Unique
    private static final String ECOLOGY_RELOCATION_TARGET_POS_TAG = "EcologyRelocationTargetPos";
    @Unique
    private static final String ECOLOGY_TRADEBOARD_DIMENSION_TAG = "EcologyTradeboardDimension";
    @Unique
    private static final String ECOLOGY_TRADEBOARD_POS_TAG = "EcologyTradeboardPos";
    @Unique
    private static final String ECOLOGY_TRADE_INPUT_DIMENSION_TAG = "EcologyTradeInputDimension";
    @Unique
    private static final String ECOLOGY_TRADE_INPUT_POS_TAG = "EcologyTradeInputPos";
    @Unique
    private static final String ECOLOGY_TRADE_OUTPUT_DIMENSION_TAG = "EcologyTradeOutputDimension";
    @Unique
    private static final String ECOLOGY_TRADE_OUTPUT_POS_TAG = "EcologyTradeOutputPos";
    @Unique
    private static final String ECOLOGY_HOUSEHOLD_ID_TAG = "EcologyHouseholdId";
    @Unique
    private static final String ECOLOGY_FIRST_PARENT_ID_TAG = "EcologyFirstParentId";
    @Unique
    private static final String ECOLOGY_SECOND_PARENT_ID_TAG = "EcologySecondParentId";
    @Unique
    private static final String ECOLOGY_PARTNER_ID_TAG = "EcologyPartnerId";
    @Unique
    private int ecology$confinementPressure;
    @Unique
    private Optional<GlobalPos> ecology$marketStall = Optional.empty();
    @Unique
    private Optional<UUID> ecology$relocationGuide = Optional.empty();
    @Unique
    private Optional<GlobalPos> ecology$relocationTarget = Optional.empty();
    @Unique
    private Optional<GlobalPos> ecology$tradeboard = Optional.empty();
    @Unique
    private Optional<GlobalPos> ecology$tradeInput = Optional.empty();
    @Unique
    private Optional<GlobalPos> ecology$tradeOutput = Optional.empty();
    @Unique
    private Optional<UUID> ecology$householdId = Optional.empty();
    @Unique
    private Optional<UUID> ecology$firstParentId = Optional.empty();
    @Unique
    private Optional<UUID> ecology$secondParentId = Optional.empty();
    @Unique
    private Optional<UUID> ecology$partnerId = Optional.empty();

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void ecology$saveVillageWelfare(CompoundTag compound, CallbackInfo callback) {
        compound.putInt(ECOLOGY_CONFINEMENT_PRESSURE_TAG, ecology$confinementPressure);
        ecology$marketStall().ifPresent(stall -> {
            compound.putString(ECOLOGY_MARKET_STALL_DIMENSION_TAG, stall.dimension().location().toString());
            compound.putLong(ECOLOGY_MARKET_STALL_POS_TAG, stall.pos().asLong());
        });
        ecology$relocationGuide().ifPresent(guide -> compound.putUUID(ECOLOGY_RELOCATION_GUIDE_TAG, guide));
        ecology$relocationTarget().ifPresent(target -> {
            compound.putString(ECOLOGY_RELOCATION_TARGET_DIMENSION_TAG, target.dimension().location().toString());
            compound.putLong(ECOLOGY_RELOCATION_TARGET_POS_TAG, target.pos().asLong());
        });
        ecology$tradeboard().ifPresent(tradeboard -> ecology$writeGlobalPos(compound, ECOLOGY_TRADEBOARD_DIMENSION_TAG, ECOLOGY_TRADEBOARD_POS_TAG, tradeboard));
        ecology$tradeInput().ifPresent(input -> ecology$writeGlobalPos(compound, ECOLOGY_TRADE_INPUT_DIMENSION_TAG, ECOLOGY_TRADE_INPUT_POS_TAG, input));
        ecology$tradeOutput().ifPresent(output -> ecology$writeGlobalPos(compound, ECOLOGY_TRADE_OUTPUT_DIMENSION_TAG, ECOLOGY_TRADE_OUTPUT_POS_TAG, output));
        ecology$householdId().ifPresent(id -> compound.putUUID(ECOLOGY_HOUSEHOLD_ID_TAG, id));
        ecology$firstParentId().ifPresent(id -> compound.putUUID(ECOLOGY_FIRST_PARENT_ID_TAG, id));
        ecology$secondParentId().ifPresent(id -> compound.putUUID(ECOLOGY_SECOND_PARENT_ID_TAG, id));
        ecology$partnerId().ifPresent(id -> compound.putUUID(ECOLOGY_PARTNER_ID_TAG, id));
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void ecology$readVillageWelfare(CompoundTag compound, CallbackInfo callback) {
        if (compound.contains(ECOLOGY_CONFINEMENT_PRESSURE_TAG, 99)) {
            ecology$confinementPressure = compound.getInt(ECOLOGY_CONFINEMENT_PRESSURE_TAG);
        }
        if (compound.contains(ECOLOGY_MARKET_STALL_DIMENSION_TAG, 8) && compound.contains(ECOLOGY_MARKET_STALL_POS_TAG, 99)) {
            ResourceLocation dimension = ResourceLocation.tryParse(compound.getString(ECOLOGY_MARKET_STALL_DIMENSION_TAG));
            if (dimension != null) {
                ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, dimension);
                ecology$marketStall = Optional.of(GlobalPos.of(key, BlockPos.of(compound.getLong(ECOLOGY_MARKET_STALL_POS_TAG))));
            }
        } else {
            ecology$marketStall = Optional.empty();
        }
        ecology$relocationGuide = compound.hasUUID(ECOLOGY_RELOCATION_GUIDE_TAG)
                ? Optional.of(compound.getUUID(ECOLOGY_RELOCATION_GUIDE_TAG))
                : Optional.empty();
        if (compound.contains(ECOLOGY_RELOCATION_TARGET_DIMENSION_TAG, 8) && compound.contains(ECOLOGY_RELOCATION_TARGET_POS_TAG, 99)) {
            ResourceLocation dimension = ResourceLocation.tryParse(compound.getString(ECOLOGY_RELOCATION_TARGET_DIMENSION_TAG));
            if (dimension != null) {
                ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, dimension);
                ecology$relocationTarget = Optional.of(GlobalPos.of(key, BlockPos.of(compound.getLong(ECOLOGY_RELOCATION_TARGET_POS_TAG))));
            }
        } else {
            ecology$relocationTarget = Optional.empty();
        }
        ecology$tradeboard = ecology$readGlobalPos(compound, ECOLOGY_TRADEBOARD_DIMENSION_TAG, ECOLOGY_TRADEBOARD_POS_TAG);
        ecology$tradeInput = ecology$readGlobalPos(compound, ECOLOGY_TRADE_INPUT_DIMENSION_TAG, ECOLOGY_TRADE_INPUT_POS_TAG);
        ecology$tradeOutput = ecology$readGlobalPos(compound, ECOLOGY_TRADE_OUTPUT_DIMENSION_TAG, ECOLOGY_TRADE_OUTPUT_POS_TAG);
        ecology$householdId = ecology$readUuid(compound, ECOLOGY_HOUSEHOLD_ID_TAG);
        ecology$firstParentId = ecology$readUuid(compound, ECOLOGY_FIRST_PARENT_ID_TAG);
        ecology$secondParentId = ecology$readUuid(compound, ECOLOGY_SECOND_PARENT_ID_TAG);
        ecology$partnerId = ecology$readUuid(compound, ECOLOGY_PARTNER_ID_TAG);
    }

    @Override
    public int ecology$getConfinementPressure() {
        return ecology$confinementPressure;
    }

    @Override
    public void ecology$setConfinementPressure(int pressure) {
        ecology$confinementPressure = pressure;
    }

    @Override
    public Optional<GlobalPos> ecology$getMarketStall() {
        return ecology$marketStall();
    }

    @Override
    public void ecology$setMarketStall(Optional<GlobalPos> stall) {
        ecology$marketStall = ecology$normalize(stall);
    }

    @Override
    public Optional<UUID> ecology$getRelocationGuide() {
        return ecology$relocationGuide();
    }

    @Override
    public void ecology$setRelocationGuide(Optional<UUID> guide) {
        ecology$relocationGuide = ecology$normalize(guide);
    }

    @Override
    public Optional<GlobalPos> ecology$getRelocationTarget() {
        return ecology$relocationTarget();
    }

    @Override
    public void ecology$setRelocationTarget(Optional<GlobalPos> target) {
        ecology$relocationTarget = ecology$normalize(target);
    }

    @Override
    public Optional<GlobalPos> ecology$getTradeboard() {
        return ecology$tradeboard();
    }

    @Override
    public void ecology$setTradeboard(Optional<GlobalPos> tradeboard) {
        ecology$tradeboard = ecology$normalize(tradeboard);
    }

    @Override
    public Optional<GlobalPos> ecology$getTradeInput() {
        return ecology$tradeInput();
    }

    @Override
    public void ecology$setTradeInput(Optional<GlobalPos> input) {
        ecology$tradeInput = ecology$normalize(input);
    }

    @Override
    public Optional<GlobalPos> ecology$getTradeOutput() {
        return ecology$tradeOutput();
    }

    @Override
    public void ecology$setTradeOutput(Optional<GlobalPos> output) {
        ecology$tradeOutput = ecology$normalize(output);
    }

    @Override
    public Optional<UUID> ecology$getHouseholdId() {
        return ecology$householdId();
    }

    @Override
    public void ecology$setHouseholdId(Optional<UUID> householdId) {
        ecology$householdId = ecology$normalize(householdId);
    }

    @Override
    public Optional<UUID> ecology$getFirstParentId() {
        return ecology$firstParentId();
    }

    @Override
    public Optional<UUID> ecology$getSecondParentId() {
        return ecology$secondParentId();
    }

    @Override
    public void ecology$setParentIds(Optional<UUID> firstParentId, Optional<UUID> secondParentId) {
        ecology$firstParentId = ecology$normalize(firstParentId);
        ecology$secondParentId = ecology$normalize(secondParentId);
    }

    @Override
    public Optional<UUID> ecology$getPartnerId() {
        return ecology$partnerId();
    }

    @Override
    public void ecology$setPartnerId(Optional<UUID> partnerId) {
        ecology$partnerId = ecology$normalize(partnerId);
    }

    @Unique
    private Optional<GlobalPos> ecology$marketStall() {
        if (ecology$marketStall == null) {
            ecology$marketStall = Optional.empty();
        }
        return ecology$marketStall;
    }

    @Unique
    private Optional<UUID> ecology$relocationGuide() {
        if (ecology$relocationGuide == null) {
            ecology$relocationGuide = Optional.empty();
        }
        return ecology$relocationGuide;
    }

    @Unique
    private Optional<GlobalPos> ecology$relocationTarget() {
        if (ecology$relocationTarget == null) {
            ecology$relocationTarget = Optional.empty();
        }
        return ecology$relocationTarget;
    }

    @Unique
    private Optional<GlobalPos> ecology$tradeboard() {
        if (ecology$tradeboard == null) {
            ecology$tradeboard = Optional.empty();
        }
        return ecology$tradeboard;
    }

    @Unique
    private Optional<GlobalPos> ecology$tradeInput() {
        if (ecology$tradeInput == null) {
            ecology$tradeInput = Optional.empty();
        }
        return ecology$tradeInput;
    }

    @Unique
    private Optional<GlobalPos> ecology$tradeOutput() {
        if (ecology$tradeOutput == null) {
            ecology$tradeOutput = Optional.empty();
        }
        return ecology$tradeOutput;
    }

    @Unique
    private Optional<UUID> ecology$householdId() {
        if (ecology$householdId == null) {
            ecology$householdId = Optional.empty();
        }
        return ecology$householdId;
    }

    @Unique
    private Optional<UUID> ecology$firstParentId() {
        if (ecology$firstParentId == null) {
            ecology$firstParentId = Optional.empty();
        }
        return ecology$firstParentId;
    }

    @Unique
    private Optional<UUID> ecology$secondParentId() {
        if (ecology$secondParentId == null) {
            ecology$secondParentId = Optional.empty();
        }
        return ecology$secondParentId;
    }

    @Unique
    private Optional<UUID> ecology$partnerId() {
        if (ecology$partnerId == null) {
            ecology$partnerId = Optional.empty();
        }
        return ecology$partnerId;
    }

    @Unique
    private static <T> Optional<T> ecology$normalize(Optional<T> value) {
        return value == null ? Optional.empty() : value;
    }

    @Unique
    private static void ecology$writeGlobalPos(CompoundTag compound, String dimensionTag, String posTag, GlobalPos pos) {
        compound.putString(dimensionTag, pos.dimension().location().toString());
        compound.putLong(posTag, pos.pos().asLong());
    }

    @Unique
    private static Optional<GlobalPos> ecology$readGlobalPos(CompoundTag compound, String dimensionTag, String posTag) {
        if (!compound.contains(dimensionTag, 8) || !compound.contains(posTag, 99)) {
            return Optional.empty();
        }
        ResourceLocation dimension = ResourceLocation.tryParse(compound.getString(dimensionTag));
        if (dimension == null) {
            return Optional.empty();
        }
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, dimension);
        return Optional.of(GlobalPos.of(key, BlockPos.of(compound.getLong(posTag))));
    }

    @Unique
    private static Optional<UUID> ecology$readUuid(CompoundTag compound, String tag) {
        return compound.hasUUID(tag) ? Optional.of(compound.getUUID(tag)) : Optional.empty();
    }
}
