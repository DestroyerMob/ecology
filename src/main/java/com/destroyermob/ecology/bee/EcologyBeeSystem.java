package com.destroyermob.ecology.bee;

import com.destroyermob.ecology.Ecology;
import com.destroyermob.ecology.EcologyConfig;
import com.destroyermob.ecology.mixin.BeeAccessor;
import com.destroyermob.ecology.registry.EcologyAttachments;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.player.Player;
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
    }

    public static void rememberAtHomeHive(ServerLevel level, BeeMemory memory) {
        if (!EcologyConfig.ENABLE_BEE_SYSTEM.get() || !EcologyConfig.ENABLE_HIVE_COLONY_TICKING.get() || memory.homeHive() == null) {
            return;
        }
        BlockEntity blockEntity = level.getBlockEntity(memory.homeHive());
        if (blockEntity instanceof BeehiveBlockEntity hive) {
            tickHiveColony(level, hive);
            ColonyData colony = colony(blockEntity);
            boolean changed = ensureMinimumColony(level, hive, memory);
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
        if (hive.getOccupantCount() <= 0 || level.getGameTime() % 20L != 0L) {
            return;
        }
        tickHiveColony(level, hive);
    }

    public static void tickHiveColony(ServerLevel level, BeehiveBlockEntity hive) {
        if (!EcologyConfig.ENABLE_BEE_SYSTEM.get() || !EcologyConfig.ENABLE_HIVE_COLONY_TICKING.get()) {
            return;
        }
        ColonyData colony = colony(hive);
        boolean changed = false;
        if (hive.getOccupantCount() > 0) {
            changed |= ensureMinimumColony(level, hive, null);
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
            if (changed) {
                hive.setChanged();
            }
            return;
        }

        long daysToSimulate = Math.min(day - colony.lastSimulatedDay(), EcologyConfig.COLONY_CATCHUP_DAYS.get());
        long firstDay = day - daysToSimulate + 1;
        for (long simulatedDay = firstDay; simulatedDay <= day; simulatedDay++) {
            changed |= removeExpiredLogicalBees(colony, simulatedDay);
            changed |= updateColonyDeclineState(colony, simulatedDay);
            if (canProduceLogicalChild(colony, simulatedDay)) {
                changed |= rememberLogicalBee(colony, nextNeededRole(colony, simulatedDay).orElse(BeeRole.WORKER), simulatedDay);
                colony.setLastChildDay(simulatedDay);
            }
        }

        colony.setLastSimulatedDay(day);
        if (changed || daysToSimulate > 0) {
            hive.setChanged();
        }
    }

    public static void ensureStarterColony(ServerLevel level, BeehiveBlockEntity hive) {
        if (!EcologyConfig.ENABLE_BEE_SYSTEM.get()
                || !EcologyConfig.ENABLE_HIVE_COLONY_TICKING.get()
                || !EcologyConfig.AUTO_SEED_EMPTY_HIVES.get()
                || !hive.isEmpty()) {
            return;
        }
        if (ensureMinimumColony(level, hive, null)) {
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
                && colony.lastChildDay() != day
                && nextNeededRole(colony, day).isPresent();
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

    private static boolean ensureMinimumColony(ServerLevel level, BeehiveBlockEntity hive, @Nullable BeeMemory visibleBeeMemory) {
        ColonyData colony = colony(hive);
        if (colony.abandoned()) {
            return false;
        }

        long day = day(level);
        boolean changed = false;
        if (colony.lastSimulatedDay() < 0) {
            colony.setLastSimulatedDay(day);
            changed = true;
        }
        if (colony.queenId() == null) {
            changed |= rememberLogicalBee(colony, BeeRole.QUEEN, day);
        }
        if (colony.workerIds().isEmpty()) {
            if (visibleBeeMemory != null) {
                visibleBeeMemory.setRole(BeeRole.WORKER);
                changed |= colony.remember(visibleBeeMemory);
            } else {
                changed |= rememberLogicalBee(colony, BeeRole.WORKER, day);
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

        if (colony.queenId() == null) {
            memory.setRole(BeeRole.QUEEN);
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
        return findNearestLocalBlock(level, center, pos -> level.getBlockState(pos).is(BlockTags.BEEHIVES));
    }

    public static Optional<BlockPos> findLocalHive(ServerLevel level, BeeMemory memory, BlockPos center) {
        if (!shouldSearchFrom(memory.hiveSearchOrigin(), center)) {
            return Optional.empty();
        }
        memory.setHiveSearchOrigin(center);
        return findNearestHive(level, center, EcologyConfig.BEE_LOCAL_SEARCH_HORIZONTAL_RADIUS.get());
    }

    public static Optional<BlockPos> findForeignQueenHive(ServerLevel level, BlockPos center, @Nullable BlockPos homeHive, int range) {
        return findNearestLocalBlock(level, center, pos -> !pos.equals(homeHive)
                && level.getBlockState(pos).is(BlockTags.BEEHIVES)
                && level.getBlockEntity(pos) instanceof BeehiveBlockEntity
                && colony(level.getBlockEntity(pos)).queenId() != null);
    }

    public static Optional<BlockPos> findLocalForeignQueenHive(ServerLevel level, BeeMemory memory, BlockPos center) {
        if (!shouldSearchFrom(memory.foreignHiveSearchOrigin(), center)) {
            return Optional.empty();
        }
        memory.setForeignHiveSearchOrigin(center);
        Optional<BlockPos> target = findForeignQueenHive(level, center, memory.homeHive(), EcologyConfig.BEE_LOCAL_SEARCH_HORIZONTAL_RADIUS.get());
        target.ifPresent(memory::setMateHive);
        return target;
    }

    public static Optional<BlockPos> findEmptyHiveNear(ServerLevel level, BlockPos center, int range) {
        return findNearestLocalBlock(level, center, pos -> level.getBlockState(pos).is(BlockTags.BEEHIVES)
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
        return findEmptyHiveNear(level, center, EcologyConfig.BEE_LOCAL_SEARCH_HORIZONTAL_RADIUS.get());
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
        BlockPos center = bee.blockPosition();
        BlockPos failedOrigin = memory.failedFlowerSearchOriginNear(center);
        if (failedOrigin != null) {
            memory.setFlowerSearchOrigin(failedOrigin);
            return Optional.empty();
        }
        if (!shouldSearchFrom(memory.flowerSearchOrigin(), center)) {
            return Optional.empty();
        }
        memory.setFlowerSearchOrigin(center);
        Optional<BlockPos> flower = findNearestFlowerBlock(bee.level(), center, EcologyConfig.FLOWER_SEARCH_RANGE.get(), pos -> isValidFlower(bee.level(), pos)
                && !hasRouteStop(memory, pos, BeeRouteStopType.FLOWER));
        if (flower.isEmpty()) {
            memory.rememberFailedFlowerSearch(center);
        }
        return flower;
    }

    public static Optional<BlockPos> findLocalCrop(Bee bee, BlockPos center) {
        BeeMemory memory = memory(bee);
        if (!shouldSearchFrom(memory.cropSearchOrigin(), center)) {
            return Optional.empty();
        }
        memory.setCropSearchOrigin(center);
        return findNearestLocalBlock(bee.level(), center, pos -> canGrowPollinationCrop(bee.level(), pos)
                && !hasRouteStop(memory, pos, BeeRouteStopType.CROP));
    }

    private static boolean hasRouteStop(BeeMemory memory, BlockPos pos, BeeRouteStopType type) {
        for (BeeRouteStop stop : memory.route()) {
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
        return BlockPos.betweenClosedStream(center.offset(-horizontalRange, -verticalRange, -horizontalRange), center.offset(horizontalRange, verticalRange, horizontalRange))
                .filter(level::isLoaded)
                .filter(predicate)
                .min(Comparator.comparingDouble(pos -> pos.distSqr(center)))
                .map(BlockPos::immutable);
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
        return findNearestLocalBlock(level, center, predicate);
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
        if (colony.lastChildDay() == day || colony.doomed() || colony.queenId() == null) {
            return false;
        }

        Optional<BeeRole> nextRole = nextNeededRole(colony, day);
        if (nextRole.isEmpty()) {
            return false;
        }

        rememberLogicalBee(colony, nextRole.get(), day);
        colony.setLastChildDay(day);
        hive.setChanged();
        return true;
    }

    public static boolean mateColony(ServerLevel level, BlockPos hivePos) {
        if (!(level.getBlockEntity(hivePos) instanceof BeehiveBlockEntity hive)) {
            return false;
        }
        ColonyData colony = colony(hive);
        if (colony.queenId() == null || colony.doomed() || colony.abandoned()) {
            return false;
        }

        long day = day(level);
        colony.setLastMatedDay(day);
        colony.setFertileUntilDay(Math.max(colony.fertileUntilDay(), day + 1));
        colony.setDoomed(false);
        colony.setDeclining(colony.workerIds().isEmpty());
        produceChild(level, hivePos);
        hive.setChanged();
        return true;
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

}
