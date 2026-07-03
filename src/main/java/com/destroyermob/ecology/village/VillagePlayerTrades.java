package com.destroyermob.ecology.village;

import com.destroyermob.ecology.EcologyConfig;
import com.destroyermob.ecology.block.TradeboardBlock;
import com.destroyermob.ecology.block.TradeboardBlockEntity;
import com.destroyermob.ecology.registry.EcologyBlocks;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.entity.player.TradeWithVillagerEvent;

public final class VillagePlayerTrades {
    private static final String STORED_TRADEBOARD_DIMENSION_TAG = "EcologyStoredTradeboardDimension";
    private static final String STORED_TRADEBOARD_POS_TAG = "EcologyStoredTradeboardPos";
    private static final String STORED_INPUT_DIMENSION_TAG = "EcologyStoredTradeInputDimension";
    private static final String STORED_INPUT_POS_TAG = "EcologyStoredTradeInputPos";
    private static final String STORED_OUTPUT_DIMENSION_TAG = "EcologyStoredTradeOutputDimension";
    private static final String STORED_OUTPUT_POS_TAG = "EcologyStoredTradeOutputPos";
    private static final String PLAYER_STOCKED_TRADE_TAG = "EcologyTradeboardTrade";
    private static final String PLAYER_STOCKED_TRADE_POS_TAG = "EcologyTradeboardTradePos";
    private static final int MAX_BOARD_SIZE = 15;
    private static final int MAX_BOARD_TILES = MAX_BOARD_SIZE * MAX_BOARD_SIZE;
    private static final int MAX_USES_PER_OFFER_REFRESH = 1024;

    private VillagePlayerTrades() {
    }

    public static boolean recordLedgerTarget(ItemStack ledger, ServerLevel level, BlockPos clickedPos, Player player) {
        BlockState state = level.getBlockState(clickedPos);
        if (state.is(EcologyBlocks.TRADEBOARD.get())) {
            if (!EcologyConfig.villagePlayerTradesEnabled()) {
                player.displayClientMessage(Component.translatable("message.ecology.village.trade.disabled"), true);
                return true;
            }
            recordTradeboard(ledger, level, clickedPos, player);
            return true;
        }

        if (storedTradeboard(ledger).isEmpty()) {
            return false;
        }
        BlockEntity blockEntity = level.getBlockEntity(clickedPos);
        if (blockEntity == null) {
            return false;
        }
        if (!(blockEntity instanceof Container)) {
            player.displayClientMessage(Component.translatable("message.ecology.village.trade.storage.invalid"), true);
            return true;
        }
        recordStorage(ledger, level, clickedPos, player);
        return true;
    }

    public static boolean hasCompleteAssignment(ItemStack ledger) {
        return storedTradeboard(ledger).isPresent()
                && storedTradeInput(ledger).isPresent()
                && storedTradeOutput(ledger).isPresent();
    }

    public static boolean hasStoredAssignment(ItemStack ledger) {
        return storedTradeboard(ledger).isPresent()
                || storedTradeInput(ledger).isPresent()
                || storedTradeOutput(ledger).isPresent();
    }

    public static boolean assignStoredTradeSetup(ItemStack ledger, ServerLevel level, Player player, Villager villager) {
        if (!EcologyConfig.villagePlayerTradesEnabled()) {
            player.displayClientMessage(Component.translatable("message.ecology.village.trade.disabled"), true);
            return true;
        }
        if (villager.isBaby()) {
            player.displayClientMessage(Component.translatable("message.ecology.village.trade.baby"), true);
            return true;
        }
        if (VillageWelfare.isConfined(villager)) {
            player.displayClientMessage(Component.translatable("message.ecology.village.trade.confined"), true);
            return true;
        }
        Optional<TradeAssignment> assignment = assignmentFromLedger(ledger, level, player);
        if (assignment.isEmpty()) {
            return true;
        }
        if (!(villager instanceof VillageTradeboardHolder holder)) {
            return false;
        }

        holder.ecology$setTradeboard(Optional.of(assignment.get().tradeboard()));
        holder.ecology$setTradeInput(Optional.of(assignment.get().input()));
        holder.ecology$setTradeOutput(Optional.of(assignment.get().output()));
        refreshOffers(villager);
        player.displayClientMessage(Component.translatable(
                "message.ecology.village.trade.assigned",
                formatPos(assignment.get().tradeboard().pos()),
                formatPos(assignment.get().input().pos()),
                formatPos(assignment.get().output().pos())).withStyle(ChatFormatting.GREEN), true);
        level.playSound(null, villager.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 0.75F, 1.0F);
        return true;
    }

