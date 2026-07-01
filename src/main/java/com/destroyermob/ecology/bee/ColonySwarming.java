package com.destroyermob.ecology.bee;

import com.destroyermob.ecology.EcologyConfig;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;

final class ColonySwarming {
    private static final int QUEEN_CELL_SPLIT_WORKERS = 1;

    private ColonySwarming() {
    }

    static QueenCellReadiness queenCellReadiness(ServerLevel level, BeehiveBlockEntity hive) {
        ColonyData colony = EcologyBeeSystem.colony(hive);
        long day = EcologyBeeSystem.colonyDay(level, colony);
        if (!EcologyBeeSystem.hasColonyState(colony)) {
            return QueenCellReadiness.NO_COLONY;
        }
        if (colony.queenId() == null) {
            return QueenCellReadiness.MISSING_QUEEN;
        }
        if (colony.workerIds().size() < 2) {
            return QueenCellReadiness.NEEDS_WORKERS;
        }
        if (colony.abandoned() || colony.doomed()) {
            return QueenCellReadiness.UNHEALTHY;
        }
        if (EcologyBeeSystem.colonyHealth(level, hive.getBlockPos(), colony).score() < 65) {
            return QueenCellReadiness.LOW_HEALTH;
        }
        if (EcologyBeeSystem.nextNeededRole(colony, day).isEmpty()) {
            return QueenCellReadiness.NO_BROOD_NEED;
        }
        return QueenCellReadiness.READY;
    }

    static boolean canHarvestQueenCell(ServerLevel level, BeehiveBlockEntity hive) {
        return queenCellReadiness(level, hive).ready();
    }

    static CompoundTag createQueenCellData(ServerLevel level, BeehiveBlockEntity hive) {
        EcologyBeeSystem.tickHiveColony(level, hive);
        ColonyData colony = EcologyBeeSystem.colony(hive);
        CompoundTag tag = new CompoundTag();
        tag.put(BeeDataKeys.COLONY, colony.serializeNBT(level.registryAccess()));
        tag.putString(BeeDataKeys.LINEAGE_ID, colony.lineageId().toString());
        tag.put(BeeDataKeys.TRAITS, ColonyTraits.toListTag(colony));
        tag.putInt(BeeDataKeys.INBREEDING_PERCENT, (int) Math.round(colony.inbreedingCoefficient() * 100.0));
        tag.putLong(BeeDataKeys.SOURCE_HIVE, hive.getBlockPos().asLong());
        tag.putLong(BeeDataKeys.ORIGINAL_HOME_HIVE, hive.getBlockPos().asLong());
        tag.putLong(BeeDataKeys.CREATED_DAY, EcologyBeeSystem.day(level));
        return tag;
    }

    static boolean installQueenCell(ServerLevel level, BeehiveBlockEntity hive, BlockPos hivePos, CompoundTag cellData) {
        if (!canInstallQueenCell(hive, cellData)) {
            return false;
        }

        ColonyData colony = EcologyBeeSystem.colony(hive);
        ColonyData sourceColony = new ColonyData();
        sourceColony.deserializeNBT(level.registryAccess(), cellData.getCompound(BeeDataKeys.COLONY));
        colony.clear();
        colony.inheritDaughterGeneticsFrom(sourceColony, ColonyTraits.randomMutation(level.getRandom(), sourceColony));
        long currentDay = EcologyBeeSystem.day(level);
        colony.setLastSimulatedDay(currentDay);
        if (!EcologyBeeSystem.installStoredBee(level, hive, hivePos, BeeRole.QUEEN, currentDay, null)) {
            return false;
        }
        for (int index = 0; index < QUEEN_CELL_SPLIT_WORKERS; index++) {
            if (!EcologyBeeSystem.installStoredBee(level, hive, hivePos, BeeRole.WORKER, currentDay, null)) {
                break;
            }
        }
        hive.setChanged();
        return true;
    }

    static boolean canInstallQueenCell(BeehiveBlockEntity hive, CompoundTag cellData) {
        ColonyData colony = EcologyBeeSystem.colony(hive);
        return cellData.contains(BeeDataKeys.COLONY, Tag.TAG_COMPOUND)
                && hive.isEmpty()
                && colony.totalBees() == 0;
    }

    static boolean forceSwarm(ServerLevel level, BeehiveBlockEntity hive) {
        ColonyData colony = EcologyBeeSystem.colony(hive);
        return trySwarm(level, hive, colony, EcologyBeeSystem.colonyDay(level, colony), true);
    }

