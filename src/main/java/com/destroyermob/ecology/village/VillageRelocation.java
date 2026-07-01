package com.destroyermob.ecology.village;

import com.destroyermob.ecology.EcologyConfig;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BellBlock;

public final class VillageRelocation {
    private static final String STORED_VILLAGE_DIMENSION_TAG = "EcologyStoredVillageDimension";
    private static final String STORED_VILLAGE_POS_TAG = "EcologyStoredVillagePos";
    private static final int ADOPTION_SEARCH_RADIUS = 48;
    private static final int ADOPTION_DISTANCE = 18;
    private static final int GUIDE_MAX_DISTANCE = 96;
    private static final int GUIDE_WALK_INTERVAL_TICKS = 20;

    private VillageRelocation() {
    }

    public static void tickVillager(ServerLevel level, Villager villager) {
        if (!(villager instanceof VillageRelocationHolder holder) || !isRelocating(villager)) {
            return;
        }
        if (!EcologyConfig.villageEcologyEnabled() || villager.isBaby() || villager.isTrading() || villager.isSleeping()) {
            return;
        }
        Optional<UUID> guideId = relocationGuide(holder);
        Optional<GlobalPos> target = relocationTarget(holder);
        if (guideId.isEmpty() || target.isEmpty()) {
            clearRelocation(villager);
            return;
        }
        if (!target.get().dimension().equals(level.dimension())) {
            return;
        }
        if (!(level.getBlockState(target.get().pos()).getBlock() instanceof BellBlock)) {
            clearRelocation(villager);
            return;
        }
        Entity guideEntity = level.getEntity(guideId.get());
        if (!(guideEntity instanceof Player player)) {
            return;
        }
        if (villager.blockPosition().distSqr(target.get().pos()) <= ADOPTION_DISTANCE * ADOPTION_DISTANCE) {
            adoptVillage(level, player, villager, target.get().pos());
            clearRelocation(villager);
            return;
        }
        if (villager.distanceToSqr(player) > GUIDE_MAX_DISTANCE * GUIDE_MAX_DISTANCE) {
            return;
        }
        if (villager.tickCount % GUIDE_WALK_INTERVAL_TICKS == 0 && villager.distanceToSqr(player) > 9.0D) {
            villager.getBrain().setMemoryWithExpiry(MemoryModuleType.WALK_TARGET, new WalkTarget(player, 0.62F, 2), 60L);
        }
    }

    public static boolean canRecordVillage(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof BellBlock;
    }

    public static void recordVillageAnchor(ItemStack ledger, ServerLevel level, BlockPos pos, Player player) {
        CompoundTag root = ledger.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        root.putString(STORED_VILLAGE_DIMENSION_TAG, level.dimension().location().toString());
        root.putLong(STORED_VILLAGE_POS_TAG, pos.asLong());
        ledger.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
        player.displayClientMessage(Component.translatable("message.ecology.village.relocation.recorded", formatPos(pos)), true);
        level.playSound(null, pos, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 0.65F, 1.1F);
    }

