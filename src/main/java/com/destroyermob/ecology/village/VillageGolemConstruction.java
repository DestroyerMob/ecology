package com.destroyermob.ecology.village;

import com.destroyermob.ecology.Ecology;
import com.destroyermob.ecology.EcologyConfig;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.sensing.GolemSensor;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CarvedPumpkinBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class VillageGolemConstruction {
    private static final int VANILLA_GOLEM_SEARCH_RANGE = 10;
    private static final int BUILD_SITE_HORIZONTAL_RANGE = 8;
    private static final int BUILD_SITE_VERTICAL_RANGE = 4;
    private static final int RANDOM_SITE_ATTEMPTS = 96;
    private static final int NO_SITE_RETRY_TICKS = 100;
    private static final int HELP_REQUEST_TICKS = 40;
    private static final int BUILD_WORK_TICKS = 16;
    private static final int MAX_PIECE_WAIT_TICKS = 20 * 8;
    private static final int MAX_FINALIZE_WAIT_TICKS = 80;
    private static final double BUILDER_READY_DISTANCE_SQR = 2.25;
    private static final String DISPLAY_TAG = "EcologyGolemConstructionDisplay";
    private static final VillagerProfession[] DEBUG_BUILDER_PROFESSIONS = {
            VillagerProfession.MASON,
            VillagerProfession.TOOLSMITH,
            VillagerProfession.WEAPONSMITH,
            VillagerProfession.ARMORER,
            VillagerProfession.LEATHERWORKER
    };

    private static final Map<ResourceKey<Level>, List<Construction>> ACTIVE_CONSTRUCTIONS = new HashMap<>();
    private static final Set<UUID> ACTIVE_DISPLAY_IDS = new HashSet<>();

    private VillageGolemConstruction() {
    }

    public static boolean handleSpawnAttempt(ServerLevel level, Villager initiator, long gameTime, int minVillagerAmount) {
        if (!EcologyConfig.ENABLE_VILLAGER_GOLEM_CONSTRUCTION.get() || !initiator.wantsToSpawnGolem(gameTime)) {
            return false;
        }

        AABB villagerSearch = initiator.getBoundingBox().inflate(VANILLA_GOLEM_SEARCH_RANGE);
        List<Villager> nearbyVillagers = level.getEntitiesOfClass(Villager.class, villagerSearch);
        List<Villager> participants = nearbyVillagers.stream()
                .filter(villager -> villager.wantsToSpawnGolem(gameTime))
                .limit(5L)
                .collect(Collectors.toList());
        if (participants.size() < minVillagerAmount) {
            return false;
        }

        Optional<Construction> activeConstruction = activeConstructionNear(level, initiator.blockPosition());
        if (activeConstruction.isPresent()) {
            activeConstruction.get().requestHelp(initiator, participants);
            nearbyVillagers.forEach(GolemSensor::golemDetected);
            return true;
        }

        Optional<BuildSite> site = findBuildSite(level, initiator);
        if (site.isEmpty()) {
            nearbyVillagers.forEach(villager -> villager.getBrain()
                    .setMemoryWithExpiry(MemoryModuleType.GOLEM_DETECTED_RECENTLY, true, NO_SITE_RETRY_TICKS));
            debug("No safe golem construction site near {} in {}", initiator.blockPosition(), level.dimension().location());
            return true;
        }

        Construction construction = new Construction(level, site.get(), initiator, participants);
        ACTIVE_CONSTRUCTIONS.computeIfAbsent(level.dimension(), key -> new ArrayList<>()).add(construction);
        nearbyVillagers.forEach(GolemSensor::golemDetected);
        debug("Started golem construction at {} in {}", site.get().base(), level.dimension().location());
        return true;
    }

    public static void tick(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            List<Construction> constructions = ACTIVE_CONSTRUCTIONS.get(level.dimension());
            if (constructions == null || constructions.isEmpty()) {
                continue;
            }

            Iterator<Construction> iterator = constructions.iterator();
            while (iterator.hasNext()) {
                Construction construction = iterator.next();
                if (construction.tick()) {
                    iterator.remove();
                }
            }
        }
    }

    public static boolean isOrphanedConstructionDisplay(Entity entity) {
        return entity instanceof Display.BlockDisplay
                && entity.getPersistentData().getBoolean(DISPLAY_TAG)
                && !ACTIVE_DISPLAY_IDS.contains(entity.getUUID());
    }

    public static boolean hasActiveConstructionNear(ServerLevel level, BlockPos pos) {
        return activeConstructionNear(level, pos).isPresent();
    }

    public static Optional<DebugConstructionStart> startDebugConstruction(ServerLevel level, BlockPos base) {
        Optional<BuildSite> site = validSiteAt(level, base, true);
        if (site.isEmpty() || hasActiveConstructionNear(level, base)) {
            return Optional.empty();
        }

        AABB villagerSearch = AABB.encapsulatingFullBlocks(base.offset(-8, -4, -8), base.offset(8, 4, 8));
        List<Villager> participants = level.getEntitiesOfClass(Villager.class, villagerSearch).stream()
                .limit(5L)
                .collect(Collectors.toList());
        if (participants.isEmpty()) {
            return Optional.empty();
        }
        participants.forEach(villager -> prepareForGolemConstruction(level, villager));
        Construction construction = new Construction(level, site.get(), participants);
        ACTIVE_CONSTRUCTIONS.computeIfAbsent(level.dimension(), key -> new ArrayList<>()).add(construction);
        debug("Started debug golem construction at {} in {}", site.get().base(), level.dimension().location());
        return Optional.of(new DebugConstructionStart(site.get().base(), participants.size()));
    }

    public static Optional<DebugConstructionStart> spawnDebugBuildersAndStart(ServerLevel level, BlockPos base) {
        Optional<BuildSite> site = validSiteAt(level, base, true);
        if (site.isEmpty() || hasActiveConstructionNear(level, base)) {
            return Optional.empty();
        }

        List<Villager> participants = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Villager villager = EntityType.VILLAGER.create(level);
            if (villager == null) {
                continue;
            }

            BlockPos spawnPos = site.get().spawnPosForBuilder(level, i);
            villager.moveTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, 0.0F, 0.0F);
            villager.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPos), MobSpawnType.COMMAND, null);
            villager.setVillagerData(villager.getVillagerData()
                    .setType(VillagerType.byBiome(level.getBiome(spawnPos)))
                    .setProfession(DEBUG_BUILDER_PROFESSIONS[i % DEBUG_BUILDER_PROFESSIONS.length])
                    .setLevel(2));
            villager.setPersistenceRequired();
            prepareForGolemConstruction(level, villager);
            if (level.addFreshEntity(villager)) {
                participants.add(villager);
            }
        }

        if (participants.isEmpty()) {
            return Optional.empty();
        }

        Construction construction = new Construction(level, site.get(), participants);
        ACTIVE_CONSTRUCTIONS.computeIfAbsent(level.dimension(), key -> new ArrayList<>()).add(construction);
        debug("Spawned {} debug builder villagers for golem construction at {}", participants.size(), site.get().base());
        return Optional.of(new DebugConstructionStart(site.get().base(), participants.size()));
    }

    public static int primeNearbyVillagers(ServerLevel level, BlockPos center, int radius) {
        AABB area = AABB.encapsulatingFullBlocks(center.offset(-radius, -radius, -radius), center.offset(radius, radius, radius));
        List<Villager> villagers = level.getEntitiesOfClass(Villager.class, area);
        villagers.forEach(villager -> prepareForGolemConstruction(level, villager));
        return villagers.size();
    }

    private static void prepareForGolemConstruction(ServerLevel level, Villager villager) {
        villager.getBrain().setMemory(MemoryModuleType.LAST_SLEPT, level.getGameTime());
        villager.getBrain().eraseMemory(MemoryModuleType.GOLEM_DETECTED_RECENTLY);
    }

    private static Optional<Construction> activeConstructionNear(ServerLevel level, BlockPos pos) {
        List<Construction> constructions = ACTIVE_CONSTRUCTIONS.get(level.dimension());
        if (constructions == null) {
            return Optional.empty();
        }
        return constructions.stream()
                .filter(construction -> construction.site.base().distSqr(pos) <= 16.0 * 16.0)
                .findFirst();
    }

    private static Optional<BuildSite> findBuildSite(ServerLevel level, Villager initiator) {
        BlockPos origin = initiator.blockPosition();
        RandomSource random = initiator.getRandom();
        for (int attempt = 0; attempt < RANDOM_SITE_ATTEMPTS; attempt++) {
            int xOffset = random.nextInt(BUILD_SITE_HORIZONTAL_RANGE * 2 + 1) - BUILD_SITE_HORIZONTAL_RANGE;
            int zOffset = random.nextInt(BUILD_SITE_HORIZONTAL_RANGE * 2 + 1) - BUILD_SITE_HORIZONTAL_RANGE;
            int yOffset = random.nextInt(BUILD_SITE_VERTICAL_RANGE * 2 + 1) - BUILD_SITE_VERTICAL_RANGE;
            Optional<BuildSite> site = validSiteAt(level, origin.offset(xOffset, yOffset, zOffset), random.nextBoolean());
            if (site.isPresent()) {
                return site;
            }
        }

        for (int radius = 1; radius <= BUILD_SITE_HORIZONTAL_RANGE; radius++) {
            for (int xOffset = -radius; xOffset <= radius; xOffset++) {
                for (int zOffset = -radius; zOffset <= radius; zOffset++) {
                    if (Math.max(Math.abs(xOffset), Math.abs(zOffset)) != radius) {
                        continue;
                    }
                    for (int yOffset = -BUILD_SITE_VERTICAL_RANGE; yOffset <= BUILD_SITE_VERTICAL_RANGE; yOffset++) {
                        Optional<BuildSite> site = validSiteAt(level, origin.offset(xOffset, yOffset, zOffset), true);
                        if (site.isPresent()) {
                            return site;
                        }
                    }
                }
            }
        }

        return Optional.empty();
    }

    private static Optional<BuildSite> validSiteAt(ServerLevel level, BlockPos base, boolean preferX) {
        BuildSite first = new BuildSite(base, preferX ? Direction.Axis.X : Direction.Axis.Z);
        if (first.isValid(level)) {
            return Optional.of(first);
        }

        BuildSite second = new BuildSite(base, preferX ? Direction.Axis.Z : Direction.Axis.X);
        return second.isValid(level) ? Optional.of(second) : Optional.empty();
    }

    private static void debug(String message, Object... args) {
        if (EcologyConfig.DEBUG_VILLAGER_GOLEM_CONSTRUCTION.get()) {
            Ecology.LOGGER.debug(message, args);
        }
    }

    private record BuildSite(BlockPos base, Direction.Axis axis) {
        private List<ConstructionPiece> pieces() {
            BlockState iron = Blocks.IRON_BLOCK.defaultBlockState();
            BlockState pumpkin = Blocks.CARVED_PUMPKIN.defaultBlockState()
                    .setValue(CarvedPumpkinBlock.FACING, axis == Direction.Axis.X ? Direction.SOUTH : Direction.EAST);
            return List.of(
                    new ConstructionPiece(base, iron),
                    new ConstructionPiece(base.above(), iron),
                    new ConstructionPiece(negativeArm().above(), iron),
                    new ConstructionPiece(positiveArm().above(), iron),
                    new ConstructionPiece(base.above(2), pumpkin));
        }

        private boolean isValid(ServerLevel level) {
            return hasClearBlocks(level) && hasClearFinalSpace(level);
        }

        private boolean hasClearBlocks(ServerLevel level) {
            if (!level.isInWorldBounds(base) || !level.isInWorldBounds(base.above(2))) {
                return false;
            }
            if (!level.isLoaded(base) || !level.isLoaded(base.above(2))) {
                return false;
            }
            BlockPos support = base.below();
            if (!level.getBlockState(support).isFaceSturdy(level, support, Direction.UP)) {
                return false;
            }
            for (BlockPos pos : patternPositions()) {
                if (!level.isInWorldBounds(pos) || !level.isLoaded(pos) || !level.getBlockState(pos).isAir()) {
                    return false;
                }
            }
            return true;
        }

        private boolean hasClearFinalSpace(ServerLevel level) {
            return level.noCollision(volume());
        }

        private AABB volume() {
            return AABB.encapsulatingFullBlocks(negativeArm(), positiveArm().above(2));
        }

        private Set<BlockPos> patternPositions() {
            Set<BlockPos> positions = new HashSet<>();
            for (int y = 0; y <= 2; y++) {
                positions.add(base.above(y));
                positions.add(negativeArm().above(y));
                positions.add(positiveArm().above(y));
            }
            return positions;
        }

        private BlockPos negativeArm() {
            return axis == Direction.Axis.X ? base.west() : base.north();
        }

        private BlockPos positiveArm() {
            return axis == Direction.Axis.X ? base.east() : base.south();
        }

        private BlockPos spawnPosForBuilder(ServerLevel level, int index) {
            int[][] offsets = standOffsets();
            int[] offset = offsets[index % offsets.length];
            return standPosFor(level, base, index).orElse(base.offset(offset[0], 0, offset[1]));
        }

        private Optional<BlockPos> standPosFor(ServerLevel level, BlockPos focus, int index) {
            int[][] offsets = standOffsets();
            int groundY = base.getY();
            for (int offsetIndex = 0; offsetIndex < offsets.length; offsetIndex++) {
                int[] offset = offsets[(index + offsetIndex) % offsets.length];
                BlockPos candidate = new BlockPos(focus.getX() + offset[0], groundY, focus.getZ() + offset[1]);
                if (isStandable(level, candidate)) {
                    return Optional.of(candidate);
                }
            }
            return Optional.empty();
        }

        private int[][] standOffsets() {
            return axis == Direction.Axis.X
                    ? new int[][]{{0, -2}, {0, 2}, {-2, 0}, {2, 0}, {-2, -2}, {2, -2}, {-2, 2}, {2, 2}}
                    : new int[][]{{-2, 0}, {2, 0}, {0, -2}, {0, 2}, {-2, -2}, {-2, 2}, {2, -2}, {2, 2}};
        }

        private boolean isStandable(ServerLevel level, BlockPos pos) {
            return level.isInWorldBounds(pos.above())
                    && level.isLoaded(pos)
                    && level.getBlockState(pos).isAir()
                    && level.getBlockState(pos.above()).isAir()
                    && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
        }
    }

    private record ConstructionPiece(BlockPos pos, BlockState state) {
    }

    public record DebugConstructionStart(BlockPos base, int participantCount) {
    }

    private static final class Construction {
        private final ServerLevel level;
        private final BuildSite site;
        private final List<UUID> participantIds;
        private final List<Display.BlockDisplay> displays = new ArrayList<>();
        private UUID requesterId;
        private int helpRequestTicks;
        private int displayedPieces;
        private int stepTicks;
        private int workTicks;
        private int finalizeWaitTicks;

        private Construction(ServerLevel level, BuildSite site, List<Villager> participants) {
            this.level = level;
            this.site = site;
            this.participantIds = participants.stream().map(Villager::getUUID).collect(Collectors.toList());
        }

        private Construction(ServerLevel level, BuildSite site, Villager requester, List<Villager> participants) {
            this(level, site, participants);
            requestHelp(requester, participants);
        }

        private boolean tick() {
            if (!site.hasClearBlocks(level)) {
                cleanupDisplays();
                debug("Cancelled golem construction at {} because the site changed", site.base());
                return true;
            }

            List<ConstructionPiece> pieces = site.pieces();
            if (!hasLivingParticipants()) {
                cleanupDisplays();
                debug("Cancelled golem construction at {} because all builders disappeared", site.base());
                return true;
            }
            if (helpRequestTicks > 0) {
                tickHelpRequest();
                helpRequestTicks--;
                return false;
            }
            if (displayedPieces < pieces.size()) {
                tickBuildStep(pieces.get(displayedPieces), displayedPieces);
                return false;
            }

            if (!site.hasClearFinalSpace(level)) {
                if (finalizeWaitTicks++ < MAX_FINALIZE_WAIT_TICKS) {
                    guideCurrentParticipants(pieces.get(pieces.size() - 1));
                    return false;
                }
            }
            finish(pieces);
            return true;
        }

        private void requestHelp(Villager requester, List<Villager> responders) {
            requesterId = requester.getUUID();
            addParticipants(responders);
            if (!participantIds.contains(requesterId)) {
                participantIds.add(requesterId);
            }
            helpRequestTicks = Math.max(helpRequestTicks, HELP_REQUEST_TICKS);
            requester.swing(InteractionHand.MAIN_HAND);
            requester.getLookControl().setLookAt(Vec3.atCenterOf(site.base()));

            for (int index = 0; index < responders.size(); index++) {
                Villager responder = responders.get(index);
                if (responder.getUUID().equals(requesterId)) {
                    responder.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(site.base()));
                    continue;
                }
                BlockPos rallyPos = site.standPosFor(level, site.base(), index).orElse(site.base());
                responder.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(rallyPos, 0.6F, 2));
                responder.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(requester.blockPosition()));
            }
        }

        private void tickHelpRequest() {
            Villager requester = requester();
            if (requester != null) {
                requester.getLookControl().setLookAt(Vec3.atCenterOf(site.base()));
            }
        }

        private Villager requester() {
            if (requesterId == null) {
                return null;
            }
            Entity entity = level.getEntity(requesterId);
            return entity instanceof Villager villager && villager.isAlive() ? villager : null;
        }

        private void addParticipants(List<Villager> villagers) {
            for (Villager villager : villagers) {
                if (!participantIds.contains(villager.getUUID())) {
                    participantIds.add(villager.getUUID());
                }
            }
        }

        private void tickBuildStep(ConstructionPiece piece, int pieceIndex) {
            stepTicks++;
            Villager builder = builderForPiece(pieceIndex);
            if (builder == null) {
                return;
            }

            guideCurrentParticipants(piece);
            BlockPos standPos = workPosFor(piece, pieceIndex);
            boolean ready = builder.distanceToSqr(Vec3.atCenterOf(standPos)) <= BUILDER_READY_DISTANCE_SQR;
            if (ready) {
                workTicks++;
                builder.getLookControl().setLookAt(Vec3.atCenterOf(piece.pos()));
                if (workTicks == 1 || workTicks % 8 == 0) {
                    builder.swing(InteractionHand.MAIN_HAND);
                }
            } else {
                workTicks = 0;
            }

            if ((ready && workTicks >= BUILD_WORK_TICKS) || stepTicks >= MAX_PIECE_WAIT_TICKS) {
                performWork(builder, piece);
                addDisplay(piece);
                displayedPieces++;
                stepTicks = 0;
                workTicks = 0;
            }
        }

        private boolean hasLivingParticipants() {
            for (UUID participantId : participantIds) {
                Entity entity = level.getEntity(participantId);
                if (entity instanceof Villager villager && villager.isAlive()) {
                    return true;
                }
            }
            return false;
        }

        private Villager builderForPiece(int pieceIndex) {
            for (int offset = 0; offset < participantIds.size(); offset++) {
                UUID participantId = participantIds.get((pieceIndex + offset) % participantIds.size());
                Entity entity = level.getEntity(participantId);
                if (entity instanceof Villager villager && villager.isAlive()) {
                    return villager;
                }
            }
            return null;
        }

        private void guideCurrentParticipants(ConstructionPiece piece) {
            for (int index = 0; index < participantIds.size(); index++) {
                UUID participantId = participantIds.get(index);
                Entity entity = level.getEntity(participantId);
                if (entity instanceof Villager villager) {
                    guide(villager, piece, index);
                }
            }
        }

        private void guide(Villager villager, ConstructionPiece piece, int participantIndex) {
            BlockPos standPos = workPosFor(piece, participantIndex);
            villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(standPos, 0.55F, 1));
            villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(piece.pos()));
        }

        private BlockPos workPosFor(ConstructionPiece piece, int participantIndex) {
            return site.standPosFor(level, piece.pos(), participantIndex)
                    .orElse(site.base().relative(Direction.NORTH, 3));
        }

        private void performWork(Villager builder, ConstructionPiece piece) {
            builder.swing(InteractionHand.MAIN_HAND);
            builder.getLookControl().setLookAt(Vec3.atCenterOf(piece.pos()));
            if (builder.getVillagerData().getProfession().workSound() != null) {
                builder.playWorkSound();
            }
        }

        private void addDisplay(ConstructionPiece piece) {
            Display.BlockDisplay display = EntityType.BLOCK_DISPLAY.create(level);
            if (display == null) {
                return;
            }

            display.moveTo(piece.pos().getX(), piece.pos().getY(), piece.pos().getZ(), 0.0F, 0.0F);
            display.setNoGravity(true);

            CompoundTag tag = display.saveWithoutId(new CompoundTag());
            tag.put(Display.BlockDisplay.TAG_BLOCK_STATE, NbtUtils.writeBlockState(piece.state()));
            tag.putFloat(Display.TAG_VIEW_RANGE, 0.5F);
            tag.putFloat(Display.TAG_WIDTH, 1.0F);
            tag.putFloat(Display.TAG_HEIGHT, 1.0F);
            display.load(tag);
            display.getPersistentData().putBoolean(DISPLAY_TAG, true);

            ACTIVE_DISPLAY_IDS.add(display.getUUID());
            if (level.addFreshEntity(display)) {
                displays.add(display);
                playPlaceSound(piece);
            } else {
                ACTIVE_DISPLAY_IDS.remove(display.getUUID());
            }
        }

        private void playPlaceSound(ConstructionPiece piece) {
            SoundType sound = piece.state().getSoundType(level, piece.pos(), null);
            level.playSound(
                    null,
                    piece.pos(),
                    sound.getPlaceSound(),
                    SoundSource.BLOCKS,
                    (sound.getVolume() + 1.0F) / 2.0F,
                    sound.getPitch() * 0.8F);
        }

        private void finish(List<ConstructionPiece> pieces) {
            cleanupDisplays();
            if (!site.hasClearBlocks(level) || !site.hasClearFinalSpace(level)) {
                return;
            }

            AABB golemSearch = site.volume().inflate(4.0);
            Set<UUID> existingGolems = level.getEntitiesOfClass(IronGolem.class, golemSearch).stream()
                    .map(IronGolem::getUUID)
                    .collect(Collectors.toSet());
            List<BlockPos> placed = new ArrayList<>();
            for (ConstructionPiece piece : pieces) {
                if (!level.setBlock(piece.pos(), piece.state(), 3)) {
                    removePlacedBlocks(placed);
                    return;
                }
                placed.add(piece.pos());
            }

            level.getEntitiesOfClass(IronGolem.class, golemSearch).stream()
                    .filter(golem -> !existingGolems.contains(golem.getUUID()))
                    .forEach(golem -> golem.setPlayerCreated(false));
            markParticipantsGolemDetected();
            level.getEntitiesOfClass(Villager.class, golemSearch.inflate(VANILLA_GOLEM_SEARCH_RANGE))
                    .forEach(GolemSensor::golemDetected);
            removePlacedBlocks(placed);
            debug("Finished golem construction at {} in {}", site.base(), level.dimension().location());
        }

        private void markParticipantsGolemDetected() {
            for (UUID participantId : participantIds) {
                Entity entity = level.getEntity(participantId);
                if (entity instanceof Villager villager) {
                    GolemSensor.golemDetected(villager);
                }
            }
        }

        private void removePlacedBlocks(List<BlockPos> positions) {
            for (BlockPos pos : positions) {
                BlockState state = level.getBlockState(pos);
                if (state.is(Blocks.IRON_BLOCK) || state.is(Blocks.CARVED_PUMPKIN)) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }

        private void cleanupDisplays() {
            for (Display.BlockDisplay display : displays) {
                ACTIVE_DISPLAY_IDS.remove(display.getUUID());
                display.discard();
            }
            displays.clear();
        }
    }
}
