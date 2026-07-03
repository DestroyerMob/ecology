package com.destroyermob.ecology.village;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

final class VillageBuildSafety {
    private static final List<Block> PROVISIONABLE_WORKSTATIONS = List.of(
            Blocks.BLAST_FURNACE,
            Blocks.SMOKER,
            Blocks.CARTOGRAPHY_TABLE,
            Blocks.BREWING_STAND,
            Blocks.COMPOSTER,
            Blocks.BARREL,
            Blocks.FLETCHING_TABLE,
            Blocks.CAULDRON,
            Blocks.LECTERN,
            Blocks.STONECUTTER,
            Blocks.LOOM,
            Blocks.SMITHING_TABLE,
            Blocks.GRINDSTONE);

    private VillageBuildSafety() {
    }

    static boolean structureFits(
            ServerLevel level,
            BlockPos origin,
            Vec3i size,
            int clearanceMargin,
            boolean allowExistingConstruction,
            Predicate<BlockState> existingConstruction) {
        return structureFits(level, origin, size, clearanceMargin, true, allowExistingConstruction, existingConstruction);
    }

    static boolean basicStructureFits(
            ServerLevel level,
            BlockPos origin,
            Vec3i size,
            boolean allowExistingConstruction,
            Predicate<BlockState> existingConstruction) {
        return structureFits(level, origin, size, 0, false, allowExistingConstruction, existingConstruction);
    }

    static boolean structureFits(
            ServerLevel level,
            BlockPos origin,
            Vec3i size,
            int clearanceMargin,
            boolean requireVillageGround,
            boolean allowExistingConstruction,
            Predicate<BlockState> existingConstruction) {
        return structureFits(
                level,
                origin,
                size,
                clearanceMargin,
                requireVillageGround,
                allowExistingConstruction,
                (pos, state) -> existingConstruction.test(state));
    }

    static boolean structureFits(
            ServerLevel level,
            BlockPos origin,
            Vec3i size,
            int clearanceMargin,
            boolean requireVillageGround,
            boolean allowExistingConstruction,
            BiPredicate<BlockPos, BlockState> existingConstruction) {
        if (size.getX() <= 0 || size.getY() <= 0 || size.getZ() <= 0) {
            return false;
        }
        if (!groundFits(level, origin, size, requireVillageGround)) {
            return false;
        }
        if (!volumeFits(level, origin, size, allowExistingConstruction, existingConstruction)) {
            return false;
        }
        return clearanceMargin <= 0 || clearanceFits(level, origin, size, clearanceMargin);
    }

    static Optional<List<BlockPos>> pathRouteForStructure(
            ServerLevel level,
            BlockPos origin,
            Vec3i size,
            int radius,
            int maxLength) {
        BlockPos center = origin.offset(size.getX() / 2, -1, size.getZ() / 2);
        Optional<BlockPos> path = nearestPath(level, center, radius + Math.max(size.getX(), size.getZ()));
        if (path.isEmpty()) {
            return Optional.empty();
        }
        return structurePathTarget(level, origin, size, path.get())
                .flatMap(target -> pathRoute(level, target, path.get(), maxLength));
    }

    static Optional<List<BlockPos>> pathRouteForTarget(ServerLevel level, BlockPos target, int radius, int maxLength) {
        return nearestPath(level, target, radius).flatMap(path -> pathRoute(level, target, path, maxLength));
    }

    static void placePathRoute(ServerLevel level, List<BlockPos> route) {
        for (BlockPos pos : route) {
            if (!tryPlacePath(level, pos)) {
                return;
            }
        }
    }

