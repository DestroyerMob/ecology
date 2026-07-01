package com.destroyermob.ecology.mixin;

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
public abstract class VillagerWelfareMixin implements VillageWelfareHolder, VillageMarketStallHolder, VillageRelocationHolder, VillageTradeboardHolder {
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

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void ecology$saveVillageWelfare(CompoundTag compound, CallbackInfo callback) {
        compound.putInt(ECOLOGY_CONFINEMENT_PRESSURE_TAG, ecology$confinementPressure);
        ecology$marketStall.ifPresent(stall -> {
            compound.putString(ECOLOGY_MARKET_STALL_DIMENSION_TAG, stall.dimension().location().toString());
            compound.putLong(ECOLOGY_MARKET_STALL_POS_TAG, stall.pos().asLong());
        });
        ecology$relocationGuide.ifPresent(guide -> compound.putUUID(ECOLOGY_RELOCATION_GUIDE_TAG, guide));
        ecology$relocationTarget.ifPresent(target -> {
            compound.putString(ECOLOGY_RELOCATION_TARGET_DIMENSION_TAG, target.dimension().location().toString());
            compound.putLong(ECOLOGY_RELOCATION_TARGET_POS_TAG, target.pos().asLong());
        });
        ecology$tradeboard.ifPresent(tradeboard -> ecology$writeGlobalPos(compound, ECOLOGY_TRADEBOARD_DIMENSION_TAG, ECOLOGY_TRADEBOARD_POS_TAG, tradeboard));
        ecology$tradeInput.ifPresent(input -> ecology$writeGlobalPos(compound, ECOLOGY_TRADE_INPUT_DIMENSION_TAG, ECOLOGY_TRADE_INPUT_POS_TAG, input));
        ecology$tradeOutput.ifPresent(output -> ecology$writeGlobalPos(compound, ECOLOGY_TRADE_OUTPUT_DIMENSION_TAG, ECOLOGY_TRADE_OUTPUT_POS_TAG, output));
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
        return ecology$marketStall;
    }

    @Override
    public void ecology$setMarketStall(Optional<GlobalPos> stall) {
        ecology$marketStall = stall;
    }

    @Override
    public Optional<UUID> ecology$getRelocationGuide() {
        return ecology$relocationGuide;
    }

    @Override
    public void ecology$setRelocationGuide(Optional<UUID> guide) {
        ecology$relocationGuide = guide;
    }

    @Override
    public Optional<GlobalPos> ecology$getRelocationTarget() {
        return ecology$relocationTarget;
    }

    @Override
    public void ecology$setRelocationTarget(Optional<GlobalPos> target) {
        ecology$relocationTarget = target;
    }

    @Override
    public Optional<GlobalPos> ecology$getTradeboard() {
        return ecology$tradeboard;
    }

    @Override
    public void ecology$setTradeboard(Optional<GlobalPos> tradeboard) {
        ecology$tradeboard = tradeboard;
    }

    @Override
    public Optional<GlobalPos> ecology$getTradeInput() {
        return ecology$tradeInput;
    }

    @Override
    public void ecology$setTradeInput(Optional<GlobalPos> input) {
        ecology$tradeInput = input;
    }

    @Override
    public Optional<GlobalPos> ecology$getTradeOutput() {
        return ecology$tradeOutput;
    }

    @Override
    public void ecology$setTradeOutput(Optional<GlobalPos> output) {
        ecology$tradeOutput = output;
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
}
