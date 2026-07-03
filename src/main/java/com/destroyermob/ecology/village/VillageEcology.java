package com.destroyermob.ecology.village;

import com.destroyermob.ecology.EcologyConfig;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;

public final class VillageEcology {
    private static final int VERTICAL_SCAN_RANGE = 8;
    private static final int MAINTENANCE_RADIUS = 7;
    private static final int SURVEY_CACHE_CELL_SIZE = 96;
    private static final int SURVEY_CACHE_TICKS = 20 * 20;
    private static final int SURVEY_CACHE_MAX_ENTRIES = 256;
    private static final int VILLAGE_ANCHOR_SEARCH_RADIUS = 96;
    private static final Block[] GARDEN_FLOWERS = {
            Blocks.DANDELION,
            Blocks.POPPY,
            Blocks.AZURE_BLUET,
            Blocks.OXEYE_DAISY,
            Blocks.CORNFLOWER
    };
    private static final Map<SurveyKey, CachedSurvey> SURVEY_CACHE = new HashMap<>();

    private VillageEcology() {
    }

    public static VillageEcologyReport surveyCached(ServerLevel level, BlockPos center) {
        BlockPos resolvedCenter = surveyCenter(level, center);
        long gameTime = level.getGameTime();
        SurveyKey key = SurveyKey.of(level, resolvedCenter);
        CachedSurvey cached = SURVEY_CACHE.get(key);
        if (cached != null && cached.expiresAt() > gameTime) {
            return cached.report();
        }
        return surveyResolved(level, resolvedCenter);
    }

    public static VillageEcologyReport survey(ServerLevel level, BlockPos center) {
        return surveyResolved(level, surveyCenter(level, center));
    }

    public static BlockPos surveyCenter(ServerLevel level, BlockPos origin) {
        return VillageZones.centerFor(level, origin)
                .or(() -> nearestPoi(level, origin, PoiTypes.HOME, fallbackHomeSearchRadius()))
                .orElse(origin.immutable());
    }

    public static Optional<BlockPos> discoverVillageCenter(ServerLevel level, BlockPos origin) {
        return nearestPoi(level, origin, PoiTypes.MEETING, VILLAGE_ANCHOR_SEARCH_RADIUS)
                .or(() -> nearestVillageAnchorFromVillagers(level, origin));
    }

    private static int fallbackHomeSearchRadius() {
        return Math.max(48, EcologyConfig.VILLAGE_ECOLOGY_RADIUS.get());
    }

