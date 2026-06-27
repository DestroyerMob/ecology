package com.destroyermob.ecology.bee;

import com.destroyermob.ecology.Ecology;
import com.destroyermob.ecology.EcologyConfig;
import com.destroyermob.ecology.mixin.BeeAccessor;
import com.destroyermob.ecology.mixin.BeehiveBeeDataAccessor;
import com.destroyermob.ecology.mixin.BeehiveBlockEntityAccessor;
import com.destroyermob.ecology.network.EcologyNetworking;
import com.destroyermob.ecology.registry.EcologyAttachments;
import com.destroyermob.ecology.registry.EcologyItems;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import org.jetbrains.annotations.Nullable;

public final class EcologyBeeSystem {
    public static final TagKey<Block> POLLINATION_FLOWERS =
            TagKey.create(Registries.BLOCK, Ecology.id("pollination_flowers"));
    public static final TagKey<Block> POLLINATION_CROPS =
            TagKey.create(Registries.BLOCK, Ecology.id("pollination_crops"));
    private static final int HIVE_ROUTE_SCAN_HORIZONTAL_RADIUS = 7;
    private static final int HIVE_ROUTE_SCAN_VERTICAL_RADIUS = 2;
    private static final long HIVE_ROUTE_ASSIGNMENT_CACHE_TICKS = 24000L;
    private static final int SIMULATED_HIVE_DAY_TICKS = 24000;
    private static final int FLOWER_SEARCH_EXPANSION_STEP = 2;
    private static final int MAX_FLOWER_SEARCH_EXPANSION_FAILURES = 3;
    private static final Map<HiveRouteAssignmentKey, CachedHiveRouteTargets> HIVE_ROUTE_ASSIGNMENTS = new HashMap<>();

    private EcologyBeeSystem() {
    }

    public record HiveDaySimulationResult(boolean colonySimulated, long simulatedDay, int storedBeesAdvanced, int beesAged, int workersReadied) {
        public static HiveDaySimulationResult empty() {
            return new HiveDaySimulationResult(false, -1, 0, 0, 0);
        }
    }

    private record StoredBeeSimulationResult(int storedBeesAdvanced, int beesAged, int workersReadied) {
        private boolean hasChanges() {
            return storedBeesAdvanced > 0 || beesAged > 0 || workersReadied > 0;
        }
    }

    public static BeeMemory memory(Bee bee) {
        return bee.getData(EcologyAttachments.BEE_MEMORY.get());
    }

    public static ColonyData colony(BlockEntity blockEntity) {
        return blockEntity.getData(EcologyAttachments.COLONY.get());
    }

    public static long day(Level level) {
        return level.getDayTime() / 24000L;
    }

    public static long colonyDay(Level level, ColonyData colony) {
        return Math.max(day(level), colony.lastSimulatedDay());
    }

    public static long ageDays(Level level, BeeMemory memory) {
        if (memory.birthDay() < 0) {
            return 0;
        }
        return Math.max(0, day(level) - memory.birthDay() + memory.simulatedAgeDays());
    }

    public static void initializeBee(Bee bee) {
        if (!(bee.level() instanceof ServerLevel level)) {
            return;
        }

        BeeMemory memory = memory(bee);
        long day = day(level);
        if (memory.birthDay() < 0) {
            memory.setBirthDay(day);
        }

        if (memory.homeHive() == null && bee.hasHive()) {
            memory.setHomeHive(bee.getHivePos());
        }
        if (memory.homeHive() == null && EcologyConfig.REPLACE_VANILLA_BEE_GOALS.get()) {
            findLocalHive(level, memory, bee.blockPosition())
                    .ifPresent(memory::setHomeHive);
        }
        if (memory.homeHive() != null) {
            bee.setHivePos(memory.homeHive());
            if (EcologyConfig.ENABLE_HIVE_COLONY_TICKING.get()) {
                rememberAtHomeHive(level, memory);
            }
        }
        if (memory.role() == BeeRole.WORKER) {
            if (memory.routeDay() != day || shouldRetryEmptyCompletedRoute(memory)) {
                memory.resetDailyRoute(day);
                setPollenVisual(bee, false);
            }
            assignHiveRouteIfNeeded(level, bee, memory, day);
        }
    }

    public static void rememberAtHomeHive(ServerLevel level, BeeMemory memory) {
        if (!EcologyConfig.ENABLE_BEE_SYSTEM.get() || !EcologyConfig.ENABLE_HIVE_COLONY_TICKING.get() || memory.homeHive() == null) {
            return;
        }
        BlockEntity blockEntity = level.getBlockEntity(memory.homeHive());
        if (blockEntity instanceof BeehiveBlockEntity hive) {
            tickHiveColony(level, hive);
            ColonyData colony = colony(blockEntity);
            boolean changed = false;
            if (!hasColonyState(colony)) {
                changed |= seedStarterColony(level, hive, memory);
            }
            assignRoleIfNeeded(memory, colony);
            changed |= colony.remember(memory);
            if (changed) {
                blockEntity.setChanged();
            }
        }
    }

    public static void tickOccupiedHiveColony(ServerLevel level, BeehiveBlockEntity hive) {
        if (!EcologyConfig.ENABLE_BEE_SYSTEM.get() || !EcologyConfig.ENABLE_HIVE_COLONY_TICKING.get()) {
            return;
        }
        if (hive.getOccupantCount() <= 0) {
            return;
        }
        if (!hasColonyState(colony(hive)) || level.getGameTime() % 20L == 0L) {
            tickHiveColony(level, hive);
        }
    }

    public static void tickHiveColony(ServerLevel level, BeehiveBlockEntity hive) {
        if (!EcologyConfig.ENABLE_BEE_SYSTEM.get() || !EcologyConfig.ENABLE_HIVE_COLONY_TICKING.get()) {
            return;
        }
        ColonyData colony = colony(hive);
        boolean changed = false;
        if (hive.getOccupantCount() > 0 && !hasColonyState(colony)) {
            changed |= seedStarterColony(level, hive, null);
        }
        if (!hasColonyState(colony)) {
            return;
        }

        long day = day(level);
        if (colony.lastSimulatedDay() < 0) {
            colony.setLastSimulatedDay(day);
            hive.setChanged();
            return;
        }
        if (day <= colony.lastSimulatedDay()) {
            changed |= updateMatingFlightPlan(level, hive, colony, colonyDay(level, colony));
            if (changed) {
                hive.setChanged();
            }
            return;
        }

        long daysToSimulate = Math.min(day - colony.lastSimulatedDay(), EcologyConfig.COLONY_CATCHUP_DAYS.get());
        long firstDay = day - daysToSimulate + 1;
        for (long simulatedDay = firstDay; simulatedDay <= day; simulatedDay++) {
            changed |= simulateColonyDay(level, colony, simulatedDay);
        }

        colony.setLastSimulatedDay(day);
        changed |= updateMatingFlightPlan(level, hive, colony, day);
        if (changed || daysToSimulate > 0) {
            hive.setChanged();
        }
    }

    public static HiveDaySimulationResult simulateHiveDay(ServerLevel level, BeehiveBlockEntity hive) {
        if (!EcologyConfig.ENABLE_BEE_SYSTEM.get() || !EcologyConfig.ENABLE_HIVE_COLONY_TICKING.get()) {
            return HiveDaySimulationResult.empty();
        }

        ColonyData colony = colony(hive);
        boolean changed = false;
        if (hive.getOccupantCount() > 0 && !hasColonyState(colony)) {
            changed |= seedStarterColony(level, hive, null);
        }

        boolean hasColony = hasColonyState(colony);
        long simulatedDay = -1;
        if (hasColony) {
            long previousDay = colony.lastSimulatedDay() < 0 ? day(level) : colony.lastSimulatedDay();
            simulatedDay = previousDay + 1;
            changed |= simulateColonyDay(level, colony, simulatedDay);
            colony.setLastSimulatedDay(simulatedDay);
            changed = true;
        }

        StoredBeeSimulationResult storedBees = advanceStoredBeesOneDay(level, hive);
        if (hasColony) {
            changed |= updateMatingFlightPlan(level, hive, colony, simulatedDay);
        }
        if (changed || storedBees.hasChanges()) {
            hive.setChanged();
        }
        return new HiveDaySimulationResult(
                hasColony,
                simulatedDay,
                storedBees.storedBeesAdvanced(),
                storedBees.beesAged(),
                storedBees.workersReadied());
    }

    public static CompoundTag createBroodCombData(ServerLevel level, BeehiveBlockEntity hive) {
        tickHiveColony(level, hive);
        ColonyData colony = colony(hive);
        long day = colonyDay(level, colony);
        CompoundTag tag = new CompoundTag();
        tag.put("Colony", colony.serializeNBT(level.registryAccess()));
        tag.putLong("OriginalHomeHive", hive.getBlockPos().asLong());
        tag.putBoolean("HasQueen", colony.queenId() != null);
        tag.putInt("WorkerCount", colony.workerIds().size());
        tag.putInt("DroneCount", colony.droneIds().size());
        if (colony.queenBirthDay() >= 0) {
            tag.putLong("QueenAgeDays", Math.max(0, day - colony.queenBirthDay()));
        }
        return tag;
    }

