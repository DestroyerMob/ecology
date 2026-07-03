package com.destroyermob.ecology.item;

import com.destroyermob.ecology.EcologyConfig;
import com.destroyermob.ecology.village.VillageCurrencySystem;
import com.destroyermob.ecology.village.VillageEcology;
import com.destroyermob.ecology.village.VillageEcologyReport;
import com.destroyermob.ecology.village.VillageHouseholds;
import com.destroyermob.ecology.village.VillageMarketStalls;
import com.destroyermob.ecology.village.VillagePlayerTrades;
import com.destroyermob.ecology.village.VillageLedgerReports;
import com.destroyermob.ecology.village.VillageRelocation;
import com.destroyermob.ecology.village.VillageSupplies;
import com.destroyermob.ecology.village.VillageVillagerDiagnostics;
import com.destroyermob.ecology.village.VillageZones;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

public class VillageLedgerItem extends Item {
    public VillageLedgerItem() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        if (VillageHouseholds.inspectHomeDeed(serverLevel, context.getClickedPos(), player)) {
            return InteractionResult.CONSUME;
        }
        if (player.isShiftKeyDown() && VillagePlayerTrades.recordLedgerTarget(context.getItemInHand(), serverLevel, context.getClickedPos(), player)) {
            return InteractionResult.CONSUME;
        }
        if (player.isShiftKeyDown() && VillageHouseholds.recordHousePlotCorner(context.getItemInHand(), serverLevel, context.getClickedPos(), context.getHand(), player)) {
            return InteractionResult.CONSUME;
        }
        if (player.isShiftKeyDown() && VillageCurrencySystem.setVillageCurrencyFromPayment(serverLevel, player, context.getHand(), context.getClickedPos())) {
            return InteractionResult.CONSUME;
        }
        if (player.isShiftKeyDown() && VillageSupplies.donateOtherHand(serverLevel, player, context.getHand(), context.getClickedPos())) {
            return InteractionResult.CONSUME;
        }
        if (player.isShiftKeyDown() && VillageRelocation.canRecordVillage(serverLevel, context.getClickedPos())) {
            VillageRelocation.recordVillageAnchor(context.getItemInHand(), serverLevel, context.getClickedPos(), player);
            return InteractionResult.CONSUME;
        }
        if (player.isShiftKeyDown() && EcologyConfig.villageMarketStallsEnabled()) {
            VillageMarketStalls.recordStall(context.getItemInHand(), serverLevel, context.getClickedPos(), context.getClickedFace(), player);
            return InteractionResult.CONSUME;
        }
        inspect(serverLevel, player, context.getItemInHand(), context.getClickedPos());
        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!(target instanceof Villager villager)) {
            return InteractionResult.PASS;
        }
        if (!hasVillagerAction(stack, player)) {
            return InteractionResult.PASS;
        }
        if (!(player.level() instanceof ServerLevel)) {
            return InteractionResult.SUCCESS;
        }
        return interactWithVillager(stack, player, villager);
    }

    public static boolean hasVillagerAction(ItemStack stack, Player player) {
        return player.isSecondaryUseActive()
                || VillageRelocation.storedVillageAnchor(stack).isPresent()
                || VillageMarketStalls.storedStall(stack).isPresent()
                || VillagePlayerTrades.hasCompleteAssignment(stack);
    }

    public static InteractionResult interactWithVillager(ItemStack stack, Player player, Villager villager) {
        if (!(player.level() instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }
        if (!player.isSecondaryUseActive() && VillagePlayerTrades.hasCompleteAssignment(stack)) {
            return VillagePlayerTrades.assignStoredTradeSetup(stack, serverLevel, player, villager) ? InteractionResult.CONSUME : InteractionResult.PASS;
        }
        if (!player.isSecondaryUseActive() && VillageRelocation.storedVillageAnchor(stack).isPresent()) {
            return VillageRelocation.adoptStoredVillage(stack, serverLevel, player, villager) ? InteractionResult.CONSUME : InteractionResult.PASS;
        }
        if (player.isSecondaryUseActive()) {
            if (VillageRelocation.stopGuidedRelocation(serverLevel, player, villager)) {
                return InteractionResult.CONSUME;
            }
            if (VillagePlayerTrades.clearAssignedTradeSetup(serverLevel, player, villager)) {
                return InteractionResult.CONSUME;
            }
            if (EcologyConfig.villageMarketStallsEnabled() && VillageMarketStalls.clearAssignedStall(serverLevel, player, villager)) {
                return InteractionResult.CONSUME;
            }
            if (player instanceof ServerPlayer serverPlayer) {
                VillageLedgerReports.openVillager(serverLevel, serverPlayer, villager);
            } else {
                VillageVillagerDiagnostics.sendReport(serverLevel, player, villager);
            }
            return InteractionResult.CONSUME;
        }
        if (VillageMarketStalls.storedStall(stack).isPresent()) {
            if (!EcologyConfig.villageMarketStallsEnabled()) {
                player.displayClientMessage(Component.translatable("message.ecology.village.stall.disabled"), true);
                return InteractionResult.CONSUME;
            }
            return VillageMarketStalls.assignStoredStall(stack, serverLevel, player, villager) ? InteractionResult.CONSUME : InteractionResult.PASS;
        }
        return InteractionResult.PASS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResultHolder.success(stack);
        }
        if (player.isShiftKeyDown() && VillageSupplies.donateOtherHand(serverLevel, player, hand, player.blockPosition())) {
            return InteractionResultHolder.consume(stack);
        }
        inspect(serverLevel, player, stack, player.blockPosition());
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.ecology.village_ledger").withStyle(ChatFormatting.GRAY));
        VillagePlayerTrades.storedTradeboard(stack)
                .map(GlobalPos::pos)
                .ifPresent(pos -> tooltip.add(Component.translatable(
                        "tooltip.ecology.village_ledger.tradeboard",
                        pos.getX(),
                        pos.getY(),
                        pos.getZ()).withStyle(ChatFormatting.DARK_GRAY)));
        VillagePlayerTrades.storedTradeInput(stack)
                .map(GlobalPos::pos)
                .ifPresent(pos -> tooltip.add(Component.translatable(
                        "tooltip.ecology.village_ledger.trade_input",
                        pos.getX(),
                        pos.getY(),
                        pos.getZ()).withStyle(ChatFormatting.DARK_GRAY)));
        VillagePlayerTrades.storedTradeOutput(stack)
                .map(GlobalPos::pos)
                .ifPresent(pos -> tooltip.add(Component.translatable(
                        "tooltip.ecology.village_ledger.trade_output",
                        pos.getX(),
                        pos.getY(),
                        pos.getZ()).withStyle(ChatFormatting.DARK_GRAY)));
        VillageMarketStalls.storedStall(stack)
                .map(GlobalPos::pos)
                .ifPresent(pos -> tooltip.add(Component.translatable(
                        "tooltip.ecology.village_ledger.stall",
                        pos.getX(),
                        pos.getY(),
                        pos.getZ()).withStyle(ChatFormatting.DARK_GRAY)));
        VillageRelocation.storedVillageAnchor(stack)
                .map(GlobalPos::pos)
                .ifPresent(pos -> tooltip.add(Component.translatable(
                        "tooltip.ecology.village_ledger.village",
                        pos.getX(),
                        pos.getY(),
                        pos.getZ()).withStyle(ChatFormatting.DARK_GRAY)));
        VillageHouseholds.storedPlotCorner(stack)
                .map(GlobalPos::pos)
                .ifPresent(pos -> tooltip.add(Component.translatable(
                        "tooltip.ecology.village_ledger.house_plot",
                        pos.getX(),
                        pos.getY(),
                        pos.getZ()).withStyle(ChatFormatting.DARK_GRAY)));
    }

    private static void inspect(ServerLevel level, Player player, ItemStack stack, BlockPos center) {
        if (!EcologyConfig.villageEcologyEnabled()) {
            player.displayClientMessage(Component.translatable("message.ecology.village.disabled"), true);
            return;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            VillageLedgerReports.openVillage(level, serverPlayer, stack, center);
            return;
        }
        BlockPos villageCenter = VillageZones.refreshAndResolveCenter(level, center, true)
                .orElseGet(() -> VillageEcology.surveyCenter(level, center));
        VillageEcologyReport report = VillageEcology.survey(level, villageCenter);
        VillageEcology.sendReport(player, report);
        if (EcologyConfig.villageSuppliesEnabled()) {
            VillageSupplies.sendReport(player, VillageSupplies.report(level, villageCenter));
        }
        if (EcologyConfig.villageHouseholdsEnabled()) {
            VillageHouseholds.sendReport(player, VillageHouseholds.report(level, villageCenter));
        }
        level.playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.7F, 1.05F);
    }
}
