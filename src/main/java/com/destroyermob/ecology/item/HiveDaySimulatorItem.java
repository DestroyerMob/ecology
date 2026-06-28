package com.destroyermob.ecology.item;

import com.destroyermob.ecology.EcologyConfig;
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
import net.minecraft.world.level.gameevent.GameEvent;

public class HiveDaySimulatorItem extends Item {
    public HiveDaySimulatorItem() {
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
        if (!EcologyConfig.beeRelocationItemsEnabled()) {
            if (player != null) {
                player.displayClientMessage(Component.translatable("message.ecology.bee_feature_disabled"), true);
            }
            return InteractionResult.CONSUME;
        }

        EcologyBeeSystem.HiveDaySimulationResult result = EcologyBeeSystem.simulateHiveDay(serverLevel, hive);
        if (player != null) {
            Component message = result.colonySimulated()
                    ? Component.translatable(
                            "message.ecology.hive_day_simulated",
                            result.simulatedDay(),
                            result.storedBeesAdvanced(),
                            result.beesAged(),
                            result.workersReadied())
                    : Component.translatable(
                            "message.ecology.hive_day_simulated_no_colony",
                            result.storedBeesAdvanced(),
                            result.beesAged(),
                            result.workersReadied());
            player.displayClientMessage(message, true);
            player.getCooldowns().addCooldown(this, 10);
        }

        serverLevel.playSound(null, context.getClickedPos(), SoundEvents.BEEHIVE_WORK, SoundSource.BLOCKS, 1.0F, 1.0F);
        serverLevel.gameEvent(player, GameEvent.BLOCK_CHANGE, context.getClickedPos());
        return InteractionResult.CONSUME;
    }
}
