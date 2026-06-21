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
            if (colony.remember(memory)) {
                blockEntity.setChanged();
            }
        }
    }

    public static void ensureStarterColony(ServerLevel level, BeehiveBlockEntity hive) {
        ColonyData colony = colony(hive);
        if (colony.abandoned() || colony.doomed()) {
            return;
        }
        if (colony.queenId() != null || !colony.workerIds().isEmpty() || !colony.droneIds().isEmpty()) {
            return;
        }

        int missingOccupants = 3 - hive.getOccupantCount();
        if (missingOccupants <= 0) {
            return;
        }

        BlockPos hivePos = hive.getBlockPos();
        long day = day(level);
        if (missingOccupants >= 1) {
            seedHiveBee(level, hive, hivePos, BeeRole.QUEEN, day);
        }
        if (missingOccupants >= 2) {
            seedHiveBee(level, hive, hivePos, BeeRole.DRONE, day);
        }
        if (missingOccupants >= 3) {
            seedHiveBee(level, hive, hivePos, BeeRole.WORKER, day);
        }
        hive.setChanged();
    }

    private static void seedHiveBee(ServerLevel level, BeehiveBlockEntity hive, BlockPos hivePos, BeeRole role, long day) {
        Bee bee = createColonyBee(level, hivePos, role, day);
        if (bee == null) {
            return;
        }

        ColonyData colony = colony(hive);
        colony.remember(memory(bee));
        hive.addOccupant(bee);
    }

    public static void forgetAtHomeHive(ServerLevel level, BeeMemory memory) {
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

        if (colony.workerIds().isEmpty()) {
            memory.setRole(BeeRole.WORKER);
            return;
        }
        if (colony.queenId() == null) {
            memory.setRole(BeeRole.QUEEN);
            return;
        }
        if (colony.droneIds().isEmpty()) {
            memory.setRole(BeeRole.DRONE);
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
            Optional<RoutePair> pair = findNextRoutePair(bee.level(), cursor, usedFlowers, usedCrops);
            if (pair.isEmpty()) {
                break;
            }

            stops.add(new BeeRouteStop(pair.get().flower(), BeeRouteStopType.FLOWER));
            stops.add(new BeeRouteStop(pair.get().crop(), BeeRouteStopType.CROP));
            cursor = pair.get().crop();
        }
        return stops;
    }

    private static Optional<RoutePair> findNextRoutePair(Level level, BlockPos cursor, Set<BlockPos> usedFlowers, Set<BlockPos> usedCrops) {
        while (true) {
            Optional<BlockPos> flower = findNearestBlock(
                    level,
                    cursor,
                    EcologyConfig.FLOWER_SEARCH_RANGE.get(),
                    pos -> isValidFlower(level, pos) && !usedFlowers.contains(pos));
            if (flower.isEmpty()) {
                return Optional.empty();
            }

            BlockPos flowerPos = flower.get().immutable();
            usedFlowers.add(flowerPos);
            Optional<BlockPos> crop = findNearestBlock(
                    level,
                    flowerPos,
                    EcologyConfig.CROP_SEARCH_RANGE.get(),
                    pos -> isGrowableCrop(level, pos) && !usedCrops.contains(pos));
            if (crop.isPresent()) {
                BlockPos cropPos = crop.get().immutable();
                usedCrops.add(cropPos);
                return Optional.of(new RoutePair(flowerPos, cropPos));
            }
        }
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
            BeeMemory memory = memory(bee);
            memory.setReturningHome(false);
            memory.setCarryingPollen(false);
            setPollenVisual(bee, false);
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

        Optional<BeeRole> nextRole = nextNeededRole(colony, day);
        if (nextRole.isEmpty()) {
            return false;
        }

        Bee child = createColonyBee(level, hivePos, nextRole.get(), day);
        if (child == null) {
            return false;
        }
        child.setAge(-24000);

        BeeMemory childMemory = memory(child);
        colony.remember(childMemory);
        colony.setLastChildDay(day);
        hive.setChanged();
        return level.addFreshEntity(child);
    }

    private static Bee createColonyBee(ServerLevel level, BlockPos hivePos, BeeRole role, long day) {
        Bee bee = EntityType.BEE.create(level);
        if (bee == null) {
            return null;
        }
        bee.moveTo(hivePos.getX() + 0.5, hivePos.getY() + 1.0, hivePos.getZ() + 0.5, 0.0F, 0.0F);
        bee.setHivePos(hivePos);

        BeeMemory memory = memory(bee);
        memory.setBirthDay(day);
        memory.setHomeHive(hivePos);
        memory.setRole(role);
        return bee;
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
            return true;
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

    private record RoutePair(BlockPos flower, BlockPos crop) {
    }
}
