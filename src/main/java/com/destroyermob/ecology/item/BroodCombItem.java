package com.destroyermob.ecology.item;

import com.destroyermob.ecology.EcologyConfig;
import com.destroyermob.ecology.bee.BeeDataKeys;
import com.destroyermob.ecology.bee.EcologyBeeSystem;
import java.util.List;
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

public class BroodCombItem extends Item {
    public BroodCombItem() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        RelocationDataTooltips.addBroodCombTooltip(stack, tooltip);
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

        CustomData customData = context.getItemInHand().getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag broodData = customData.copyTag();
        if (!broodData.getBoolean(BeeDataKeys.HAS_QUEEN)) {
            player.displayClientMessage(Component.translatable("message.ecology.brood_comb.no_queen"), true);
            return InteractionResult.CONSUME;
        }
        if (!EcologyBeeSystem.installBroodComb(serverLevel, hive, context.getClickedPos(), broodData)) {
            player.displayClientMessage(Component.translatable("message.ecology.brood_comb.hive_not_ready"), true);
            return InteractionResult.CONSUME;
        }

        if (!player.getAbilities().instabuild) {
            context.getItemInHand().shrink(1);
        }
        serverLevel.playSound(null, context.getClickedPos(), SoundEvents.BEEHIVE_WORK, SoundSource.BLOCKS, 1.0F, 1.05F);
        serverLevel.gameEvent(player, GameEvent.BLOCK_CHANGE, context.getClickedPos());
        player.displayClientMessage(Component.translatable(
                "message.ecology.brood_comb.installed",
                Math.max(0, broodData.getLong(BeeDataKeys.QUEEN_AGE_DAYS))), true);
        return InteractionResult.CONSUME;
    }
}