    public static boolean installBroodComb(ServerLevel level, BeehiveBlockEntity hive, BlockPos hivePos, CompoundTag broodData) {
        ColonyData colony = colony(hive);
        if (!hive.isEmpty() || colony.totalBees() > 0 || !broodData.getBoolean("HasQueen") || !broodData.contains("Colony", Tag.TAG_COMPOUND)) {
            return false;
        }

        ColonyData sourceColony = new ColonyData();
        sourceColony.deserializeNBT(level.registryAccess(), broodData.getCompound("Colony"));
        colony.clear();
        colony.copyGeneticsFrom(sourceColony);
        colony.setLastSimulatedDay(day(level));
        long queenAgeDays = Math.max(0, broodData.getLong("QueenAgeDays"));
        long queenBirthDay = day(level) - queenAgeDays;
        return installStoredBee(level, hive, hivePos, BeeRole.QUEEN, queenBirthDay, originalHomeHive(broodData));
    }

    public static boolean installStoredBee(ServerLevel level, BeehiveBlockEntity hive, BlockPos hivePos, BeeRole role) {
        return installStoredBee(level, hive, hivePos, role, day(level));
    }

    public static boolean installStoredBee(ServerLevel level, BeehiveBlockEntity hive, BlockPos hivePos, BeeRole role, long birthDay) {
        return installStoredBee(level, hive, hivePos, role, birthDay, null);
    }

    public static boolean installStoredBee(ServerLevel level, BeehiveBlockEntity hive, BlockPos hivePos, BeeRole role, @Nullable BlockPos relocationReturnHive) {
        return installStoredBee(level, hive, hivePos, role, day(level), relocationReturnHive);
    }

    public static boolean installStoredBee(ServerLevel level, BeehiveBlockEntity hive, BlockPos hivePos, BeeRole role, long birthDay, @Nullable BlockPos relocationReturnHive) {
        if (hive.isFull()) {
            return false;
        }
        Bee bee = EntityType.BEE.create(level);
        if (bee == null) {
            return false;
        }

        bee.moveTo(hivePos.getX() + 0.5, hivePos.getY() + 0.5, hivePos.getZ() + 0.5, 0.0F, 0.0F);
        bee.setHivePos(hivePos);
        bee.setPersistenceRequired();

        BeeMemory memory = memory(bee);
        memory.setRole(role);
        memory.setBirthDay(birthDay);
        memory.setHomeHive(hivePos);
        if (relocationReturnHive != null && !relocationReturnHive.equals(hivePos)) {
            memory.setRelocationReturnHive(relocationReturnHive);
        }
        memory.resetDailyRoute(day(level));
        colony(hive).remember(memory);
        hive.setChanged();
        return enterFreshHive(bee, hivePos);
    }

    @Nullable
    public static BlockPos originalHomeHive(CompoundTag broodData) {
        return broodData.contains("OriginalHomeHive") ? BlockPos.of(broodData.getLong("OriginalHomeHive")) : null;
    }

    public static void clearCutOutNest(BeehiveBlockEntity hive) {
        ((BeehiveBlockEntityAccessor) hive).ecology$getStored().clear();
        ColonyData colony = colony(hive);
        colony.clear();
        colony.setAbandoned(true);
        colony.setDoomed(true);
        hive.setChanged();
    }

    public static boolean sealCutOutNest(ServerLevel level, BlockPos pos) {
        if (!level.getBlockState(pos).is(Blocks.BEE_NEST)
                || !(level.getBlockEntity(pos) instanceof BeehiveBlockEntity hive)) {
            return false;
        }
        ColonyData colony = colony(hive);
        if (!hive.isEmpty() || !colony.abandoned()) {
            return false;
        }
        level.setBlock(pos, Blocks.HONEYCOMB_BLOCK.defaultBlockState(), 3);
        return true;
    }

    private static boolean simulateColonyDay(ServerLevel level, ColonyData colony, long simulatedDay) {
        boolean changed = false;
        changed |= removeExpiredLogicalBees(colony, simulatedDay);
        changed |= updateColonyDeclineState(colony, simulatedDay);
        changed |= tryProduceLogicalChild(level, colony, simulatedDay);
        return changed;
    }

    private static boolean updateMatingFlightPlan(ServerLevel level, BeehiveBlockEntity hive, ColonyData colony, long day) {
        boolean changed = updateDroneMatingTarget(level, hive.getBlockPos(), colony, day);
        if (!colonyNeedsMating(colony, day)) {
            return changed;
        }

        if (colony.droneIds().isEmpty() && colony.lastDroneFailureDay() != day) {
            colony.setLastDroneFailureDay(day);
            changed = true;
        }
        if (shouldQueenSearchForMating(colony, day)) {
            changed |= readyStoredQueenForMatingFlight(hive, colony);
        }
        return changed;
    }

    private static boolean updateDroneMatingTarget(ServerLevel level, BlockPos hivePos, ColonyData colony, long day) {
        if (colony.droneIds().isEmpty() || colony.doomed() || colony.abandoned()) {
            if (colony.matingHive() != null) {
                colony.setMatingHive(null);
                return true;
            }
            return false;
        }

        if (colony.lastMatingHiveSearchDay() == day) {
            if (colony.matingHive() == null || isDroneMatingTargetValid(level, hivePos, colony, colony.matingHive())) {
                return false;
            }
            colony.setMatingHive(null);
            if (colony.lastDroneFailureDay() != day) {
                colony.setLastDroneFailureDay(day);
            }
            return true;
        }

        boolean changed = colony.lastMatingHiveSearchDay() != day;
        colony.setLastMatingHiveSearchDay(day);
        Optional<BlockPos> target = findForeignQueenHive(level, hivePos, hivePos, EcologyConfig.HIVE_SEARCH_RANGE.get(), true);
        if (target.isPresent()) {
            BlockPos targetHive = target.get();
            changed |= !targetHive.equals(colony.matingHive());
            colony.setMatingHive(targetHive);
            if (colony.lastDroneFailureDay() == day) {
                colony.setLastDroneFailureDay(day - 1);
                changed = true;
            }
        } else {
            changed |= colony.matingHive() != null;
            colony.setMatingHive(null);
            if (colony.lastDroneFailureDay() != day) {
                colony.setLastDroneFailureDay(day);
                changed = true;
            }
        }
        return changed;
    }

    private static boolean readyStoredQueenForMatingFlight(BeehiveBlockEntity hive, ColonyData colony) {
        for (Object storedBee : ((BeehiveBlockEntityAccessor) hive).ecology$getStored()) {
            BeehiveBeeDataAccessor beeData = (BeehiveBeeDataAccessor) storedBee;
            BeehiveBlockEntity.Occupant occupant = beeData.ecology$getOccupant();
            CompoundTag entityTag = occupant.entityData().copyTag();
            CompoundTag memoryTag = beeMemoryTag(entityTag);
            if (!isStoredColonyQueen(memoryTag, colony)) {
                continue;
            }

            int ticksInHive = Math.max(Math.max(beeData.ecology$getTicksInHive(), occupant.ticksInHive()), 2);
            memoryTag.putBoolean("ReturningHome", false);
            entityTag.putBoolean("HasNectar", false);
            beeData.ecology$setOccupant(new BeehiveBlockEntity.Occupant(
                    CustomData.of(entityTag),
                    ticksInHive,
                    1));
            beeData.ecology$setTicksInHive(ticksInHive);
            return true;
        }
        return false;
    }

    private static boolean isStoredColonyQueen(@Nullable CompoundTag memoryTag, ColonyData colony) {
        if (memoryTag == null || !BeeRole.QUEEN.name().equals(memoryTag.getString("Role"))) {
            return false;
        }
        return colony.queenId() == null || colony.queenId().toString().equals(memoryTag.getString("BeeId"));
    }

