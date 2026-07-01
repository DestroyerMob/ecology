package com.destroyermob.ecology.item;

import com.destroyermob.ecology.EcologyConfig;
import com.destroyermob.ecology.bee.BeeDataKeys;
import com.destroyermob.ecology.bee.BeeText;
import com.destroyermob.ecology.bee.ColonyData;
import com.destroyermob.ecology.bee.ColonyHealth;
import com.destroyermob.ecology.bee.ColonyHealthIssue;
import com.destroyermob.ecology.bee.EcologyBeeSystem;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;

public class BeekeepersJournalItem extends Item {
    private static final int MAX_ENTRIES = 12;
    private static final int CHAT_ENTRY_COUNT = 5;

    public BeekeepersJournalItem() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (!(level.getBlockEntity(context.getClickedPos()) instanceof BeehiveBlockEntity hive)) {
            return InteractionResult.PASS;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }

        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        if (!EcologyConfig.hiveHealthEnabled()) {
            player.displayClientMessage(Component.translatable("message.ecology.bee_feature_disabled"), true);
            return InteractionResult.CONSUME;
        }

        EcologyBeeSystem.tickHiveColony(serverLevel, hive);
        CompoundTag entry = createEntry(serverLevel, context.getClickedPos(), hive);
        recordEntry(context.getItemInHand(), entry);
        serverLevel.playSound(null, context.getClickedPos(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.75F, 1.15F);
        player.displayClientMessage(Component.translatable(
                "message.ecology.journal.recorded",
                formatPos(context.getClickedPos()),
                healthStatus(entry),
                entry.getInt(BeeDataKeys.HEALTH_SCORE)), true);
        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) {
            return InteractionResultHolder.sidedSuccess(stack, true);
        }

        ListTag entries = entries(stack);
        if (entries.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.ecology.journal.empty"), true);
            return InteractionResultHolder.consume(stack);
        }

        player.sendSystemMessage(Component.translatable("message.ecology.journal.header", Math.min(entries.size(), CHAT_ENTRY_COUNT), entries.size()));
        for (int index = 0; index < Math.min(entries.size(), CHAT_ENTRY_COUNT); index++) {
            CompoundTag entry = entries.getCompound(index);
            player.sendSystemMessage(Component.translatable(
                    "message.ecology.journal.entry",
                    formatPos(BlockPos.of(entry.getLong(BeeDataKeys.HIVE))),
                    healthStatus(entry),
                    entry.getInt(BeeDataKeys.HEALTH_SCORE),
                    entry.getInt(BeeDataKeys.QUEENS),
                    entry.getInt(BeeDataKeys.WORKERS),
                    entry.getInt(BeeDataKeys.DRONES),
                    entry.getLong(BeeDataKeys.DAY)));
            if (entry.contains(BeeDataKeys.TRAITS, Tag.TAG_LIST) && !entry.getList(BeeDataKeys.TRAITS, Tag.TAG_STRING).isEmpty()) {
                player.sendSystemMessage(Component.translatable("message.ecology.journal.entry_traits", BeeText.traitList(entry.getList(BeeDataKeys.TRAITS, Tag.TAG_STRING))).withStyle(ChatFormatting.GRAY));
            }
            if (entry.getBoolean(BeeDataKeys.SWARM_READY)) {
                player.sendSystemMessage(Component.translatable("message.ecology.journal.entry_swarm_ready").withStyle(ChatFormatting.YELLOW));
            }
        }
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        ListTag entries = entries(stack);
        if (entries.isEmpty()) {
            tooltip.add(Component.translatable("tooltip.ecology.journal.empty").withStyle(ChatFormatting.GRAY));
            return;
        }