    private static VillageEcologyReport surveyResolved(ServerLevel level, BlockPos center) {
        int radius = EcologyConfig.VILLAGE_ECOLOGY_RADIUS.get();
        AABB entityArea = AABB.encapsulatingFullBlocks(
                center.offset(-radius, -VERTICAL_SCAN_RANGE, -radius),
                center.offset(radius, VERTICAL_SCAN_RANGE, radius));
        List<Villager> villagers = level.getEntitiesOfClass(Villager.class, entityArea);
        int golems = level.getEntitiesOfClass(IronGolem.class, entityArea).size();
        int guards = level.getEntitiesOfClass(Entity.class, entityArea, VillageEcology::isGuardVillager).size();
        int monsters = level.getEntitiesOfClass(Monster.class, entityArea).size();
        int farmers = 0;
        for (Villager villager : villagers) {
            if (villager.getVillagerData().getProfession() == VillagerProfession.FARMER) {
                farmers++;
            }
        }

        ScanCounts counts = scanBlocks(level, center, radius);
        int food = clampScore(counts.cropCount * 4 + counts.matureCropCount * 3 + counts.composterCount * 12 + counts.waterCount * 2 + farmers * 8);
        int shelter = clampScore(counts.bedCount * 12 + counts.doorCount * 3 + counts.workstationCount * 4 + counts.bellCount * 8);
        int safety = clampScore(35 + golems * 18 + guards * 12 + counts.bellCount * 8 + counts.lightCount / 4 - monsters * 15);
        int green = clampScore(counts.flowerCount * 6 + counts.saplingCount * 8 + counts.leafCount / 8);
        int water = clampScore(counts.waterCount * 10);
        int maintenance = clampScore(counts.pathCount * 4 + counts.composterCount * 10 + counts.bellCount * 10 + counts.workstationCount * 2 - counts.emptyFarmlandCount * 5);
        int score = clampScore((food * 25 + shelter * 18 + safety * 20 + green * 17 + water * 8 + maintenance * 12) / 100);

        List<VillageEcologyIssue> issues = new ArrayList<>();
        if (villagers.isEmpty()) {
            issues.add(VillageEcologyIssue.NO_VILLAGERS);
        }
        if (food < 45) {
            issues.add(VillageEcologyIssue.LOW_FOOD);
        }
        if (shelter < 45) {
            issues.add(VillageEcologyIssue.LOW_SHELTER);
        }
        if (safety < 50) {
            issues.add(VillageEcologyIssue.UNSAFE);
        }
        if (green < 35) {
            issues.add(VillageEcologyIssue.LOW_GREEN_SPACE);
        }
        if (water < 30) {
            issues.add(VillageEcologyIssue.LOW_WATER);
        }
        if (maintenance < 45) {
            issues.add(VillageEcologyIssue.LOW_MAINTENANCE);
        }

        VillageEcologyReport report = new VillageEcologyReport(
                center.immutable(),
                score,
                statusFor(score),
                villagers.size(),
                golems,
                guards,
                counts.bedCount,
                counts.cropCount,
                counts.matureCropCount,
                counts.flowerCount,
                counts.waterCount,
                counts.pathCount,
                food,
                shelter,
                safety,
                green,
                water,
                maintenance,
                List.copyOf(issues));
        cacheSurvey(SurveyKey.of(level, center), report, level.getGameTime());
        return report;
    }

    private static Optional<BlockPos> nearestVillageAnchorFromVillagers(ServerLevel level, BlockPos origin) {
        int radius = VILLAGE_ANCHOR_SEARCH_RADIUS;
        AABB area = AABB.encapsulatingFullBlocks(
                origin.offset(-radius, -VERTICAL_SCAN_RANGE, -radius),
                origin.offset(radius, VERTICAL_SCAN_RANGE, radius));
        return level.getEntitiesOfClass(Villager.class, area, Villager::isAlive)
                .stream()
                .min(Comparator.comparingDouble(villager -> villager.blockPosition().distSqr(origin)))
                .map(villager -> villagerAnchor(level, villager).orElse(villager.blockPosition()).immutable());
    }

    private static Optional<BlockPos> villagerAnchor(ServerLevel level, Villager villager) {
        return brainAnchor(level, villager, MemoryModuleType.MEETING_POINT)
                .or(() -> brainAnchor(level, villager, MemoryModuleType.HOME))
                .or(() -> brainAnchor(level, villager, MemoryModuleType.JOB_SITE));
    }

    private static Optional<BlockPos> brainAnchor(ServerLevel level, Villager villager, MemoryModuleType<GlobalPos> memoryType) {
        return villager.getBrain().getMemory(memoryType)
                .filter(pos -> pos.dimension() == level.dimension())
                .map(GlobalPos::pos);
    }

    private static Optional<BlockPos> nearestPoi(ServerLevel level, BlockPos center, ResourceKey<PoiType> poiType, int radius) {
        return level.getPoiManager().findClosest(
                holder -> holder.is(poiType),
                center,
                radius,
                PoiManager.Occupancy.ANY);
    }