    private static StoredBeeSimulationResult advanceStoredBeesOneDay(ServerLevel level, BeehiveBlockEntity hive) {
        int storedBeesAdvanced = 0;
        int beesAged = 0;
        int workersReadied = 0;
        long routeDay = day(level);
        for (Object storedBee : ((BeehiveBlockEntityAccessor) hive).ecology$getStored()) {
            BeehiveBeeDataAccessor beeData = (BeehiveBeeDataAccessor) storedBee;
            BeehiveBlockEntity.Occupant occupant = beeData.ecology$getOccupant();
            int simulatedTicksInHive = Math.max(beeData.ecology$getTicksInHive(), occupant.ticksInHive()) + SIMULATED_HIVE_DAY_TICKS;
            CompoundTag entityTag = occupant.entityData().copyTag();
            boolean aged = ageStoredBeeMemory(entityTag);
            boolean readiedWorker = resetStoredWorkerForNewRoute(entityTag, routeDay);
            if (aged || readiedWorker) {
                if (readiedWorker) {
                    entityTag.putBoolean("HasNectar", false);
                    workersReadied++;
                }
                if (aged) {
                    beesAged++;
                }
                beeData.ecology$setOccupant(new BeehiveBlockEntity.Occupant(
                        CustomData.of(entityTag),
                        simulatedTicksInHive,
                        readiedWorker ? 1 : occupant.minTicksInHive()));
            }
            beeData.ecology$setTicksInHive(simulatedTicksInHive);
            storedBeesAdvanced++;
        }
        return new StoredBeeSimulationResult(storedBeesAdvanced, beesAged, workersReadied);
    }

    private static boolean ageStoredBeeMemory(CompoundTag entityTag) {
        CompoundTag memoryTag = beeMemoryTag(entityTag);
        if (memoryTag == null) {
            return false;
        }

        if (memoryTag.contains("BirthDay") && memoryTag.getLong("BirthDay") < 0) {
            return false;
        }

        memoryTag.putInt("SimulatedAgeDays", Math.max(0, memoryTag.getInt("SimulatedAgeDays")) + 1);
        return true;
    }

    private static boolean resetStoredWorkerForNewRoute(CompoundTag entityTag, long routeDay) {
        CompoundTag memoryTag = beeMemoryTag(entityTag);
        if (memoryTag == null || !BeeRole.WORKER.name().equals(memoryTag.getString("Role"))) {
            return false;
        }

        memoryTag.putInt("RouteIndex", 0);
        memoryTag.putLong("RouteDay", routeDay);
        memoryTag.put("Route", new ListTag());
        memoryTag.putBoolean("DailyComplete", false);
        memoryTag.putBoolean("CarryingPollen", false);
        memoryTag.putBoolean("ReturningHome", false);
        memoryTag.putInt("RouteAgitationTicks", 0);
        memoryTag.putString("AggressionCause", BeeAggressionCause.NONE.name());
        memoryTag.remove("FlowerSearchOrigin");
        memoryTag.remove("CropSearchOrigin");
        memoryTag.remove("HiveSearchOrigin");
        memoryTag.remove("ForeignHiveSearchOrigin");
        memoryTag.remove("EmptyHiveSearchOrigin");
        memoryTag.put("FailedFlowerSearchOrigins", new ListTag());
        memoryTag.putInt("FlowerSearchExpansionFailures", 0);
        memoryTag.putInt("RouteSearchMisses", 0);
        memoryTag.putString("WorkerState", WorkerBeeState.SEARCHING_FLOWER.name());
        memoryTag.putInt("WorkerTaskTicks", 0);
        return true;
    }

    public static void assignHiveRouteIfNeeded(ServerLevel level, Bee bee, BeeMemory memory, long currentDay) {
        if (!EcologyConfig.ENABLE_BEE_SYSTEM.get()
                || !EcologyConfig.REPLACE_VANILLA_BEE_GOALS.get()
                || memory.role() != BeeRole.WORKER
                || memory.homeHive() == null
                || memory.returningHome()
                || memory.dailyComplete()
                || !memory.route().isEmpty()) {
            return;
        }
        if (assignLearnedRouteIfValid(level, bee, memory, currentDay)) {
            return;
        }
        if (memory.hasLearnedFlowerRoute()) {
            return;
        }

        BeeSearchArea routeSearchArea = hiveRouteSearchArea(memory.homeHive());
        memory.setLastSearchArea(routeSearchArea);
        HiveRouteTargets targets = cachedHiveRouteTargets(level, memory.homeHive(), currentDay);
        if (!targets.hasFlowers()) {
            return;
        }

        List<BeeRouteStop> assignedRoute = targets.buildRoute(EcologyConfig.MAX_ROUTE_PAIRS.get(), level.getRandom());
        if (assignedRoute.isEmpty()) {
            return;
        }

        memory.replaceRoute(assignedRoute);
        memory.setRouteDay(currentDay);
        memory.setDailyComplete(false);
        memory.setReturningHome(false);
        memory.setCarryingPollen(false);
        memory.setWorkerState(WorkerBeeState.SEARCHING_FLOWER);
        memory.setWorkerTaskTicks(0);
        memory.clearSearchOrigins();
        memory.setLastSearchArea(routeSearchArea);
        setPollenVisual(bee, false);
        EcologyNetworking.sendBeeRouteUpdate(bee);
    }

    private static boolean assignLearnedRouteIfValid(ServerLevel level, Bee bee, BeeMemory memory, long currentDay) {
        if (!memory.hasLearnedRoute()) {
            return false;
        }
        BeeRouteStopType expectedType = BeeRouteStopType.FLOWER;
        for (BeeRouteStop stop : memory.learnedRoute()) {
            if (stop.type() != expectedType || !isKnownRouteStopValid(level, stop)) {
                memory.clearLearnedRoute();
                return false;
            }
            expectedType = expectedType == BeeRouteStopType.FLOWER ? BeeRouteStopType.CROP : BeeRouteStopType.FLOWER;
        }

        memory.replaceRoute(List.copyOf(memory.learnedRoute()));
        memory.setRouteDay(currentDay);
        memory.setDailyComplete(false);
        memory.setReturningHome(false);
        memory.setCarryingPollen(false);
        memory.setWorkerState(WorkerBeeState.SEARCHING_FLOWER);
        memory.setWorkerTaskTicks(0);
        memory.clearSearchOrigins();
        setPollenVisual(bee, false);
        EcologyNetworking.sendBeeRouteUpdate(bee);
        return true;
    }

    private static boolean isKnownRouteStopValid(Level level, BeeRouteStop stop) {
        if (!level.isLoaded(stop.pos())) {
            return false;
        }
        return stop.type() == BeeRouteStopType.FLOWER
                ? isValidFlower(level, stop.pos())
                : isPollinationCrop(level, stop.pos());
    }

    public static boolean shouldForceReleaseStoredWorker(BeehiveBlockEntity.Occupant occupant, int ticksInHive) {
        if (!EcologyConfig.ENABLE_BEE_SYSTEM.get()
                || occupant.minTicksInHive() <= EcologyConfig.FRESH_HIVE_RELEASE_TICKS.get()
                || ticksInHive <= EcologyConfig.FRESH_HIVE_RELEASE_TICKS.get()) {
            return false;
        }
        return hasEmptyCompletedWorkerMemory(occupant.entityData().copyTag());
    }

    private static boolean shouldRetryEmptyCompletedRoute(BeeMemory memory) {
        return memory.dailyComplete()
                && memory.route().isEmpty()
                && !memory.hasLearnedRoute()
                && !memory.hasLearnedFlowerRoute();
    }

    private static boolean hasEmptyCompletedWorkerMemory(CompoundTag entityTag) {
        CompoundTag memoryTag = beeMemoryTag(entityTag);
        return memoryTag != null
                && BeeRole.WORKER.name().equals(memoryTag.getString("Role"))
                && memoryTag.getBoolean("DailyComplete")
                && memoryTag.getList("Route", Tag.TAG_COMPOUND).isEmpty()
                && memoryTag.getList("LearnedRoute", Tag.TAG_COMPOUND).isEmpty()
                && memoryTag.getList("LearnedFlowerRoute", Tag.TAG_COMPOUND).isEmpty();
    }

    @Nullable
    private static CompoundTag beeMemoryTag(CompoundTag entityTag) {
        if (!entityTag.contains("neoforge:attachments")) {
            return null;
        }
        CompoundTag attachmentsTag = entityTag.getCompound("neoforge:attachments");
        if (!attachmentsTag.contains("ecology:bee_memory")) {
            return null;
        }

        return attachmentsTag.getCompound("ecology:bee_memory");
    }

    private static HiveRouteTargets cachedHiveRouteTargets(ServerLevel level, BlockPos hivePos, long currentDay) {
        long gameTime = level.getGameTime();
        if (gameTime % 1200L == 0L) {
            HIVE_ROUTE_ASSIGNMENTS.entrySet().removeIf(entry -> gameTime - entry.getValue().lastUsedGameTime() > HIVE_ROUTE_ASSIGNMENT_CACHE_TICKS);
        }

        HiveRouteAssignmentKey key = new HiveRouteAssignmentKey(level.dimension().location().toString(), hivePos.asLong(), currentDay);
        CachedHiveRouteTargets cached = HIVE_ROUTE_ASSIGNMENTS.get(key);
        if (cached != null) {
            cached.setLastUsedGameTime(gameTime);
            return cached.targets();
        }

        HiveRouteTargets targets = scanHiveRouteTargets(level, hivePos);
        targets.shuffle(level.getRandom());
        if (targets.hasFlowers()) {
            HIVE_ROUTE_ASSIGNMENTS.put(key, new CachedHiveRouteTargets(targets, gameTime));
        }
        return targets;
    }