    public static Optional<GlobalPos> storedVillageAnchor(ItemStack ledger) {
        CompoundTag root = ledger.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!root.contains(STORED_VILLAGE_DIMENSION_TAG, 8) || !root.contains(STORED_VILLAGE_POS_TAG, 99)) {
            return Optional.empty();
        }
        ResourceLocation dimension = ResourceLocation.tryParse(root.getString(STORED_VILLAGE_DIMENSION_TAG));
        if (dimension == null) {
            return Optional.empty();
        }
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, dimension);
        return Optional.of(GlobalPos.of(key, BlockPos.of(root.getLong(STORED_VILLAGE_POS_TAG))));
    }

    public static boolean adoptStoredVillage(ItemStack ledger, ServerLevel level, Player player, Villager villager) {
        Optional<GlobalPos> stored = storedVillageAnchor(ledger);
        if (stored.isEmpty()) {
            return false;
        }
        GlobalPos anchor = stored.get();
        if (!anchor.dimension().equals(level.dimension())) {
            player.displayClientMessage(Component.translatable("message.ecology.village.relocation.wrong_dimension"), true);
            return true;
        }
        if (villager.blockPosition().distSqr(anchor.pos()) > ADOPTION_DISTANCE * ADOPTION_DISTANCE) {
            return beginGuidedRelocation(level, player, villager, anchor);
        }
        return adoptVillage(level, player, villager, anchor.pos());
    }

    public static boolean beginGuidedRelocation(ServerLevel level, Player player, Villager villager, GlobalPos anchor) {
        if (!EcologyConfig.villageEcologyEnabled()) {
            player.displayClientMessage(Component.translatable("message.ecology.village.disabled"), true);
            return true;
        }
        if (villager.isBaby()) {
            player.displayClientMessage(Component.translatable("message.ecology.village.relocation.baby"), true);
            return true;
        }
        if (!anchor.dimension().equals(level.dimension())) {
            player.displayClientMessage(Component.translatable("message.ecology.village.relocation.wrong_dimension"), true);
            return true;
        }
        if (!(level.getBlockState(anchor.pos()).getBlock() instanceof BellBlock)) {
            player.displayClientMessage(Component.translatable("message.ecology.village.relocation.missing_bell"), true);
            return true;
        }
        if (!(villager instanceof VillageRelocationHolder holder)) {
            return false;
        }
        holder.ecology$setRelocationGuide(Optional.of(player.getUUID()));
        holder.ecology$setRelocationTarget(Optional.of(anchor));
        player.displayClientMessage(Component.translatable("message.ecology.village.relocation.following", formatPos(anchor.pos())).withStyle(ChatFormatting.GREEN), true);
        level.playSound(null, villager.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 0.75F, 1.05F);
        villager.getBrain().setMemoryWithExpiry(MemoryModuleType.WALK_TARGET, new WalkTarget(player, 0.62F, 2), 80L);
        return true;
    }

    public static boolean stopGuidedRelocation(ServerLevel level, Player player, Villager villager) {
        if (!isRelocating(villager)) {
            return false;
        }
        clearRelocation(villager);
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        player.displayClientMessage(Component.translatable("message.ecology.village.relocation.stopped"), true);
        level.playSound(null, villager.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 0.55F, 1.1F);
        return true;
    }

    public static boolean isRelocating(Villager villager) {
        return villager instanceof VillageRelocationHolder holder
                && relocationGuide(holder).isPresent()
                && relocationTarget(holder).isPresent();
    }

    public static boolean adoptVillage(ServerLevel level, Player player, Villager villager, BlockPos anchor) {
        if (!EcologyConfig.villageEcologyEnabled()) {
            player.displayClientMessage(Component.translatable("message.ecology.village.disabled"), true);
            return true;
        }
        if (villager.isBaby()) {
            player.displayClientMessage(Component.translatable("message.ecology.village.relocation.baby"), true);
            return true;
        }
        if (!(level.getBlockState(anchor).getBlock() instanceof BellBlock)) {
            player.displayClientMessage(Component.translatable("message.ecology.village.relocation.missing_bell"), true);
            return true;
        }

        GlobalPos meetingPoint = GlobalPos.of(level.dimension(), anchor.immutable());
        villager.getBrain().setMemory(MemoryModuleType.MEETING_POINT, meetingPoint);
        villager.getBrain().eraseMemory(MemoryModuleType.HOME);
        villager.getBrain().eraseMemory(MemoryModuleType.JOB_SITE);

        nearestPoi(level, anchor, PoiTypes.HOME)
                .map(home -> GlobalPos.of(level.dimension(), home))
                .ifPresent(home -> villager.getBrain().setMemory(MemoryModuleType.HOME, home));
        professionPoi(villager.getVillagerData().getProfession())
                .flatMap(poi -> nearestPoi(level, anchor, poi))
                .map(job -> GlobalPos.of(level.dimension(), job))
                .ifPresent(job -> villager.getBrain().setMemory(MemoryModuleType.JOB_SITE, job));

        VillageCurrencySystem.assignCurrency(level, villager);
        VillageSupplies.prepareTrades(villager);
        clearRelocation(villager);
        player.displayClientMessage(Component.translatable("message.ecology.village.relocation.adopted", formatPos(anchor)).withStyle(ChatFormatting.GREEN), true);
        level.playSound(null, villager.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 0.75F, 1.0F);
        return true;
    }

    private static void clearRelocation(Villager villager) {
        if (villager instanceof VillageRelocationHolder holder) {
            holder.ecology$setRelocationGuide(Optional.empty());
            holder.ecology$setRelocationTarget(Optional.empty());
        }
    }

    private static Optional<UUID> relocationGuide(VillageRelocationHolder holder) {
        return normalize(holder.ecology$getRelocationGuide());
    }

    private static Optional<GlobalPos> relocationTarget(VillageRelocationHolder holder) {
        return normalize(holder.ecology$getRelocationTarget());
    }

    private static <T> Optional<T> normalize(Optional<T> value) {
        return value == null ? Optional.empty() : value;
    }

    private static Optional<BlockPos> nearestPoi(ServerLevel level, BlockPos center, ResourceKey<PoiType> poiType) {
        return level.getPoiManager().findClosest(
                holder -> holder.is(poiType),
                center,
                ADOPTION_SEARCH_RADIUS,
                PoiManager.Occupancy.ANY);
    }

    private static Optional<ResourceKey<PoiType>> professionPoi(VillagerProfession profession) {
        if (profession == VillagerProfession.ARMORER) {
            return Optional.of(PoiTypes.ARMORER);
        }
        if (profession == VillagerProfession.BUTCHER) {
            return Optional.of(PoiTypes.BUTCHER);
        }
        if (profession == VillagerProfession.CARTOGRAPHER) {
            return Optional.of(PoiTypes.CARTOGRAPHER);
        }
        if (profession == VillagerProfession.CLERIC) {
            return Optional.of(PoiTypes.CLERIC);
        }
        if (profession == VillagerProfession.FARMER) {
            return Optional.of(PoiTypes.FARMER);
        }
        if (profession == VillagerProfession.FISHERMAN) {
            return Optional.of(PoiTypes.FISHERMAN);
        }
        if (profession == VillagerProfession.FLETCHER) {
            return Optional.of(PoiTypes.FLETCHER);
        }
        if (profession == VillagerProfession.LEATHERWORKER) {
            return Optional.of(PoiTypes.LEATHERWORKER);
        }
        if (profession == VillagerProfession.LIBRARIAN) {
            return Optional.of(PoiTypes.LIBRARIAN);
        }
        if (profession == VillagerProfession.MASON) {
            return Optional.of(PoiTypes.MASON);
        }
        if (profession == VillagerProfession.SHEPHERD) {
            return Optional.of(PoiTypes.SHEPHERD);
        }
        if (profession == VillagerProfession.TOOLSMITH) {
            return Optional.of(PoiTypes.TOOLSMITH);
        }
        if (profession == VillagerProfession.WEAPONSMITH) {
            return Optional.of(PoiTypes.WEAPONSMITH);
        }
        return Optional.empty();
    }

    private static String formatPos(BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }
}
