package com.destroyermob.ecology.item;

import com.destroyermob.ecology.EcologyConfig;
import com.destroyermob.ecology.bee.BeeRole;
import com.destroyermob.ecology.bee.ColonyData;
import com.destroyermob.ecology.bee.EcologyBeeSystem;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.gameevent.GameEvent;

public class CapturedWorkerBeeItem extends Item {
    public CapturedWorkerBeeItem() {
        super(new Item.Properties().stacksTo(64));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        RelocationDataTooltips.addWorkerBeeTooltip(stack, tooltip);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (!level.getBlockState(context.getClickedPos()).is(Blocks.BEEHIVE)) {
            return InteractionResult.PASS;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }
        Player player = context.getPlayer();
        if (player == null || !(level.getBlockEntity(context.getClickedPos()) instanceof BeehiveBlockEntity hive)) {
            return InteractionResult.PASS;
        }
        if (!EcologyConfig.beeRelocationItemsEnabled()) {
            player.displayClientMessage(Component.translatable("message.ecology.bee_feature_disabled"), true);
            return InteractionResult.CONSUME;
        }

        ColonyData colony = EcologyBeeSystem.colony(hive);
        if (colony.queenId() == null) {
            player.displayClientMessage(Component.translatable("message.ecology.worker_bee.needs_brood"), true);
            return InteractionResult.CONSUME;
        }
        ItemStack stack = context.getItemInHand();
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag broodData = customData.copyTag();
        BlockPos originalHome = EcologyBeeSystem.originalHomeHive(broodData);
        long workerBirthDay = EcologyBeeSystem.takeWorkerBirthDay(broodData, EcologyBeeSystem.day(serverLevel));
        if (!EcologyBeeSystem.installStoredBee(serverLevel, hive, context.getClickedPos(), BeeRole.WORKER, workerBirthDay, originalHome)) {
            player.displayClientMessage(Component.translatable("message.ecology.worker_bee.hive_full"), true);
            return InteractionResult.CONSUME;
        }

        if (!player.getAbilities().instabuild) {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(broodData));
            stack.shrink(1);
        }
        serverLevel.playSound(null, context.getClickedPos(), SoundEvents.BEEHIVE_ENTER, SoundSource.BLOCKS, 1.0F, 1.0F);
        serverLevel.gameEvent(player, GameEvent.BLOCK_CHANGE, context.getClickedPos());
        player.displayClientMessage(Component.translatable("message.ecology.worker_bee.installed"), true);
        return InteractionResult.CONSUME;
    }
}