    public static boolean clearAssignedTradeSetup(ServerLevel level, Player player, Villager villager) {
        if (!(villager instanceof VillageTradeboardHolder holder)
                || (holder.ecology$getTradeboard().isEmpty()
                && holder.ecology$getTradeInput().isEmpty()
                && holder.ecology$getTradeOutput().isEmpty())) {
            return false;
        }
        holder.ecology$setTradeboard(Optional.empty());
        holder.ecology$setTradeInput(Optional.empty());
        holder.ecology$setTradeOutput(Optional.empty());
        removeTradeboardOffers(villager);
        player.displayClientMessage(Component.translatable("message.ecology.village.trade.cleared"), true);
        level.playSound(null, villager.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 0.55F, 1.05F);
        return true;
    }

    public static void describeBoard(ServerLevel level, BlockPos pos, Player player) {
        BoardScan scan = scanBoard(level, pos);
        if (!scan.valid()) {
            player.displayClientMessage(Component.translatable("message.ecology.tradeboard.invalid"), true);
            return;
        }
        player.displayClientMessage(Component.translatable(
                "message.ecology.tradeboard.valid",
                scan.width(),
                scan.height(),
                scan.trades().size()), true);
    }

    public static void refreshOffers(Villager villager) {
        if (!(villager.level() instanceof ServerLevel level) || villager.isBaby()) {
            return;
        }
        if (!EcologyConfig.villagePlayerTradesEnabled() || VillageWelfare.isConfined(villager)) {
            removeTradeboardOffers(villager);
            return;
        }

        Optional<TradeAssignment> assignment = assignmentFromVillager(villager, level);
        if (assignment.isEmpty()) {
            removeTradeboardOffers(villager);
            return;
        }
        BoardScan scan = scanBoard(level, assignment.get().tradeboard().pos());
        Optional<Container> input = containerAt(level, assignment.get().input());
        Optional<Container> output = containerAt(level, assignment.get().output());
        if (!scan.valid() || input.isEmpty() || output.isEmpty()) {
            removeTradeboardOffers(villager);
            return;
        }

        MerchantOffers rebuilt = new MerchantOffers();
        for (MerchantOffer offer : villager.getOffers()) {
            if (!isPlayerStocked(offer)) {
                rebuilt.add(offer);
            }
        }

        int generated = 0;
        int maxOffers = Math.max(1, Math.min(EcologyConfig.VILLAGE_PLAYER_TRADE_MAX_OFFERS.get(), MAX_BOARD_TILES));
        for (TradeEntry trade : scan.trades()) {
            if (generated >= maxOffers) {
                break;
            }
            int availableUses = countMatching(input.get(), trade.saleStack()) / trade.saleStack().getCount();
            if (availableUses <= 0) {
                continue;
            }
            VillageCurrency currency = VillageCurrencySystem.tradeCurrency(villager, generated);
            Item currencyItem = currency.item().orElse(Items.EMERALD);
            rebuilt.add(createOffer(currencyItem, trade, Math.min(availableUses, MAX_USES_PER_OFFER_REFRESH)));
            generated++;
        }
        villager.setOffers(rebuilt);
    }

    public static void prepareForTrading(Villager villager) {
        if (!(villager.level() instanceof ServerLevel) || villager.isBaby() || !hasAnyTradeAssignment(villager)) {
            return;
        }
        refreshOffers(villager);
    }

