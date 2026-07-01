package com.destroyermob.ecology.item;

import com.destroyermob.ecology.EcologyConfig;
import com.destroyermob.ecology.bee.BeeDataKeys;
import com.destroyermob.ecology.bee.BeekeeperActionCheck;
import com.destroyermob.ecology.bee.EcologyBeeSystem;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;

public class QueenCellItem extends Item {
    public QueenCellItem() {
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
        if (!EcologyConfig.beeRelocationItemsEnabled()) {
            player.displayClientMessage(Component.translatable("message.ecology.bee_feature_disabled"), true);
            return InteractionResult.CONSUME;
        }

        ItemStack stack = context.getItemInHand();
        CompoundTag data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (data.contains(BeeDataKeys.COLONY, Tag.TAG_COMPOUND)) {
            BeekeeperActionCheck installCheck = EcologyBeeSystem.queenCellInstallCheck(hive, data);
            if (!installCheck.allowed()) {
                player.displayClientMessage(installCheck.message(), true);
                return InteractionResult.CONSUME;
            }
            if (!EcologyBeeSystem.installQueenCell(serverLevel, hive, context.getClickedPos(), data)) {
                player.displayClientMessage(Component.translatable("message.ecology.queen_cell.install_failed"), true);
                return InteractionResult.CONSUME;
            }
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            serverLevel.playSound(null, context.getClickedPos(), SoundEvents.BEEHIVE_ENTER, SoundSource.BLOCKS, 1.0F, 1.2F);
            player.displayClientMessage(Component.translatable("message.ecology.queen_cell.installed"), true);
            return InteractionResult.CONSUME;
        }

        BeekeeperActionCheck harvestCheck = EcologyBeeSystem.queenCellHarvestCheck(serverLevel, hive);
        if (!harvestCheck.allowed()) {
            player.displayClientMessage(harvestCheck.message(), true);
            return InteractionResult.CONSUME;
        }
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(EcologyBeeSystem.createQueenCellData(serverLevel, hive)));
        serverLevel.playSound(null, context.getClickedPos(), SoundEvents.BEEHIVE_WORK, SoundSource.BLOCKS, 0.9F, 1.25F);
        player.displayClientMessage(Component.translatable("message.ecology.queen_cell.harvested"), true);
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        CompoundTag data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!data.contains(BeeDataKeys.COLONY, Tag.TAG_COMPOUND)) {
            tooltip.add(Component.translatable("tooltip.ecology.queen_cell.empty").withStyle(ChatFormatting.GRAY));
            return;
        }
        tooltip.add(Component.translatable("tooltip.ecology.queen_cell.ready").withStyle(ChatFormatting.GOLD));
        if (data.contains(BeeDataKeys.TRAITS, Tag.TAG_LIST)) {
            tooltip.add(Component.translatable("tooltip.ecology.queen_cell.traits", data.getList(BeeDataKeys.TRAITS, Tag.TAG_STRING).size()).withStyle(ChatFormatting.DARK_GRAY));
        }
        if (data.contains(BeeDataKeys.SOURCE_HIVE, Tag.TAG_LONG)) {
            tooltip.add(Component.translatable("tooltip.ecology.queen_cell.source").withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
