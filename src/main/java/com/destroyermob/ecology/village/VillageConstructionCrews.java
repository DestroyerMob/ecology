package com.destroyermob.ecology.village;

import com.destroyermob.ecology.EcologyConfig;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Clearable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class VillageConstructionCrews {
    private static final int CREW_SEARCH_VERTICAL_RANGE = 16;
    private static final int CREW_SEARCH_EXTRA_RADIUS = 32;
    private static final int LOG_MERGE_RADIUS = 96;
    private static final int MAX_BLOCK_WAIT_TICKS = 20 * 8;
    private static final double BUILDER_READY_DISTANCE_SQR = 2.56D;
    private static final int STAGE_CLEAR = 0;
    private static final int STAGE_GROUND_AND_STRUCTURE = 1;
    private static final int STAGE_PLANTS_AND_DECOR = 2;
    private static final int STAGE_WORKSTATIONS = 3;
    private static final int STAGE_FLUIDS = 4;
    private static final Map<ResourceKey<Level>, Map<BlockPos, ConstructionLog>> LOGS = new HashMap<>();
    private static final Set<UUID> ACTIVE_BUILDERS = new HashSet<>();

    private VillageConstructionCrews() {
    }

    public static void tick(MinecraftServer server) {
        if (!EcologyConfig.villageConstructionCrewsEnabled()) {
            LOGS.clear();
            ACTIVE_BUILDERS.clear();
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            Map<BlockPos, ConstructionLog> logs = LOGS.get(level.dimension());
            if (logs == null || logs.isEmpty()) {
                continue;
            }

            Iterator<Map.Entry<BlockPos, ConstructionLog>> iterator = logs.entrySet().iterator();
            while (iterator.hasNext()) {
                ConstructionLog log = iterator.next().getValue();
                if (log.tick(level)) {
                    iterator.remove();
                }
            }
        }
    }

    public static boolean isBuilder(Villager villager) {
        return ACTIVE_BUILDERS.contains(villager.getUUID());
    }

    public static boolean hasJob(ServerLevel level, BlockPos villageCenter, String key) {
        ConstructionLog log = logFor(level, villageCenter, false);
        return log != null && log.hasJob(key);
    }

    public static boolean queueJob(
            ServerLevel level,
            BlockPos villageCenter,
            String key,
            BlockPos focus,
            List<BlockPlan> pieces,
            Runnable onComplete) {
        if (!EcologyConfig.villageConstructionCrewsEnabled() || pieces.isEmpty()) {
            return false;
        }
        ConstructionLog log = logFor(level, villageCenter, true);
        if (log.hasJob(key)) {
            return true;
        }
        log.add(new ConstructionJob(key, focus.immutable(), pieces, onComplete));
        return true;
    }

    public static List<BlockPlan> pathPieces(ServerLevel level, List<BlockPos> route) {
        List<BlockPlan> pieces = new ArrayList<>();
        for (BlockPos ground : route) {
            if (!level.getBlockState(ground.above()).isAir()) {
                pieces.add(BlockPlan.simple(ground.above(), Blocks.AIR.defaultBlockState()));
            }
            if (!VillageBuildSafety.isPathBlock(level.getBlockState(ground))) {
                pieces.add(BlockPlan.simple(ground, Blocks.DIRT_PATH.defaultBlockState()));
            }
        }
        return pieces;
    }

    public static List<BlockPlan> structurePieces(
            ServerLevel level,
            StructureTemplate structure,
            BlockPos origin,
            StructurePlaceSettings settings) {
        CompoundTag saved = structure.save(new CompoundTag());
        List<StructureTemplate.StructureBlockInfo> rawBlocks = rawBlocks(level, saved);
        if (rawBlocks.isEmpty()) {
            return List.of();
        }

        List<BlockPlan> pieces = new ArrayList<>();
        for (StructureTemplate.StructureBlockInfo info : StructureTemplate.processBlockInfos(level, origin, origin, settings, rawBlocks, structure)) {
            BlockState state = info.state().mirror(settings.getMirror()).rotate(settings.getRotation());
            BlockPos pos = info.pos().immutable();
            if (shouldBuild(level, pos, state)) {
                pieces.add(new BlockPlan(pos, state, info.nbt()));
            }
        }
        pieces.sort(Comparator
                .comparingInt(VillageConstructionCrews::structurePiecePriority)
                .thenComparingInt(piece -> piece.pos().getY())
                .thenComparingInt(piece -> Math.abs(piece.pos().getX() - origin.getX()) + Math.abs(piece.pos().getZ() - origin.getZ())));
        return pieces;
    }

    public record BlockPlan(BlockPos pos, BlockState state, CompoundTag nbt) {
        public static BlockPlan simple(BlockPos pos, BlockState state) {
            return new BlockPlan(pos.immutable(), state, null);
        }

        private boolean place(ServerLevel level, RandomSource random) {
            if (!canPlaceNow(level)) {
                return false;
            }
            if (state.isAir()) {
                level.removeBlock(pos, false);
                return true;
            }
            if (nbt != null) {
                Clearable.tryClear(level.getBlockEntity(pos));
                level.setBlock(pos, Blocks.BARRIER.defaultBlockState(), 20);
            }
            if (!level.setBlock(pos, state, 3)) {
                return false;
            }
            if (nbt != null && level.getBlockEntity(pos) != null) {
                CompoundTag copy = nbt.copy();
                if (level.getBlockEntity(pos) instanceof RandomizableContainer) {
                    copy.putLong("LootTableSeed", random.nextLong());
                }
                level.getBlockEntity(pos).loadWithComponents(copy, level.registryAccess());
                level.getBlockEntity(pos).setChanged();
            }
            return true;
        }

        private boolean canPlaceNow(ServerLevel level) {
            BlockState current = level.getBlockState(pos);
            if (current.equals(state)) {
                return true;
            }
            if (state.isAir()) {
                return current.isAir() || VillageBuildSafety.canReplaceForBuilding(current);
            }
            if (state.is(Blocks.DIRT_PATH)) {
                return VillageBuildSafety.canUseAsPath(level, pos);
            }
            return current.isAir()
                    || VillageBuildSafety.canReplaceForBuilding(current)
                    || VillageBuildSafety.canReplaceWorksiteGround(current);
        }
    }

    private static ConstructionLog logFor(ServerLevel level, BlockPos villageCenter, boolean create) {
        Map<BlockPos, ConstructionLog> logs = LOGS.computeIfAbsent(level.dimension(), ignored -> new HashMap<>());
        BlockPos center = canonicalLogCenter(level, villageCenter, logs);
        if (create) {
            return logs.computeIfAbsent(center, ConstructionLog::new);
        }
        return logs.get(center);
    }

    private static BlockPos canonicalLogCenter(ServerLevel level, BlockPos villageCenter, Map<BlockPos, ConstructionLog> logs) {
        BlockPos center = VillageZones.centerFor(level, villageCenter).orElse(villageCenter).immutable();
        return logs.keySet().stream()
                .filter(existing -> existing.distSqr(center) <= LOG_MERGE_RADIUS * LOG_MERGE_RADIUS)
                .min(Comparator.comparingDouble(existing -> existing.distSqr(center)))
                .orElse(center)
                .immutable();
    }

    private static List<StructureTemplate.StructureBlockInfo> rawBlocks(ServerLevel level, CompoundTag structureTag) {
        ListTag palette = structureTag.getList("palette", Tag.TAG_COMPOUND);
        ListTag blocks = structureTag.getList("blocks", Tag.TAG_COMPOUND);
        if (palette.isEmpty() || blocks.isEmpty()) {
            return List.of();
        }

        HolderGetter<Block> blockGetter = level.holderLookup(Registries.BLOCK);
        List<BlockState> states = new ArrayList<>();
        for (int i = 0; i < palette.size(); i++) {
            states.add(NbtUtils.readBlockState(blockGetter, palette.getCompound(i)));
        }

        List<StructureTemplate.StructureBlockInfo> raw = new ArrayList<>();
        for (int i = 0; i < blocks.size(); i++) {
            CompoundTag blockTag = blocks.getCompound(i);
            int stateIndex = blockTag.getInt("state");
            if (stateIndex < 0 || stateIndex >= states.size()) {
                continue;
            }
            ListTag posTag = blockTag.getList("pos", Tag.TAG_INT);
            if (posTag.size() < 3) {
                continue;
            }
            CompoundTag nbt = blockTag.contains("nbt", Tag.TAG_COMPOUND) ? blockTag.getCompound("nbt").copy() : null;
            raw.add(new StructureTemplate.StructureBlockInfo(
                    new BlockPos(posTag.getInt(0), posTag.getInt(1), posTag.getInt(2)),
                    states.get(stateIndex),
                    nbt));
        }
        return raw;
    }

    private static boolean shouldBuild(ServerLevel level, BlockPos pos, BlockState state) {
        if (!state.isAir()) {
            return true;
        }
        BlockState current = level.getBlockState(pos);
        return !current.isAir() && VillageBuildSafety.canReplaceForBuilding(current);
    }

    private static int structurePiecePriority(BlockPlan piece) {
        return buildStage(piece);
    }

    private static int buildStage(BlockPlan piece) {
        BlockState state = piece.state();
        if (state.isAir()) {
            return STAGE_CLEAR;
        }
        if (!state.getFluidState().isEmpty()) {
            return STAGE_FLUIDS;
        }
        if (VillageBuildSafety.isProvisionableWorkstation(state.getBlock())) {
            return STAGE_WORKSTATIONS;
        }
        if (state.getBlock() instanceof CropBlock || state.is(BlockTags.FLOWERS) || state.is(BlockTags.SAPLINGS)) {
            return STAGE_PLANTS_AND_DECOR;
        }
        return STAGE_GROUND_AND_STRUCTURE;
    }

    private static List<Villager> chooseCrew(ServerLevel level, BlockPos center) {
        int radius = EcologyConfig.VILLAGE_ECOLOGY_RADIUS.get() + CREW_SEARCH_EXTRA_RADIUS;
        AABB area = AABB.encapsulatingFullBlocks(
                center.offset(-radius, -CREW_SEARCH_VERTICAL_RANGE, -radius),
                center.offset(radius, CREW_SEARCH_VERTICAL_RANGE, radius));
        return level.getEntitiesOfClass(Villager.class, area, villager -> villager.isAlive() && !villager.isBaby())
                .stream()
                .filter(villager -> !ACTIVE_BUILDERS.contains(villager.getUUID()))
                .sorted(Comparator.comparingInt(VillageConstructionCrews::crewPriority)
                        .thenComparingDouble(villager -> villager.blockPosition().distSqr(center)))
                .limit(EcologyConfig.villageConstructionCrewMaxSize())
                .toList();
    }

    private static int crewPriority(Villager villager) {
        VillagerProfession profession = villager.getVillagerData().getProfession();
        if (profession == VillagerProfession.NITWIT) {
            return 0;
        }
        return profession == VillagerProfession.NONE ? 1 : 2;
    }

    private static boolean canStandAt(ServerLevel level, BlockPos pos) {
        return level.hasChunkAt(pos)
                && level.hasChunkAt(pos.above())
                && level.hasChunkAt(pos.below())
                && level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()
                && !level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty();
    }

    private static Optional<BlockPos> standPosFor(ServerLevel level, BlockPos focus, int index) {
        for (int radius = 1; radius <= 4; radius++) {
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                Direction side = index % 2 == 0 ? direction.getClockWise() : direction.getCounterClockWise();
                for (int sideOffset = -1; sideOffset <= 1; sideOffset++) {
                    for (int yOffset = 1; yOffset >= -2; yOffset--) {
                        BlockPos candidate = focus.relative(direction, radius).relative(side, sideOffset).offset(0, yOffset, 0);
                        if (canStandAt(level, candidate)) {
                            return Optional.of(candidate.immutable());
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    private record ConstructionJob(String key, BlockPos focus, List<BlockPlan> pieces, Runnable onComplete) {
    }

    private static final class ConstructionLog {
        private final BlockPos center;
        private final Queue<ConstructionJob> jobs = new ArrayDeque<>();
        private Crew activeCrew;

        private ConstructionLog(BlockPos center) {
            this.center = center.immutable();
        }

        private boolean hasJob(String key) {
            return activeCrew != null && activeCrew.hasJob(key) || jobs.stream().anyMatch(job -> job.key().equals(key));
        }

        private void add(ConstructionJob job) {
            jobs.add(job);
        }

        private boolean tick(ServerLevel level) {
            if (activeCrew == null && !jobs.isEmpty()) {
                List<Villager> villagers = chooseCrew(level, center);
                if (!villagers.isEmpty()) {
                    activeCrew = new Crew(center, villagers);
                }
            }
            if (activeCrew != null) {
                if (jobs.isEmpty() || activeCrew.tick(level, jobs)) {
                    activeCrew.release(level);
                    activeCrew = null;
                }
            }
            return activeCrew == null && jobs.isEmpty();
        }
    }

    private static final class Crew {
        private final BlockPos center;
        private final List<UUID> memberIds;
        private String activeJobKey = "";
        private final Map<UUID, Integer> assignments = new HashMap<>();
        private final Map<UUID, Integer> assignmentWaitTicks = new HashMap<>();
        private final Map<UUID, Integer> assignmentWorkTicks = new HashMap<>();

        private Crew(BlockPos center, List<Villager> members) {
            this.center = center.immutable();
            this.memberIds = members.stream().map(Villager::getUUID).toList();
            ACTIVE_BUILDERS.addAll(memberIds);
        }

        private boolean hasJob(String key) {
            return activeJobKey.equals(key);
        }

        private boolean tick(ServerLevel level, Queue<ConstructionJob> jobs) {
            if (!hasLivingMember(level)) {
                return true;
            }
            ConstructionJob job = jobs.peek();
            if (job == null) {
                return true;
            }
            if (!activeJobKey.equals(job.key())) {
                startJob(job.key());
            }

            pruneAssignments(level, job);
            assignAvailablePieces(level, job);
            if (jobFinished(level, job)) {
                completeJob(jobs, job);
                return false;
            }

            for (Map.Entry<UUID, Integer> assignment : new ArrayList<>(assignments.entrySet())) {
                Integer currentAssignment = assignments.get(assignment.getKey());
                if (currentAssignment != null && currentAssignment.equals(assignment.getValue())) {
                    tickAssignedPiece(level, job, assignment.getKey(), assignment.getValue());
                }
            }

            if (jobFinished(level, job)) {
                completeJob(jobs, job);
                return false;
            }
            return false;
        }

        private void startJob(String key) {
            activeJobKey = key;
            assignments.clear();
            assignmentWaitTicks.clear();
            assignmentWorkTicks.clear();
        }

        private void completeJob(Queue<ConstructionJob> jobs, ConstructionJob job) {
            jobs.remove();
            job.onComplete().run();
            activeJobKey = "";
            assignments.clear();
            assignmentWaitTicks.clear();
            assignmentWorkTicks.clear();
        }

        private boolean jobFinished(ServerLevel level, ConstructionJob job) {
            return assignments.isEmpty() && nextAssignablePiece(level, job) < 0;
        }

        private void pruneAssignments(ServerLevel level, ConstructionJob job) {
            Iterator<Map.Entry<UUID, Integer>> iterator = assignments.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<UUID, Integer> assignment = iterator.next();
                UUID memberId = assignment.getKey();
                int assignedPieceIndex = assignment.getValue();
                Entity entity = level.getEntity(memberId);
                if (!(entity instanceof Villager villager)
                        || !villager.isAlive()
                        || assignedPieceIndex >= job.pieces().size()
                        || alreadyPlaced(level, job.pieces().get(assignedPieceIndex))) {
                    iterator.remove();
                    clearAssignmentState(memberId);
                }
            }
        }

        private void assignAvailablePieces(ServerLevel level, ConstructionJob job) {
            for (UUID memberId : memberIds) {
                if (assignments.containsKey(memberId)) {
                    continue;
                }
                Entity entity = level.getEntity(memberId);
                if (!(entity instanceof Villager villager) || !villager.isAlive()) {
                    continue;
                }
                int assignedPieceIndex = nextAssignablePiece(level, job);
                if (assignedPieceIndex < 0) {
                    return;
                }
                assignments.put(memberId, assignedPieceIndex);
                assignmentWaitTicks.put(memberId, 0);
                assignmentWorkTicks.put(memberId, 0);
            }
        }

        private int nextAssignablePiece(ServerLevel level, ConstructionJob job) {
            int activeStage = nextIncompleteStage(level, job);
            if (activeStage < 0) {
                return -1;
            }
            for (int candidate = 0; candidate < job.pieces().size(); candidate++) {
                BlockPlan piece = job.pieces().get(candidate);
                if (!assignments.containsValue(candidate)
                        && !alreadyPlaced(level, piece)
                        && buildStage(piece) == activeStage) {
                    return candidate;
                }
            }
            return -1;
        }

        private int nextIncompleteStage(ServerLevel level, ConstructionJob job) {
            int stage = Integer.MAX_VALUE;
            for (BlockPlan piece : job.pieces()) {
                if (!alreadyPlaced(level, piece)) {
                    stage = Math.min(stage, buildStage(piece));
                }
            }
            return stage == Integer.MAX_VALUE ? -1 : stage;
        }

        private void tickAssignedPiece(ServerLevel level, ConstructionJob job, UUID memberId, int assignedPieceIndex) {
            Entity entity = level.getEntity(memberId);
            if (!(entity instanceof Villager builder) || !builder.isAlive() || assignedPieceIndex >= job.pieces().size()) {
                clearAssignment(memberId);
                return;
            }

            BlockPlan piece = job.pieces().get(assignedPieceIndex);
            if (alreadyPlaced(level, piece)) {
                clearAssignment(memberId);
                return;
            }

            int memberIndex = Math.max(0, memberIds.indexOf(memberId));
            guideMember(level, builder, piece, memberIndex);
            BlockPos standPos = standPosFor(level, piece.pos(), memberIndex).orElse(center);
            boolean ready = builder.distanceToSqr(Vec3.atCenterOf(standPos)) <= BUILDER_READY_DISTANCE_SQR;
            int waitTicks = assignmentWaitTicks.merge(memberId, 1, Integer::sum);
            int workTicks = 0;
            if (ready) {
                workTicks = assignmentWorkTicks.merge(memberId, 1, Integer::sum);
                builder.getLookControl().setLookAt(Vec3.atCenterOf(piece.pos()));
                if (workTicks == 1 || workTicks % 8 == 0) {
                    builder.swing(InteractionHand.MAIN_HAND);
                }
            } else {
                assignmentWorkTicks.put(memberId, 0);
            }

            if ((ready && workTicks >= EcologyConfig.villageConstructionBlockWorkTicks()) || waitTicks >= MAX_BLOCK_WAIT_TICKS) {
                placePiece(level, builder, piece);
                clearAssignment(memberId);
            }
        }

        private void release(ServerLevel level) {
            for (UUID memberId : memberIds) {
                ACTIVE_BUILDERS.remove(memberId);
                Entity entity = level.getEntity(memberId);
                if (entity instanceof Villager villager) {
                    villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
                    villager.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
                }
            }
        }

        private boolean hasLivingMember(ServerLevel level) {
            return memberIds.stream()
                    .map(level::getEntity)
                    .anyMatch(entity -> entity instanceof Villager villager && villager.isAlive());
        }

        private void guideMember(ServerLevel level, Villager villager, BlockPlan piece, int index) {
            BlockPos standPos = standPosFor(level, piece.pos(), index).orElse(center);
            villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(standPos, 0.58F, 1));
            villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(piece.pos()));
        }

        private void clearAssignment(UUID memberId) {
            assignments.remove(memberId);
            clearAssignmentState(memberId);
        }

        private void clearAssignmentState(UUID memberId) {
            assignmentWaitTicks.remove(memberId);
            assignmentWorkTicks.remove(memberId);
        }

        private void placePiece(ServerLevel level, Villager builder, BlockPlan piece) {
            builder.swing(InteractionHand.MAIN_HAND);
            builder.getLookControl().setLookAt(Vec3.atCenterOf(piece.pos()));
            if (builder.getVillagerData().getProfession().workSound() != null) {
                builder.playWorkSound();
            }
            if (piece.place(level, builder.getRandom()) && !piece.state().isAir()) {
                SoundType sound = piece.state().getSoundType(level, piece.pos(), null);
                level.playSound(
                        null,
                        piece.pos(),
                        sound.getPlaceSound(),
                        SoundSource.BLOCKS,
                        (sound.getVolume() + 1.0F) / 2.0F,
                        sound.getPitch() * 0.8F);
            }
        }

        private boolean alreadyPlaced(ServerLevel level, BlockPlan piece) {
            return level.getBlockState(piece.pos()).equals(piece.state());
        }
    }
}