    public static boolean onTrade(TradeWithVillagerEvent event) {
        MerchantOffer offer = event.getMerchantOffer();
        if (!isPlayerStocked(offer)) {
            return false;
        }
        AbstractVillager merchant = event.getAbstractVillager();
        if (!(merchant instanceof Villager villager) || !(villager.level() instanceof ServerLevel level)) {
            return true;
        }
        Optional<TradeAssignment> assignment = assignmentFromVillager(villager, level);
        if (assignment.isEmpty()) {
            refreshOffers(villager);
            return true;
        }
        Optional<Container> input = containerAt(level, assignment.get().input());
        Optional<Container> output = containerAt(level, assignment.get().output());
        if (input.isEmpty() || output.isEmpty()) {
            event.getEntity().displayClientMessage(Component.translatable("message.ecology.village.trade.storage.invalid"), true);
            refreshOffers(villager);
            return true;
        }

        ItemStack soldStack = saleStackFor(level, offer).orElse(stripTradeMarker(offer.getResult()));
        if (!removeMatching(input.get(), soldStack)) {
            event.getEntity().displayClientMessage(Component.translatable("message.ecology.village.trade.stock_missing"), true);
            refreshOffers(villager);
            return true;
        }

        ItemStack leftover = insertStack(output.get(), offer.getCostA());
        if (!leftover.isEmpty()) {
            BlockPos dropPos = assignment.get().output().pos();
            Containers.dropItemStack(level, dropPos.getX() + 0.5D, dropPos.getY() + 1.0D, dropPos.getZ() + 0.5D, leftover);
            event.getEntity().displayClientMessage(Component.translatable("message.ecology.village.trade.output_full"), true);
        }
        refreshOffers(villager);
        return true;
    }

    public static boolean isPlayerStocked(MerchantOffer offer) {
        return isMarked(offer.getResult());
    }

    public static ItemStack stripTradeMarker(ItemStack stack) {
        if (!isMarked(stack)) {
            return stack;
        }
        ItemStack stripped = stack.copy();
        CompoundTag root = stripped.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        root.remove(PLAYER_STOCKED_TRADE_TAG);
        root.remove(PLAYER_STOCKED_TRADE_POS_TAG);
        if (root.isEmpty()) {
            stripped.remove(DataComponents.CUSTOM_DATA);
        } else {
            stripped.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
        }
        return stripped;
    }

    public static boolean pruneExhausted(MerchantOffers offers) {
        return offers.removeIf(offer -> isPlayerStocked(offer) && offer.isOutOfStock());
    }

    public static Optional<GlobalPos> storedTradeboard(ItemStack ledger) {
        return storedGlobalPos(ledger, STORED_TRADEBOARD_DIMENSION_TAG, STORED_TRADEBOARD_POS_TAG);
    }

    public static Optional<GlobalPos> storedTradeInput(ItemStack ledger) {
        return storedGlobalPos(ledger, STORED_INPUT_DIMENSION_TAG, STORED_INPUT_POS_TAG);
    }

    public static Optional<GlobalPos> storedTradeOutput(ItemStack ledger) {
        return storedGlobalPos(ledger, STORED_OUTPUT_DIMENSION_TAG, STORED_OUTPUT_POS_TAG);
    }

    private static void recordTradeboard(ItemStack ledger, ServerLevel level, BlockPos pos, Player player) {
        BoardScan scan = scanBoard(level, pos);
        if (!scan.valid()) {
            player.displayClientMessage(Component.translatable("message.ecology.village.trade.board.invalid"), true);
            return;
        }
        CompoundTag root = ledger.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        putGlobalPos(root, STORED_TRADEBOARD_DIMENSION_TAG, STORED_TRADEBOARD_POS_TAG, GlobalPos.of(level.dimension(), pos.immutable()));
        clearGlobalPos(root, STORED_INPUT_DIMENSION_TAG, STORED_INPUT_POS_TAG);
        clearGlobalPos(root, STORED_OUTPUT_DIMENSION_TAG, STORED_OUTPUT_POS_TAG);
        ledger.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
        player.displayClientMessage(Component.translatable(
                "message.ecology.village.trade.board.recorded",
                formatPos(pos),
                scan.width(),
                scan.height(),
                scan.trades().size()), true);
        level.playSound(null, pos, SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundSource.PLAYERS, 0.65F, 1.1F);
    }

