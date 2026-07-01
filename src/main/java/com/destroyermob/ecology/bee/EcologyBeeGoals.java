package com.destroyermob.ecology.bee;

import com.destroyermob.ecology.EcologyConfig;
import com.destroyermob.ecology.network.EcologyNetworking;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public final class EcologyBeeGoals {
    private static final double FLOWER_ARRIVAL_DISTANCE = 1.1;
    private static final double CROP_ARRIVAL_DISTANCE = 1.25;
    private static final double DIRECT_HOVER_DISTANCE = 1.5;
    private static final int MATURE_CROP_POLLEN_SPEED_TICKS = 20 * 3;
    private static final int MATURE_CROP_POLLEN_SPEED_AMPLIFIER = 1;

    private EcologyBeeGoals() {
    }

    private static void wanderBeyondSearchArea(Bee bee, double speed) {
        if (!bee.getNavigation().isDone()) {
            return;
        }

        BlockPos current = bee.blockPosition();
        int horizontalStep = EcologyConfig.BEE_LOCAL_SEARCH_HORIZONTAL_RADIUS.get() + 1;
        int xDirection = randomDirection(bee);
        int zDirection = randomDirection(bee);

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
            if (!EcologyConfig.advancedBeeSimulationEnabled() || !EcologyConfig.replaceVanillaBeeGoalsEnabled()) {
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
            syncPollenState(memory);

            switch (memory.workerState()) {
                case SEARCHING_FLOWER -> tickSearchingFlower(memory);
                case MOVING_TO_FLOWER -> tickMovingToFlower(memory);
                case SEARCHING_CROP -> tickSearchingCrop(memory);
                case MOVING_TO_CROP -> tickMovingToCrop(memory);
            }
        }

        private void syncPollenState(BeeMemory memory) {
            if (bee.hasNectar() && !memory.carryingPollen()) {
                memory.setCarryingPollen(true);
                memory.resetCropSearch();
                memory.setRouteSearchMisses(0);
                if (memory.workerState() == WorkerBeeState.SEARCHING_FLOWER
                        || memory.workerState() == WorkerBeeState.MOVING_TO_FLOWER) {
                    transition(memory, WorkerBeeState.SEARCHING_CROP);
                }
            } else if (!bee.hasNectar() && memory.carryingPollen()) {
                EcologyBeeSystem.setPollenVisual(bee, true);
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
                if (bee.level() instanceof ServerLevel level) {
                    EcologyBeeSystem.assignHiveRouteIfNeeded(level, bee, memory, day);
                }
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
            if (completedPairs(memory) >= routePairLimit(memory)) {
                finishDay(memory);
                return;
            }

            if (targetAssignedStop(memory, BeeRouteStopType.FLOWER, WorkerBeeState.MOVING_TO_FLOWER)) {
                return;
            }
            if (memory.hasLearnedFlowerRoute()) {
                Optional<BlockPos> learnedTarget = nextLearnedFlowerTarget(memory);
                if (learnedTarget.isPresent()) {
                    memory.route().add(new BeeRouteStop(learnedTarget.get(), BeeRouteStopType.FLOWER));
                    memory.setRouteSearchMisses(0);
                    EcologyNetworking.sendBeeRouteUpdate(bee);
                    transition(memory, WorkerBeeState.MOVING_TO_FLOWER);
                    return;
                }
            }

            Optional<BlockPos> flowerTarget = EcologyBeeSystem.findLocalFlower(bee);
            if (flowerTarget.isPresent()) {
                memory.route().add(new BeeRouteStop(flowerTarget.get(), BeeRouteStopType.FLOWER));
                memory.setRouteSearchMisses(0);
                EcologyNetworking.sendBeeRouteUpdate(bee);
                transition(memory, WorkerBeeState.MOVING_TO_FLOWER);
                return;
            }

            if (memory.routeSearchMisses() >= EcologyConfig.MAX_ROUTE_SEARCH_MISSES.get() && !memory.route().isEmpty()) {
                finishDay(memory);
                return;
            }
            wanderBeyondSearchArea(bee, 1.0);
        }

        private void tickMovingToFlower(BeeMemory memory) {
            if (memory.carryingPollen() && targetLocalCrop(memory)) {
                return;
            }

            BeeRouteStop stop = currentStop(memory, BeeRouteStopType.FLOWER);
            if (stop == null) {
                abandonCurrentTargetAndSearch(memory);
                return;
            }
            if (tickTaskTimer(memory)) {
                abandonCurrentTargetAndSearch(memory);
                return;
            }
            if (!isStopStillValid(stop)) {
                memory.removeLearnedFlower(stop.pos());
                abandonCurrentTargetAndSearch(memory);
                return;
            }
            if (!isCloseTo(stop.pos(), FLOWER_ARRIVAL_DISTANCE)) {
                moveToward(stop.pos(), 1.2);
                return;
            }

            bee.setSavedFlowerPos(stop.pos());
            EcologyBeeSystem.setPollenVisual(bee, true);
            memory.setCarryingPollen(true);
            memory.resetCropSearch();
            memory.setRouteSearchMisses(0);
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

            if (targetAssignedStop(memory, BeeRouteStopType.CROP, WorkerBeeState.MOVING_TO_CROP)) {
                return;
            }
            if (targetLocalCrop(memory)) {
                return;
            }

            if (memory.routeSearchMisses() >= EcologyConfig.MAX_ROUTE_SEARCH_MISSES.get()) {
                finishDay(memory);
                return;
            }
            wanderBeyondSearchArea(bee, 1.0);
        }

        private void tickMovingToCrop(BeeMemory memory) {
            BeeRouteStop stop = currentStop(memory, BeeRouteStopType.CROP);
            if (stop == null) {
                abandonCurrentTargetAndSearch(memory);
                return;
            }
            if (tickTaskTimer(memory)) {
                abandonCurrentTargetAndSearch(memory);
                return;
            }
            if (!isStopStillValid(stop)) {
                abandonCurrentTargetAndSearch(memory);
                return;
            }
            if (!isCloseTo(stop.pos(), CROP_ARRIVAL_DISTANCE)) {
                moveToward(stop.pos(), 1.2);
                return;
            }

            if (bee.level() instanceof ServerLevel level) {
                consumePollenAtCrop(level, memory, stop.pos());
            }
            EcologyBeeSystem.setPollenVisual(bee, false);
            memory.setCarryingPollen(false);
            memory.resetFlowerSearch();
            memory.setRouteSearchMisses(0);
            memory.setRouteIndex(memory.routeIndex() + 1);
            if (completedPairs(memory) >= routePairLimit(memory)) {
                finishDay(memory);
            } else {
                transition(memory, WorkerBeeState.SEARCHING_FLOWER);
            }
        }

        private int routePairLimit(BeeMemory memory) {
            return bee.level() instanceof ServerLevel level
                    ? EcologyBeeSystem.routePairLimit(level, memory)
                    : EcologyConfig.MAX_ROUTE_PAIRS.get();
        }

        private void consumePollenAtCrop(ServerLevel level, BeeMemory memory, BlockPos cropPos) {
            if (EcologyBeeSystem.canGrowPollinationCrop(level, cropPos)) {
                EcologyBeeSystem.growCropFromPollination(level, memory, cropPos);
            } else if (EcologyBeeSystem.isFullyGrownPollinationCrop(level, cropPos)) {
                bee.addEffect(new MobEffectInstance(
                        MobEffects.MOVEMENT_SPEED,
                        MATURE_CROP_POLLEN_SPEED_TICKS,
                        MATURE_CROP_POLLEN_SPEED_AMPLIFIER));
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

        private boolean abandonCurrentTarget(BeeMemory memory) {
            boolean changed = false;
            while (memory.route().size() > memory.routeIndex()) {
                memory.route().remove(memory.route().size() - 1);
                changed = true;
            }
            return changed;
        }

        private void abandonCurrentTargetAndSearch(BeeMemory memory) {
            if (abandonCurrentTarget(memory)) {
                EcologyNetworking.sendBeeRouteUpdate(bee);
            }
            transition(memory, memory.carryingPollen() ? WorkerBeeState.SEARCHING_CROP : WorkerBeeState.SEARCHING_FLOWER);
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

        private int cropStops(BeeMemory memory) {
            int count = 0;
            for (BeeRouteStop stop : memory.route()) {
                if (stop.type() == BeeRouteStopType.CROP) {
                    count++;
                }
            }
            return count;
        }

        private int completedPairs(BeeMemory memory) {
            int count = 0;
            int completedStops = Math.min(memory.routeIndex(), memory.route().size());
            for (int i = 0; i < completedStops; i++) {
                if (memory.route().get(i).type() == BeeRouteStopType.CROP) {
                    count++;
                }
            }
            return count;
        }

        private boolean targetLocalCrop(BeeMemory memory) {
            Optional<BlockPos> cropTarget = EcologyBeeSystem.findLocalCrop(bee, bee.blockPosition());
            if (cropTarget.isEmpty()) {
                return false;
            }
            abandonCurrentTarget(memory);
            memory.route().add(new BeeRouteStop(cropTarget.get(), BeeRouteStopType.CROP));
            memory.setRouteSearchMisses(0);
            EcologyNetworking.sendBeeRouteUpdate(bee);
            transition(memory, WorkerBeeState.MOVING_TO_CROP);
            return true;
        }

        private boolean targetAssignedStop(BeeMemory memory, BeeRouteStopType expectedType, WorkerBeeState movingState) {
            BeeRouteStop stop = currentStop(memory, expectedType);
            if (stop == null) {
                return false;
            }
            if (!isStopStillValid(stop)) {
                if (abandonCurrentTarget(memory)) {
                    EcologyNetworking.sendBeeRouteUpdate(bee);
                }
                return false;
            }
            memory.setRouteSearchMisses(0);
            transition(memory, movingState);
            return true;
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
            if (trimUnpairedTrailingStops(memory)) {
                EcologyNetworking.sendBeeRouteUpdate(bee);
            }
            if (memory.route().isEmpty()) {
                returnHomeForRetry(memory);
                return;
            }
            boolean routeUpdated = false;
            if (canLearnRoute(memory)) {
                routeUpdated = memory.learnOptimizedRoute(memory.homeHive());
                if (memory.hasLearnedRoute()) {
                    memory.replaceRoute(List.copyOf(memory.learnedRoute()));
                    routeUpdated = true;
                }
            } else {
                memory.clearLearnedRoute();
            }
            if (routeUpdated) {
                EcologyNetworking.sendBeeRouteUpdate(bee);
            }
            EcologyBeeSystem.setPollenVisual(bee, false);
            memory.setCarryingPollen(false);
            memory.setDailyComplete(true);
            memory.setWorkerState(WorkerBeeState.SEARCHING_FLOWER);
            memory.setWorkerTaskTicks(0);
            EcologyBeeSystem.sendHome(bee);
        }

        private boolean trimUnpairedTrailingStops(BeeMemory memory) {
            boolean changed = false;
            int lastCropIndex = -1;
            for (int i = 0; i < memory.route().size(); i++) {
                if (memory.route().get(i).type() == BeeRouteStopType.CROP) {
                    lastCropIndex = i;
                }
            }
            while (memory.route().size() > lastCropIndex + 1) {
                memory.route().remove(memory.route().size() - 1);
                changed = true;
            }
            memory.setRouteIndex(Math.min(memory.routeIndex(), memory.route().size()));
            return changed;
        }

        private void returnHomeForRetry(BeeMemory memory) {
            EcologyBeeSystem.setPollenVisual(bee, false);
            memory.setCarryingPollen(false);
            memory.setDailyComplete(false);
            memory.setRouteIndex(0);
            memory.setWorkerState(WorkerBeeState.SEARCHING_FLOWER);
            memory.setWorkerTaskTicks(0);
            memory.setRouteSearchMisses(0);
            memory.clearSearchOrigins();
            EcologyBeeSystem.sendHome(bee);
        }

        private boolean canLearnRoute(BeeMemory memory) {
            return flowerStops(memory) > 2 || cropStops(memory) > 0;
        }

        private boolean isStopStillValid(BeeRouteStop stop) {
            return stop.type() == BeeRouteStopType.FLOWER
                    ? EcologyBeeSystem.isValidFlower(bee.level(), stop.pos())
                    : EcologyBeeSystem.isPollinationCrop(bee.level(), stop.pos());
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
            if (!EcologyConfig.advancedBeeSimulationEnabled() || !EcologyConfig.replaceVanillaBeeGoalsEnabled()) {
                return false;
            }
            BeeMemory memory = EcologyBeeSystem.memory(bee);
            return !bee.isAngry()
                    && !bee.hasStung()
                    && memory.returningHome()
                    && EcologyBeeSystem.returnHomeTarget(memory) != null;
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void tick() {
            BeeMemory memory = EcologyBeeSystem.memory(bee);
            BlockPos hivePos = EcologyBeeSystem.returnHomeTarget(memory);
            if (hivePos == null) {
                memory.setReturningHome(false);
                return;
            }
            boolean pendingRelocationReturn = EcologyBeeSystem.isPendingRelocationReturnTarget(memory, hivePos);
            if (!pendingRelocationReturn && !(bee.level().getBlockEntity(hivePos) instanceof BeehiveBlockEntity)) {
                memory.setHomeHive(null);
                memory.setReturningHome(false);
                return;
            }
            bee.setHivePos(hivePos);
            if (Vec3.atCenterOf(hivePos).distanceTo(bee.position()) <= 2.0) {
                if (pendingRelocationReturn && EcologyBeeSystem.completePendingRelocationReturn(bee, memory)) {
                    repathCooldown = 0;
                    return;
                }
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
            if (!EcologyConfig.advancedBeeSimulationEnabled()
                    || !EcologyConfig.replaceVanillaBeeGoalsEnabled()
                    || !EcologyConfig.queenMigrationGoalEnabled()) {
                return false;
            }
            if (!(bee.level() instanceof ServerLevel level) || bee.isAngry() || bee.hasStung()) {
                return false;
            }
            BeeMemory memory = EcologyBeeSystem.memory(bee);
            if (memory.role() != BeeRole.QUEEN || memory.homeHive() == null || memory.returningHome()) {
                return false;
            }
            if (!(level.getBlockEntity(memory.homeHive()) instanceof BeehiveBlockEntity)) {
                return false;
            }
            return !EcologyBeeSystem.shouldQueenSearchForMating(level, memory);
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
            if (!EcologyConfig.advancedBeeSimulationEnabled()
                    || !EcologyConfig.replaceVanillaBeeGoalsEnabled()
                    || !EcologyConfig.droneMatingGoalEnabled()) {
                return false;
            }
            if (!(bee.level() instanceof ServerLevel level) || bee.isAngry() || bee.hasStung()) {
                return false;
            }
            BeeMemory memory = EcologyBeeSystem.memory(bee);
            return memory.role() == BeeRole.DRONE
                    && memory.homeHive() != null
                    && !memory.returningHome()
                    && EcologyBeeSystem.shouldDroneSearchForMating(level, memory);
        }

        @Override
        public boolean canContinueToUse() {
            if (targetHive == null) {
                return canUse();
            }
            if (!EcologyConfig.advancedBeeSimulationEnabled()
                    || !EcologyConfig.replaceVanillaBeeGoalsEnabled()
                    || !EcologyConfig.droneMatingGoalEnabled()) {
                return false;
            }
            if (!(bee.level() instanceof ServerLevel) || bee.isAngry() || bee.hasStung()) {
                return false;
            }
            BeeMemory memory = EcologyBeeSystem.memory(bee);
            return memory.role() == BeeRole.DRONE
                    && memory.homeHive() != null
                    && !memory.returningHome();
        }

        @Override
        public void tick() {
            if (!(bee.level() instanceof ServerLevel level)) {
                return;
            }
            BeeMemory memory = EcologyBeeSystem.memory(bee);
            if (targetHive == null) {
                targetHive = EcologyBeeSystem.assignedDroneMatingHive(level, memory).orElse(null);
                if (targetHive == null) {
                    markDroneFailure(level, memory);
                    EcologyBeeSystem.sendHome(bee);
                    return;
                }
            }

            if (Vec3.atCenterOf(targetHive).distanceTo(bee.position()) <= 2.0) {
                if (EcologyBeeSystem.mateColony(level, targetHive, memory.homeHive())) {
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
                long day = EcologyBeeSystem.colonyDay(level, colony);
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
        private int repathCooldown;

        public QueenMigrationGoal(Bee bee) {
            this.bee = bee;
            this.setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (!EcologyConfig.advancedBeeSimulationEnabled()
                    || !EcologyConfig.replaceVanillaBeeGoalsEnabled()
                    || !EcologyConfig.queenMigrationGoalEnabled()) {
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
            return EcologyBeeSystem.shouldQueenSearchForMating(level, memory);
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void start() {
            this.targetHive = null;
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
                targetHive = EcologyBeeSystem.findLocalForeignQueenHive(level, memory, bee.blockPosition(), false).orElse(null);
                if (targetHive == null) {
                    if (EcologyBeeSystem.isBeyondHomeSearchDistance(bee, memory)) {
                        markDoomed(level, memory.homeHive());
                        EcologyBeeSystem.sendHome(bee);
                    } else {
                        wanderBeyondSearchArea(bee, 1.0);
                    }
                    return;
                }
            }
            if (Vec3.atCenterOf(targetHive).distanceTo(bee.position()) <= 2.0) {
                if (!EcologyBeeSystem.mateColony(level, memory.homeHive(), targetHive)) {
                    markDoomed(level, memory.homeHive());
                }
                EcologyBeeSystem.sendHome(bee);
                return;
            }
            moveToward(targetHive);
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
    }
}