    static boolean trySwarm(ServerLevel level, BeehiveBlockEntity sourceHive, ColonyData sourceColony, long day, boolean forced) {
        if (!readiness(level, sourceHive.getBlockPos(), sourceColony, day).ready()) {
            return false;
        }

        double chance = EcologyConfig.SWARMING_CHANCE.get();
        if (sourceColony.hasTrait(ColonyTrait.RESTLESS)) {
            chance = Math.min(1.0, chance + 0.15);
        }
        if (!forced && level.getRandom().nextDouble() >= chance) {
            return false;
        }

        Optional<BlockPos> targetPos = EcologyBeeSystem.findEmptyHiveNear(level, sourceHive.getBlockPos(), EcologyConfig.HIVE_SEARCH_RANGE.get());
        if (targetPos.isEmpty() || !(level.getBlockEntity(targetPos.get()) instanceof BeehiveBlockEntity targetHive)) {
            return false;
        }

        ColonyData daughter = EcologyBeeSystem.colony(targetHive);
        daughter.clear();
        daughter.inheritDaughterGeneticsFrom(sourceColony, ColonyTraits.randomMutation(level.getRandom(), sourceColony));
        daughter.setLastSimulatedDay(day);
        if (!EcologyBeeSystem.installStoredBee(level, targetHive, targetPos.get(), BeeRole.QUEEN, day, null)) {
            daughter.clear();
            return false;
        }
        EcologyBeeSystem.installStoredBee(level, targetHive, targetPos.get(), BeeRole.WORKER, day, null);
        sourceColony.setLastSwarmDay(day);
        sourceHive.setChanged();
        targetHive.setChanged();
        level.playSound(null, sourceHive.getBlockPos(), SoundEvents.BEEHIVE_WORK, SoundSource.BLOCKS, 1.0F, 1.35F);
        level.playSound(null, targetPos.get(), SoundEvents.BEEHIVE_ENTER, SoundSource.BLOCKS, 1.0F, 1.1F);
        return true;
    }

    static boolean isReady(Level level, BlockPos hivePos, ColonyData colony, long day) {
        return readiness(level, hivePos, colony, day).ready();
    }

    static ColonySwarmReadiness readiness(Level level, BlockPos hivePos, ColonyData colony, long day) {
        if (!EcologyConfig.swarmingEnabled()) {
            return ColonySwarmReadiness.FEATURE_DISABLED;
        }
        if (!EcologyBeeSystem.hasColonyState(colony)) {
            return ColonySwarmReadiness.NO_COLONY;
        }
        if (colony.hasQueenExcluder(day)) {
            return ColonySwarmReadiness.QUEEN_EXCLUDED;
        }
        if (colony.queenId() == null) {
            return ColonySwarmReadiness.MISSING_QUEEN;
        }
        if (colony.workerIds().size() < 2) {
            return ColonySwarmReadiness.NEEDS_WORKERS;
        }
        if (colony.abandoned() || colony.doomed()) {
            return ColonySwarmReadiness.UNHEALTHY;
        }
        if (colony.lastSwarmDay() >= 0 && day - colony.lastSwarmDay() < EcologyConfig.SWARM_COOLDOWN_DAYS.get()) {
            return ColonySwarmReadiness.COOLDOWN;
        }

        boolean crowded = colony.totalBees() >= EcologyConfig.hiveCapacity()
                || colony.workerIds().size() >= EcologyConfig.MAX_WORKERS_PER_HIVE.get();
        boolean restless = colony.hasTrait(ColonyTrait.RESTLESS)
                && colony.workerIds().size() >= Math.max(2, EcologyConfig.MAX_WORKERS_PER_HIVE.get() - 1);
        if (!crowded && !restless) {
            return ColonySwarmReadiness.NOT_CROWDED;
        }
        if (colony.inbreedingCoefficient() >= 0.75) {
            return ColonySwarmReadiness.INBRED;
        }
        if (EcologyBeeSystem.nearbyForageCount(level, hivePos, EcologyBeeSystem.HIVE_HEALTH_MIN_FORAGE) < Math.max(2, EcologyBeeSystem.HIVE_HEALTH_MIN_FORAGE / 2)) {
            return ColonySwarmReadiness.LOW_FORAGE;
        }
        return ColonySwarmReadiness.READY;
    }
}
