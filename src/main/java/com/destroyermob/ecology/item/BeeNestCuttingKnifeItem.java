package com.destroyermob.ecology.item;

import com.destroyermob.ecology.EcologyConfig;
import com.destroyermob.ecology.bee.EcologyBeeSystem;
import com.destroyermob.ecology.registry.EcologyItems;
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
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.gameevent.GameEvent;

public class BeeNestCuttingKnifeItem extends Item {
    private static final int SMOKE_CHECK_DEPTH = 5;

    public BeeNestCuttingKnifeItem() {
        super(new Item.Properties().stacksTo(1).durability(128));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (!level.getBlockState(pos).is(Blocks.BEE_NEST)) {
            return InteractionResult.PASS;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }

        Player player = context.getPlayer();
        if (player == null || !(level.getBlockEntity(pos) instanceof BeehiveBlockEntity hive)) {
            return InteractionResult.PASS;
        }
        if (!EcologyConfig.beeRelocationItemsEnabled()) {
            player.displayClientMessage(Component.translatable("message.ecology.bee_feature_disabled"), true);
            return InteractionResult.CONSUME;
        }
        if (!isNight(serverLevel)) {
            player.displayClientMessage(Component.translatable("message.ecology.cutout.night_required"), true);
            return InteractionResult.CONSUME;
        }
        if (!isSmoked(serverLevel, pos)) {
            player.displayClientMessage(Component.translatable("message.ecology.cutout.smoke_required"), true);
            return InteractionResult.CONSUME;
        }

        CompoundTag broodData = EcologyBeeSystem.createBroodCombData(serverLevel, hive);
        int workers = Math.max(0, broodData.getInt("WorkerCount"));
        if (!broodData.getBoolean("HasQueen") && workers <= 0 && hive.getOccupantCount() <= 0) {
            player.displayClientMessage(Component.translatable("message.ecology.cutout.empty"), true);
            return InteractionResult.CONSUME;
        }

        ItemStack broodComb = new ItemStack(EcologyItems.BROOD_COMB.get());
        broodComb.set(DataComponents.CUSTOM_DATA, CustomData.of(broodData));
        giveOrDrop(player, broodComb);
        giveWorkers(player, workers, broodData);

        EcologyBeeSystem.clearCutOutNest(hive);
        BlockState state = level.getBlockState(pos);
        if (state.hasProperty(BeehiveBlock.HONEY_LEVEL)) {
            serverLevel.setBlock(pos, state.setValue(BeehiveBlock.HONEY_LEVEL, 0), 3);
        }
        serverLevel.playSound(null, pos, SoundEvents.BEEHIVE_WORK, SoundSource.BLOCKS, 1.0F, 0.85F);
        serverLevel.gameEvent(player, GameEvent.BLOCK_CHANGE, pos);
        player.displayClientMessage(Component.translatable("message.ecology.cutout.success", workers), true);
        return InteractionResult.CONSUME;
    }

    private static boolean isNight(ServerLevel level) {
        long dayTime = Math.floorMod(level.getDayTime(), 24000L);
        return dayTime >= 13000L && dayTime <= 23000L;
    }

    private static boolean isSmoked(Level level, BlockPos nestPos) {
        for (int yOffset = 1; yOffset <= SMOKE_CHECK_DEPTH; yOffset++) {
            BlockState state = level.getBlockState(nestPos.below(yOffset));
            if (state.getBlock() instanceof CampfireBlock
                    && state.hasProperty(BlockStateProperties.LIT)
                    && state.getValue(BlockStateProperties.LIT)) {
                return true;
            }
        }
        return false;
    }

    private static void giveWorkers(Player player, int workers, CompoundTag broodData) {
        int remaining = workers;
        while (remaining > 0) {
            int count = Math.min(remaining, EcologyItems.CAPTURED_WORKER_BEE.get().getDefaultMaxStackSize());
            ItemStack stack = new ItemStack(EcologyItems.CAPTURED_WORKER_BEE.get(), count);
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(broodData.copy()));
            giveOrDrop(player, stack);
            remaining -= count;
        }
    }

    private static void giveOrDrop(Player player, ItemStack stack) {
        if (!player.addItem(stack)) {
            player.drop(stack, false);
        }
    }
}
