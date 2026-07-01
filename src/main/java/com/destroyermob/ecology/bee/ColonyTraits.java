package com.destroyermob.ecology.bee;

import com.destroyermob.ecology.EcologyConfig;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import org.jetbrains.annotations.Nullable;

public final class ColonyTraits {
    private ColonyTraits() {
    }

    public static ListTag toListTag(ColonyData colony) {
        ListTag traits = new ListTag();
        colony.traits().forEach(trait -> traits.add(StringTag.valueOf(trait.serializedName())));
        return traits;
    }

    static boolean ensureInitialTraits(ServerLevel level, ColonyData colony) {
        if (!EcologyConfig.colonyTraitsEnabled() || !EcologyBeeSystem.hasColonyState(colony) || !colony.traits().isEmpty()) {
            return false;
        }
        colony.addTrait(ColonyTrait.random(level.getRandom()));
        if (level.getRandom().nextFloat() < 0.25F) {
            colony.addTrait(ColonyTrait.random(level.getRandom()));
        }
        return true;
    }

    @Nullable
    static ColonyTrait randomMutation(RandomSource random, ColonyData sourceColony) {
        if (!EcologyConfig.colonyTraitsEnabled() || random.nextFloat() >= 0.20F) {
            return null;
        }
        for (int attempt = 0; attempt < ColonyTrait.values().length; attempt++) {
            ColonyTrait trait = ColonyTrait.random(random);
            if (!sourceColony.hasTrait(trait)) {
                return trait;
            }
        }
        return null;
    }

    public static int routePairLimit(ServerLevel level, BeeMemory memory) {
        int limit = EcologyConfig.MAX_ROUTE_PAIRS.get();
        if (memory.homeHive() != null && level.getBlockEntity(memory.homeHive()) instanceof BeehiveBlockEntity hive) {
            ColonyData colony = EcologyBeeSystem.colony(hive);
            long day = EcologyBeeSystem.colonyDay(level, colony);
            if (colony.hasTrait(ColonyTrait.INDUSTRIOUS)) {
                limit += 2;
            }
            if (colony.hasApiarySupport(day)) {
                limit += 1;
            }
        }
        return Math.max(1, limit);
    }

    public static int routeAgitationThreshold(ServerLevel level, BeeMemory memory) {
        int threshold = EcologyConfig.ROUTE_AGITATION_ATTACK_TICKS.get();
        if (memory.homeHive() != null && level.getBlockEntity(memory.homeHive()) instanceof BeehiveBlockEntity hive) {
            ColonyData colony = EcologyBeeSystem.colony(hive);
            if (colony.hasTrait(ColonyTrait.CALM)) {
                threshold += 40;
            }
            if (colony.isCalmed(EcologyBeeSystem.colonyDay(level, colony))) {
                threshold += 80;
            }
        }
        return Math.max(20, threshold);
    }

    public static int lifespanDays(ServerLevel level, BeeMemory memory) {
        int lifespan = memory.role().lifespanDays();
        if (memory.homeHive() != null && level.getBlockEntity(memory.homeHive()) instanceof BeehiveBlockEntity hive
                && EcologyBeeSystem.colony(hive).hasTrait(ColonyTrait.LONG_LIVED)) {
            lifespan += Math.max(1, lifespan / 3);
        }
        return lifespan;
    }

    static int effectiveLifespanDays(ColonyData colony, BeeRole role) {
        int lifespan = role.lifespanDays();
        return colony.hasTrait(ColonyTrait.LONG_LIVED) ? lifespan + Math.max(1, lifespan / 3) : lifespan;
    }
}
