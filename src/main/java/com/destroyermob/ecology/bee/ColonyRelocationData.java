package com.destroyermob.ecology.bee;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import org.jetbrains.annotations.Nullable;

final class ColonyRelocationData {
    private ColonyRelocationData() {
    }

    static CompoundTag createBroodCombData(ServerLevel level, BeehiveBlockEntity hive) {
        EcologyBeeSystem.tickHiveColony(level, hive);
        ColonyData colony = EcologyBeeSystem.colony(hive);
        long day = EcologyBeeSystem.colonyDay(level, colony);
        CompoundTag tag = new CompoundTag();
        tag.put(BeeDataKeys.COLONY, colony.serializeNBT(level.registryAccess()));
        tag.putString(BeeDataKeys.LINEAGE_ID, colony.lineageId().toString());
        tag.put(BeeDataKeys.TRAITS, ColonyTraits.toListTag(colony));
        tag.putInt(BeeDataKeys.INBREEDING_PERCENT, (int) Math.round(colony.inbreedingCoefficient() * 100.0));
        tag.putLong(BeeDataKeys.ORIGINAL_HOME_HIVE, hive.getBlockPos().asLong());
        tag.putBoolean(BeeDataKeys.HAS_QUEEN, colony.queenId() != null);
        tag.putInt(BeeDataKeys.WORKER_COUNT, colony.workerIds().size());
        tag.putInt(BeeDataKeys.DRONE_COUNT, colony.droneIds().size());
        tag.put(BeeDataKeys.WORKER_BIRTH_DAYS, workerBirthDays(colony));
        if (colony.queenBirthDay() >= 0) {
            tag.putLong(BeeDataKeys.QUEEN_AGE_DAYS, Math.max(0, day - colony.queenBirthDay()));
        }
        return tag;
    }

    @Nullable
    static BlockPos originalHomeHive(CompoundTag broodData) {
        return broodData.contains(BeeDataKeys.ORIGINAL_HOME_HIVE)
                ? BlockPos.of(broodData.getLong(BeeDataKeys.ORIGINAL_HOME_HIVE))
                : null;
    }

    static long takeWorkerBirthDay(CompoundTag broodData, long fallbackBirthDay) {
        ListTag birthDays = broodData.getList(BeeDataKeys.WORKER_BIRTH_DAYS, Tag.TAG_LONG);
        if (birthDays.isEmpty()) {
            return fallbackBirthDay;
        }

        long birthDay = birthDays.get(0) instanceof LongTag birthDayTag ? birthDayTag.getAsLong() : fallbackBirthDay;
        birthDays.remove(0);
        broodData.put(BeeDataKeys.WORKER_BIRTH_DAYS, birthDays);
        broodData.putInt(BeeDataKeys.WORKER_COUNT, Math.max(0, broodData.getInt(BeeDataKeys.WORKER_COUNT) - 1));
        return birthDay >= 0 ? birthDay : fallbackBirthDay;
    }

    private static ListTag workerBirthDays(ColonyData colony) {
        ListTag birthDays = new ListTag();
        for (UUID workerId : colony.workerIds()) {
            long birthDay = colony.birthDay(workerId);
            if (birthDay >= 0) {
                birthDays.add(LongTag.valueOf(birthDay));
            }
        }
        return birthDays;
    }
}