    private static HiveRouteTargets scanHiveRouteTargets(Level level, BlockPos hivePos) {
        List<BlockPos> flowers = new ArrayList<>();
        List<BlockPos> crops = new ArrayList<>();
        BlockPos.betweenClosedStream(
                        hivePos.offset(-HIVE_ROUTE_SCAN_HORIZONTAL_RADIUS, -HIVE_ROUTE_SCAN_VERTICAL_RADIUS, -HIVE_ROUTE_SCAN_HORIZONTAL_RADIUS),
                        hivePos.offset(HIVE_ROUTE_SCAN_HORIZONTAL_RADIUS, HIVE_ROUTE_SCAN_VERTICAL_RADIUS, HIVE_ROUTE_SCAN_HORIZONTAL_RADIUS))
                .filter(level::isLoaded)
                .forEach(pos -> {
                    if (isValidFlower(level, pos)) {
                        flowers.add(pos.immutable());
                    }
                    if (isPollinationCrop(level, pos)) {
                        crops.add(pos.immutable());
                    }
                });
        return new HiveRouteTargets(flowers, crops);
    }

    private static BeeSearchArea hiveRouteSearchArea(BlockPos hivePos) {
        return BeeSearchArea.around(
                hivePos,
                HIVE_ROUTE_SCAN_HORIZONTAL_RADIUS,
                HIVE_ROUTE_SCAN_VERTICAL_RADIUS,
                BeeRouteStopType.FLOWER);
    }

    private static <T> void shuffle(List<T> values, RandomSource random) {
        for (int index = values.size() - 1; index > 0; index--) {
            Collections.swap(values, index, random.nextInt(index + 1));
        }
    }

    private record HiveRouteAssignmentKey(String dimension, long hivePos, long day) {
    }

    private static final class CachedHiveRouteTargets {
        private final HiveRouteTargets targets;
        private long lastUsedGameTime;

        private CachedHiveRouteTargets(HiveRouteTargets targets, long lastUsedGameTime) {
            this.targets = targets;
            this.lastUsedGameTime = lastUsedGameTime;
        }

        private HiveRouteTargets targets() {
            return targets;
        }

        private long lastUsedGameTime() {
            return lastUsedGameTime;
        }

        private void setLastUsedGameTime(long lastUsedGameTime) {
            this.lastUsedGameTime = lastUsedGameTime;
        }
    }

    private static final class HiveRouteTargets {
        private final RouteTargetPool flowers;
        private final RouteTargetPool crops;

        private HiveRouteTargets(List<BlockPos> flowers, List<BlockPos> crops) {
            this.flowers = new RouteTargetPool(flowers);
            this.crops = new RouteTargetPool(crops);
        }

        private boolean hasFlowers() {
            return !flowers.isEmpty();
        }

        private void shuffle(RandomSource random) {
            flowers.shuffle(random);
            crops.shuffle(random);
        }

        private List<BeeRouteStop> buildRoute(int maxPairs, RandomSource random) {
            List<BeeRouteStop> route = new ArrayList<>();
            Set<BlockPos> usedFlowers = new HashSet<>();
            Set<BlockPos> usedCrops = new HashSet<>();
            for (int stop = 0; stop < maxPairs; stop++) {
                BlockPos flower = flowers.nextExcluding(usedFlowers, random);
                if (flower == null) {
                    break;
                }
                usedFlowers.add(flower);
                route.add(new BeeRouteStop(flower, BeeRouteStopType.FLOWER));

                BlockPos crop = crops.nextExcluding(usedCrops, random);
                if (crop != null) {
                    usedCrops.add(crop);
                    route.add(new BeeRouteStop(crop, BeeRouteStopType.CROP));
                }
            }
            return route;
        }
    }

    private static final class RouteTargetPool {
        private final List<BlockPos> positions;
        private int cursor;

        private RouteTargetPool(List<BlockPos> positions) {
            this.positions = new ArrayList<>(positions);
        }

        private boolean isEmpty() {
            return positions.isEmpty();
        }

        private void shuffle(RandomSource random) {
            EcologyBeeSystem.shuffle(positions, random);
            cursor = 0;
        }

        @Nullable
        private BlockPos nextExcluding(Set<BlockPos> excluded, RandomSource random) {
            if (positions.isEmpty() || excluded.size() >= positions.size()) {
                return null;
            }

            for (int attempt = 0; attempt < positions.size() * 2; attempt++) {
                if (cursor >= positions.size()) {
                    shuffle(random);
                }
                BlockPos candidate = positions.get(cursor++);
                if (!excluded.contains(candidate)) {
                    return candidate;
                }
            }

            for (BlockPos candidate : positions) {
                if (!excluded.contains(candidate)) {
                    return candidate;
                }
            }
            return null;
        }
    }

    public static void ensureStarterColony(ServerLevel level, BeehiveBlockEntity hive) {
        if (!EcologyConfig.ENABLE_BEE_SYSTEM.get()
                || !EcologyConfig.ENABLE_HIVE_COLONY_TICKING.get()
                || !EcologyConfig.AUTO_SEED_EMPTY_HIVES.get()
                || !hive.isEmpty()) {
            return;
        }
        if (seedStarterColony(level, hive, null)) {
            hive.setChanged();
        }
    }

    private static boolean hasColonyState(ColonyData colony) {
        return colony.queenId() != null
                || !colony.workerIds().isEmpty()
                || !colony.droneIds().isEmpty()
                || colony.lastSimulatedDay() >= 0
                || colony.lastMatedDay() >= 0
                || colony.doomed()
                || colony.declining()
                || colony.abandoned();
    }

    private static boolean removeExpiredLogicalBees(ColonyData colony, long day) {
        boolean changed = false;
        if (colony.queenId() != null
                && colony.queenBirthDay() >= 0
                && isExpired(day, colony.queenBirthDay(), EcologyConfig.QUEEN_LIFESPAN_DAYS.get())) {
            colony.removeBeeId(colony.queenId());
            changed = true;
        }
        changed |= removeExpiredIds(colony, colony.workerIds(), day, EcologyConfig.WORKER_LIFESPAN_DAYS.get());
        changed |= removeExpiredIds(colony, colony.droneIds(), day, EcologyConfig.DRONE_LIFESPAN_DAYS.get());
        return changed;
    }

    private static boolean removeExpiredIds(ColonyData colony, Set<UUID> ids, long day, int lifespanDays) {
        List<UUID> expired = ids.stream()
                .filter(id -> colony.birthDay(id) >= 0)
                .filter(id -> isExpired(day, colony.birthDay(id), lifespanDays))
                .toList();
        expired.forEach(colony::removeBeeId);
        return !expired.isEmpty();
    }

    private static boolean updateColonyDeclineState(ColonyData colony, long day) {
        boolean shouldDecline = colony.queenId() == null || colony.workerIds().isEmpty();
        boolean shouldDoom = colony.queenId() == null || colony.workerIds().isEmpty();
        boolean changed = colony.declining() != shouldDecline || colony.doomed() != shouldDoom;
        colony.setDeclining(shouldDecline);
        colony.setDoomed(shouldDoom);
        return changed;
    }

    private static boolean canProduceLogicalChild(ColonyData colony, long day) {
        return colony.queenId() != null
                && !colony.doomed()
                && !colony.workerIds().isEmpty()
                && isColonyFertile(colony, day)
                && colony.lastChildDay() != day
                && nextNeededRole(colony, day).isPresent();
    }

    private static boolean tryProduceLogicalChild(ServerLevel level, ColonyData colony, long day) {
        if (!canProduceLogicalChild(colony, day)) {
            return false;
        }

        Optional<BeeRole> nextRole = nextNeededRole(colony, day);
        if (nextRole.isEmpty() || !broodSucceeds(level, colony)) {
            return false;
        }

        rememberLogicalBee(colony, nextRole.get(), day);
        colony.setLastChildDay(day);
        return true;
    }

    private static boolean broodSucceeds(ServerLevel level, ColonyData colony) {
        double failureChance = clamp01(colony.inbreedingCoefficient() * EcologyConfig.INBREEDING_BROOD_PENALTY.get());
        return failureChance <= 0.0 || level.getRandom().nextDouble() >= failureChance;
    }

    public static boolean shouldDroneSearchForMating(ServerLevel level, BeeMemory memory) {
        if (memory.homeHive() == null || !(level.getBlockEntity(memory.homeHive()) instanceof BeehiveBlockEntity hive)) {
            return false;
        }
        ColonyData colony = colony(hive);
        long day = colonyDay(level, colony);
        return !colony.doomed()
                && !colony.abandoned()
                && colony.lastDroneFailureDay() != day
                && assignedDroneMatingHive(level, memory).isPresent();
    }

