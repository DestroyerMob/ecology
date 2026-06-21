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

public final class EcologyBeeGoals {
    private EcologyBeeGoals() {
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
            if (!(bee.level() instanceof ServerLevel) || bee.isAngry() || bee.hasStung()) {
                return false;
            }
            BeeMemory memory = EcologyBeeSystem.memory(bee);
            if (memory.role() != BeeRole.WORKER || memory.returningHome()) {
                return false;
            }
            prepareDay(memory);
            return !memory.dailyComplete();
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void tick() {
            BeeMemory memory = EcologyBeeSystem.memory(bee);
            if (memory.route().isEmpty()) {
                EcologyBeeSystem.sendHome(bee);
                memory.setDailyComplete(true);
                return;
            }
            if (memory.routeIndex() >= memory.route().size()) {
                finishDay(memory);
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
                memory.setRouteIndex(memory.routeIndex() + 1);
            } else if (bee.level() instanceof ServerLevel level && memory.carryingPollen() && EcologyBeeSystem.growCrop(level, stop.pos())) {
                EcologyBeeSystem.setPollenVisual(bee, false);
                memory.setCarryingPollen(false);
                memory.setRouteIndex(memory.routeIndex() + 1);
            } else {
                finishDay(memory);
            }
        }

        @Override
        public void stop() {
            bee.getNavigation().stop();
        }

        private void prepareDay(BeeMemory memory) {
            long day = EcologyBeeSystem.day(bee.level());
            if (memory.routeDay() != day) {
                memory.resetDailyRoute(day);
                EcologyBeeSystem.setPollenVisual(bee, false);
                if (memory.route().isEmpty()) {
                    memory.replaceRoute(EcologyBeeSystem.buildWorkerRoute(bee));
                }
            }
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
                    : EcologyBeeSystem.isGrowableCrop(bee.level(), stop.pos());
        }

        private boolean isCloseTo(BlockPos pos, double distance) {
            return Vec3.atCenterOf(pos).distanceTo(bee.position()) <= distance;
        }

        private void moveToward(BlockPos pos, double speed) {
            if (repathCooldown-- <= 0 || bee.getNavigation().isDone()) {
                repathCooldown = 20;
                bee.getNavigation().moveTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, speed);
                if (bee.getNavigation().getPath() == null) {
                    EcologyBeeSystem.pathfindRandomlyTowards(bee, pos);
                }
            }
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
    }

    public static class DroneMatingGoal extends Goal {
        private final Bee bee;
        private BlockPos targetHive;
        private int repathCooldown;
        private int searchCooldown;

        public DroneMatingGoal(Bee bee) {
            this.bee = bee;
            this.setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (!(bee.level() instanceof ServerLevel) || bee.isAngry() || bee.hasStung()) {
                return false;
            }
            BeeMemory memory = EcologyBeeSystem.memory(bee);
            return memory.role() == BeeRole.DRONE && memory.homeHive() != null;
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
            if (targetHive == null || searchCooldown-- <= 0) {
                searchCooldown = 200;
                targetHive = EcologyBeeSystem.findForeignQueenHive(level, bee.blockPosition(), memory.homeHive(), EcologyConfig.MIGRATION_SEARCH_RANGE.get())
                        .orElse(null);
                if (targetHive == null) {
                    markDroneFailure(level, memory);
                    EcologyBeeSystem.sendHome(bee);
                    return;
                }
            }

            if (Vec3.atCenterOf(targetHive).distanceTo(bee.position()) <= 2.0) {
                EcologyBeeSystem.produceChild(level, targetHive);
                if (level.getBlockEntity(targetHive) instanceof BeehiveBlockEntity hive && !hive.isFull()) {
                    hive.addOccupant(bee);
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
                colony.setLastDroneFailureDay(EcologyBeeSystem.day(level));
                hive.setChanged();
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
            if (!(bee.level() instanceof ServerLevel level) || bee.isAngry() || bee.hasStung()) {
                return false;
            }
            BeeMemory memory = EcologyBeeSystem.memory(bee);
            if (memory.role() != BeeRole.QUEEN || memory.homeHive() == null) {
                return false;
            }
            if (!(level.getBlockEntity(memory.homeHive()) instanceof BeehiveBlockEntity hive)) {
                return false;
            }
            ColonyData colony = EcologyBeeSystem.colony(hive);
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
                targetHive = EcologyBeeSystem.findForeignQueenHive(level, bee.blockPosition(), memory.homeHive(), EcologyConfig.MIGRATION_SEARCH_RANGE.get())
                        .orElse(null);
                if (targetHive != null) {
                    emptyHive = EcologyBeeSystem.findEmptyHiveNear(level, targetHive, EcologyConfig.HIVE_SEARCH_RANGE.get()).orElse(null);
                }
                if (targetHive == null || emptyHive == null) {
                    markDoomed(level, memory.homeHive());
                    EcologyBeeSystem.sendHome(bee);
                    return;
                }
            }

            BlockPos destination = returningToOldHive ? memory.homeHive() : targetHive;
            if (Vec3.atCenterOf(destination).distanceTo(bee.position()) <= 2.0) {
                if (!returningToOldHive) {
                    EcologyBeeSystem.produceChild(level, targetHive);
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
                colony.setDoomed(true);
                hive.setChanged();
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
                        EcologyBeeSystem.colony(newHiveEntity).remember(workerMemory);
                        newHiveEntity.setChanged();
                    }
                }
            }
        }
    }
}
