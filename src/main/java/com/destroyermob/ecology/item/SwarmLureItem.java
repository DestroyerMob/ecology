package com.destroyermob.ecology.item;

import com.destroyermob.ecology.EcologyConfig;
import com.destroyermob.ecology.bee.BeekeeperActionCheck;
import com.destroyermob.ecology.bee.EcologyBeeSystem;
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

public class SwarmLureItem extends Item {
    public SwarmLureItem() {
        super(new Item.Properties().stacksTo(16));
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
        if (!EcologyConfig.swarmingEnabled()) {
            player.displayClientMessage(Component.translatable("message.ecology.bee_feature_disabled"), true);
            return InteractionResult.CONSUME;
        }
        BeekeeperActionCheck swarmCheck = EcologyBeeSystem.swarmLureCheck(serverLevel, hive);
        if (!swarmCheck.allowed()) {
            player.displayClientMessage(swarmCheck.message(), true);
            return InteractionResult.CONSUME;
        }
        if (!EcologyBeeSystem.forceSwarm(serverLevel, hive)) {
            player.displayClientMessage(Component.translatable("message.ecology.swarm_lure.failed"), true);
            return InteractionResult.CONSUME;
        }
        if (!player.getAbilities().instabuild) {
            context.getItemInHand().shrink(1);
        }
        serverLevel.playSound(null, context.getClickedPos(), SoundEvents.BEEHIVE_WORK, SoundSource.BLOCKS, 1.0F, 1.45F);
        player.displayClientMessage(Component.translatable("message.ecology.swarm_lure.success"), true);
        return InteractionResult.CONSUME;
    }
}