    public static Optional<BlockPos> assignedDroneMatingHive(ServerLevel level, BeeMemory memory) {
        if (memory.homeHive() == null || !(level.getBlockEntity(memory.homeHive()) instanceof BeehiveBlockEntity hive)) {
            return Optional.empty();
        }
        ColonyData colony = colony(hive);
        BlockPos targetHive = colony.matingHive();
        if (targetHive == null) {
            return Optional.empty();
        }
        if (isDroneMatingTargetValid(level, memory.homeHive(), colony, targetHive)) {
            memory.setMateHive(targetHive);
            return Optional.of(targetHive);
        }
        colony.setMatingHive(null);
        hive.setChanged();
        return Optional.empty();
    }

    public static boolean shouldQueenSearchForMating(ServerLevel level, BeeMemory memory) {
        if (memory.homeHive() == null || !(level.getBlockEntity(memory.homeHive()) instanceof BeehiveBlockEntity hive)) {
            return false;
        }
        ColonyData colony = colony(hive);
        return shouldQueenSearchForMating(colony, colonyDay(level, colony));
    }

    private static boolean shouldQueenSearchForMating(ColonyData colony, long day) {
        return colonyNeedsMating(colony, day)
                && (colony.droneIds().isEmpty() || colony.lastDroneFailureDay() == day);
    }

    private static boolean colonyNeedsMating(ColonyData colony, long day) {
        return colony.queenId() != null
                && !colony.doomed()
                && !colony.abandoned()
                && !colony.workerIds().isEmpty()
                && !isColonyFertile(colony, day)
                && isQueenMatureForMating(colony, day)
                && colony.lastChildDay() != day
                && nextNeededRole(colony, day).isPresent();
    }

    private static boolean isColonyFertile(ColonyData colony, long day) {
        return colony.lastMatedDay() >= 0 && colony.fertileUntilDay() >= day;
    }

    private static boolean isQueenMatureForMating(ColonyData colony, long day) {
        return colony.queenBirthDay() < 0
                || day - colony.queenBirthDay() >= EcologyConfig.QUEEN_MATING_MATURITY_DAYS.get();
    }

    private static boolean isDroneMatingTargetValid(ServerLevel level, BlockPos homeHive, ColonyData sourceColony, BlockPos targetHive) {
        if (targetHive.equals(homeHive) || !(level.getBlockEntity(targetHive) instanceof BeehiveBlockEntity hive)) {
            return false;
        }
        ColonyData targetColony = colony(hive);
        long targetDay = colonyDay(level, targetColony);
        return isCompatibleMatingTarget(sourceColony, targetColony, targetDay, true);
    }

    private static boolean rememberLogicalBee(ColonyData colony, BeeRole role, long day) {
        BeeMemory memory = new BeeMemory();
        memory.setBirthDay(day);
        memory.setRole(role);
        return colony.remember(memory);
    }

    private static boolean isExpired(long day, long birthDay, int lifespanDays) {
        return birthDay >= 0 && day - birthDay >= lifespanDays;
    }

    private static boolean seedStarterColony(ServerLevel level, BeehiveBlockEntity hive, @Nullable BeeMemory visibleBeeMemory) {
        ColonyData colony = colony(hive);
        if (colony.abandoned() || hasColonyState(colony)) {
            return false;
        }

        long day = day(level);
        boolean changed = false;
        colony.setLastSimulatedDay(day);
        changed = true;
        if (visibleBeeMemory != null && visibleBeeMemory.role() == BeeRole.QUEEN) {
            changed |= colony.remember(visibleBeeMemory);
            changed |= rememberLogicalBee(colony, BeeRole.WORKER, day);
        } else {
            changed |= rememberLogicalBee(colony, BeeRole.QUEEN, day);
            if (visibleBeeMemory == null) {
                changed |= rememberLogicalBee(colony, BeeRole.WORKER, day);
            } else {
                visibleBeeMemory.setRole(BeeRole.WORKER);
                changed |= colony.remember(visibleBeeMemory);
            }
        }
        if (colony.queenId() != null && !colony.workerIds().isEmpty()) {
            if (colony.doomed()) {
                colony.setDoomed(false);
                changed = true;
            }
            if (colony.declining()) {
                colony.setDeclining(false);
                changed = true;
            }
        }
        return changed;
    }

    public static void forgetAtHomeHive(ServerLevel level, BeeMemory memory) {
        if (!EcologyConfig.ENABLE_BEE_SYSTEM.get() || !EcologyConfig.ENABLE_HIVE_COLONY_TICKING.get()) {
            return;
        }
        if (memory.homeHive() != null && level.getBlockEntity(memory.homeHive()) instanceof BeehiveBlockEntity hive) {
            if (colony(hive).forget(memory)) {
                hive.setChanged();
            }
        }
    }

    private static void assignRoleIfNeeded(BeeMemory memory, ColonyData colony) {
        if (memory.ecologyId().equals(colony.queenId())) {
            memory.setRole(BeeRole.QUEEN);
            return;
        }
        if (colony.workerIds().contains(memory.ecologyId())) {
            memory.setRole(BeeRole.WORKER);
            return;
        }
        if (colony.droneIds().contains(memory.ecologyId())) {
            memory.setRole(BeeRole.DRONE);
            return;
        }

        if (colony.queenId() == null
                && memory.role() == BeeRole.QUEEN
                && colony.queenCount() < EcologyConfig.MAX_QUEENS_PER_HIVE.get()
                && !colony.doomed()
                && !colony.abandoned()) {
            return;
        }
        if (colony.workerIds().isEmpty()) {
            memory.setRole(BeeRole.WORKER);
            return;
        }
        if (colony.workerIds().size() < EcologyConfig.MAX_WORKERS_PER_HIVE.get()) {
            memory.setRole(BeeRole.WORKER);
            return;
        }
        if (colony.droneIds().size() < EcologyConfig.MAX_DRONES_PER_HIVE.get()) {
            memory.setRole(BeeRole.DRONE);
            return;
        }
        memory.setRole(BeeRole.WORKER);
    }

    public static Optional<BlockPos> findNearestHive(ServerLevel level, BlockPos center, int range) {
        return findNearestBlock(level, center, range, pos -> level.getBlockState(pos).is(BlockTags.BEEHIVES)
                && (!(level.getBlockEntity(pos) instanceof BeehiveBlockEntity hive) || !colony(hive).abandoned()));
    }

    public static Optional<BlockPos> findLocalHive(ServerLevel level, BeeMemory memory, BlockPos center) {
        if (!shouldSearchFrom(memory.hiveSearchOrigin(), center)) {
            return Optional.empty();
        }
        memory.setHiveSearchOrigin(center);
        return findNearestHive(level, center, EcologyConfig.HIVE_SEARCH_RANGE.get());
    }

    public static Optional<BlockPos> findForeignQueenHive(ServerLevel level, BlockPos center, @Nullable BlockPos homeHive, int range, boolean requireMatingNeed) {
        ColonyData sourceColony = homeHive != null && level.getBlockEntity(homeHive) instanceof BeehiveBlockEntity hive
                ? colony(hive)
                : null;
        return findNearestBlock(level, center, range, pos -> {
            if (pos.equals(homeHive)
                    || !level.getBlockState(pos).is(BlockTags.BEEHIVES)
                    || !(level.getBlockEntity(pos) instanceof BeehiveBlockEntity hive)) {
                return false;
            }
            ColonyData targetColony = colony(hive);
            long targetDay = colonyDay(level, targetColony);
            return isCompatibleMatingTarget(sourceColony, targetColony, targetDay, requireMatingNeed);
        });
    }

    public static Optional<BlockPos> findLocalForeignQueenHive(ServerLevel level, BeeMemory memory, BlockPos center, boolean requireMatingNeed) {
        if (!shouldSearchFrom(memory.foreignHiveSearchOrigin(), center)) {
            return Optional.empty();
        }
        memory.setForeignHiveSearchOrigin(center);
        Optional<BlockPos> target = findForeignQueenHive(level, center, memory.homeHive(), EcologyConfig.HIVE_SEARCH_RANGE.get(), requireMatingNeed);
        target.ifPresent(memory::setMateHive);
        return target;
    }

    private static boolean isCompatibleMatingTarget(@Nullable ColonyData sourceColony, ColonyData targetColony, long day, boolean requireMatingNeed) {
        if (targetColony.queenId() == null || targetColony.doomed() || targetColony.abandoned()) {
            return false;
        }
        if (sourceColony != null && exceedsMatingOverlapLimit(sourceColony, targetColony)) {
            return false;
        }
        if (!isQueenMatureForMating(targetColony, day)) {
            return false;
        }
        return !requireMatingNeed || colonyNeedsMating(targetColony, day);
    }