    private static void recordStorage(ItemStack ledger, ServerLevel level, BlockPos pos, Player player) {
        CompoundTag root = ledger.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        Optional<GlobalPos> input = storedTradeInput(ledger);
        Optional<GlobalPos> output = storedTradeOutput(ledger);
        GlobalPos storage = GlobalPos.of(level.dimension(), pos.immutable());
        if (input.isEmpty()) {
            putGlobalPos(root, STORED_INPUT_DIMENSION_TAG, STORED_INPUT_POS_TAG, storage);
            player.displayClientMessage(Component.translatable("message.ecology.village.trade.input.recorded", formatPos(pos)), true);
        } else if (output.isEmpty()) {
            if (input.get().equals(storage)) {
                player.displayClientMessage(Component.translatable("message.ecology.village.trade.storage.same"), true);
                return;
            }
            putGlobalPos(root, STORED_OUTPUT_DIMENSION_TAG, STORED_OUTPUT_POS_TAG, storage);
            player.displayClientMessage(Component.translatable("message.ecology.village.trade.output.recorded", formatPos(pos)), true);
        } else {
            putGlobalPos(root, STORED_INPUT_DIMENSION_TAG, STORED_INPUT_POS_TAG, storage);
            clearGlobalPos(root, STORED_OUTPUT_DIMENSION_TAG, STORED_OUTPUT_POS_TAG);
            player.displayClientMessage(Component.translatable("message.ecology.village.trade.storage.restart", formatPos(pos)), true);
        }
        ledger.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
        level.playSound(null, pos, SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundSource.PLAYERS, 0.65F, 1.0F);
    }

    private static Optional<TradeAssignment> assignmentFromLedger(ItemStack ledger, ServerLevel level, Player player) {
        Optional<GlobalPos> tradeboard = storedTradeboard(ledger);
        Optional<GlobalPos> input = storedTradeInput(ledger);
        Optional<GlobalPos> output = storedTradeOutput(ledger);
        if (tradeboard.isEmpty() || input.isEmpty() || output.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.ecology.village.trade.incomplete"), true);
            return Optional.empty();
        }
        if (!tradeboard.get().dimension().equals(level.dimension())
                || !input.get().dimension().equals(level.dimension())
                || !output.get().dimension().equals(level.dimension())) {
            player.displayClientMessage(Component.translatable("message.ecology.village.trade.wrong_dimension"), true);
            return Optional.empty();
        }
        if (input.get().pos().equals(output.get().pos())) {
            player.displayClientMessage(Component.translatable("message.ecology.village.trade.storage.same"), true);
            return Optional.empty();
        }
        BoardScan scan = scanBoard(level, tradeboard.get().pos());
        if (!scan.valid()) {
            player.displayClientMessage(Component.translatable("message.ecology.village.trade.board.invalid"), true);
            return Optional.empty();
        }
        if (containerAt(level, input.get()).isEmpty() || containerAt(level, output.get()).isEmpty()) {
            player.displayClientMessage(Component.translatable("message.ecology.village.trade.storage.invalid"), true);
            return Optional.empty();
        }
        return Optional.of(new TradeAssignment(tradeboard.get(), input.get(), output.get()));
    }

    private static Optional<TradeAssignment> assignmentFromVillager(Villager villager, ServerLevel level) {
        if (!(villager instanceof VillageTradeboardHolder holder)) {
            return Optional.empty();
        }
        Optional<GlobalPos> tradeboard = holder.ecology$getTradeboard();
        Optional<GlobalPos> input = holder.ecology$getTradeInput();
        Optional<GlobalPos> output = holder.ecology$getTradeOutput();
        if (tradeboard.isEmpty() || input.isEmpty() || output.isEmpty()) {
            return Optional.empty();
        }
        if (!tradeboard.get().dimension().equals(level.dimension())
                || !input.get().dimension().equals(level.dimension())
                || !output.get().dimension().equals(level.dimension())
                || input.get().pos().equals(output.get().pos())) {
            return Optional.empty();
        }
        return Optional.of(new TradeAssignment(tradeboard.get(), input.get(), output.get()));
    }

    private static boolean hasAnyTradeAssignment(Villager villager) {
        if (!(villager instanceof VillageTradeboardHolder holder)) {
            return false;
        }
        return holder.ecology$getTradeboard().isPresent()
                || holder.ecology$getTradeInput().isPresent()
                || holder.ecology$getTradeOutput().isPresent();
    }