    public static void sendReport(Player player, VillageEcologyReport report) {
        player.sendSystemMessage(Component.translatable("message.ecology.village.header", report.center().toShortString()).withStyle(ChatFormatting.GOLD));
        player.sendSystemMessage(Component.translatable(
                "message.ecology.village.score",
                Component.translatable(statusKey(report.status())),
                report.score()));
        player.sendSystemMessage(Component.translatable(
                "message.ecology.village.population",
                report.villagerCount(),
                report.golemCount(),
                report.guardCount(),
                report.bedCount()));
        player.sendSystemMessage(Component.translatable(
                "message.ecology.village.categories",
                report.foodScore(),
                report.shelterScore(),
                report.safetyScore(),
                report.greenScore(),
                report.waterScore(),
                report.maintenanceScore()).withStyle(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.translatable(
                "message.ecology.village.landscape",
                report.cropCount(),
                report.matureCropCount(),
                report.flowerCount(),
                report.waterCount(),
                report.pathCount()).withStyle(ChatFormatting.GRAY));

        if (report.issues().isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.ecology.village.advice", Component.translatable("ecology.village.advice.steady")).withStyle(ChatFormatting.AQUA));
            return;
        }
        for (VillageEcologyIssue issue : report.issues()) {
            player.sendSystemMessage(Component.translatable(
                    "message.ecology.village.issue",
                    Component.translatable(issueKey(issue))).withStyle(ChatFormatting.YELLOW));
        }
        adviceFor(report).stream()
                .limit(4)
                .forEach(advice -> player.sendSystemMessage(Component.translatable("message.ecology.village.advice", advice).withStyle(ChatFormatting.AQUA)));
    }

    public static void tickVillager(ServerLevel level, Villager villager) {
        if (!EcologyConfig.villageMaintenanceEnabled() || villager.isBaby() || VillageConstructionCrews.isBuilder(villager)) {
            return;
        }
        int interval = EcologyConfig.VILLAGE_MAINTENANCE_INTERVAL_TICKS.get();
        if (villager.tickCount < 200 || Math.floorMod(villager.tickCount + villager.getId(), interval) != 0) {
            return;
        }
        RandomSource random = villager.getRandom();
        if (random.nextDouble() >= EcologyConfig.VILLAGE_MAINTENANCE_CHANCE.get()) {
            return;
        }

        if (villager.getVillagerData().getProfession() == VillagerProfession.FARMER && tryReplantFarm(level, villager, random)) {
            return;
        }
        if (tryRepairPath(level, villager, random)) {
            return;
        }
        if (tryPlantGarden(level, villager, random)) {
            return;
        }
        if (villager.getVillagerData().getProfession() == VillagerProfession.FARMER) {
            tryReplantFarm(level, villager, random);
        }
    }

    public static int golemConstructionParticipantRequirement(ServerLevel level, BlockPos center, int vanillaRequirement) {
        if (!EcologyConfig.villageEcologyEnabled()) {
            return vanillaRequirement;
        }
        VillageEcologyReport report = surveyCached(level, center);
        return report.score() >= 80 ? Math.max(3, vanillaRequirement - 1) : vanillaRequirement;
    }

    public static int golemConstructionWorkTicks(ServerLevel level, BlockPos center, int baseTicks) {
        if (!EcologyConfig.villageEcologyEnabled()) {
            return baseTicks;
        }
        VillageEcologyReport report = surveyCached(level, center);
        if (report.score() >= 80) {
            return Math.max(8, baseTicks - 4);
        }
        if (report.score() < 40) {
            return baseTicks + 6;
        }
        return baseTicks;
    }

    private static ScanCounts scanBlocks(ServerLevel level, BlockPos center, int radius) {
        ScanCounts counts = new ScanCounts();
        BlockPos min = center.offset(-radius, -VERTICAL_SCAN_RANGE, -radius);
        BlockPos max = center.offset(radius, VERTICAL_SCAN_RANGE, radius);
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (!level.hasChunkAt(pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            Block block = state.getBlock();
            if (block instanceof CropBlock crop) {
                counts.cropCount++;
                if (crop.isMaxAge(state)) {
                    counts.matureCropCount++;
                }
            }
            if (block instanceof FarmBlock && level.getBlockState(pos.above()).isAir()) {
                counts.emptyFarmlandCount++;
            }
            if (block instanceof ComposterBlock) {
                counts.composterCount++;
                counts.workstationCount++;
            } else if (isWorkstation(block)) {
                counts.workstationCount++;
            }
            if (block instanceof BellBlock) {
                counts.bellCount++;
            }
            if (isBedFoot(state)) {
                counts.bedCount++;
            }
            if (state.is(BlockTags.DOORS)) {
                counts.doorCount++;
            }
            if (state.is(BlockTags.FLOWERS)) {
                counts.flowerCount++;
            }
            if (state.is(BlockTags.SAPLINGS)) {
                counts.saplingCount++;
            }
            if (state.is(BlockTags.LEAVES)) {
                counts.leafCount++;
            }
            if (state.is(Blocks.WATER)) {
                counts.waterCount++;
            }
            if (state.is(Blocks.DIRT_PATH)) {
                counts.pathCount++;
            }
            if (state.getLightEmission(level, pos) >= 8) {
                counts.lightCount++;
            }
        }
        return counts;
    }

    private static boolean isBedFoot(BlockState state) {
        return state.is(BlockTags.BEDS)
                && state.hasProperty(BedBlock.PART)
                && state.getValue(BedBlock.PART) == BedPart.FOOT;
    }

    private static boolean isGuardVillager(Entity entity) {
        ResourceKey<?> key = BuiltInRegistries.ENTITY_TYPE.getResourceKey(entity.getType()).orElse(null);
        return key != null && "guardvillagers".equals(key.location().getNamespace());
    }

    private static boolean tryReplantFarm(ServerLevel level, Villager villager, RandomSource random) {
        for (int attempt = 0; attempt < 18; attempt++) {
            BlockPos farmland = randomNearby(villager.blockPosition(), random, MAINTENANCE_RADIUS, 2);
            if (!level.getBlockState(farmland).is(Blocks.FARMLAND) || !level.getBlockState(farmland.above()).isAir()) {
                continue;
            }
            BlockPos cropPos = farmland.above();
            level.setBlock(cropPos, Blocks.WHEAT.defaultBlockState(), 3);
            markVillageWork(level, villager, cropPos, SoundEvents.CROP_PLANTED);
            return true;
        }
        return false;
    }

    private static boolean tryRepairPath(ServerLevel level, Villager villager, RandomSource random) {
        for (int attempt = 0; attempt < 16; attempt++) {
            BlockPos pos = randomNearby(villager.blockPosition(), random, MAINTENANCE_RADIUS, 1);
            BlockState state = level.getBlockState(pos);
            if ((!state.is(Blocks.DIRT) && !state.is(Blocks.GRASS_BLOCK)) || !level.getBlockState(pos.above()).isAir()) {
                continue;
            }
            if (!hasAdjacentPath(level, pos)) {
                continue;
            }
            level.setBlock(pos, Blocks.DIRT_PATH.defaultBlockState(), 3);
            markVillageWork(level, villager, pos, SoundEvents.GRAVEL_PLACE);
            return true;
        }
        return false;
    }

    private static boolean tryPlantGarden(ServerLevel level, Villager villager, RandomSource random) {
        for (int attempt = 0; attempt < 16; attempt++) {
            BlockPos ground = randomNearby(villager.blockPosition(), random, MAINTENANCE_RADIUS, 1);
            if (!level.getBlockState(ground).is(Blocks.GRASS_BLOCK) || !level.getBlockState(ground.above()).isAir()) {
                continue;
            }
            if (!hasAdjacentPath(level, ground) && !hasNearbyBellOrComposter(level, ground)) {
                continue;
            }
            BlockState flower = GARDEN_FLOWERS[random.nextInt(GARDEN_FLOWERS.length)].defaultBlockState();
            BlockPos flowerPos = ground.above();
            if (!flower.canSurvive(level, flowerPos)) {
                continue;
            }
            level.setBlock(flowerPos, flower, 3);
            markVillageWork(level, villager, flowerPos, SoundEvents.GRASS_PLACE);
            return true;
        }
        return false;
    }

    private static List<Component> adviceFor(VillageEcologyReport report) {
        List<Component> advice = new ArrayList<>();
        for (VillageEcologyIssue issue : report.issues()) {
            advice.add(Component.translatable("ecology.village.advice." + serialized(issue)));
        }
        return advice;
    }

    private static String statusKey(VillageEcologyStatus status) {
        return "ecology.village.status." + status.name().toLowerCase(Locale.ROOT);
    }

    private static String issueKey(VillageEcologyIssue issue) {
        return "ecology.village.issue." + serialized(issue);
    }

    private static String serialized(VillageEcologyIssue issue) {
        return issue.name().toLowerCase(Locale.ROOT);
    }

    private static VillageEcologyStatus statusFor(int score) {
        if (score >= 80) {
            return VillageEcologyStatus.THRIVING;
        }
        if (score >= 60) {
            return VillageEcologyStatus.STABLE;
        }
        if (score >= 40) {
            return VillageEcologyStatus.STRUGGLING;
        }
        return VillageEcologyStatus.NEGLECTED;
    }

    private static int clampScore(int score) {
        return Math.max(0, Math.min(100, score));
    }

    private static BlockPos randomNearby(BlockPos origin, RandomSource random, int horizontalRange, int verticalRange) {
        int x = random.nextInt(horizontalRange * 2 + 1) - horizontalRange;
        int y = random.nextInt(verticalRange * 2 + 1) - verticalRange;
        int z = random.nextInt(horizontalRange * 2 + 1) - horizontalRange;
        return origin.offset(x, y, z);
    }

    private static boolean hasAdjacentPath(ServerLevel level, BlockPos pos) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (level.getBlockState(pos.relative(direction)).is(Blocks.DIRT_PATH)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasNearbyBellOrComposter(ServerLevel level, BlockPos pos) {
        for (BlockPos candidate : BlockPos.betweenClosed(pos.offset(-4, -2, -4), pos.offset(4, 2, 4))) {
            Block block = level.getBlockState(candidate).getBlock();
            if (block instanceof BellBlock || block instanceof ComposterBlock) {
                return true;
            }
        }
        return false;
    }

    private static void markVillageWork(ServerLevel level, Villager villager, BlockPos pos, net.minecraft.sounds.SoundEvent sound) {
        villager.swing(InteractionHand.MAIN_HAND);
        level.playSound(null, pos, sound, SoundSource.BLOCKS, 0.55F, 1.0F);
        level.gameEvent(villager, GameEvent.BLOCK_CHANGE, pos);
    }

    private static boolean isWorkstation(Block block) {
        return block == Blocks.BARREL
                || block == Blocks.BLAST_FURNACE
                || block == Blocks.BREWING_STAND
                || block == Blocks.CARTOGRAPHY_TABLE
                || block == Blocks.CAULDRON
                || block == Blocks.FLETCHING_TABLE
                || block == Blocks.GRINDSTONE
                || block == Blocks.LECTERN
                || block == Blocks.LOOM
                || block == Blocks.SMITHING_TABLE
                || block == Blocks.SMOKER
                || block == Blocks.STONECUTTER;
    }

    private static void cacheSurvey(SurveyKey key, VillageEcologyReport report, long gameTime) {
        if (SURVEY_CACHE.size() >= SURVEY_CACHE_MAX_ENTRIES) {
            SURVEY_CACHE.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= gameTime);
        }
        if (SURVEY_CACHE.size() >= SURVEY_CACHE_MAX_ENTRIES) {
            SURVEY_CACHE.clear();
        }
        SURVEY_CACHE.put(key, new CachedSurvey(report, gameTime + SURVEY_CACHE_TICKS));
    }

    private record SurveyKey(ResourceKey<Level> dimension, int cellX, int cellZ) {
        private static SurveyKey of(ServerLevel level, BlockPos center) {
            return new SurveyKey(
                    level.dimension(),
                    Math.floorDiv(center.getX(), SURVEY_CACHE_CELL_SIZE),
                    Math.floorDiv(center.getZ(), SURVEY_CACHE_CELL_SIZE));
        }
    }

    private record CachedSurvey(VillageEcologyReport report, long expiresAt) {
    }

    private static final class ScanCounts {
        private int cropCount;
        private int matureCropCount;
        private int emptyFarmlandCount;
        private int composterCount;
        private int workstationCount;
        private int bellCount;
        private int bedCount;
        private int doorCount;
        private int flowerCount;
        private int saplingCount;
        private int leafCount;
        private int waterCount;
        private int pathCount;
        private int lightCount;
    }
}