    private static boolean exceedsMatingOverlapLimit(ColonyData sourceColony, ColonyData targetColony) {
        double maxOverlap = EcologyConfig.MAX_MATING_LINEAGE_OVERLAP.get();
        return sourceColony.lineageOverlap(targetColony) > maxOverlap;
    }

    public static Optional<BlockPos> findEmptyHiveNear(ServerLevel level, BlockPos center, int range) {
        return findNearestBlock(level, center, range, pos -> level.getBlockState(pos).is(BlockTags.BEEHIVES)
                && level.getBlockEntity(pos) instanceof BeehiveBlockEntity hive
                && hive.isEmpty()
                && colony(level.getBlockEntity(pos)).queenId() == null
                && !colony(level.getBlockEntity(pos)).abandoned());
    }

    public static Optional<BlockPos> findLocalEmptyHive(ServerLevel level, BeeMemory memory, BlockPos center) {
        if (!shouldSearchFrom(memory.emptyHiveSearchOrigin(), center)) {
            return Optional.empty();
        }
        memory.setEmptyHiveSearchOrigin(center);
        return findEmptyHiveNear(level, center, EcologyConfig.HIVE_SEARCH_RANGE.get());
    }

    public static List<BeeRouteStop> buildWorkerRoute(Bee bee) {
        BeeMemory memory = memory(bee);
        List<BeeRouteStop> stops = new ArrayList<>();

        if (memory.carryingPollen()) {
            Optional<BlockPos> crop = findLocalCrop(bee, bee.blockPosition());
            if (crop.isPresent()) {
                stops.add(new BeeRouteStop(crop.get(), BeeRouteStopType.CROP));
                memory.setRouteSearchMisses(0);
                return stops;
            }
        }

        findLocalFlower(bee).ifPresent(pos -> stops.add(new BeeRouteStop(pos, BeeRouteStopType.FLOWER)));
        if (!stops.isEmpty()) {
            memory.setRouteSearchMisses(0);
        }
        return stops;
    }

    public static Optional<BlockPos> findLocalFlower(Bee bee) {
        BeeMemory memory = memory(bee);
        BlockPos current = bee.blockPosition();
        BlockPos searchOrigin = memory.flowerSearchOrigin();
        BlockPos center = current;
        BlockPos failedOrigin = memory.failedFlowerSearchOriginNear(current);
        if (failedOrigin != null
                && memory.flowerSearchExpansionFailures() >= MAX_FLOWER_SEARCH_EXPANSION_FAILURES) {
            memory.setFlowerSearchOrigin(failedOrigin);
            return Optional.empty();
        }

        boolean expandingPreviousSearch = memory.flowerSearchExpansionFailures() > 0
                && searchOrigin != null
                && isWithinLocalSearchArea(searchOrigin, current);
        if (expandingPreviousSearch) {
            center = searchOrigin;
        } else {
            if (!shouldSearchFrom(searchOrigin, current)) {
                return Optional.empty();
            }
            memory.setFlowerSearchOrigin(current);
        }
        int searchRange = flowerSearchRange(memory);
        memory.setLastSearchArea(new BeeSearchArea(
                center.offset(-searchRange, -searchRange - 1, -searchRange),
                center.offset(searchRange, searchRange - 1, searchRange),
                BeeRouteStopType.FLOWER));
        sendSearchAreaUpdate(bee);
        Optional<BlockPos> flower = findNearestFlowerBlock(bee.level(), center, searchRange, pos -> isValidFlower(bee.level(), pos)
                && !hasCompletedRouteStop(memory, pos, BeeRouteStopType.FLOWER));
        if (flower.isEmpty()) {
            if (memory.flowerSearchExpansionFailures() >= MAX_FLOWER_SEARCH_EXPANSION_FAILURES) {
                memory.rememberFailedFlowerSearch(center);
            }
            memory.setFlowerSearchExpansionFailures(Math.min(
                    MAX_FLOWER_SEARCH_EXPANSION_FAILURES,
                    memory.flowerSearchExpansionFailures() + 1));
            memory.setRouteSearchMisses(memory.routeSearchMisses() + 1);
        } else {
            memory.setFlowerSearchExpansionFailures(0);
            memory.setRouteSearchMisses(0);
        }
        return flower;
    }

    private static int flowerSearchRange(BeeMemory memory) {
        return EcologyConfig.FLOWER_SEARCH_RANGE.get()
                + Math.min(memory.flowerSearchExpansionFailures(), MAX_FLOWER_SEARCH_EXPANSION_FAILURES) * FLOWER_SEARCH_EXPANSION_STEP;
    }

    public static Optional<BlockPos> findLocalCrop(Bee bee, BlockPos center) {
        BeeMemory memory = memory(bee);
        if (!shouldSearchFrom(memory.cropSearchOrigin(), center)) {
            return Optional.empty();
        }
        memory.setCropSearchOrigin(center);
        int searchRange = EcologyConfig.CROP_SEARCH_RANGE.get();
        memory.setLastSearchArea(BeeSearchArea.around(center, searchRange, searchRange, BeeRouteStopType.CROP));
        sendSearchAreaUpdate(bee);
        Optional<BlockPos> crop = findNearestBlock(bee.level(), center, searchRange, pos -> isPollinationCrop(bee.level(), pos)
                && !hasCompletedRouteStop(memory, pos, BeeRouteStopType.CROP));
        if (crop.isEmpty()) {
            memory.setRouteSearchMisses(memory.routeSearchMisses() + 1);
        } else {
            memory.setRouteSearchMisses(0);
        }
        return crop;
    }

    private static void sendSearchAreaUpdate(Bee bee) {
        EcologyNetworking.sendBeeRouteUpdate(bee);
    }

    private static boolean hasCompletedRouteStop(BeeMemory memory, BlockPos pos, BeeRouteStopType type) {
        int completedStops = Math.min(memory.routeIndex(), memory.route().size());
        for (int i = 0; i < completedStops; i++) {
            BeeRouteStop stop = memory.route().get(i);
            if (stop.type() == type && stop.pos().equals(pos)) {
                return true;
            }
        }
        return false;
    }

    public static boolean shouldSearchFrom(@Nullable BlockPos searchOrigin, BlockPos current) {
        return searchOrigin == null || !isWithinLocalSearchArea(searchOrigin, current);
    }

    public static boolean isWithinLocalSearchArea(BlockPos searchOrigin, BlockPos current) {
        return Math.abs(current.getX() - searchOrigin.getX()) <= EcologyConfig.BEE_LOCAL_SEARCH_HORIZONTAL_RADIUS.get()
                && Math.abs(current.getY() - searchOrigin.getY()) <= EcologyConfig.BEE_LOCAL_SEARCH_VERTICAL_RADIUS.get()
                && Math.abs(current.getZ() - searchOrigin.getZ()) <= EcologyConfig.BEE_LOCAL_SEARCH_HORIZONTAL_RADIUS.get();
    }

    public static Optional<BlockPos> findNearestLocalBlock(Level level, BlockPos center, java.util.function.Predicate<BlockPos> predicate) {
        int horizontalRange = EcologyConfig.BEE_LOCAL_SEARCH_HORIZONTAL_RADIUS.get();
        int verticalRange = EcologyConfig.BEE_LOCAL_SEARCH_VERTICAL_RADIUS.get();
        return findNearestBlock(level, center, horizontalRange, verticalRange, predicate);
    }