    private static BoardScan scanBoard(ServerLevel level, BlockPos seed) {
        BlockState seedState = level.getBlockState(seed);
        if (!seedState.is(EcologyBlocks.TRADEBOARD.get())) {
            return BoardScan.invalid();
        }
        Direction facing = seedState.getValue(TradeboardBlock.FACING);
        Direction horizontal = facing.getClockWise();
        Direction[] searchDirections = new Direction[] {
                Direction.UP,
                Direction.DOWN,
                facing.getCounterClockWise(),
                facing.getClockWise()
        };
        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        visited.add(seed.immutable());
        queue.add(seed.immutable());

        while (!queue.isEmpty()) {
            BlockPos current = queue.removeFirst();
            for (Direction direction : searchDirections) {
                BlockPos next = current.relative(direction);
                if (visited.contains(next)) {
                    continue;
                }
                if (TradeboardBlock.isSameBoard(seedState, level.getBlockState(next))) {
                    visited.add(next.immutable());
                    if (visited.size() > MAX_BOARD_TILES) {
                        return BoardScan.invalid();
                    }
                    queue.add(next.immutable());
                }
            }
        }

        int minHorizontal = Integer.MAX_VALUE;
        int maxHorizontal = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (BlockPos pos : visited) {
            int h = horizontalOffset(seed, pos, horizontal);
            int y = pos.getY() - seed.getY();
            minHorizontal = Math.min(minHorizontal, h);
            maxHorizontal = Math.max(maxHorizontal, h);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
        }

        int width = maxHorizontal - minHorizontal + 1;
        int height = maxY - minY + 1;
        if (width < 1 || height < 1 || width > MAX_BOARD_SIZE || height > MAX_BOARD_SIZE || visited.size() != width * height) {
            return BoardScan.invalid(width, height);
        }
        for (int h = minHorizontal; h <= maxHorizontal; h++) {
            for (int y = minY; y <= maxY; y++) {
                BlockPos expected = seed.relative(horizontal, h).above(y).immutable();
                if (!visited.contains(expected) || !TradeboardBlock.isSameBoard(seedState, level.getBlockState(expected))) {
                    return BoardScan.invalid(width, height);
                }
            }
        }

        List<TradeEntry> trades = visited.stream()
                .sorted(Comparator
                        .comparingInt((BlockPos pos) -> -(pos.getY() - seed.getY()))
                        .thenComparingInt(pos -> horizontalOffset(seed, pos, horizontal)))
                .map(pos -> tradeAt(level, pos))
                .flatMap(Optional::stream)
                .toList();
        return new BoardScan(true, width, height, trades);
    }

