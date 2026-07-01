package com.destroyermob.ecology.item;

import com.destroyermob.ecology.EcologyConfig;
import com.destroyermob.ecology.bee.BeekeeperAdvice;
import com.destroyermob.ecology.bee.BeeText;
import com.destroyermob.ecology.bee.ColonyData;
import com.destroyermob.ecology.bee.ColonyHealth;
import com.destroyermob.ecology.bee.ColonyHealthIssue;
import com.destroyermob.ecology.bee.EcologyBeeSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;

public class InspectionTrayItem extends Item {
    public InspectionTrayItem() {
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
        ColonyData colony = EcologyBeeSystem.colony(hive);
        ColonyHealth health = EcologyBeeSystem.colonyHealth(serverLevel, context.getClickedPos(), colony);
        long day = EcologyBeeSystem.colonyDay(serverLevel, colony);

        player.sendSystemMessage(Component.translatable(
                "message.ecology.inspection.header",
                context.getClickedPos().toShortString()).withStyle(ChatFormatting.GOLD));
        player.sendSystemMessage(Component.translatable(
                "message.ecology.inspection.health",
                BeeText.healthStatus(health.status()),
                health.score()));
        player.sendSystemMessage(Component.translatable(
                "message.ecology.inspection.focus",
                BeekeeperAdvice.focus(serverLevel, hive, colony, health)).withStyle(ChatFormatting.AQUA));
        player.sendSystemMessage(Component.translatable(
                "message.ecology.inspection.population",
                colony.queenCount(),
                colony.workerIds().size(),
                colony.droneIds().size(),
                hive.getOccupantCount()));
        if (!colony.traits().isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.ecology.inspection.traits", BeeText.traitList(colony.traits())));
        }
        for (ColonyHealthIssue issue : health.issues()) {
            player.sendSystemMessage(Component.translatable(
                    "message.ecology.inspection.issue",
                    Component.translatable("ecology.health.issue." + issue.name().toLowerCase())).withStyle(ChatFormatting.YELLOW));
        }
        if (colony.isCalmed(day) || colony.hasApiarySupport(day) || colony.hasQueenExcluder(day)) {
            player.sendSystemMessage(Component.translatable(
                    "message.ecology.inspection.treatments",
                    colony.isCalmed(day),
                    colony.hasApiarySupport(day),
                    colony.hasQueenExcluder(day)).withStyle(ChatFormatting.GRAY));
        }
        for (Component advice : BeekeeperAdvice.forHive(serverLevel, hive, colony, health)) {
            player.sendSystemMessage(Component.translatable("message.ecology.inspection.advice", advice).withStyle(ChatFormatting.AQUA));
        }
        serverLevel.playSound(null, context.getClickedPos(), SoundEvents.BEEHIVE_WORK, SoundSource.BLOCKS, 0.6F, 1.4F);
        return InteractionResult.CONSUME;
    }
}