    private static Optional<BlockPos> findNearestBlock(Level level, BlockPos center, int horizontalRange, int verticalRange, java.util.function.Predicate<BlockPos> predicate) {
        horizontalRange = Math.max(0, horizontalRange);
        verticalRange = Math.max(0, verticalRange);
        BlockPos min = center.offset(-horizontalRange, -verticalRange, -horizontalRange);
        BlockPos max = center.offset(horizontalRange, verticalRange, horizontalRange);
        BlockPos nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (!level.isLoaded(pos) || !predicate.test(pos)) {
                continue;
            }
            BlockPos candidate = pos.immutable();
            double distance = candidate.distSqr(center);
            if (distance < nearestDistance) {
                nearest = candidate;
                nearestDistance = distance;
            }
        }
        return Optional.ofNullable(nearest);
    }

    public static Optional<BlockPos> findNearestFlowerBlock(Level level, BlockPos center, double distance, java.util.function.Predicate<BlockPos> predicate) {
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        for (int y = 0; (double) y <= distance; y = y > 0 ? -y : 1 - y) {
            for (int ring = 0; (double) ring < distance; ring++) {
                for (int x = 0; x <= ring; x = x > 0 ? -x : 1 - x) {
                    for (int z = x < ring && x > -ring ? ring : 0; z <= ring; z = z > 0 ? -z : 1 - z) {
                        mutable.setWithOffset(center, x, y - 1, z);
                        if (center.closerThan(mutable, distance) && level.isLoaded(mutable) && predicate.test(mutable)) {
                            return Optional.of(mutable.immutable());
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    public static boolean isBeyondHomeSearchDistance(Bee bee, BeeMemory memory) {
        return memory.homeHive() != null
                && !bee.blockPosition().closerThan(memory.homeHive(), EcologyConfig.MIGRATION_SEARCH_RANGE.get());
    }

    public static Optional<BlockPos> findNearestBlock(Level level, BlockPos center, int range, java.util.function.Predicate<BlockPos> predicate) {
        return findNearestBlock(level, center, range, range, predicate);
    }

    public static boolean isValidFlower(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.hasProperty(BlockStateProperties.WATERLOGGED) && state.getValue(BlockStateProperties.WATERLOGGED)) {
            return false;
        }
        if (!(state.is(POLLINATION_FLOWERS) || state.is(BlockTags.FLOWERS))) {
            return false;
        }
        return !state.is(Blocks.SUNFLOWER)
                || !(state.getBlock() instanceof DoublePlantBlock)
                || !state.hasProperty(DoublePlantBlock.HALF)
                || state.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.UPPER;
    }

    public static boolean isGrowableCrop(Level level, BlockPos pos) {
        return canGrowPollinationCrop(level, pos);
    }

    public static boolean isPollinationCrop(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.is(POLLINATION_CROPS) && state.getBlock() instanceof CropBlock;
    }

    public static boolean canGrowPollinationCrop(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return isPollinationCrop(level, pos) && state.getBlock() instanceof CropBlock crop && !crop.isMaxAge(state);
    }

    public static boolean isFullyGrownPollinationCrop(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return isPollinationCrop(level, pos) && state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state);
    }

    public static boolean growCrop(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof CropBlock crop && !crop.isMaxAge(state)) {
            level.levelEvent(2011, pos, 15);
            level.setBlockAndUpdate(pos, crop.getStateForAge(crop.getAge(state) + 1));
            return true;
        }
        return false;
    }

    public static void setPollenVisual(Bee bee, boolean hasPollen) {
        ((BeeAccessor) bee).ecology$setHasNectar(hasPollen);
    }

    public static void setStung(Bee bee, boolean stung) {
        ((BeeAccessor) bee).ecology$setHasStung(stung);
    }

    public static void ensureDroneHasNoStinger(Bee bee, BeeMemory memory) {
        if (memory.role() != BeeRole.DRONE) {
            return;
        }
        if (bee.hasStung()) {
            setStung(bee, false);
        }
        if (bee.getTarget() != null) {
            bee.setTarget(null);
        }
        if (bee.isAngry() || bee.getRemainingPersistentAngerTime() > 0 || bee.getPersistentAngerTarget() != null) {
            bee.stopBeingAngry();
        }
        if (memory.aggressionCause() != BeeAggressionCause.NONE) {
            memory.setAggressionCause(BeeAggressionCause.NONE);
        }
    }

    public static boolean isHoldingHiveDaySimulator(Player player) {
        return player.getMainHandItem().is(EcologyItems.HIVE_DAY_SIMULATOR.get())
                || player.getOffhandItem().is(EcologyItems.HIVE_DAY_SIMULATOR.get());
    }

    public static boolean clearSimulatorProtectedAggression(Bee bee, BeeMemory memory) {
        if (bee.getTarget() instanceof Player target && isHoldingHiveDaySimulator(target)) {
            clearAggression(bee, memory);
            return true;
        }

        UUID angryAt = bee.getPersistentAngerTarget();
        if (angryAt != null && bee.level() instanceof ServerLevel level) {
            for (Player player : level.players()) {
                if (player.getUUID().equals(angryAt) && isHoldingHiveDaySimulator(player)) {
                    clearAggression(bee, memory);
                    return true;
                }
            }
        }
        return false;
    }

    private static void clearAggression(Bee bee, BeeMemory memory) {
        bee.setTarget(null);
        bee.stopBeingAngry();
        if (memory.aggressionCause() != BeeAggressionCause.NONE) {
            memory.setAggressionCause(BeeAggressionCause.NONE);
        }
    }

    public static void pathfindRandomlyTowards(Bee bee, BlockPos pos) {
        ((BeeAccessor) bee).ecology$pathfindRandomlyTowards(pos);
    }

    public static void sendHome(Bee bee) {
        BeeMemory memory = memory(bee);
        memory.setReturningHome(true);
        BlockPos target = returnHomeTarget(memory);
        if (target != null) {
            bee.setHivePos(target);
        }
    }

    @Nullable
    public static BlockPos returnHomeTarget(BeeMemory memory) {
        return memory.relocationReturnHive() != null ? memory.relocationReturnHive() : memory.homeHive();
    }

    public static boolean isPendingRelocationReturnTarget(BeeMemory memory, BlockPos target) {
        return memory.relocationReturnHive() != null && memory.relocationReturnHive().equals(target);
    }

    public static boolean completePendingRelocationReturn(Bee bee, BeeMemory memory) {
        if (memory.relocationReturnHive() == null || memory.homeHive() == null) {
            return false;
        }
        memory.clearPendingRelocationReturn();
        memory.setReturningHome(true);
        bee.setHivePos(memory.homeHive());
        return true;
    }

    public static boolean enterHive(Bee bee, BlockPos hivePos) {
        if (bee.level().getBlockEntity(hivePos) instanceof BeehiveBlockEntity hive && !hive.isFull()) {
            BeeMemory memory = memory(bee);
            int minTicksInHive = releaseTicksForHiveEntry(bee, memory);
            memory.setReturningHome(false);
            memory.setCarryingPollen(false);
            memory.setWorkerState(WorkerBeeState.SEARCHING_FLOWER);
            memory.setWorkerTaskTicks(0);
            memory.setRouteSearchMisses(0);
            memory.clearSearchOrigins();
            setPollenVisual(bee, false);
            return storeBeeInHive(bee, hive, hivePos, minTicksInHive);
        }
        return false;
    }

    public static boolean enterFreshHive(Bee bee, BlockPos hivePos) {
        if (bee.level().getBlockEntity(hivePos) instanceof BeehiveBlockEntity hive && !hive.isFull()) {
            BeeMemory memory = memory(bee);
            int minTicksInHive = memory.hasPendingRelocationReturn() && memory.role() == BeeRole.QUEEN
                    ? 1
                    : memory.role() == BeeRole.QUEEN
                            ? queenReleaseTicks()
                            : EcologyConfig.FRESH_HIVE_RELEASE_TICKS.get();
            return storeBeeInHive(bee, hive, hivePos, minTicksInHive);
        }
        return false;
    }

    private static int releaseTicksForHiveEntry(Bee bee, BeeMemory memory) {
        if (memory.hasPendingRelocationReturn()) {
            return memory.role() == BeeRole.QUEEN ? 1 : EcologyConfig.FRESH_HIVE_RELEASE_TICKS.get();
        }
        if (memory.role() == BeeRole.QUEEN) {
            return queenReleaseTicks();
        }
        if (memory.role() == BeeRole.WORKER
                && memory.dailyComplete()
                && memory.routeDay() == day(bee.level())) {
            return ticksUntilNextDay(bee.level()) + EcologyConfig.DAILY_COMPLETE_RELEASE_PADDING_TICKS.get();
        }
        return bee.hasNectar() ? 2400 : EcologyConfig.FRESH_HIVE_RELEASE_TICKS.get();
    }

    private static int queenReleaseTicks() {
        return Integer.MAX_VALUE / 4;
    }

    private static int ticksUntilNextDay(Level level) {
        long dayTime = Math.floorMod(level.getDayTime(), 24000L);
        return (int) (24000L - dayTime);
    }

    private static boolean storeBeeInHive(Bee bee, BeehiveBlockEntity hive, BlockPos hivePos, int minTicksInHive) {
        bee.stopRiding();
        bee.ejectPassengers();
        BeehiveBlockEntity.Occupant occupant = BeehiveBlockEntity.Occupant.of(bee);
        hive.storeBee(new BeehiveBlockEntity.Occupant(
                occupant.entityData(),
                occupant.ticksInHive(),
                Math.max(1, minTicksInHive)));

        Level level = bee.level();
        level.playSound(
                null,
                hivePos.getX(),
                hivePos.getY(),
                hivePos.getZ(),
                SoundEvents.BEEHIVE_ENTER,
                SoundSource.BLOCKS,
                1.0F,
                1.0F);
        level.gameEvent(GameEvent.BLOCK_CHANGE, hivePos, GameEvent.Context.of(bee, hive.getBlockState()));
        bee.discard();
        hive.setChanged();
        return true;
    }

    public static boolean isFirstDay(Bee bee) {
        BeeMemory memory = memory(bee);
        return ageDays(bee.level(), memory) <= 0;
    }

    public static void markDirectAttack(Bee bee, LivingEntity attacker) {
        BeeMemory memory = memory(bee);
        if (memory.role() == BeeRole.DRONE) {
            ensureDroneHasNoStinger(bee, memory);
            return;
        }
        if (attacker instanceof Player player && isHoldingHiveDaySimulator(player)) {
            clearSimulatorProtectedAggression(bee, memory);
            return;
        }
        memory.setAggressionCause(BeeAggressionCause.DIRECT_ATTACK);
        bee.setTarget(attacker);
        bee.startPersistentAngerTimer();
        if (attacker instanceof Player player && memory.homeHive() != null && bee.level() instanceof ServerLevel level) {
            angerHomeHive(level, memory.homeHive(), player);
        }
    }

    public static void markPathBlocked(Bee bee, Player player) {
        BeeMemory memory = memory(bee);
        if (memory.role() == BeeRole.DRONE) {
            ensureDroneHasNoStinger(bee, memory);
            return;
        }
        if (isHoldingHiveDaySimulator(player)) {
            clearSimulatorProtectedAggression(bee, memory);
            return;
        }
        if (memory.aggressionCause() != BeeAggressionCause.DIRECT_ATTACK) {
            memory.setAggressionCause(BeeAggressionCause.PATH_BLOCKED);
        }
        bee.setTarget(player);
        bee.startPersistentAngerTimer();
    }

    private static void angerHomeHive(ServerLevel level, BlockPos hivePos, Player player) {
        if (isHoldingHiveDaySimulator(player)) {
            return;
        }
        BlockEntity blockEntity = level.getBlockEntity(hivePos);
        if (blockEntity instanceof BeehiveBlockEntity hive) {
            hive.emptyAllLivingFromHive(player, level.getBlockState(hivePos), BeehiveBlockEntity.BeeReleaseStatus.EMERGENCY);
        }
        AABB area = new AABB(hivePos).inflate(12.0, 8.0, 12.0);
        for (Bee bee : level.getEntitiesOfClass(Bee.class, area)) {
            BeeMemory memory = memory(bee);
            if (memory.role() == BeeRole.DRONE) {
                ensureDroneHasNoStinger(bee, memory);
                continue;
            }
            bee.setTarget(player);
            bee.startPersistentAngerTimer();
            memory.setAggressionCause(BeeAggressionCause.DIRECT_ATTACK);
        }
    }

    public static boolean produceChild(ServerLevel level, BlockPos hivePos) {
        if (!(level.getBlockEntity(hivePos) instanceof BeehiveBlockEntity hive)) {
            return false;
        }
        ColonyData colony = colony(hive);
        long day = colonyDay(level, colony);
        if (!tryProduceLogicalChild(level, colony, day)) {
            return false;
        }

        hive.setChanged();
        return true;
    }

    public static boolean mateColony(ServerLevel level, BlockPos hivePos, @Nullable BlockPos mateHivePos) {
        if (!(level.getBlockEntity(hivePos) instanceof BeehiveBlockEntity hive)) {
            return false;
        }
        ColonyData colony = colony(hive);
        if (colony.queenId() == null || colony.doomed() || colony.abandoned()) {
            return false;
        }
        ColonyData mateColony = null;
        if (mateHivePos != null) {
            if (mateHivePos.equals(hivePos) || !(level.getBlockEntity(mateHivePos) instanceof BeehiveBlockEntity mateHive)) {
                return false;
            }
            mateColony = colony(mateHive);
            long mateDay = colonyDay(level, mateColony);
            if (!isCompatibleMatingTarget(colony, mateColony, mateDay, false)) {
                return false;
            }
        }

        long day = colonyDay(level, colony);
        if (!isQueenMatureForMating(colony, day)) {
            return false;
        }
        if (mateColony != null) {
            colony.setInbreedingCoefficient(estimateMatingInbreeding(colony, mateColony));
            colony.rememberMateLineages(mateColony);
        }
        colony.setLastMatedDay(day);
        colony.setFertileUntilDay(Math.max(colony.fertileUntilDay(), day + 1));
        colony.setDoomed(false);
        colony.setDeclining(colony.workerIds().isEmpty());
        produceChild(level, hivePos);
        hive.setChanged();
        return true;
    }

    private static double estimateMatingInbreeding(ColonyData queenColony, ColonyData mateColony) {
        double overlapRisk = queenColony.lineageOverlap(mateColony) * 0.5;
        double inheritedRisk = (queenColony.inbreedingCoefficient() + mateColony.inbreedingCoefficient()) * 0.5;
        return clamp01(overlapRisk + inheritedRisk * 0.5);
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static Optional<BeeRole> nextNeededRole(ColonyData colony, long day) {
        if (queenNeedsReplacement(colony, day)) {
            return Optional.of(BeeRole.QUEEN);
        }

        double workerNeed = casteNeed(colony.workerIds(), colony, day, EcologyConfig.MAX_WORKERS_PER_HIVE.get(), EcologyConfig.WORKER_LIFESPAN_DAYS.get());
        double droneNeed = casteNeed(colony.droneIds(), colony, day, EcologyConfig.MAX_DRONES_PER_HIVE.get(), EcologyConfig.DRONE_LIFESPAN_DAYS.get());
        if (workerNeed <= 0.0 && droneNeed <= 0.0) {
            return Optional.empty();
        }
        return workerNeed >= droneNeed ? Optional.of(BeeRole.WORKER) : Optional.of(BeeRole.DRONE);
    }

    private static boolean queenNeedsReplacement(ColonyData colony, long day) {
        if (colony.queenId() == null) {
            return colony.queenCount() < EcologyConfig.MAX_QUEENS_PER_HIVE.get();
        }
        if (colony.queenBirthDay() < 0) {
            return false;
        }
        return remainingDays(day, colony.queenBirthDay(), EcologyConfig.QUEEN_LIFESPAN_DAYS.get()) <= EcologyConfig.QUEEN_REPLACEMENT_DAYS.get();
    }

    private static double casteNeed(Set<java.util.UUID> ids, ColonyData colony, long day, int cap, int lifespanDays) {
        int shortage = Math.max(0, cap - ids.size());
        long expiringSoon = ids.stream()
                .filter(id -> colony.birthDay(id) >= 0)
                .filter(id -> remainingDays(day, colony.birthDay(id), lifespanDays) <= 2)
                .count();
        return shortage * 3.0 + expiringSoon;
    }

    private static long remainingDays(long day, long birthDay, int lifespanDays) {
        return lifespanDays - Math.max(0, day - birthDay);
    }

    public static boolean isPlayerNearRoute(Player player, BeeMemory memory) {
        if (isHoldingHiveDaySimulator(player)) {
            return false;
        }
        List<BlockPos> positions = memory.routePositions();
        if (positions.isEmpty() || memory.routeIndex() >= positions.size()) {
            return false;
        }

        Vec3 playerPos = player.position();
        Vec3 start = currentRouteLegStart(memory, positions);
        Vec3 end = Vec3.atCenterOf(positions.get(memory.routeIndex()));
        return Math.abs(playerPos.y - closestYOnSegment(playerPos, start, end)) <= EcologyConfig.PATH_VERTICAL_TRIGGER_RANGE.get()
                && horizontalDistanceToSegment(playerPos, start, end) <= EcologyConfig.PATH_HORIZONTAL_TRIGGER_RANGE.get();
    }

    private static Vec3 currentRouteLegStart(BeeMemory memory, List<BlockPos> positions) {
        if (memory.routeIndex() > 0) {
            return Vec3.atCenterOf(positions.get(memory.routeIndex() - 1));
        }
        return memory.homeHive() == null ? Vec3.atCenterOf(positions.get(0)) : Vec3.atCenterOf(memory.homeHive());
    }

    private static double closestYOnSegment(Vec3 point, Vec3 start, Vec3 end) {
        Vec3 segment = end.subtract(start);
        double lengthSqr = segment.lengthSqr();
        if (lengthSqr < 1.0E-6) {
            return start.y;
        }
        double t = point.subtract(start).dot(segment) / lengthSqr;
        t = Math.max(0.0, Math.min(1.0, t));
        return start.y + segment.y * t;
    }

    private static double horizontalDistanceToSegment(Vec3 point, Vec3 start, Vec3 end) {
        Vec3 flatPoint = new Vec3(point.x, 0.0, point.z);
        Vec3 flatStart = new Vec3(start.x, 0.0, start.z);
        Vec3 flatEnd = new Vec3(end.x, 0.0, end.z);
        Vec3 segment = flatEnd.subtract(flatStart);
        double lengthSqr = segment.lengthSqr();
        if (lengthSqr < 1.0E-6) {
            return flatPoint.distanceTo(flatStart);
        }
        double t = flatPoint.subtract(flatStart).dot(segment) / lengthSqr;
        t = Math.max(0.0, Math.min(1.0, t));
        return flatPoint.distanceTo(flatStart.add(segment.scale(t)));
    }

}
