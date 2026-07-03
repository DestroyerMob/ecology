package com.destroyermob.ecology.village;

import com.destroyermob.ecology.Ecology;
import com.destroyermob.ecology.EcologyConfig;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.JigsawReplacementProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.block.Rotation;

public final class VillageWorksites {
    private static final int VILLAGER_REQUEST_COOLDOWN_TICKS = 20 * 30;
    private static final int REQUEST_EXPIRY_TICKS = 20 * 120;
    private static final int PROCESS_INTERVAL_TICKS = 20 * 5;
    private static final int FAILED_PLACEMENT_RETRY_TICKS = 20 * 30;
    private static final int MAX_PLACEMENTS_PER_LEVEL_TICK = 1;
    private static final int BUILDING_SEARCH_RADIUS = 42;
    private static final int BUILDING_SEARCH_VERTICAL_RANGE = 4;
    private static final int BUILDING_ANCHOR_LIMIT = 192;
    private static final int BUILDING_CLEARANCE_MARGIN = 2;
    private static final int BUILDING_WORKSITE_SPACING_RADIUS = 5;
    private static final int COMPACT_WORKSITE_SPACING_RADIUS = 7;
    private static final int PATH_CONNECTION_RADIUS = 18;
    private static final int PATH_CONNECTION_MAX_LENGTH = 36;
    private static final int RELAXED_BUILDING_CLEARANCE_MARGIN = 1;
    private static final int RELAXED_BUILDING_WORKSITE_SPACING_RADIUS = 3;
    private static final int RELAXED_PATH_CONNECTION_MAX_LENGTH = 48;
    private static final int WORKSTATION_PLACEMENT_RADIUS = 18;
    private static final int WORKSTATION_PLACEMENT_VERTICAL_RANGE = 3;
    private static final int WORKSITE_FLOOR_RADIUS = 1;
    private static final List<Rotation> ROTATIONS = List.of(Rotation.NONE, Rotation.CLOCKWISE_90, Rotation.CLOCKWISE_180, Rotation.COUNTERCLOCKWISE_90);
    private static final List<BuildingPlacementProfile> BUILDING_PLACEMENT_PROFILES = List.of(
            new BuildingPlacementProfile(BUILDING_CLEARANCE_MARGIN, BUILDING_WORKSITE_SPACING_RADIUS, PATH_CONNECTION_MAX_LENGTH),
            new BuildingPlacementProfile(RELAXED_BUILDING_CLEARANCE_MARGIN, RELAXED_BUILDING_WORKSITE_SPACING_RADIUS, RELAXED_PATH_CONNECTION_MAX_LENGTH));
    private static final Map<ResourceKey<Level>, Map<WorksiteKey, WorksiteRequest>> REQUESTS = new HashMap<>();
    private static final Map<ResourceKey<Level>, Map<UUID, Long>> REQUESTER_COOLDOWNS = new HashMap<>();

    private VillageWorksites() {
    }

    public static void requestJobSite(ServerLevel level, Villager villager, VillagerProfession profession) {
        if (!EcologyConfig.villageWorkstationProvisioningEnabled()
                || villager.isBaby()
                || !VillageVocations.isAssignableProfession(profession)
                || workstationBlockFor(profession).isEmpty()) {
            return;
        }

        long gameTime = level.getGameTime();
        Map<UUID, Long> cooldowns = REQUESTER_COOLDOWNS.computeIfAbsent(level.dimension(), ignored -> new HashMap<>());
        Long nextRequestTime = cooldowns.get(villager.getUUID());
        if (nextRequestTime != null && gameTime < nextRequestTime) {
            return;
        }
        cooldowns.put(villager.getUUID(), gameTime + VILLAGER_REQUEST_COOLDOWN_TICKS);

        BlockPos center = VillageZones.centerFor(level, villager.blockPosition())
                .orElseGet(() -> VillageCurrencySystem.villageAnchor(level, villager))
                .immutable();

        WorksiteKey key = new WorksiteKey(center, profession);
        REQUESTS.computeIfAbsent(level.dimension(), ignored -> new HashMap<>())
                .computeIfAbsent(key, ignored -> new WorksiteRequest(key.center(), key.profession()))
                .refresh(gameTime, villager.blockPosition());
        debug("Queued {} worksite request at {} for villager {} near {}",
                VillageVocations.professionName(profession),
                center.toShortString(),
                villager.getUUID(),
                villager.blockPosition().toShortString());
    }

    public static void tick(MinecraftServer server) {
        if (!EcologyConfig.villageWorkstationProvisioningEnabled()) {
            REQUESTS.clear();
            REQUESTER_COOLDOWNS.clear();
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            processLevel(level);
        }
    }

