package com.destroyermob.ecology.village;

import com.destroyermob.ecology.EcologyConfig;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;

public final class VillageMarketStalls {
    private static final String STORED_STALL_DIMENSION_TAG = "EcologyStoredMarketStallDimension";
    private static final String STORED_STALL_POS_TAG = "EcologyStoredMarketStallPos";
    private static final int STALL_ACCESS_RADIUS = 96;

    private VillageMarketStalls() {
    }

    public static void tickVillager(ServerLevel level, Villager villager) {
        if (!EcologyConfig.villageMarketStallsEnabled()
                || villager.isBaby()
                || villager.isTrading()
                || villager.getVillagerData().getProfession() == VillagerProfession.NONE
                || villager.getVillagerData().getProfession() == VillagerProfession.NITWIT) {
            return;
        }
        int interval = EcologyConfig.VILLAGE_MARKET_STALL_WALK_INTERVAL_TICKS.get();
        if (villager.tickCount < 80 || Math.floorMod(villager.tickCount + villager.getId() * 11, interval) != 0) {
            return;
        }
        if (!isWorkTime(level)) {
            return;
        }

        assignedStall(level, villager).ifPresent(stall -> guideToStall(level, villager, stall));
    }

    public static void recordStall(ItemStack ledger, ServerLevel level, BlockPos clickedPos, Direction clickedFace, Player player) {
        BlockPos stall = bestStallPos(level, clickedPos, clickedFace);
        CompoundTag root = ledger.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        root.putString(STORED_STALL_DIMENSION_TAG, level.dimension().location().toString());
        root.putLong(STORED_STALL_POS_TAG, stall.asLong());
        ledger.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
        player.displayClientMessage(Component.translatable("message.ecology.village.stall.recorded", formatPos(stall)), true);
        level.playSound(null, stall, SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundSource.PLAYERS, 0.65F, 1.1F);
    }

    public static boolean assignStoredStall(ItemStack ledger, ServerLevel level, Player player, Villager villager) {
        Optional<GlobalPos> stored = storedStall(ledger);
        if (stored.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.ecology.village.stall.no_stored"), true);
            return true;
        }
        GlobalPos stall = stored.get();
        if (!stall.dimension().equals(level.dimension())) {
            player.displayClientMessage(Component.translatable("message.ecology.village.stall.wrong_dimension"), true);
            return true;
        }
        if (!(villager instanceof VillageMarketStallHolder holder)) {
            return false;
        }
        holder.ecology$setMarketStall(Optional.of(stall));
        String reachabilityKey = canReach(villager, stall.pos())
                ? "message.ecology.village.stall.assigned"
                : "message.ecology.village.stall.assigned_unreachable";
        player.displayClientMessage(Component.translatable(reachabilityKey, formatPos(stall.pos())).withStyle(ChatFormatting.GREEN), true);
        level.playSound(null, villager.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 0.75F, 1.0F);
        guideToStall(level, villager, stall.pos());
        return true;
    }

    public static boolean clearAssignedStall(ServerLevel level, Player player, Villager villager) {
        if (!(villager instanceof VillageMarketStallHolder holder)) {
            return false;
        }
        holder.ecology$setMarketStall(Optional.empty());
        player.displayClientMessage(Component.translatable("message.ecology.village.stall.cleared"), true);
        level.playSound(null, villager.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 0.55F, 1.1F);
        return true;
    }

    public static Optional<BlockPos> assignedStall(ServerLevel level, Villager villager) {
        if (!(villager instanceof VillageMarketStallHolder holder)) {
            return Optional.empty();
        }
        return holder.ecology$getMarketStall()
                .filter(stall -> stall.dimension().equals(level.dimension()))
                .map(GlobalPos::pos);
    }

    public static boolean canReachAssignedStall(ServerLevel level, Villager villager) {
        return assignedStall(level, villager).map(stall -> canReach(villager, stall)).orElse(false);
    }

    public static Optional<GlobalPos> storedStall(ItemStack ledger) {
        CompoundTag root = ledger.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!root.contains(STORED_STALL_DIMENSION_TAG, 8) || !root.contains(STORED_STALL_POS_TAG, 99)) {
            return Optional.empty();
        }
        ResourceLocation dimension = ResourceLocation.tryParse(root.getString(STORED_STALL_DIMENSION_TAG));
        if (dimension == null) {
            return Optional.empty();
        }
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, dimension);
        return Optional.of(GlobalPos.of(key, BlockPos.of(root.getLong(STORED_STALL_POS_TAG))));
    }

    private static void guideToStall(ServerLevel level, Villager villager, BlockPos stall) {
        if (villager.blockPosition().distSqr(stall) <= 6.25D || villager.blockPosition().distSqr(stall) > STALL_ACCESS_RADIUS * STALL_ACCESS_RADIUS) {
            return;
        }
        if (!canReach(villager, stall)) {
            return;
        }
        villager.getBrain().setMemoryWithExpiry(MemoryModuleType.WALK_TARGET, new WalkTarget(stall, 0.55F, 2), 160L);
    }

    private static boolean canReach(Villager villager, BlockPos target) {
        if (villager.blockPosition().distSqr(target) <= 9.0D) {
            return true;
        }
        if (villager.blockPosition().distSqr(target) > STALL_ACCESS_RADIUS * STALL_ACCESS_RADIUS) {
            return false;
        }
        Path path = villager.getNavigation().createPath(target, 1);
        return path != null && path.canReach();
    }

    private static BlockPos bestStallPos(ServerLevel level, BlockPos clickedPos, Direction clickedFace) {
        BlockPos relative = clickedPos.relative(clickedFace);
        if (canStandAt(level, relative)) {
            return relative.immutable();
        }
        BlockPos above = clickedPos.above();
        if (canStandAt(level, above)) {
            return above.immutable();
        }
        if (canStandAt(level, clickedPos)) {
            return clickedPos.immutable();
        }
        return relative.immutable();
    }

    private static boolean canStandAt(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()
                && !level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty();
    }

    private static boolean isWorkTime(ServerLevel level) {
        long dayTime = level.getDayTime() % 24000L;
        return dayTime >= 2000L && dayTime <= 9000L;
    }

    private static String formatPos(BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }
}
