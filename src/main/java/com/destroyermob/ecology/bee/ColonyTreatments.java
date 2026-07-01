package com.destroyermob.ecology.bee;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;

final class ColonyTreatments {
    private ColonyTreatments() {
    }

    static boolean apply(ServerLevel level, BeehiveBlockEntity hive, ApiaryTreatment treatment) {
        ColonyData colony = EcologyBeeSystem.colony(hive);
        if (!EcologyBeeSystem.hasColonyState(colony)) {
            return false;
        }
        long day = EcologyBeeSystem.colonyDay(level, colony);
        switch (treatment) {
            case SMOKE -> colony.setCalmedUntilDay(Math.max(colony.calmedUntilDay(), day + 1));
            case HIVE_STAND -> colony.setSupportedUntilDay(Math.max(colony.supportedUntilDay(), day + 14));
            case QUEEN_EXCLUDER -> colony.setQueenExcluderUntilDay(Math.max(colony.queenExcluderUntilDay(), day + 14));
            case BROOD_FRAME -> {
                colony.setSupportedUntilDay(Math.max(colony.supportedUntilDay(), day + 3));
                EcologyBeeSystem.tryProduceLogicalChild(level, colony, day);
            }
        }
        hive.setChanged();
        return true;
    }
}