        CompoundTag latest = entries.getCompound(0);
        tooltip.add(Component.translatable("tooltip.ecology.journal.entries", entries.size()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(
                "tooltip.ecology.journal.latest",
                formatPos(BlockPos.of(latest.getLong(BeeDataKeys.HIVE))),
                healthStatus(latest),
                latest.getInt(BeeDataKeys.HEALTH_SCORE)).withStyle(BeeText.healthStyle(latest.getString(BeeDataKeys.HEALTH_STATUS))));
        tooltip.add(Component.translatable(
                "tooltip.ecology.journal.population",
                latest.getInt(BeeDataKeys.QUEENS),
                latest.getInt(BeeDataKeys.WORKERS),
                latest.getInt(BeeDataKeys.DRONES)).withStyle(ChatFormatting.DARK_GRAY));
        if (latest.contains(BeeDataKeys.TRAITS, Tag.TAG_LIST) && !latest.getList(BeeDataKeys.TRAITS, Tag.TAG_STRING).isEmpty()) {
            tooltip.add(Component.translatable(
                    "tooltip.ecology.journal.traits",
                    BeeText.traitList(latest.getList(BeeDataKeys.TRAITS, Tag.TAG_STRING))).withStyle(ChatFormatting.DARK_GRAY));
        }

        ListTag issues = latest.getList(BeeDataKeys.ISSUES, Tag.TAG_STRING);
        if (!issues.isEmpty()) {
            tooltip.add(Component.translatable(
                    "tooltip.ecology.journal.issue",
                    Component.translatable("ecology.health.issue." + issues.getString(0))).withStyle(ChatFormatting.YELLOW));
        }
    }

    private static CompoundTag createEntry(ServerLevel level, BlockPos pos, BeehiveBlockEntity hive) {
        ColonyData colony = EcologyBeeSystem.colony(hive);
        ColonyHealth health = EcologyBeeSystem.colonyHealth(level, pos, colony);
        CompoundTag entry = new CompoundTag();
        entry.putString(BeeDataKeys.DIMENSION, level.dimension().location().toString());
        entry.putLong(BeeDataKeys.HIVE, pos.asLong());
        entry.putLong(BeeDataKeys.DAY, EcologyBeeSystem.day(level));
        entry.putInt(BeeDataKeys.HEALTH_SCORE, health.score());
        entry.putString(BeeDataKeys.HEALTH_STATUS, health.status().name().toLowerCase());
        entry.putInt(BeeDataKeys.QUEENS, colony.queenCount());
        entry.putInt(BeeDataKeys.WORKERS, colony.workerIds().size());
        entry.putInt(BeeDataKeys.DRONES, colony.droneIds().size());
        entry.putInt(BeeDataKeys.OCCUPANTS, hive.getOccupantCount());
        entry.putInt(BeeDataKeys.INBREEDING_PERCENT, (int) Math.round(colony.inbreedingCoefficient() * 100.0));
        entry.put(BeeDataKeys.TRAITS, EcologyBeeSystem.traitNames(colony));
        long day = EcologyBeeSystem.colonyDay(level, colony);
        entry.putBoolean(BeeDataKeys.CALMED, colony.isCalmed(day));
        entry.putBoolean(BeeDataKeys.SUPPORTED, colony.hasApiarySupport(day));
        entry.putBoolean(BeeDataKeys.QUEEN_EXCLUDER, colony.hasQueenExcluder(day));
        entry.putBoolean(BeeDataKeys.SWARM_READY, health.issues().contains(ColonyHealthIssue.SWARM_READY));
        ListTag issues = new ListTag();
        for (ColonyHealthIssue issue : health.issues()) {
            issues.add(StringTag.valueOf(issue.name().toLowerCase()));
        }
        entry.put(BeeDataKeys.ISSUES, issues);
        return entry;
    }

    private static void recordEntry(ItemStack stack, CompoundTag entry) {
        CompoundTag root = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        ListTag existingEntries = root.getList(BeeDataKeys.ENTRIES, Tag.TAG_COMPOUND);
        ListTag nextEntries = new ListTag();
        nextEntries.add(entry);
        String dimension = entry.getString(BeeDataKeys.DIMENSION);
        long hive = entry.getLong(BeeDataKeys.HIVE);

        for (int index = 0; index < existingEntries.size() && nextEntries.size() < MAX_ENTRIES; index++) {
            CompoundTag existing = existingEntries.getCompound(index);
            if (dimension.equals(existing.getString(BeeDataKeys.DIMENSION)) && hive == existing.getLong(BeeDataKeys.HIVE)) {
                continue;
            }
            nextEntries.add(existing);
        }
        root.put(BeeDataKeys.ENTRIES, nextEntries);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
    }

    private static ListTag entries(ItemStack stack) {
        CompoundTag root = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return root.getList(BeeDataKeys.ENTRIES, Tag.TAG_COMPOUND);
    }

    private static Component healthStatus(CompoundTag entry) {
        return BeeText.healthStatus(entry.getString(BeeDataKeys.HEALTH_STATUS));
    }

    private static String formatPos(BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }
}