    static boolean hasPathNear(ServerLevel level, BlockPos center, int radius) {
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -1, -radius), center.offset(radius, 1, radius))) {
            if (level.hasChunkAt(pos) && isPathBlock(level.getBlockState(pos))) {
                return true;
            }
        }
        return false;
    }

    static boolean hasPathNear(ServerLevel level, BlockPos min, Vec3i size, int radius) {
        BlockPos max = min.offset(size.getX() - 1, 0, size.getZ() - 1);
        for (BlockPos pos : BlockPos.betweenClosed(min.offset(-radius, -1, -radius), max.offset(radius, 1, radius))) {
            if (level.hasChunkAt(pos) && isPathBlock(level.getBlockState(pos))) {
                return true;
            }
        }
        return false;
    }

    static boolean hasMatchingBlockNearFootprint(
            ServerLevel level,
            BlockPos origin,
            Vec3i size,
            int radius,
            Predicate<BlockState> matcher) {
        BlockPos min = origin.offset(-radius, -2, -radius);
        BlockPos max = origin.offset(size.getX() - 1 + radius, size.getY() + 2, size.getZ() - 1 + radius);
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (pos.getX() >= origin.getX()
                    && pos.getX() < origin.getX() + size.getX()
                    && pos.getZ() >= origin.getZ()
                    && pos.getZ() < origin.getZ() + size.getZ()) {
                continue;
            }
            if (level.hasChunkAt(pos) && matcher.test(level.getBlockState(pos))) {
                return true;
            }
        }
        return false;
    }

    static boolean canReplaceForBuilding(BlockState state) {
        return state.isAir()
                || state.is(Blocks.SHORT_GRASS)
                || state.is(Blocks.TALL_GRASS)
                || state.is(Blocks.FERN)
                || state.is(Blocks.LARGE_FERN)
                || state.is(Blocks.SNOW)
                || state.is(BlockTags.FLOWERS)
                || state.is(BlockTags.SAPLINGS);
    }

    static boolean canReplaceForSmallSite(BlockState state) {
        return state.isAir() || state.is(Blocks.SHORT_GRASS) || state.is(Blocks.FERN) || state.is(BlockTags.FLOWERS);
    }

    static boolean canReplaceWorksiteGround(BlockState state) {
        return canTurnIntoPath(state)
                || state.is(Blocks.SAND)
                || state.is(Blocks.RED_SAND);
    }

    static boolean canBuildOnVillageGround(BlockState state) {
        return canTurnIntoPath(state)
                || state.is(Blocks.GRASS_BLOCK)
                || state.is(Blocks.SAND)
                || state.is(Blocks.RED_SAND)
                || state.is(Blocks.SNOW_BLOCK);
    }

    static boolean hasSolidFloor(ServerLevel level, BlockPos pos) {
        BlockPos below = pos.below();
        return level.hasChunkAt(below) && !level.getBlockState(below).getCollisionShape(level, below).isEmpty();
    }

    static boolean hasAdjacentPath(ServerLevel level, BlockPos ground) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (isPathBlock(level.getBlockState(ground.relative(direction)))) {
                return true;
            }
        }
        return false;
    }

    static boolean hasShelter(ServerLevel level, BlockPos pos) {
        for (int y = 2; y <= 5; y++) {
            BlockPos roof = pos.above(y);
            if (level.hasChunkAt(roof) && !level.getBlockState(roof).getCollisionShape(level, roof).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    static boolean isPathBlock(BlockState state) {
        return state.is(Blocks.DIRT_PATH);
    }

    static boolean isProvisionableWorkstation(Block block) {
        return PROVISIONABLE_WORKSTATIONS.contains(block);
    }

    static List<Block> provisionableWorkstations() {
        return PROVISIONABLE_WORKSTATIONS;
    }

    static String villageStyle(ServerLevel level, BlockPos anchor) {
        var biome = level.getBiome(anchor);
        if (biome.is(BiomeTags.HAS_VILLAGE_DESERT)) {
            return "desert";
        }
        if (biome.is(BiomeTags.HAS_VILLAGE_SAVANNA) || biome.is(BiomeTags.IS_SAVANNA)) {
            return "savanna";
        }
        if (biome.is(BiomeTags.HAS_VILLAGE_SNOWY) || biome.value().coldEnoughToSnow(anchor)) {
            return "snowy";
        }
        if (biome.is(BiomeTags.HAS_VILLAGE_TAIGA) || biome.is(BiomeTags.IS_TAIGA)) {
            return "taiga";
        }
        return "plains";
    }

    private static boolean groundFits(ServerLevel level, BlockPos origin, Vec3i size, boolean requireVillageGround) {
        for (BlockPos ground : BlockPos.betweenClosed(origin.below(), origin.offset(size.getX() - 1, -1, size.getZ() - 1))) {
            if (!level.hasChunkAt(ground) || level.getBlockState(ground).getCollisionShape(level, ground).isEmpty()) {
                return false;
            }
            if (requireVillageGround && !canBuildOnVillageGround(level.getBlockState(ground))) {
                return false;
            }
        }
        return true;
    }

    private static boolean volumeFits(
            ServerLevel level,
            BlockPos origin,
            Vec3i size,
            boolean allowExistingConstruction,
            BiPredicate<BlockPos, BlockState> existingConstruction) {
        for (BlockPos pos : BlockPos.betweenClosed(origin, origin.offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1))) {
            if (!level.hasChunkAt(pos)) {
                return false;
            }
            BlockState state = level.getBlockState(pos);
            if (!canReplaceForBuilding(state) && !(allowExistingConstruction && existingConstruction.test(pos, state))) {
                return false;
            }
        }
        return true;
    }

    private static boolean clearanceFits(ServerLevel level, BlockPos origin, Vec3i size, int margin) {
        BlockPos min = origin.offset(-margin, 1, -margin);
        BlockPos max = origin.offset(size.getX() - 1 + margin, Math.max(1, size.getY() - 1), size.getZ() - 1 + margin);
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            boolean insideFootprint = pos.getX() >= origin.getX()
                    && pos.getX() < origin.getX() + size.getX()
                    && pos.getZ() >= origin.getZ()
                    && pos.getZ() < origin.getZ() + size.getZ();
            if (insideFootprint) {
                continue;
            }
            if (!level.hasChunkAt(pos) || !canReplaceForBuilding(level.getBlockState(pos))) {
                return false;
            }
        }
        return true;
    }

    private static Optional<BlockPos> structurePathTarget(ServerLevel level, BlockPos origin, Vec3i size, BlockPos path) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        int y = origin.getY() - 1;
        int minX = origin.getX() - 1;
        int maxX = origin.getX() + size.getX();
        int minZ = origin.getZ() - 1;
        int maxZ = origin.getZ() + size.getZ();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                boolean edge = x == minX || x == maxX || z == minZ || z == maxZ;
                if (!edge) {
                    continue;
                }
                BlockPos candidate = new BlockPos(x, y, z);
                if (!canUseAsPath(level, candidate)) {
                    continue;
                }
                double distance = candidate.distSqr(path);
                if (distance < bestDistance) {
                    best = candidate;
                    bestDistance = distance;
                }
            }
        }
        return Optional.ofNullable(best);
    }

    private static Optional<BlockPos> nearestPath(ServerLevel level, BlockPos target, int radius) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos candidate : BlockPos.betweenClosed(target.offset(-radius, -2, -radius), target.offset(radius, 2, radius))) {
            if (candidate.equals(target) || !level.hasChunkAt(candidate) || !isPathBlock(level.getBlockState(candidate))) {
                continue;
            }
            double distance = candidate.distSqr(target);
            if (distance < bestDistance) {
                best = candidate.immutable();
                bestDistance = distance;
            }
        }
        return Optional.ofNullable(best);
    }

    private static Optional<List<BlockPos>> pathRoute(ServerLevel level, BlockPos start, BlockPos end, int maxLength) {
        List<BlockPos> xFirst = pathRoute(level, start, end, true);
        List<BlockPos> zFirst = pathRoute(level, start, end, false);
        List<BlockPos> route = betterPathRoute(xFirst, zFirst, maxLength);
        if (route.isEmpty() || route.size() > maxLength) {
            return Optional.empty();
        }
        return Optional.of(route);
    }

    private static List<BlockPos> betterPathRoute(List<BlockPos> first, List<BlockPos> second, int maxLength) {
        boolean firstValid = isValidPathRoute(first, maxLength);
        boolean secondValid = isValidPathRoute(second, maxLength);
        if (firstValid && secondValid) {
            return first.size() <= second.size() ? first : second;
        }
        if (firstValid) {
            return first;
        }
        return secondValid ? second : List.of();
    }

    private static boolean isValidPathRoute(List<BlockPos> route, int maxLength) {
        return !route.isEmpty() && route.size() <= maxLength;
    }

    private static List<BlockPos> pathRoute(ServerLevel level, BlockPos start, BlockPos end, boolean xFirst) {
        List<BlockPos> route = new ArrayList<>();
        int x = start.getX();
        int y = start.getY();
        int z = start.getZ();
        if (xFirst) {
            while (x != end.getX()) {
                x += Integer.compare(end.getX(), x);
                Optional<BlockPos> step = surfacePathPos(level, x, z, y);
                if (step.isEmpty()) {
                    return List.of();
                }
                y = step.get().getY();
                route.add(step.get());
            }
            while (z != end.getZ()) {
                z += Integer.compare(end.getZ(), z);
                Optional<BlockPos> step = surfacePathPos(level, x, z, y);
                if (step.isEmpty()) {
                    return List.of();
                }
                y = step.get().getY();
                route.add(step.get());
            }
        } else {
            while (z != end.getZ()) {
                z += Integer.compare(end.getZ(), z);
                Optional<BlockPos> step = surfacePathPos(level, x, z, y);
                if (step.isEmpty()) {
                    return List.of();
                }
                y = step.get().getY();
                route.add(step.get());
            }
            while (x != end.getX()) {
                x += Integer.compare(end.getX(), x);
                Optional<BlockPos> step = surfacePathPos(level, x, z, y);
                if (step.isEmpty()) {
                    return List.of();
                }
                y = step.get().getY();
                route.add(step.get());
            }
        }
        route.add(0, start.immutable());
        return route;
    }

    private static Optional<BlockPos> surfacePathPos(ServerLevel level, int x, int z, int aroundY) {
        for (int y = aroundY + 1; y >= aroundY - 2; y--) {
            BlockPos pos = new BlockPos(x, y, z);
            if (canUseAsPath(level, pos)) {
                return Optional.of(pos);
            }
        }
        return Optional.empty();
    }

    static boolean canUseAsPath(ServerLevel level, BlockPos ground) {
        return level.hasChunkAt(ground)
                && level.hasChunkAt(ground.above())
                && (isPathBlock(level.getBlockState(ground)) || canTurnIntoPath(level.getBlockState(ground)))
                && canReplaceForBuilding(level.getBlockState(ground.above()));
    }

    static boolean tryPlacePath(ServerLevel level, BlockPos ground) {
        if (!canUseAsPath(level, ground)) {
            return false;
        }
        if (!level.getBlockState(ground.above()).isAir()) {
            level.setBlock(ground.above(), Blocks.AIR.defaultBlockState(), 3);
        }
        if (!isPathBlock(level.getBlockState(ground))) {
            level.setBlock(ground, Blocks.DIRT_PATH.defaultBlockState(), 3);
        }
        return true;
    }

    static boolean canTurnIntoPath(BlockState state) {
        return state.is(Blocks.GRASS_BLOCK)
                || state.is(Blocks.DIRT)
                || state.is(Blocks.COARSE_DIRT)
                || state.is(Blocks.ROOTED_DIRT)
                || state.is(Blocks.PODZOL)
                || state.is(Blocks.MYCELIUM);
    }
}
