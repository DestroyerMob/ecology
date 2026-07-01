package com.destroyermob.ecology.item;

import com.destroyermob.ecology.EcologyConfig;
import com.destroyermob.ecology.bee.ApiaryTreatment;
import com.destroyermob.ecology.bee.EcologyBeeSystem;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;

public class ApiaryTreatmentItem extends Item {
    private final ApiaryTreatment treatment;
    private final boolean consumed;

    public ApiaryTreatmentItem(ApiaryTreatment treatment, boolean consumed) {
        super(new Item.Properties().stacksTo(consumed ? 16 : 1));
        this.treatment = treatment;
        this.consumed = consumed;
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
        if (!EcologyBeeSystem.applyApiaryTreatment(serverLevel, hive, treatment)) {
            player.displayClientMessage(Component.translatable("message.ecology.apiary_treatment.no_colony"), true);
            return InteractionResult.CONSUME;
        }
        if (consumed && !player.getAbilities().instabuild) {
            context.getItemInHand().shrink(1);
        }
        serverLevel.playSound(null, context.getClickedPos(), SoundEvents.BEEHIVE_WORK, SoundSource.BLOCKS, 0.85F, treatment == ApiaryTreatment.SMOKE ? 0.75F : 1.15F);
        player.displayClientMessage(Component.translatable("message.ecology.apiary_treatment.applied", Component.translatable("ecology.apiary_treatment." + treatment.serializedName())), true);
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.ecology.apiary_treatment." + treatment.serializedName()).withStyle(ChatFormatting.GRAY));
    }
}