    private static void processLevel(ServerLevel level) {
        long gameTime = level.getGameTime();
        if (Math.floorMod(gameTime, PROCESS_INTERVAL_TICKS) != 0L) {
            return;
        }

        Map<WorksiteKey, WorksiteRequest> requests = REQUESTS.get(level.dimension());
        if (requests == null || requests.isEmpty()) {
            cleanupRequesterCooldowns(level.dimension(), gameTime);
            return;
        }

        requests.values().removeIf(request -> gameTime - request.lastRequestedGameTime > REQUEST_EXPIRY_TICKS);
        if (requests.isEmpty()) {
            cleanupRequesterCooldowns(level.dimension(), gameTime);
            return;
        }

        int placements = 0;
        List<WorksiteRequest> dueRequests = requests.values().stream()
                .filter(request -> request.nextAttemptGameTime <= gameTime)
                .sorted(Comparator.comparingLong((WorksiteRequest request) -> request.lastAttemptGameTime)
                        .thenComparingLong(request -> request.lastRequestedGameTime))
                .toList();
        for (WorksiteRequest request : dueRequests) {
            if (placeWorkstation(level, request)) {
                requests.remove(new WorksiteKey(request.center, request.profession));
                placements++;
                if (placements >= MAX_PLACEMENTS_PER_LEVEL_TICK) {
                    break;
                }
            } else {
                request.defer(gameTime);
            }
        }
        cleanupRequesterCooldowns(level.dimension(), gameTime);
    }

    private static void cleanupRequesterCooldowns(ResourceKey<Level> dimension, long gameTime) {
        Map<UUID, Long> cooldowns = REQUESTER_COOLDOWNS.get(dimension);
        if (cooldowns == null) {
            return;
        }
        cooldowns.entrySet().removeIf(entry -> entry.getValue() <= gameTime);
        if (cooldowns.isEmpty()) {
            REQUESTER_COOLDOWNS.remove(dimension);
        }
    }

    private static boolean placeWorkstation(ServerLevel level, WorksiteRequest request) {
        if (placeProfessionBuilding(level, request)) {
            return true;
        }
        // Keep retrying real profession buildings instead of masking failed placement with standalone POIs.
        if (!buildingTemplatesFor(level, request.center, request.profession).isEmpty()) {
            debug("Deferred {} worksite near {}: no safe profession building placement found",
                    VillageVocations.professionName(request.profession),
                    request.center.toShortString());
            return false;
        }

        Optional<Block> block = workstationBlockFor(request.profession);
        if (block.isEmpty()) {
            return false;
        }
        Optional<CompactWorksitePlacement> placement = findWorkstationPlacement(level, request.center, request.lastRequesterPos);
        if (placement.isEmpty()) {
            debug("Deferred {} compact worksite near {}: no safe compact placement found",
                    VillageVocations.professionName(request.profession),
                    request.center.toShortString());
            return false;
        }
        if (EcologyConfig.villageConstructionCrewsEnabled()) {
            queueCompactWorksite(level, request, placement.get(), block.get());
        } else {
            placeCompactWorksite(level, placement.get(), request.profession, block.get());
        }
        return true;
    }

