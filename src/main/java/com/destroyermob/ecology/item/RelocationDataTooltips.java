package com.destroyermob.ecology.item;

import com.destroyermob.ecology.bee.BeeDataKeys;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

final class RelocationDataTooltips {
    private static final int SHORT_LINEAGE_LENGTH = 8;

    private RelocationDataTooltips() {
    }

    static void addBroodCombTooltip(ItemStack stack, List<Component> tooltip) {
        CompoundTag data = relocationData(stack);
        if (!hasColonyData(data)) {
            tooltip.add(Component.translatable("tooltip.ecology.relocation.no_data").withStyle(ChatFormatting.GRAY));
            return;
        }

        if (data.getBoolean(BeeDataKeys.HAS_QUEEN)) {
            tooltip.add(Component.translatable(
                    "tooltip.ecology.brood_comb.queen_age",
                    Math.max(0, data.getLong(BeeDataKeys.QUEEN_AGE_DAYS))).withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable("tooltip.ecology.brood_comb.no_queen").withStyle(ChatFormatting.YELLOW));
        }
        addSharedLines(data, tooltip);
    }

    static void addWorkerBeeTooltip(ItemStack stack, List<Component> tooltip) {
        CompoundTag data = relocationData(stack);
        if (!hasColonyData(data)) {
            tooltip.add(Component.translatable("tooltip.ecology.relocation.no_data").withStyle(ChatFormatting.GRAY));
            return;
        }

        tooltip.add(Component.translatable(
                "tooltip.ecology.worker_bee.stack",
                stack.getCount()).withStyle(ChatFormatting.GRAY));
        addSharedLines(data, tooltip);
    }

    private static void addSharedLines(CompoundTag data, List<Component> tooltip) {
        tooltip.add(Component.translatable(
                "tooltip.ecology.relocation.source_population",
                Math.max(0, data.getInt(BeeDataKeys.WORKER_COUNT)),
                Math.max(0, data.getInt(BeeDataKeys.DRONE_COUNT))).withStyle(ChatFormatting.DARK_GRAY));

        String lineageId = lineageId(data);
        if (!lineageId.isBlank()) {
            tooltip.add(Component.translatable(
                    "tooltip.ecology.relocation.lineage",
                    shortLineage(lineageId)).withStyle(ChatFormatting.DARK_GRAY));
        }

        ListTag traits = data.getList(BeeDataKeys.TRAITS, Tag.TAG_STRING);
        if (!traits.isEmpty()) {
            tooltip.add(Component.translatable(
                    "tooltip.ecology.relocation.traits",
                    traits.size()).withStyle(ChatFormatting.DARK_GRAY));
        }

        if (data.contains(BeeDataKeys.INBREEDING_PERCENT, Tag.TAG_INT)) {
            tooltip.add(Component.translatable(
                    "tooltip.ecology.relocation.inbreeding",
                    Math.max(0, data.getInt(BeeDataKeys.INBREEDING_PERCENT))).withStyle(ChatFormatting.DARK_GRAY));
        }

        if (data.contains(BeeDataKeys.ORIGINAL_HOME_HIVE, Tag.TAG_LONG)) {
            tooltip.add(Component.translatable(
                    "tooltip.ecology.relocation.origin",
                    formatPos(BlockPos.of(data.getLong(BeeDataKeys.ORIGINAL_HOME_HIVE)))).withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    private static CompoundTag relocationData(ItemStack stack) {
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        return customData.copyTag();
    }

    private static boolean hasColonyData(CompoundTag data) {
        return data.contains(BeeDataKeys.COLONY, Tag.TAG_COMPOUND);
    }

    private static String lineageId(CompoundTag data) {
        if (data.contains(BeeDataKeys.LINEAGE_ID, Tag.TAG_STRING)) {
            return data.getString(BeeDataKeys.LINEAGE_ID);
        }
        if (data.contains(BeeDataKeys.COLONY, Tag.TAG_COMPOUND)) {
            CompoundTag colony = data.getCompound(BeeDataKeys.COLONY);
            if (colony.contains(BeeDataKeys.LINEAGE_ID, Tag.TAG_STRING)) {
                return colony.getString(BeeDataKeys.LINEAGE_ID);
            }
        }
        return "";
    }

    private static String shortLineage(String lineageId) {
        return lineageId.length() <= SHORT_LINEAGE_LENGTH ? lineageId : lineageId.substring(0, SHORT_LINEAGE_LENGTH);
    }

    private static String formatPos(BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }
}
