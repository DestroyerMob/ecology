package com.destroyermob.ecology.bee;

import com.destroyermob.ecology.Ecology;
import com.destroyermob.ecology.EcologyConfig;
import com.destroyermob.ecology.mixin.BeeAccessor;
import com.destroyermob.ecology.registry.EcologyAttachments;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public final class EcologyBeeSystem {
    public static final TagKey<Block> POLLINATION_FLOWERS =
            TagKey.create(Registries.BLOCK, Ecology.id("pollination_flowers"));
    public static final TagKey<Block> POLLINATION_CROPS =
            TagKey.create(Registries.BLOCK, Ecology.id("pollination_crops"));

    private EcologyBeeSystem() {
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
        if (memory.homeHive() == null) {
            findNearestHive(level, bee.blockPosition(), EcologyConfig.HIVE_SEARCH_RANGE.get())
                    .ifPresent(memory::setHomeHive);
        }
        if (memory.homeHive() != null) {
            bee.setHivePos(memory.homeHive());
            rememberAtHomeHive(level, memory);
        }
    }

    public static void rememberAtHomeHive(ServerLevel level, BeeMemory memory) {
        BlockEntity blockEntity = level.getBlockEntity(memory.homeHive());
        if (blockEntity instanceof BeehiveBlockEntity) {
            ColonyData colony = colony(blockEntity);
            assignRoleIfNeeded(memory, colony);
            colony.remember(memory);
            blockEntity.setChanged();
        }
    }

    private static void assignRoleIfNeeded(BeeMemory memory, ColonyData colony) {
        if (colony.queenId() == null) {
            memory.setRole(BeeRole.QUEEN);
            return;
        }
        if (memory.role() == BeeRole.QUEEN && !memory.ecologyId().equals(colony.queenId())) {
            memory.setRole(BeeRole.WORKER);
        }
        if (memory.role() == BeeRole.WORKER && colony.workerIds().size() >= EcologyConfig.MAX_WORKERS_PER_HIVE.get()) {
            memory.setRole(colony.droneIds().size() < EcologyConfig.MAX_DRONES_PER_HIVE.get() ? BeeRole.DRONE : BeeRole.WORKER);
        }
    }

    public static Optional<BlockPos> findNearestHive(ServerLevel level, BlockPos center, int range) {
        return BlockPos.betweenClosedStream(center.offset(-range, -8, -range), center.offset(range, 8, range))
                .filter(pos -> level.isLoaded(pos) && level.getBlockState(pos).is(BlockTags.BEEHIVES))
                .min(Comparator.comparingDouble(pos -> pos.distSqr(center)))
                .map(BlockPos::immutable);
    }

    public static Optional<BlockPos> findForeignQueenHive(ServerLevel level, BlockPos center, @Nullable BlockPos homeHive, int range) {
        return BlockPos.betweenClosedStream(center.offset(-range, -8, -range), center.offset(range, 8, range))
                .filter(pos -> !pos.equals(homeHive))
                .filter(pos -> level.isLoaded(pos) && level.getBlockState(pos).is(BlockTags.BEEHIVES))
                .filter(pos -> level.getBlockEntity(pos) instanceof BeehiveBlockEntity)
                .filter(pos -> colony(level.getBlockEntity(pos)).queenId() != null)
                .min(Comparator.comparingDouble(pos -> pos.distSqr(center)))
                .map(BlockPos::immutable);
    }

    public static Optional<BlockPos> findEmptyHiveNear(ServerLevel level, BlockPos center, int range) {
        return BlockPos.betweenClosedStream(center.offset(-range, -8, -range), center.offset(range, 8, range))
                .filter(pos -> level.isLoaded(pos) && level.getBlockState(pos).is(BlockTags.BEEHIVES))
                .filter(pos -> level.getBlockEntity(pos) instanceof BeehiveBlockEntity hive && hive.isEmpty())
                .filter(pos -> {
                    ColonyData colony = colony(level.getBlockEntity(pos));
                    return colony.queenId() == null && !colony.abandoned();
                })
                .min(Comparator.comparingDouble(pos -> pos.distSqr(center)))
                .map(BlockPos::immutable);
    }

    public static List<BeeRouteStop> buildWorkerRoute(Bee bee) {
        List<BeeRouteStop> stops = new ArrayList<>();
        Set<BlockPos> usedFlowers = new HashSet<>();
        Set<BlockPos> usedCrops = new HashSet<>();
        BlockPos cursor = bee.blockPosition();

        for (int i = 0; i < EcologyConfig.MAX_ROUTE_PAIRS.get(); i++) {
            Optional<BlockPos> flower = findNearestBlock(
                    bee.level(), cursor, EcologyConfig.FLOWER_SEARCH_RANGE.get(), pos -> isValidFlower(bee.level(), pos) && usedFlowers.add(pos.immutable()));
            if (flower.isEmpty()) {
                break;
            }

            Optional<BlockPos> crop = findNearestBlock(
                    bee.level(), flower.get(), EcologyConfig.CROP_SEARCH_RANGE.get(), pos -> isGrowableCrop(bee.level(), pos) && usedCrops.add(pos.immutable()));
            if (crop.isEmpty()) {
                usedFlowers.remove(flower.get());
                break;
            }

            stops.add(new BeeRouteStop(flower.get(), BeeRouteStopType.FLOWER));
            stops.add(new BeeRouteStop(crop.get(), BeeRouteStopType.CROP));
            cursor = crop.get();
        }
        return stops;
    }

    public static Optional<BlockPos> findNearestBlock(Level level, BlockPos center, int range, java.util.function.Predicate<BlockPos> predicate) {
        return BlockPos.betweenClosedStream(center.offset(-range, -range, -range), center.offset(range, range, range))
                .filter(level::isLoaded)
                .filter(pos -> center.closerThan(pos, range + 0.5))
                .filter(predicate)
                .min(Comparator.comparingDouble(pos -> pos.distSqr(center)))
                .map(BlockPos::immutable);
    }

    public static boolean isValidFlower(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.hasProperty(BlockStateProperties.WATERLOGGED) && state.getValue(BlockStateProperties.WATERLOGGED)) {
            return false;
        }
        if (!(state.is(POLLINATION_FLOWERS) || state.is(BlockTags.FLOWERS))) {
            return false;
        }
        return !(state.getBlock() instanceof DoublePlantBlock) || !state.hasProperty(DoublePlantBlock.HALF)
                || state.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.UPPER;
    }

    public static boolean isGrowableCrop(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.is(POLLINATION_CROPS) && state.getBlock() instanceof CropBlock crop && !crop.isMaxAge(state);
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

    public static void pathfindRandomlyTowards(Bee bee, BlockPos pos) {
        ((BeeAccessor) bee).ecology$pathfindRandomlyTowards(pos);
    }

    public static void sendHome(Bee bee) {
        BeeMemory memory = memory(bee);
        memory.setReturningHome(true);
        if (memory.homeHive() != null) {
            bee.setHivePos(memory.homeHive());
        }
    }

    public static boolean enterHive(Bee bee, BlockPos hivePos) {
        if (bee.level().getBlockEntity(hivePos) instanceof BeehiveBlockEntity hive && !hive.isFull()) {
            hive.addOccupant(bee);
            return true;
        }
        return false;
    }

    public static boolean isFirstDay(Bee bee) {
        BeeMemory memory = memory(bee);
        return day(bee.level()) <= memory.birthDay();
    }

    public static void markDirectAttack(Bee bee, LivingEntity attacker) {
        BeeMemory memory = memory(bee);
        memory.setAggressionCause(BeeAggressionCause.DIRECT_ATTACK);
        bee.setTarget(attacker);
        bee.startPersistentAngerTimer();
        if (attacker instanceof Player player && memory.homeHive() != null && bee.level() instanceof ServerLevel level) {
            angerHomeHive(level, memory.homeHive(), player);
        }
    }

    public static void markPathBlocked(Bee bee, Player player) {
        BeeMemory memory = memory(bee);
        if (memory.aggressionCause() != BeeAggressionCause.DIRECT_ATTACK) {
            memory.setAggressionCause(BeeAggressionCause.PATH_BLOCKED);
        }
        bee.setTarget(player);
        bee.startPersistentAngerTimer();
    }

    private static void angerHomeHive(ServerLevel level, BlockPos hivePos, Player player) {
        BlockEntity blockEntity = level.getBlockEntity(hivePos);
        if (blockEntity instanceof BeehiveBlockEntity hive) {
            hive.emptyAllLivingFromHive(player, level.getBlockState(hivePos), BeehiveBlockEntity.BeeReleaseStatus.EMERGENCY);
        }
        AABB area = new AABB(hivePos).inflate(12.0, 8.0, 12.0);
        for (Bee bee : level.getEntitiesOfClass(Bee.class, area)) {
            bee.setTarget(player);
            bee.startPersistentAngerTimer();
            memory(bee).setAggressionCause(BeeAggressionCause.DIRECT_ATTACK);
        }
    }

    public static boolean produceChild(ServerLevel level, BlockPos hivePos) {
        if (!(level.getBlockEntity(hivePos) instanceof BeehiveBlockEntity hive)) {
            return false;
        }
        ColonyData colony = colony(hive);
        long day = day(level);
        if (colony.lastChildDay() == day || colony.doomed()) {
            return false;
        }

        Bee child = EntityType.BEE.create(level);
        if (child == null) {
            return false;
        }
        child.moveTo(hivePos.getX() + 0.5, hivePos.getY() + 1.0, hivePos.getZ() + 0.5, 0.0F, 0.0F);
        child.setAge(-24000);
        child.setHivePos(hivePos);

        BeeMemory childMemory = memory(child);
        childMemory.setBirthDay(day);
        childMemory.setHomeHive(hivePos);
        childMemory.setRole(nextNeededRole(colony));
        colony.remember(childMemory);
        colony.setLastChildDay(day);
        hive.setChanged();
        return level.addFreshEntity(child);
    }

    private static BeeRole nextNeededRole(ColonyData colony) {
        if (colony.queenId() == null) {
            return BeeRole.QUEEN;
        }
        if (colony.workerIds().size() < EcologyConfig.MAX_WORKERS_PER_HIVE.get()) {
            return BeeRole.WORKER;
        }
        if (colony.droneIds().size() < EcologyConfig.MAX_DRONES_PER_HIVE.get()) {
            return BeeRole.DRONE;
        }
        return BeeRole.WORKER;
    }

    public static boolean isPlayerNearRoute(Player player, BeeMemory memory) {
        List<BlockPos> positions = memory.routePositions();
        if (positions.size() < 2 || memory.routeIndex() >= positions.size()) {
            return false;
        }

        Vec3 playerPos = player.position();
        for (int i = Math.max(0, memory.routeIndex() - 1); i < positions.size() - 1; i++) {
            Vec3 start = Vec3.atCenterOf(positions.get(i));
            Vec3 end = Vec3.atCenterOf(positions.get(i + 1));
            if (Math.abs(playerPos.y - closestYOnSegment(playerPos, start, end)) <= EcologyConfig.PATH_VERTICAL_TRIGGER_RANGE.get()
                    && horizontalDistanceToSegment(playerPos, start, end) <= EcologyConfig.PATH_HORIZONTAL_TRIGGER_RANGE.get()) {
                return true;
            }
        }
        return false;
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