    private static boolean placeProfessionBuilding(ServerLevel level, WorksiteRequest request) {
        Optional<BuildingPlacement> placement = findProfessionBuildingPlacement(level, request);
        if (placement.isEmpty()) {
            return false;
        }

        BuildingPlacement selected = placement.get();
        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setRotation(selected.rotation())
                .setKnownShape(true)
                .setIgnoreEntities(true)
                .setFinalizeEntities(false)
                .addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK)
                .addProcessor(JigsawReplacementProcessor.INSTANCE);
        if (EcologyConfig.villageConstructionCrewsEnabled()) {
            List<VillageConstructionCrews.BlockPlan> pieces = new ArrayList<>();
            pieces.addAll(VillageConstructionCrews.pathPieces(level, selected.pathRoute()));
            pieces.addAll(VillageConstructionCrews.structurePieces(level, selected.structure(), selected.origin(), settings));
            return VillageConstructionCrews.queueJob(
                    level,
                    request.center,
                    "worksite_building:" + VillageVocations.professionName(request.profession),
                    selected.origin(),
                    pieces,
                    () -> level.playSound(null, selected.origin(), SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 0.75F, 1.0F));
        }
        RandomSource random = level.getRandom();
        boolean placed = selected.structure().placeInWorld(level, selected.origin(), selected.origin(), settings, random, 18);
        if (placed) {
            VillageBuildSafety.placePathRoute(level, selected.pathRoute());
            level.playSound(null, selected.origin(), SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 0.75F, 1.0F);
        }
        return placed;
    }

    private static Optional<BuildingPlacement> findProfessionBuildingPlacement(ServerLevel level, WorksiteRequest request) {
        List<ProfessionBuilding> templates = buildingTemplatesFor(level, request.center, request.profession);
        if (templates.isEmpty()) {
            return Optional.empty();
        }

        List<BlockPos> anchors = buildingCandidateAnchors(level, request.center, request.lastRequesterPos);
        if (anchors.isEmpty()) {
            debug("No candidate anchors for {} worksite near {}",
                    VillageVocations.professionName(request.profession),
                    request.center.toShortString());
            return Optional.empty();
        }

        PlacementStats stats = new PlacementStats();
        for (BuildingPlacementProfile profile : BUILDING_PLACEMENT_PROFILES) {
            BuildingPlacement best = null;
            int bestScore = Integer.MAX_VALUE;
            for (ProfessionBuilding template : templates) {
                Optional<StructureTemplate> structure = level.getServer().getStructureManager().get(template.location());
                if (structure.isEmpty()) {
                    stats.missingTemplates++;
                    continue;
                }
                for (Rotation rotation : ROTATIONS) {
                    if (!templateContainsWorkstation(structure.get(), rotation)) {
                        stats.workstationMissingRotations++;
                        continue;
                    }
                    Vec3i size = structure.get().getSize(rotation);
                    for (BlockPos anchor : anchors) {
                        BlockPos origin = anchor.offset(-size.getX() / 2, 0, -size.getZ() / 2);
                        stats.candidates++;
                        if (!buildingFits(level, origin, structure.get(), rotation, profile)) {
                            stats.rejectedByFit++;
                            continue;
                        }
                        Optional<List<BlockPos>> pathRoute = VillageBuildSafety.pathRouteForStructure(
                                level,
                                origin,
                                size,
                                PATH_CONNECTION_RADIUS,
                                profile.pathMaxLength());
                        if (pathRoute.isEmpty()) {
                            stats.rejectedByPath++;
                            continue;
                        }
                        int score = buildingPlacementScore(level, request.center, request.lastRequesterPos, origin, size, pathRoute.get());
                        if (score < bestScore) {
                            best = new BuildingPlacement(template, structure.get(), origin, rotation, pathRoute.get());
                            bestScore = score;
                        }
                    }
                }
            }
            if (best != null) {
                debug("Selected {} {} worksite at {} using clearance {}, spacing {}, path length {}",
                        best.template().style(),
                        best.template().name(),
                        best.origin().toShortString(),
                        profile.clearanceMargin(),
                        profile.worksiteSpacingRadius(),
                        best.pathRoute().size());
                return Optional.of(best);
            }
        }
        debug("No full building placement for {} near {} after {} candidates (missing templates {}, missing workstation rotations {}, fit rejects {}, path rejects {})",
                VillageVocations.professionName(request.profession),
                request.center.toShortString(),
                stats.candidates,
                stats.missingTemplates,
                stats.workstationMissingRotations,
                stats.rejectedByFit,
                stats.rejectedByPath);
        return Optional.empty();
    }

    private static boolean buildingFits(
            ServerLevel level,
            BlockPos origin,
            StructureTemplate structure,
            Rotation rotation,
            BuildingPlacementProfile profile) {
        Vec3i size = structure.getSize(rotation);
        return VillageBuildSafety.structureFits(level, origin, size, profile.clearanceMargin(), false, state -> false)
                && !VillageBuildSafety.hasMatchingBlockNearFootprint(
                        level,
                        origin,
                        size,
                        profile.worksiteSpacingRadius(),
                        state -> VillageBuildSafety.isProvisionableWorkstation(state.getBlock()));
    }

    private static List<BlockPos> buildingCandidateAnchors(ServerLevel level, BlockPos center, BlockPos requesterPos) {
        List<BlockPos> anchors = new ArrayList<>();
        for (BlockPos candidate : BlockPos.betweenClosed(
                center.offset(-BUILDING_SEARCH_RADIUS, -BUILDING_SEARCH_VERTICAL_RANGE, -BUILDING_SEARCH_RADIUS),
                center.offset(BUILDING_SEARCH_RADIUS, BUILDING_SEARCH_VERTICAL_RANGE, BUILDING_SEARCH_RADIUS))) {
            BlockPos pos = candidate.immutable();
            if (level.hasChunkAt(pos)
                    && level.hasChunkAt(pos.below())
                    && VillageBuildSafety.canReplaceForBuilding(level.getBlockState(pos))
                    && VillageBuildSafety.canBuildOnVillageGround(level.getBlockState(pos.below()))
                    && VillageBuildSafety.hasSolidFloor(level, pos)) {
                anchors.add(pos);
            }
        }
        anchors.sort(Comparator.comparingInt((BlockPos pos) -> buildingAnchorScore(level, center, requesterPos, pos))
                .thenComparingDouble(pos -> pos.distSqr(requesterPos)));
        return anchors.size() <= BUILDING_ANCHOR_LIMIT ? anchors : anchors.subList(0, BUILDING_ANCHOR_LIMIT);
    }

    private static boolean templateContainsWorkstation(StructureTemplate structure, Rotation rotation) {
        StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(rotation);
        for (Block workstation : VillageBuildSafety.provisionableWorkstations()) {
            if (!structure.filterBlocks(BlockPos.ZERO, settings, workstation, true).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static int buildingAnchorScore(ServerLevel level, BlockPos center, BlockPos requesterPos, BlockPos pos) {
        int score = (int)Math.min(Integer.MAX_VALUE / 2, pos.distSqr(requesterPos) + center.distSqr(pos) / 3.0D);
        if (VillageBuildSafety.hasPathNear(level, pos.below(), 4)) {
            score -= 1800;
        } else if (VillageBuildSafety.hasPathNear(level, pos.below(), 8)) {
            score -= 700;
        }
        if (!level.canSeeSky(pos.above())) {
            score += 250;
        }
        return score;
    }

    private static int buildingPlacementScore(ServerLevel level, BlockPos center, BlockPos requesterPos, BlockPos origin, Vec3i size, List<BlockPos> pathRoute) {
        BlockPos buildingCenter = origin.offset(size.getX() / 2, 0, size.getZ() / 2);
        int score = (int)Math.min(Integer.MAX_VALUE / 2, buildingCenter.distSqr(requesterPos) + center.distSqr(buildingCenter) / 3.0D + pathRoute.size() * 12.0D);
        if (VillageBuildSafety.hasPathNear(level, origin.below(), size, 2)) {
            score -= 2500;
        } else if (VillageBuildSafety.hasPathNear(level, origin.below(), size, 6)) {
            score -= 900;
        }
        return score;
    }

    private static List<ProfessionBuilding> buildingTemplatesFor(ServerLevel level, BlockPos anchor, VillagerProfession profession) {
        String style = VillageBuildSafety.villageStyle(level, anchor);
        List<ProfessionBuilding> templates = new ArrayList<>();
        addBuildingTemplates(templates, style, profession);
        if (!"plains".equals(style)) {
            addBuildingTemplates(templates, "plains", profession);
        }
        return templates;
    }

    private static void addBuildingTemplates(List<ProfessionBuilding> templates, String style, VillagerProfession profession) {
        for (String name : buildingNames(style, profession)) {
            templates.add(new ProfessionBuilding(
                    style,
                    name,
                    ResourceLocation.withDefaultNamespace("village/" + style + "/houses/" + name)));
        }
    }

    private static List<String> buildingNames(String style, VillagerProfession profession) {
        if (profession == VillagerProfession.ARMORER) {
            return switch (style) {
                case "desert" -> List.of("desert_armorer_1");
                case "savanna" -> List.of("savanna_armorer_1");
                case "snowy" -> List.of("snowy_armorer_house_1", "snowy_armorer_house_2");
                case "taiga" -> List.of("taiga_armorer_house_1", "taiga_armorer_2");
                default -> List.of("plains_armorer_house_1");
            };
        }
        if (profession == VillagerProfession.BUTCHER) {
            return switch (style) {
                case "desert" -> List.of("desert_butcher_shop_1");
                case "savanna" -> List.of("savanna_butchers_shop_1", "savanna_butchers_shop_2");
                case "snowy" -> List.of("snowy_butchers_shop_1", "snowy_butchers_shop_2");
                case "taiga" -> List.of("taiga_butcher_shop_1");
                default -> List.of("plains_butcher_shop_1", "plains_butcher_shop_2");
            };
        }
        if (profession == VillagerProfession.CARTOGRAPHER) {
            return switch (style) {
                case "desert" -> List.of("desert_cartographer_house_1");
                case "savanna" -> List.of("savanna_cartographer_1");
                case "snowy" -> List.of("snowy_cartographer_house_1");
                case "taiga" -> List.of("taiga_cartographer_house_1");
                default -> List.of("plains_cartographer_1");
            };
        }
        if (profession == VillagerProfession.CLERIC) {
            return switch (style) {
                case "desert" -> List.of("desert_temple_1", "desert_temple_2");
                case "savanna" -> List.of("savanna_temple_1", "savanna_temple_2");
                case "snowy" -> List.of("snowy_temple_1");
                case "taiga" -> List.of("taiga_temple_1");
                default -> List.of("plains_temple_3", "plains_temple_4");
            };
        }
        if (profession == VillagerProfession.FARMER) {
            return switch (style) {
                case "desert" -> List.of("desert_farm_1", "desert_farm_2", "desert_large_farm_1");
                case "savanna" -> List.of("savanna_small_farm", "savanna_large_farm_1", "savanna_large_farm_2");
                case "snowy" -> List.of("snowy_farm_1", "snowy_farm_2");
                case "taiga" -> List.of("taiga_small_farm_1", "taiga_large_farm_1", "taiga_large_farm_2");
                default -> List.of("plains_small_farm_1", "plains_large_farm_1");
            };
        }
        if (profession == VillagerProfession.FISHERMAN) {
            return switch (style) {
                case "desert" -> List.of("desert_fisher_1");
                case "savanna" -> List.of("savanna_fisher_cottage_1");
                case "snowy" -> List.of("snowy_fisher_cottage");
                case "taiga" -> List.of("taiga_fisher_cottage_1");
                default -> List.of("plains_fisher_cottage_1");
            };
        }
        if (profession == VillagerProfession.FLETCHER) {
            return switch (style) {
                case "desert" -> List.of("desert_fletcher_house_1");
                case "savanna" -> List.of("savanna_fletcher_house_1");
                case "snowy" -> List.of("snowy_fletcher_house_1");
                case "taiga" -> List.of("taiga_fletcher_house_1");
                default -> List.of("plains_fletcher_house_1");
            };
        }
        if (profession == VillagerProfession.LEATHERWORKER) {
            return switch (style) {
                case "desert" -> List.of("desert_tannery_1");
                case "savanna" -> List.of("savanna_tannery_1");
                case "snowy" -> List.of("snowy_tannery_1");
                case "taiga" -> List.of("taiga_tannery_1");
                default -> List.of("plains_tannery_1");
            };
        }
        if (profession == VillagerProfession.LIBRARIAN) {
            return switch (style) {
                case "desert" -> List.of("desert_library_1");
                case "savanna" -> List.of("savanna_library_1");
                case "snowy" -> List.of("snowy_library_1");
                case "taiga" -> List.of("taiga_library_1");
                default -> List.of("plains_library_1", "plains_library_2");
            };
        }
        if (profession == VillagerProfession.MASON) {
            return switch (style) {
                case "desert" -> List.of("desert_mason_1");
                case "savanna" -> List.of("savanna_mason_1");
                case "snowy" -> List.of("snowy_masons_house_1", "snowy_masons_house_2");
                case "taiga" -> List.of("taiga_masons_house_1");
                default -> List.of("plains_masons_house_1");
            };
        }
        if (profession == VillagerProfession.SHEPHERD) {
            return switch (style) {
                case "desert" -> List.of("desert_shepherd_house_1");
                case "savanna" -> List.of("savanna_shepherd_1");
                case "snowy" -> List.of("snowy_shepherds_house_1");
                case "taiga" -> List.of("taiga_shepherds_house_1");
                default -> List.of("plains_shepherds_house_1");
            };
        }
        if (profession == VillagerProfession.TOOLSMITH) {
            return switch (style) {
                case "desert" -> List.of("desert_tool_smith_1");
                case "savanna" -> List.of("savanna_tool_smith_1");
                case "snowy" -> List.of("snowy_tool_smith_1");
                case "taiga" -> List.of("taiga_tool_smith_1");
                default -> List.of("plains_tool_smith_1");
            };
        }
        if (profession == VillagerProfession.WEAPONSMITH) {
            return switch (style) {
                case "desert" -> List.of("desert_weaponsmith_1");
                case "savanna" -> List.of("savanna_weaponsmith_1", "savanna_weaponsmith_2");
                case "snowy" -> List.of("snowy_weapon_smith_1");
                case "taiga" -> List.of("taiga_weaponsmith_1", "taiga_weaponsmith_2");
                default -> List.of("plains_weaponsmith_1");
            };
        }
        return List.of();
    }

    private static void placeCompactWorksite(ServerLevel level, CompactWorksitePlacement placement, VillagerProfession profession, Block workstation) {
        prepareWorksiteFloor(level, placement.station(), placement.stand(), profession);
        placeWorksiteDecor(level, placement.station(), placement.stand(), profession);
        VillageBuildSafety.placePathRoute(level, placement.pathRoute());
        level.setBlock(placement.station(), workstation.defaultBlockState(), 3);
        level.playSound(null, placement.station(), SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 0.6F, 1.0F);
    }

    private static void queueCompactWorksite(ServerLevel level, WorksiteRequest request, CompactWorksitePlacement placement, Block workstation) {
        List<VillageConstructionCrews.BlockPlan> pieces = compactWorksitePieces(level, placement, request.profession, workstation);
        VillageConstructionCrews.queueJob(
                level,
                request.center,
                "compact_worksite:" + VillageVocations.professionName(request.profession),
                placement.station(),
                pieces,
                () -> level.playSound(null, placement.station(), SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 0.55F, 1.05F));
    }

    private static List<VillageConstructionCrews.BlockPlan> compactWorksitePieces(
            ServerLevel level,
            CompactWorksitePlacement placement,
            VillagerProfession profession,
            Block workstation) {
        List<VillageConstructionCrews.BlockPlan> pieces = new ArrayList<>(VillageConstructionCrews.pathPieces(level, placement.pathRoute()));
        Block fullFloor = worksiteFloorBlockFor(profession);
        for (BlockPos ground : BlockPos.betweenClosed(
                placement.station().below().offset(-WORKSITE_FLOOR_RADIUS, 0, -WORKSITE_FLOOR_RADIUS),
                placement.station().below().offset(WORKSITE_FLOOR_RADIUS, 0, WORKSITE_FLOOR_RADIUS))) {
            if (!level.hasChunkAt(ground) || !VillageBuildSafety.canReplaceForBuilding(level.getBlockState(ground.above()))) {
                continue;
            }
            if (!level.getBlockState(ground.above()).isAir()) {
                pieces.add(VillageConstructionCrews.BlockPlan.simple(ground.above(), Blocks.AIR.defaultBlockState()));
            }
            if (ground.equals(placement.station().below())) {
                if (fullFloor != Blocks.DIRT_PATH && VillageBuildSafety.canReplaceWorksiteGround(level.getBlockState(ground))) {
                    pieces.add(VillageConstructionCrews.BlockPlan.simple(ground, fullFloor.defaultBlockState()));
                }
                continue;
            }
            if (ground.equals(placement.stand().below()) || fullFloor == Blocks.DIRT_PATH) {
                if (!VillageBuildSafety.isPathBlock(level.getBlockState(ground))) {
                    pieces.add(VillageConstructionCrews.BlockPlan.simple(ground, Blocks.DIRT_PATH.defaultBlockState()));
                }
            } else if (VillageBuildSafety.canReplaceWorksiteGround(level.getBlockState(ground))) {
                pieces.add(VillageConstructionCrews.BlockPlan.simple(ground, fullFloor.defaultBlockState()));
            }
        }
        worksiteDecorBlockFor(profession).ifPresent(decor -> addCompactWorksiteDecor(level, pieces, placement, decor));
        pieces.add(VillageConstructionCrews.BlockPlan.simple(placement.station(), workstation.defaultBlockState()));
        return pieces;
    }

    private static void addCompactWorksiteDecor(
            ServerLevel level,
            List<VillageConstructionCrews.BlockPlan> pieces,
            CompactWorksitePlacement placement,
            Block decor) {
        int placed = 0;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos pos = placement.station().relative(direction);
            if (pos.equals(placement.stand()) || !canPlaceDecor(level, pos)) {
                continue;
            }
            pieces.add(VillageConstructionCrews.BlockPlan.simple(pos, decor.defaultBlockState()));
            placed++;
            if (placed >= 2) {
                return;
            }
        }
    }

    private static void prepareWorksiteFloor(ServerLevel level, BlockPos station, BlockPos stand, VillagerProfession profession) {
        Block fullFloor = worksiteFloorBlockFor(profession);
        for (BlockPos ground : BlockPos.betweenClosed(
                station.below().offset(-WORKSITE_FLOOR_RADIUS, 0, -WORKSITE_FLOOR_RADIUS),
                station.below().offset(WORKSITE_FLOOR_RADIUS, 0, WORKSITE_FLOOR_RADIUS))) {
            if (!level.hasChunkAt(ground) || !VillageBuildSafety.canReplaceForBuilding(level.getBlockState(ground.above()))) {
                continue;
            }
            if (ground.equals(station.below())) {
                if (fullFloor != Blocks.DIRT_PATH && VillageBuildSafety.canReplaceWorksiteGround(level.getBlockState(ground))) {
                    level.setBlock(ground, fullFloor.defaultBlockState(), 3);
                }
                continue;
            }
            if (ground.equals(stand.below()) || fullFloor == Blocks.DIRT_PATH) {
                VillageBuildSafety.tryPlacePath(level, ground);
            } else if (VillageBuildSafety.canReplaceWorksiteGround(level.getBlockState(ground))) {
                level.setBlock(ground, fullFloor.defaultBlockState(), 3);
            }
        }
    }

    private static void placeWorksiteDecor(ServerLevel level, BlockPos station, BlockPos stand, VillagerProfession profession) {
        Optional<Block> decor = worksiteDecorBlockFor(profession);
        if (decor.isEmpty()) {
            return;
        }
        int placed = 0;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos pos = station.relative(direction);
            if (pos.equals(stand) || !canPlaceDecor(level, pos)) {
                continue;
            }
            level.setBlock(pos, decor.get().defaultBlockState(), 3);
            placed++;
            if (placed >= 2) {
                return;
            }
        }
    }

    private static boolean canPlaceDecor(ServerLevel level, BlockPos pos) {
        return level.hasChunkAt(pos)
                && level.hasChunkAt(pos.below())
                && VillageBuildSafety.canReplaceForSmallSite(level.getBlockState(pos))
                && VillageBuildSafety.hasSolidFloor(level, pos);
    }

    private static Block worksiteFloorBlockFor(VillagerProfession profession) {
        if (profession == VillagerProfession.ARMORER
                || profession == VillagerProfession.MASON
                || profession == VillagerProfession.TOOLSMITH
                || profession == VillagerProfession.WEAPONSMITH) {
            return Blocks.COBBLESTONE;
        }
        return Blocks.DIRT_PATH;
    }

    private static Optional<Block> worksiteDecorBlockFor(VillagerProfession profession) {
        if (profession == VillagerProfession.FARMER || profession == VillagerProfession.BUTCHER) {
            return Optional.of(Blocks.HAY_BLOCK);
        }
        if (profession == VillagerProfession.LIBRARIAN || profession == VillagerProfession.CARTOGRAPHER) {
            return Optional.of(Blocks.BOOKSHELF);
        }
        if (profession == VillagerProfession.SHEPHERD) {
            return Optional.of(Blocks.WHITE_WOOL);
        }
        if (profession == VillagerProfession.ARMORER
                || profession == VillagerProfession.MASON
                || profession == VillagerProfession.TOOLSMITH
                || profession == VillagerProfession.WEAPONSMITH) {
            return Optional.of(Blocks.COBBLESTONE);
        }
        return Optional.empty();
    }

    private static Optional<CompactWorksitePlacement> findWorkstationPlacement(ServerLevel level, BlockPos center, BlockPos requesterPos) {
        CompactWorksitePlacement best = null;
        int bestScore = Integer.MAX_VALUE;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos candidate : BlockPos.betweenClosed(
                center.offset(-WORKSTATION_PLACEMENT_RADIUS, -WORKSTATION_PLACEMENT_VERTICAL_RANGE, -WORKSTATION_PLACEMENT_RADIUS),
                center.offset(WORKSTATION_PLACEMENT_RADIUS, WORKSTATION_PLACEMENT_VERTICAL_RANGE, WORKSTATION_PLACEMENT_RADIUS))) {
            BlockPos pos = candidate.immutable();
            if (!canPlaceWorkstation(level, pos)) {
                continue;
            }
            Optional<BlockPos> stand = openAdjacentStand(level, pos);
            if (stand.isEmpty()) {
                continue;
            }
            Optional<List<BlockPos>> pathRoute = VillageBuildSafety.pathRouteForTarget(
                    level,
                    stand.get().below(),
                    PATH_CONNECTION_RADIUS,
                    PATH_CONNECTION_MAX_LENGTH);
            if (pathRoute.isEmpty()) {
                continue;
            }
            int score = workstationPlacementScore(level, requesterPos, pos);
            score += pathRoute.get().size() * 12;
            double distance = pos.distSqr(requesterPos);
            if (score < bestScore || score == bestScore && distance < bestDistance) {
                best = new CompactWorksitePlacement(pos, stand.get(), pathRoute.get());
                bestScore = score;
                bestDistance = distance;
            }
        }
        return Optional.ofNullable(best);
    }

    private static boolean canPlaceWorkstation(ServerLevel level, BlockPos pos) {
        return level.hasChunkAt(pos)
                && level.hasChunkAt(pos.below())
                && VillageBuildSafety.canReplaceForSmallSite(level.getBlockState(pos))
                && VillageBuildSafety.hasSolidFloor(level, pos)
                && !level.getBlockState(pos.below()).is(Blocks.DIRT_PATH)
                && !level.getBlockState(pos.below()).is(Blocks.FARMLAND)
                && VillageBuildSafety.canBuildOnVillageGround(level.getBlockState(pos.below()))
                && worksiteFootprintFits(level, pos)
                && !VillageBuildSafety.hasMatchingBlockNearFootprint(
                        level,
                        pos,
                        new Vec3i(1, 1, 1),
                        COMPACT_WORKSITE_SPACING_RADIUS,
                        state -> VillageBuildSafety.isProvisionableWorkstation(state.getBlock()))
                && openAdjacentStand(level, pos).isPresent();
    }

    private static boolean worksiteFootprintFits(ServerLevel level, BlockPos station) {
        for (BlockPos ground : BlockPos.betweenClosed(
                station.below().offset(-WORKSITE_FLOOR_RADIUS, 0, -WORKSITE_FLOOR_RADIUS),
                station.below().offset(WORKSITE_FLOOR_RADIUS, 0, WORKSITE_FLOOR_RADIUS))) {
            if (!level.hasChunkAt(ground)
                    || !level.hasChunkAt(ground.above())
                    || VillageBuildSafety.isPathBlock(level.getBlockState(ground))
                    || level.getBlockState(ground).is(Blocks.FARMLAND)
                    || !VillageBuildSafety.canReplaceForBuilding(level.getBlockState(ground.above()))
                    || !VillageBuildSafety.canReplaceWorksiteGround(level.getBlockState(ground))) {
                return false;
            }
        }
        return true;
    }

    private static Optional<BlockPos> openAdjacentStand(ServerLevel level, BlockPos workstation) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos stand = workstation.relative(direction);
            if (!level.hasChunkAt(stand)
                    || !level.hasChunkAt(stand.above())
                    || !level.getBlockState(stand).getCollisionShape(level, stand).isEmpty()
                    || !level.getBlockState(stand.above()).getCollisionShape(level, stand.above()).isEmpty()
                    || !VillageBuildSafety.hasSolidFloor(level, stand)) {
                continue;
            }
            return Optional.of(stand.immutable());
        }
        return Optional.empty();
    }

    private static int workstationPlacementScore(ServerLevel level, BlockPos requesterPos, BlockPos pos) {
        int score = (int)Math.min(Integer.MAX_VALUE / 2, pos.distSqr(requesterPos));
        if (VillageBuildSafety.hasAdjacentPath(level, pos.below())) {
            score -= 700;
        }
        if (level.canSeeSky(pos.above())) {
            score -= 100;
        }
        if (VillageBuildSafety.hasShelter(level, pos)) {
            score += 120;
        }
        return score;
    }

    private static Optional<Block> workstationBlockFor(VillagerProfession profession) {
        if (profession == VillagerProfession.ARMORER) {
            return Optional.of(Blocks.BLAST_FURNACE);
        }
        if (profession == VillagerProfession.BUTCHER) {
            return Optional.of(Blocks.SMOKER);
        }
        if (profession == VillagerProfession.CARTOGRAPHER) {
            return Optional.of(Blocks.CARTOGRAPHY_TABLE);
        }
        if (profession == VillagerProfession.CLERIC) {
            return Optional.of(Blocks.BREWING_STAND);
        }
        if (profession == VillagerProfession.FARMER) {
            return Optional.of(Blocks.COMPOSTER);
        }
        if (profession == VillagerProfession.FISHERMAN) {
            return Optional.of(Blocks.BARREL);
        }
        if (profession == VillagerProfession.FLETCHER) {
            return Optional.of(Blocks.FLETCHING_TABLE);
        }
        if (profession == VillagerProfession.LEATHERWORKER) {
            return Optional.of(Blocks.CAULDRON);
        }
        if (profession == VillagerProfession.LIBRARIAN) {
            return Optional.of(Blocks.LECTERN);
        }
        if (profession == VillagerProfession.MASON) {
            return Optional.of(Blocks.STONECUTTER);
        }
        if (profession == VillagerProfession.SHEPHERD) {
            return Optional.of(Blocks.LOOM);
        }
        if (profession == VillagerProfession.TOOLSMITH) {
            return Optional.of(Blocks.SMITHING_TABLE);
        }
        if (profession == VillagerProfession.WEAPONSMITH) {
            return Optional.of(Blocks.GRINDSTONE);
        }
        return Optional.empty();
    }

    private static void debug(String message, Object... args) {
        if (EcologyConfig.villageWorksiteDebugLoggingEnabled()) {
            Ecology.LOGGER.info(message, args);
        }
    }

    private record WorksiteKey(BlockPos center, VillagerProfession profession) {
    }

    private record ProfessionBuilding(String style, String name, ResourceLocation location) {
    }

    private record BuildingPlacementProfile(int clearanceMargin, int worksiteSpacingRadius, int pathMaxLength) {
    }

    private record BuildingPlacement(ProfessionBuilding template, StructureTemplate structure, BlockPos origin, Rotation rotation, List<BlockPos> pathRoute) {
    }

    private record CompactWorksitePlacement(BlockPos station, BlockPos stand, List<BlockPos> pathRoute) {
    }

    private static final class WorksiteRequest {
        private final BlockPos center;
        private final VillagerProfession profession;
        private BlockPos lastRequesterPos;
        private long lastRequestedGameTime;
        private long lastAttemptGameTime;
        private long nextAttemptGameTime;

        private WorksiteRequest(BlockPos center, VillagerProfession profession) {
            this.center = center.immutable();
            this.profession = profession;
            this.lastRequesterPos = center.immutable();
            this.lastRequestedGameTime = -1L;
            this.lastAttemptGameTime = -1L;
            this.nextAttemptGameTime = 0L;
        }

        private void refresh(long gameTime, BlockPos requesterPos) {
            lastRequesterPos = requesterPos.immutable();
            lastRequestedGameTime = gameTime;
        }

        private void defer(long gameTime) {
            lastAttemptGameTime = gameTime;
            nextAttemptGameTime = gameTime + FAILED_PLACEMENT_RETRY_TICKS;
        }
    }

    private static final class PlacementStats {
        private int missingTemplates;
        private int workstationMissingRotations;
        private int candidates;
        private int rejectedByFit;
        private int rejectedByPath;
    }
}
