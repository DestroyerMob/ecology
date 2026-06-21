package com.destroyermob.ecology.bee;

import com.destroyermob.ecology.EcologyConfig;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public final class EcologyBeeGoals {
    private EcologyBeeGoals() {
    }

    private static void wanderBeyondSearchArea(Bee bee, @Nullable BlockPos searchOrigin, double speed) {
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
        Vec3 targetCenter = Vec3.atBottomCenterOf(target).add(0.0, 0.6, 0.0);
        bee.getMoveControl().setWantedPosition(targetCenter.x(), targetCenter.y(), targetCenter.z(), speed);
        bee.getLookControl().setLookAt(targetCenter.x(), targetCenter.y(), targetCenter.z());
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
            if (isLearningDay(memory)) {
                memory.setDailyComplete(true);
                EcologyBeeSystem.sendHome(bee);
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
            if (!ensureNextStop(memory)) {
                return;
            }

            BeeRouteStop stop = memory.route().get(memory.routeIndex());
            if (!isStopStillValid(stop)) {
                finishDay(memory);
                return;
            }

            if (!isCloseTo(stop.pos(), 1.8)) {
                moveToward(stop.pos(), 1.2);
                return;
            }

            if (stop.type() == BeeRouteStopType.FLOWER) {
                bee.setSavedFlowerPos(stop.pos());
                EcologyBeeSystem.setPollenVisual(bee, true);
                memory.setCarryingPollen(true);
                memory.setCropSearchOrigin(null);
                memory.setRouteIndex(memory.routeIndex() + 1);
            } else if (bee.level() instanceof ServerLevel level && memory.carryingPollen()) {
                if (EcologyBeeSystem.canGrowPollinationCrop(level, stop.pos())) {
                    EcologyBeeSystem.growCrop(level, stop.pos());
                }
                EcologyBeeSystem.setPollenVisual(bee, false);
                memory.setCarryingPollen(false);
                memory.setFlowerSearchOrigin(null);
                memory.setRouteIndex(memory.routeIndex() + 1);
            } else {
                finishDay(memory);
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

        private boolean ensureNextStop(BeeMemory memory) {
            if (memory.routeIndex() < memory.route().size()) {
                return true;
            }
            if (memory.route().size() >= EcologyConfig.MAX_ROUTE_PAIRS.get() * 2) {
                finishDay(memory);
                return false;
            }

            BlockPos current = bee.blockPosition();
            BlockPos searchOrigin = memory.carryingPollen() ? memory.cropSearchOrigin() : memory.flowerSearchOrigin();
            boolean searchedThisTick = EcologyBeeSystem.shouldSearchFrom(searchOrigin, current);
            java.util.Optional<BlockPos> target = memory.carryingPollen()
                    ? EcologyBeeSystem.findLocalCrop(bee, current)
                    : EcologyBeeSystem.findLocalFlower(bee);

            if (target.isPresent()) {
                BeeRouteStopType type = memory.carryingPollen() ? BeeRouteStopType.CROP : BeeRouteStopType.FLOWER;
                memory.route().add(new BeeRouteStop(target.get(), type));
                memory.setRouteSearchMisses(0);
                return true;
            }

            if (searchedThisTick) {
                memory.setRouteSearchMisses(memory.routeSearchMisses() + 1);
            }
            if (memory.routeSearchMisses() >= EcologyConfig.MAX_ROUTE_SEARCH_MISSES.get()) {
                finishDay(memory);
                return false;
            }

            BlockPos updatedOrigin = memory.carryingPollen() ? memory.cropSearchOrigin() : memory.flowerSearchOrigin();
            wanderBeyondSearchArea(bee, updatedOrigin, 1.0);
            return false;
        }

        private void finishDay(BeeMemory memory) {
            EcologyBeeSystem.setPollenVisual(bee, false);
            memory.setCarryingPollen(false);
            memory.setDailyComplete(true);
            EcologyBeeSystem.sendHome(bee);
        }

        private boolean isStopStillValid(BeeRouteStop stop) {
            return stop.type() == BeeRouteStopType.FLOWER
                    ? EcologyBeeSystem.isValidFlower(bee.level(), stop.pos())
                    : EcologyBeeSystem.isPollinationCrop(bee.level(), stop.pos());
        }

        private boolean isLearningDay(BeeMemory memory) {
            return memory.birthDay() >= 0 && EcologyBeeSystem.day(bee.level()) <= memory.birthDay();
        }

        private boolean isCloseTo(BlockPos pos, double distance) {
            return hoverPos(pos).distanceTo(bee.position()) <= distance;
        }

        private void moveToward(BlockPos pos, double speed) {
            if (repathCooldown-- <= 0) {
                repathCooldown = 5;
                Vec3 target = hoverPos(pos);
                bee.getMoveControl().setWantedPosition(target.x(), target.y(), target.z(), speed);
                bee.getLookControl().setLookAt(target.x(), target.y(), target.z());
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
