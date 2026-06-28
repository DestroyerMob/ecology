package com.destroyermob.ecology.bee;

import com.destroyermob.ecology.EcologyConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gameevent.GameEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

public class BeeNestSealingEvents {
    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getItemStack().is(Items.HONEYCOMB) || !event.getLevel().getBlockState(event.getPos()).is(Blocks.BEE_NEST)) {
            return;
        }
        if (!EcologyConfig.beeRelocationItemsEnabled()) {
            return;
        }

        Level level = event.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) {
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
            return;
        }

        Player player = event.getEntity();
        if (!EcologyBeeSystem.sealCutOutNest(serverLevel, event.getPos())) {
            player.displayClientMessage(Component.translatable("message.ecology.honeycomb.cutout_required"), true);
            event.setCancellationResult(InteractionResult.CONSUME);
            event.setCanceled(true);
            return;
        }

        if (!player.getAbilities().instabuild) {
            event.getItemStack().shrink(1);
        }
        serverLevel.playSound(null, event.getPos(), SoundEvents.HONEY_BLOCK_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
        serverLevel.gameEvent(player, GameEvent.BLOCK_CHANGE, event.getPos());
        player.displayClientMessage(Component.translatable("message.ecology.honeycomb.sealed"), true);
        event.setCancellationResult(InteractionResult.CONSUME);
        event.setCanceled(true);
    }
}
