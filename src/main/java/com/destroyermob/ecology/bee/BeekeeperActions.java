package com.destroyermob.ecology.bee;

import com.destroyermob.ecology.EcologyConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;

final class BeekeeperActions {
    private BeekeeperActions() {
    }

    static BeekeeperActionCheck queenCellHarvestCheck(ServerLevel level, BeehiveBlockEntity hive) {
        return switch (ColonySwarming.queenCellReadiness(level, hive)) {
            case NO_COLONY -> BeekeeperActionCheck.deny("message.ecology.queen_cell.no_colony");
            case MISSING_QUEEN -> BeekeeperActionCheck.deny("message.ecology.queen_cell.missing_queen");
            case NEEDS_WORKERS -> BeekeeperActionCheck.deny("message.ecology.queen_cell.needs_workers");
            case UNHEALTHY -> BeekeeperActionCheck.deny("message.ecology.queen_cell.unhealthy");
            case LOW_HEALTH -> BeekeeperActionCheck.deny("message.ecology.queen_cell.low_health");
            case NO_BROOD_NEED -> BeekeeperActionCheck.deny("message.ecology.queen_cell.no_brood_need");
            case READY -> BeekeeperActionCheck.allow("message.ecology.queen_cell.ready_to_harvest");
        };
    }

    static BeekeeperActionCheck queenCellInstallCheck(BeehiveBlockEntity hive, CompoundTag cellData) {
        if (!cellData.contains(BeeDataKeys.COLONY, Tag.TAG_COMPOUND)) {
            return BeekeeperActionCheck.deny("message.ecology.queen_cell.install_not_prepared");
        }
        if (!ColonySwarming.canInstallQueenCell(hive, cellData)) {
            return BeekeeperActionCheck.deny("message.ecology.queen_cell.install_not_empty");
        }
        return BeekeeperActionCheck.allow("message.ecology.queen_cell.install_ready");
    }

    static BeekeeperActionCheck swarmLureCheck(ServerLevel level, BeehiveBlockEntity hive) {
        ColonyData colony = EcologyBeeSystem.colony(hive);
        long day = EcologyBeeSystem.colonyDay(level, colony);
        ColonySwarmReadiness readiness = ColonySwarming.readiness(level, hive.getBlockPos(), colony, day);
        if (!readiness.ready()) {
            return swarmReadinessMessage(readiness);
        }
        if (EcologyBeeSystem.findEmptyHiveNear(level, hive.getBlockPos(), EcologyConfig.HIVE_SEARCH_RANGE.get()).isEmpty()) {
            return BeekeeperActionCheck.deny("message.ecology.swarm_lure.no_empty_hive");
        }
        return BeekeeperActionCheck.allow("message.ecology.swarm_lure.ready");
    }

    private static BeekeeperActionCheck swarmReadinessMessage(ColonySwarmReadiness readiness) {
        return switch (readiness) {
            case FEATURE_DISABLED -> BeekeeperActionCheck.deny("message.ecology.bee_feature_disabled");
            case NO_COLONY -> BeekeeperActionCheck.deny("message.ecology.swarm_lure.no_colony");
            case QUEEN_EXCLUDED -> BeekeeperActionCheck.deny("message.ecology.swarm_lure.queen_excluder");
            case MISSING_QUEEN -> BeekeeperActionCheck.deny("message.ecology.swarm_lure.missing_queen");
            case NEEDS_WORKERS -> BeekeeperActionCheck.deny("message.ecology.swarm_lure.needs_workers");
            case UNHEALTHY -> BeekeeperActionCheck.deny("message.ecology.swarm_lure.unhealthy");
            case COOLDOWN -> BeekeeperActionCheck.deny("message.ecology.swarm_lure.cooldown");
            case NOT_CROWDED -> BeekeeperActionCheck.deny("message.ecology.swarm_lure.not_crowded");
            case INBRED -> BeekeeperActionCheck.deny("message.ecology.swarm_lure.inbred");
            case LOW_FORAGE -> BeekeeperActionCheck.deny("message.ecology.swarm_lure.low_forage");
            case READY -> BeekeeperActionCheck.allow("message.ecology.swarm_lure.ready");
        };
    }
}