    private static Optional<TradeEntry> tradeAt(ServerLevel level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof TradeboardBlockEntity tradeboard && tradeboard.hasTrade()) {
            return Optional.of(new TradeEntry(pos.immutable(), tradeboard.saleStack(), tradeboard.price()));
        }
        return Optional.empty();
    }

    private static int horizontalOffset(BlockPos origin, BlockPos pos, Direction horizontal) {
        return (pos.getX() - origin.getX()) * horizontal.getStepX()
                + (pos.getZ() - origin.getZ()) * horizontal.getStepZ();
    }

    private static Optional<Container> containerAt(ServerLevel level, GlobalPos pos) {
        if (!pos.dimension().equals(level.dimension())) {
            return Optional.empty();
        }
        return level.getBlockEntity(pos.pos()) instanceof Container container ? Optional.of(container) : Optional.empty();
    }

    private static MerchantOffer createOffer(Item currencyItem, TradeEntry trade, int maxUses) {
        ItemStack result = trade.saleStack().copy();
        mark(result, trade.pos());
        return new MerchantOffer(new ItemCost(currencyItem, trade.price()), Optional.empty(), result, maxUses, 0, 0.0F);
    }

    private static Optional<ItemStack> saleStackFor(ServerLevel level, MerchantOffer offer) {
        Optional<BlockPos> pos = markedTradePos(offer);
        if (pos.isEmpty()) {
            return Optional.empty();
        }
        return tradeAt(level, pos.get()).map(TradeEntry::saleStack);
    }

    private static int countMatching(Container container, ItemStack template) {
        int count = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, template)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static boolean removeMatching(Container container, ItemStack template) {
        if (template.isEmpty() || countMatching(container, template) < template.getCount()) {
            return false;
        }
        int remaining = template.getCount();
        for (int slot = 0; slot < container.getContainerSize() && remaining > 0; slot++) {
            ItemStack stack = container.getItem(slot);
            if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, template)) {
                ItemStack removed = container.removeItem(slot, Math.min(remaining, stack.getCount()));
                remaining -= removed.getCount();
            }
        }
        container.setChanged();
        return remaining <= 0;
    }

    private static ItemStack insertStack(Container container, ItemStack stack) {
        ItemStack remaining = stack.copy();
        if (remaining.isEmpty()) {
            return ItemStack.EMPTY;
        }
        for (int slot = 0; slot < container.getContainerSize() && !remaining.isEmpty(); slot++) {
            ItemStack existing = container.getItem(slot);
            if (existing.isEmpty()
                    || !ItemStack.isSameItemSameComponents(existing, remaining)
                    || !container.canPlaceItem(slot, remaining)) {
                continue;
            }
            int limit = Math.min(container.getMaxStackSize(existing), existing.getMaxStackSize());
            int moved = Math.min(remaining.getCount(), limit - existing.getCount());
            if (moved > 0) {
                existing.grow(moved);
                remaining.shrink(moved);
                container.setChanged();
            }
        }
        for (int slot = 0; slot < container.getContainerSize() && !remaining.isEmpty(); slot++) {
            if (!container.getItem(slot).isEmpty() || !container.canPlaceItem(slot, remaining)) {
                continue;
            }
            int moved = Math.min(remaining.getCount(), container.getMaxStackSize(remaining));
            container.setItem(slot, remaining.copyWithCount(moved));
            remaining.shrink(moved);
        }
        container.setChanged();
        return remaining;
    }

    private static void removeTradeboardOffers(Villager villager) {
        MerchantOffers offers = villager.getOffers();
        if (offers.removeIf(VillagePlayerTrades::isPlayerStocked)) {
            villager.setOffers(offers);
        }
    }

    private static void mark(ItemStack stack, BlockPos tradePos) {
        CompoundTag root = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        root.putBoolean(PLAYER_STOCKED_TRADE_TAG, true);
        root.putLong(PLAYER_STOCKED_TRADE_POS_TAG, tradePos.asLong());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
    }

    private static boolean isMarked(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getBoolean(PLAYER_STOCKED_TRADE_TAG);
    }

    private static Optional<BlockPos> markedTradePos(MerchantOffer offer) {
        CompoundTag root = offer.getResult().getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!root.getBoolean(PLAYER_STOCKED_TRADE_TAG) || !root.contains(PLAYER_STOCKED_TRADE_POS_TAG, Tag.TAG_ANY_NUMERIC)) {
            return Optional.empty();
        }
        return Optional.of(BlockPos.of(root.getLong(PLAYER_STOCKED_TRADE_POS_TAG)));
    }

    private static Optional<GlobalPos> storedGlobalPos(ItemStack stack, String dimensionTag, String posTag) {
        CompoundTag root = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!root.contains(dimensionTag, Tag.TAG_STRING) || !root.contains(posTag, Tag.TAG_ANY_NUMERIC)) {
            return Optional.empty();
        }
        ResourceLocation dimension = ResourceLocation.tryParse(root.getString(dimensionTag));
        if (dimension == null) {
            return Optional.empty();
        }
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, dimension);
        return Optional.of(GlobalPos.of(key, BlockPos.of(root.getLong(posTag))));
    }

    private static void putGlobalPos(CompoundTag root, String dimensionTag, String posTag, GlobalPos pos) {
        root.putString(dimensionTag, pos.dimension().location().toString());
        root.putLong(posTag, pos.pos().asLong());
    }

    private static void clearGlobalPos(CompoundTag root, String dimensionTag, String posTag) {
        root.remove(dimensionTag);
        root.remove(posTag);
    }

    private static String formatPos(BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }

    private record TradeAssignment(GlobalPos tradeboard, GlobalPos input, GlobalPos output) {
    }

    private record TradeEntry(BlockPos pos, ItemStack saleStack, int price) {
    }

    private record BoardScan(boolean valid, int width, int height, List<TradeEntry> trades) {
        private static BoardScan invalid() {
            return invalid(0, 0);
        }

        private static BoardScan invalid(int width, int height) {
            return new BoardScan(false, width, height, List.of());
        }
    }
}
