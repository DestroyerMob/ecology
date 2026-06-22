package com.destroyermob.ecology.bee;

import com.destroyermob.ecology.EcologyConfig;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public final class EcologyBeeGoals {
    private static final double FLOWER_ARRIVAL_DISTANCE = 1.1;
    private static final double CROP_ARRIVAL_DISTANCE = 1.25;
    private static final double DIRECT_HOVER_DISTANCE = 1.5;

    private EcologyBeeGoals() {
    }

    private static void wanderBeyondSearchArea(Bee bee, @Nullable BlockPos searchOrigin, double speed) {
        if (!bee.getNavigation().isDone()) {
            return;
        }

        BlockPos current = bee.blockPosition();
        int horizontalStep = EcologyConfig.BEE_LOCAL_SEARCH_HORIZONTAL_RADIUS.get() + 1;
        int xDirection = randomDirection(bee);
        int zDirection = randomDirection(bee);
        if (searchOrigin != null && EcologyBeeSystem.isWithinLocalSearchArea(searchOrigin, current)) {
            int awayX = Integer.compare(current.getX() - searchOrigin.getX(), 0);
            int awayZ = Integer.compare(current.getZ() - searchOrigin.getZ(), 0);
            if (awayX != 0) {
                xDirection = awayX;
            }
            if (awayZ != 0) {
                zDirection = awayZ;
            }
        }

        BlockPos target = current.offset(
                xDirection * horizontalStep,
                bee.getRandom().nextInt(3) - 1,
                zDirection * horizontalStep);
        target = findOpenAirNear(bee, target);
        Vec3 targetCenter = Vec3.atBottomCenterOf(target).add(0.0, 0.6, 0.0);
        if (!bee.getNavigation().moveTo(targetCenter.x(), targetCenter.y(), targetCenter.z(), speed)
                || bee.getNavigation().getPath() == null) {
            EcologyBeeSystem.pathfindRandomlyTowards(bee, target);
        }
        bee.getLookControl().setLookAt(targetCenter.x(), targetCenter.y(), targetCenter.z());
    }

    private static BlockPos findOpenAirNear(Bee bee, BlockPos target) {
        for (int yOffset = 0; yOffset <= 4; yOffset++) {
            BlockPos candidate = target.above(yOffset);
            if (bee.level().getBlockState(candidate).isAir() && bee.level().getBlockState(candidate.above()).isAir()) {
                return candidate;
            }
        }
        for (int yOffset = 1; yOffset <= 2; yOffset++) {
            BlockPos candidate = target.below(yOffset);
            if (bee.level().getBlockState(candidate).isAir() && bee.level().getBlockState(candidate.above()).isAir()) {
                return candidate;
            }
        }
        return target.above();
    }

    private static int randomDirection(Bee bee) {
        return bee.getRandom().nextBoolean() ? 1 : -1;
    }

    public static class WorkerRouteGoal extends Goal {
        private final Bee bee;
        private int repathCooldown;

        public WorkerRouteGoal(Bee bee) {
            this.bee = bee;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (!EcologyConfig.ENABLE_BEE_SYSTEM.get() || !EcologyConfig.REPLACE_VANILLA_BEE_GOALS.get()) {
                return false;
            }
            if (!(bee.level() instanceof ServerLevel) || bee.isAngry() || bee.hasStung()) {
                return false;
            }
            BeeMemory memory = EcologyBeeSystem.memory(bee);
            if (memory.role() != BeeRole.WORKER) {
                return false;
            }
            prepareDay(memory);
            if (memory.homeHive() == null) {
                return false;
            }
            return !memory.returningHome() && !memory.dailyComplete();
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void tick() {
            BeeMemory memory = EcologyBeeSystem.memory(bee);
            if (!hasValidHome(memory)) {
                memory.setDailyComplete(true);
                return;
            }
            if (isTooFarFromHome(memory)) {
                finishDay(memory);
                return;
            }

            switch (memory.workerState()) {
                case SEARCHING_FLOWER -> tickSearchingFlower(memory);
                case MOVING_TO_FLOWER -> tickMovingToFlower(memory);
                case SEARCHING_CROP -> tickSearchingCrop(memory);
                case MOVING_TO_CROP -> tickMovingToCrop(memory);
            }
        }

        @Override
        public void stop() {
            bee.getNavigation().stop();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        private void prepareDay(BeeMemory memory) {
            long day = EcologyBeeSystem.day(bee.level());
            if (memory.routeDay() != day) {
                memory.resetDailyRoute(day);
                EcologyBeeSystem.setPollenVisual(bee, false);
            }
        }

        private void tickSearchingFlower(BeeMemory memory) {
            if (tickTaskTimer(memory)) {
                finishDay(memory);
                return;
            }
            if (memory.carryingPollen()) {
                transition(memory, WorkerBeeState.SEARCHING_CROP);
                return;
            }
            if (flowerStops(memory) >= EcologyConfig.MAX_ROUTE_PAIRS.get()) {
                finishDay(memory);
                return;
            }

            if (memory.hasLearnedFlowerRoute()) {
                Optional<BlockPos> learnedTarget = nextLearnedFlowerTarget(memory);
                if (learnedTarget.isPresent()) {
                    memory.route().add(new BeeRouteStop(learnedTarget.get(), BeeRouteStopType.FLOWER));
                    memory.setRouteSearchMisses(0);
                    transition(memory, WorkerBeeState.MOVING_TO_FLOWER);
                    return;
                }
                if (memory.hasLearnedFlowerRoute()) {
                    finishDay(memory);
                    return;
                }
            }

            Optional<BlockPos> flowerTarget = EcologyBeeSystem.findLocalFlower(bee);
            if (flowerTarget.isPresent()) {
                memory.route().add(new BeeRouteStop(flowerTarget.get(), BeeRouteStopType.FLOWER));
                memory.setRouteSearchMisses(0);
                transition(memory, WorkerBeeState.MOVING_TO_FLOWER);
                return;
            }

            if (memory.routeSearchMisses() >= EcologyConfig.MAX_ROUTE_SEARCH_MISSES.get()) {
                finishDay(memory);
                return;
            }
            wanderBeyondSearchArea(bee, searchWanderOrigin(memory), 1.0);
        }

        private void tickMovingToFlower(BeeMemory memory) {
            BeeRouteStop stop = currentStop(memory, BeeRouteStopType.FLOWER);
            if (stop == null) {
                abandonCurrentTarget(memory);
                transitionWithoutTimerReset(memory, WorkerBeeState.SEARCHING_FLOWER);
                return;
            }
            if (tickTaskTimer(memory)) {
                finishDay(memory);
                return;
            }
            if (!isStopStillValid(stop)) {
                memory.removeLearnedFlower(stop.pos());
                abandonCurrentTarget(memory);
                transitionWithoutTimerReset(memory, WorkerBeeState.SEARCHING_FLOWER);
                return;
            }
            if (!isCloseTo(stop.pos(), FLOWER_ARRIVAL_DISTANCE)) {
                moveToward(stop.pos(), 1.2);
                return;
            }

            bee.setSavedFlowerPos(stop.pos());
            EcologyBeeSystem.setPollenVisual(bee, true);
            memory.setCarryingPollen(true);
            memory.setCropSearchOrigin(null);
            memory.setRouteIndex(memory.routeIndex() + 1);
            transition(memory, WorkerBeeState.SEARCHING_CROP);
        }

        private void tickSearchingCrop(BeeMemory memory) {
            if (tickTaskTimer(memory)) {
                finishDay(memory);
                return;
            }
            if (!memory.carryingPollen()) {
                transition(memory, WorkerBeeState.SEARCHING_FLOWER);
                return;
            }

            Optional<BlockPos> cropTarget = EcologyBeeSystem.findLocalCrop(bee, bee.blockPosition());
            if (cropTarget.isPresent()) {
                memory.route().add(new BeeRouteStop(cropTarget.get(), BeeRouteStopType.CROP));
                memory.setRouteSearchMisses(0);
                transition(memory, WorkerBeeState.MOVING_TO_CROP);
                return;
            }

            if (memory.routeSearchMisses() >= EcologyConfig.MAX_ROUTE_SEARCH_MISSES.get()) {
                finishDay(memory);
                return;
            }
            wanderBeyondSearchArea(bee, memory.cropSearchOrigin(), 1.0);
        }

        private void tickMovingToCrop(BeeMemory memory) {
            BeeRouteStop stop = currentStop(memory, BeeRouteStopType.CROP);
            if (stop == null) {
                abandonCurrentTarget(memory);
                transitionWithoutTimerReset(memory, WorkerBeeState.SEARCHING_FLOWER);
                return;
            }
            if (tickTaskTimer(memory)) {
                finishDay(memory);
                return;
            }
            if (!isStopStillValid(stop)) {
                abandonCurrentTarget(memory);
                transitionWithoutTimerReset(memory, WorkerBeeState.SEARCHING_FLOWER);
                return;
            }
            if (!isCloseTo(stop.pos(), CROP_ARRIVAL_DISTANCE)) {
                moveToward(stop.pos(), 1.2);
                return;
            }

            if (bee.level() instanceof ServerLevel level && EcologyBeeSystem.canGrowPollinationCrop(level, stop.pos())) {
                EcologyBeeSystem.growCrop(level, stop.pos());
            }
            EcologyBeeSystem.setPollenVisual(bee, false);
            memory.setCarryingPollen(false);
            memory.setFlowerSearchOrigin(null);
            memory.setRouteIndex(memory.routeIndex() + 1);
            if (flowerStops(memory) >= EcologyConfig.MAX_ROUTE_PAIRS.get()) {
                finishDay(memory);
            } else {
                transition(memory, WorkerBeeState.SEARCHING_FLOWER);
            }
        }

        @Nullable
        private BeeRouteStop currentStop(BeeMemory memory, BeeRouteStopType expectedType) {
            if (memory.routeIndex() >= memory.route().size()) {
                return null;
            }
            BeeRouteStop stop = memory.route().get(memory.routeIndex());
            return stop.type() == expectedType ? stop : null;
        }

        private void abandonCurrentTarget(BeeMemory memory) {
            while (memory.route().size() > memory.routeIndex()) {
                memory.route().remove(memory.route().size() - 1);
            }
        }

        @Nullable
        private BlockPos searchWanderOrigin(BeeMemory memory) {
            return memory.flowerSearchOrigin() != null ? memory.flowerSearchOrigin() : memory.cropSearchOrigin();
        }

        private int flowerStops(BeeMemory memory) {
            int count = 0;
            for (BeeRouteStop stop : memory.route()) {
                if (stop.type() == BeeRouteStopType.FLOWER) {
                    count++;
                }
            }
            return count;
        }

        private Optional<BlockPos> nextLearnedFlowerTarget(BeeMemory memory) {
            int nextIndex = flowerStops(memory);
            while (nextIndex < memory.learnedFlowerRoute().size()) {
                BlockPos learnedFlower = memory.learnedFlowerRoute().get(nextIndex);
                if (EcologyBeeSystem.isValidFlower(bee.level(), learnedFlower)) {
                    return Optional.of(learnedFlower);
                }
                memory.removeLearnedFlower(learnedFlower);
            }
            return Optional.empty();
        }

        private void transition(BeeMemory memory, WorkerBeeState state) {
            memory.setWorkerState(state);
            memory.setWorkerTaskTicks(taskTicks(state));
            this.repathCooldown = 0;
        }

        private void transitionWithoutTimerReset(BeeMemory memory, WorkerBeeState state) {
            memory.setWorkerState(state);
            this.repathCooldown = 0;
        }

        private boolean tickTaskTimer(BeeMemory memory) {
            if (memory.workerTaskTicks() <= 0) {
                memory.setWorkerTaskTicks(taskTicks(memory.workerState()));
            }
            memory.setWorkerTaskTicks(memory.workerTaskTicks() - 1);
            return memory.workerTaskTicks() <= 0;
        }

        private int taskTicks(WorkerBeeState state) {
            return switch (state) {
                case SEARCHING_FLOWER -> EcologyConfig.WORKER_FLOWER_SEARCH_TICKS.get();
                case SEARCHING_CROP -> EcologyConfig.WORKER_CROP_SEARCH_TICKS.get();
                case MOVING_TO_FLOWER, MOVING_TO_CROP -> EcologyConfig.WORKER_TRAVEL_TARGET_TICKS.get();
            };
        }

        private boolean hasValidHome(BeeMemory memory) {
            if (memory.homeHive() == null) {
                return false;
            }
            if (bee.level().getBlockEntity(memory.homeHive()) instanceof BeehiveBlockEntity) {
                return true;
            }
            memory.setHomeHive(null);
            return false;
        }

        private boolean isTooFarFromHome(BeeMemory memory) {
            return memory.homeHive() != null
                    && !bee.blockPosition().closerThan(memory.homeHive(), EcologyConfig.WORKER_MAX_DISTANCE_FROM_HIVE.get());
        }

        private void finishDay(BeeMemory memory) {
            if (flowerStops(memory) > memory.learnedFlowerRoute().size()) {
                memory.learnOptimizedFlowerRoute(memory.homeHive());
            }
            EcologyBeeSystem.setPollenVisual(bee, false);
            memory.setCarryingPollen(false);
            memory.setDailyComplete(true);
            memory.setWorkerState(WorkerBeeState.SEARCHING_FLOWER);
            memory.setWorkerTaskTicks(0);
            EcologyBeeSystem.sendHome(bee);
        }

        private boolean isStopStillValid(BeeRouteStop stop) {
            return stop.type() == BeeRouteStopType.FLOWER
                    ? EcologyBeeSystem.isValidFlower(bee.level(), stop.pos())
                    : EcologyBeeSystem.canGrowPollinationCrop(bee.level(), stop.pos());
        }

        private boolean isCloseTo(BlockPos pos, double distance) {
            return hoverPos(pos).distanceTo(bee.position()) <= distance;
        }

        private void moveToward(BlockPos pos, double speed) {
            Vec3 target = hoverPos(pos);
            bee.getLookControl().setLookAt(target.x(), target.y(), target.z());
            if (target.distanceTo(bee.position()) <= DIRECT_HOVER_DISTANCE) {
                bee.getNavigation().stop();
                bee.getMoveControl().setWantedPosition(target.x(), target.y(), target.z(), speed);
                return;
            }

            if (repathCooldown-- <= 0 || bee.getNavigation().isDone()) {
                repathCooldown = 10;
                if (!bee.getNavigation().moveTo(target.x(), target.y(), target.z(), speed)
                        || bee.getNavigation().getPath() == null) {
                    EcologyBeeSystem.pathfindRandomlyTowards(bee, pos);
                }
            }
        }

        private Vec3 hoverPos(BlockPos pos) {
            return Vec3.atBottomCenterOf(pos).add(0.0, 0.6, 0.0);
        }
    }

    public static class ReturnHomeGoal extends Goal {
        private final Bee bee;
        private int repathCooldown;

        public ReturnHomeGoal(Bee bee) {
            this.bee = bee;
            this.setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (!EcologyConfig.ENABLE_BEE_SYSTEM.get() || !EcologyConfig.REPLACE_VANILLA_BEE_GOALS.get()) {
                return false;
            }
            BeeMemory memory = EcologyBeeSystem.memory(bee);
            return !bee.isAngry() && !bee.hasStung() && memory.returningHome() && memory.homeHive() != null;
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void tick() {
            BeeMemory memory = EcologyBeeSystem.memory(bee);
            BlockPos hivePos = memory.homeHive();
            if (hivePos == null) {
                memory.setReturningHome(false);
                return;
            }
            if (!(bee.level().getBlockEntity(hivePos) instanceof BeehiveBlockEntity)) {
                memory.setHomeHive(null);
                memory.setReturningHome(false);
                return;
            }
            bee.setHivePos(hivePos);
            if (Vec3.atCenterOf(hivePos).distanceTo(bee.position()) <= 2.0) {
                EcologyBeeSystem.enterHive(bee, hivePos);
                return;
            }
            if (repathCooldown-- <= 0 || bee.getNavigation().isDone()) {
                repathCooldown = 20;
                bee.getNavigation().moveTo(hivePos.getX() + 0.5, hivePos.getY() + 0.5, hivePos.getZ() + 0.5, 1.1);
                if (bee.getNavigation().getPath() == null) {
                    EcologyBeeSystem.pathfindRandomlyTowards(bee, hivePos);
                }
            }
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }
    }

    public static class QueenReturnHomeGoal extends Goal {
        private final Bee bee;

        public QueenReturnHomeGoal(Bee bee) {
            this.bee = bee;
        }

        @Override
        public boolean canUse() {
            if (!EcologyConfig.ENABLE_BEE_SYSTEM.get()
                    || !EcologyConfig.REPLACE_VANILLA_BEE_GOALS.get()
                    || !EcologyConfig.ENABLE_QUEEN_MIGRATION_GOAL.get()) {
                return false;
            }
            if (!(bee.level() instanceof ServerLevel level) || bee.isAngry() || bee.hasStung()) {
                return false;
            }
            BeeMemory memory = EcologyBeeSystem.memory(bee);
            if (memory.role() != BeeRole.QUEEN || memory.homeHive() == null || memory.returningHome()) {
                return false;
            }
            if (!(level.getBlockEntity(memory.homeHive()) instanceof BeehiveBlockEntity hive)) {
                return false;
            }
            ColonyData colony = EcologyBeeSystem.colony(hive);
            boolean shouldMigrate = !colony.doomed()
                    && !colony.abandoned()
                    && colony.lastDroneFailureDay() == EcologyBeeSystem.day(level)
                    && colony.lastChildDay() != EcologyBeeSystem.day(level);
            return !shouldMigrate;
        }

        @Override
        public void start() {
            EcologyBeeSystem.sendHome(bee);
        }
    }

    public static class DroneMatingGoal extends Goal {
        private final Bee bee;
        private BlockPos targetHive;
        private int repathCooldown;

        public DroneMatingGoal(Bee bee) {
            this.bee = bee;
            this.setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (!EcologyConfig.ENABLE_BEE_SYSTEM.get()
                    || !EcologyConfig.REPLACE_VANILLA_BEE_GOALS.get()
                    || !EcologyConfig.ENABLE_DRONE_MATING_GOAL.get()) {
                return false;
            }
            if (!(bee.level() instanceof ServerLevel) || bee.isAngry() || bee.hasStung()) {
                return false;
            }
            BeeMemory memory = EcologyBeeSystem.memory(bee);
            return memory.role() == BeeRole.DRONE && memory.homeHive() != null && !memory.returningHome();
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void tick() {
            if (!(bee.level() instanceof ServerLevel level)) {
                return;
            }
            BeeMemory memory = EcologyBeeSystem.memory(bee);
            if (targetHive == null) {
                boolean searchedThisTick = EcologyBeeSystem.shouldSearchFrom(memory.foreignHiveSearchOrigin(), bee.blockPosition());
                targetHive = EcologyBeeSystem.findLocalForeignQueenHive(level, memory, bee.blockPosition()).orElse(null);
                if (targetHive == null) {
                    if (searchedThisTick && EcologyBeeSystem.isBeyondHomeSearchDistance(bee, memory)) {
                        markDroneFailure(level, memory);
                        EcologyBeeSystem.sendHome(bee);
                    } else {
                        wanderBeyondSearchArea(bee, memory.foreignHiveSearchOrigin(), 1.0);
                    }
                    return;
                }
            }

            if (Vec3.atCenterOf(targetHive).distanceTo(bee.position()) <= 2.0) {
                if (EcologyBeeSystem.mateColony(level, targetHive)) {
                    EcologyBeeSystem.forgetAtHomeHive(level, memory);
                    bee.discard();
                } else {
                    targetHive = null;
                    EcologyBeeSystem.sendHome(bee);
                }
                return;
            }
            if (repathCooldown-- <= 0 || bee.getNavigation().isDone()) {
                repathCooldown = 30;
                bee.getNavigation().moveTo(targetHive.getX() + 0.5, targetHive.getY() + 0.5, targetHive.getZ() + 0.5, 1.1);
                if (bee.getNavigation().getPath() == null) {
                    EcologyBeeSystem.pathfindRandomlyTowards(bee, targetHive);
                }
            }
        }

        private void markDroneFailure(ServerLevel level, BeeMemory memory) {
            if (memory.homeHive() != null && level.getBlockEntity(memory.homeHive()) instanceof BeehiveBlockEntity hive) {
                ColonyData colony = EcologyBeeSystem.colony(hive);
                long day = EcologyBeeSystem.day(level);
                if (colony.lastDroneFailureDay() != day) {
                    colony.setLastDroneFailureDay(day);
                    hive.setChanged();
                }
            }
        }
    }

    public static class QueenMigrationGoal extends Goal {
        private final Bee bee;
        private BlockPos targetHive;
        private BlockPos emptyHive;
        private boolean returningToOldHive;
        private int repathCooldown;

        public QueenMigrationGoal(Bee bee) {
            this.bee = bee;
            this.setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (!EcologyConfig.ENABLE_BEE_SYSTEM.get()
                    || !EcologyConfig.REPLACE_VANILLA_BEE_GOALS.get()
                    || !EcologyConfig.ENABLE_QUEEN_MIGRATION_GOAL.get()) {
                return false;
            }
            if (!(bee.level() instanceof ServerLevel level) || bee.isAngry() || bee.hasStung()) {
                return false;
            }
            BeeMemory memory = EcologyBeeSystem.memory(bee);
            if (memory.role() != BeeRole.QUEEN || memory.homeHive() == null) {
                return false;
            }
            if (memory.returningHome()) {
                return false;
            }
            if (!(level.getBlockEntity(memory.homeHive()) instanceof BeehiveBlockEntity hive)) {
                return false;
            }
            ColonyData colony = EcologyBeeSystem.colony(hive);
            if (colony.doomed() || colony.abandoned()) {
                return false;
            }
            return colony.lastDroneFailureDay() == EcologyBeeSystem.day(level) && colony.lastChildDay() != EcologyBeeSystem.day(level);
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void start() {
            this.targetHive = null;
            this.emptyHive = null;
            this.returningToOldHive = false;
        }

        @Override
        public void tick() {
            if (!(bee.level() instanceof ServerLevel level)) {
                return;
            }
            BeeMemory memory = EcologyBeeSystem.memory(bee);
            if (memory.homeHive() == null) {
                return;
            }

            if (targetHive == null) {
                targetHive = EcologyBeeSystem.findLocalForeignQueenHive(level, memory, bee.blockPosition()).orElse(null);
                if (targetHive == null) {
                    if (EcologyBeeSystem.isBeyondHomeSearchDistance(bee, memory)) {
                        markDoomed(level, memory.homeHive());
                        EcologyBeeSystem.sendHome(bee);
                    } else {
                        wanderBeyondSearchArea(bee, memory.foreignHiveSearchOrigin(), 1.0);
                    }
                    return;
                }
            }
            if (emptyHive == null) {
                emptyHive = EcologyBeeSystem.findLocalEmptyHive(level, memory, bee.blockPosition()).orElse(null);
                if (emptyHive == null) {
                    if (EcologyBeeSystem.isBeyondHomeSearchDistance(bee, memory)) {
                        markDoomed(level, memory.homeHive());
                        EcologyBeeSystem.sendHome(bee);
                    } else {
                        wanderBeyondSearchArea(bee, memory.emptyHiveSearchOrigin(), 1.0);
                    }
                    return;
                }
            }

            BlockPos destination = returningToOldHive ? memory.homeHive() : targetHive;
            if (Vec3.atCenterOf(destination).distanceTo(bee.position()) <= 2.0) {
                if (!returningToOldHive) {
                    if (!EcologyBeeSystem.mateColony(level, targetHive)) {
                        markDoomed(level, memory.homeHive());
                        EcologyBeeSystem.sendHome(bee);
                        return;
                    }
                    returningToOldHive = true;
                    return;
                }
                migrateColony(level, memory.homeHive(), emptyHive);
                memory.setHomeHive(emptyHive);
                memory.setReturningHome(true);
                EcologyBeeSystem.sendHome(bee);
                return;
            }
            moveToward(destination);
        }

        private void moveToward(BlockPos destination) {
            if (repathCooldown-- <= 0 || bee.getNavigation().isDone()) {
                repathCooldown = 30;
                bee.getNavigation().moveTo(destination.getX() + 0.5, destination.getY() + 0.5, destination.getZ() + 0.5, 1.1);
                if (bee.getNavigation().getPath() == null) {
                    EcologyBeeSystem.pathfindRandomlyTowards(bee, destination);
                }
            }
        }

        private void markDoomed(ServerLevel level, BlockPos hivePos) {
            if (level.getBlockEntity(hivePos) instanceof BeehiveBlockEntity hive) {
                ColonyData colony = EcologyBeeSystem.colony(hive);
                if (!colony.doomed()) {
                    colony.setDoomed(true);
                    hive.setChanged();
                }
            }
        }

        private void migrateColony(ServerLevel level, BlockPos oldHive, BlockPos newHive) {
            if (level.getBlockEntity(oldHive) instanceof BeehiveBlockEntity oldHiveEntity) {
                ColonyData oldColony = EcologyBeeSystem.colony(oldHiveEntity);
                oldColony.setAbandoned(true);
                oldColony.setMigrationTarget(newHive);
                oldHiveEntity.emptyAllLivingFromHive(null, level.getBlockState(oldHive), BeehiveBlockEntity.BeeReleaseStatus.EMERGENCY);
                oldHiveEntity.setChanged();
            }

            List<Bee> nearbyWorkers = level.getEntitiesOfClass(Bee.class, new net.minecraft.world.phys.AABB(oldHive).inflate(16.0, 8.0, 16.0));
            for (Bee worker : nearbyWorkers) {
                BeeMemory workerMemory = EcologyBeeSystem.memory(worker);
                if (workerMemory.role() == BeeRole.WORKER || workerMemory.role() == BeeRole.QUEEN) {
                    workerMemory.setHomeHive(newHive);
                    worker.setHivePos(newHive);
                    EcologyBeeSystem.sendHome(worker);
                    if (level.getBlockEntity(newHive) instanceof BeehiveBlockEntity newHiveEntity) {
                        if (EcologyBeeSystem.colony(newHiveEntity).remember(workerMemory)) {
                            newHiveEntity.setChanged();
                        }
                    }
                }
            }
        }
    }
}
