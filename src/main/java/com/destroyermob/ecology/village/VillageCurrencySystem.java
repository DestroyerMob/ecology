package com.destroyermob.ecology.village;

import com.destroyermob.ecology.EcologyConfig;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.phys.AABB;

public final class VillageCurrencySystem {
    private static final int VILLAGE_KEY_SIZE = 96;
    private static final int ANCHOR_SEARCH_RADIUS = 48;
    private static final int CURRENCY_REFRESH_TICKS = 100;

    private VillageCurrencySystem() {
    }

    public static void tickVillager(ServerLevel level, Villager villager) {
        if (!EcologyConfig.villageCurrenciesEnabled()) {
            setEyeCurrencies(villager, VillageCurrency.EMERALD, VillageCurrency.EMERALD, true);
            return;
        }
        if (villager.tickCount < 20 || Math.floorMod(villager.tickCount + villager.getId(), CURRENCY_REFRESH_TICKS) != 0) {
            return;
        }
        repairFallbackEyes(level, villager);
    }

    private static void repairFallbackEyes(ServerLevel level, Villager villager) {
        if (!(villager instanceof VillageCurrencyHolder holder)) {
            return;
        }
        VillageCurrency currency = villageCurrency(level, villager);
        VillageCurrency fallback = fallbackCurrency();
        VillageCurrency left = availableCurrency(holder.ecology$getLeftEyeCurrency());
        VillageCurrency right = availableCurrency(holder.ecology$getRightEyeCurrency());
        if (currency != fallback && left == fallback && right == fallback) {
            VillageCurrencyGenes.EyePair eyes = VillageCurrencyGenes.initialEyesFor(level.random, currency, eyeGeneCurrencies());
            setEyeCurrencies(villager, eyes.left(), eyes.right(), true);
        }
    }

    public static void tickVillageEntity(ServerLevel level, Entity entity) {
        if (entity instanceof Villager villager) {
            tickVillager(level, villager);
            return;
        }
        if (!EcologyConfig.villageCurrenciesEnabled()) {
            setCurrency(entity, VillageCurrency.EMERALD);
            return;
        }
        if (entity.tickCount < 20 || Math.floorMod(entity.tickCount + entity.getId(), CURRENCY_REFRESH_TICKS) != 0) {
            return;
        }
        assignCurrency(level, entity);
    }

    public static void convertOffers(Villager villager) {
        MerchantOffers offers = villager.getOffers();
        if (offers.isEmpty()) {
            return;
        }
        MerchantOffers convertedOffers = new MerchantOffers();
        boolean changed = false;

        int offerIndex = 0;
        for (MerchantOffer offer : offers) {
            MerchantOffer converted = convertOffer(offer, tradeCurrency(villager, offerIndex++));
            convertedOffers.add(converted);
            changed |= converted != offer;
        }

        if (changed) {
            villager.setOffers(convertedOffers);
        }
    }

    public static void inheritCurrency(ServerLevel level, Villager child, Villager parent, AgeableMob otherParent) {
        if (!EcologyConfig.villageCurrenciesEnabled()) {
            setEyeCurrencies(child, VillageCurrency.EMERALD, VillageCurrency.EMERALD, true);
            return;
        }

        List<VillageCurrency> inherited = inheritedCurrencies(parent, otherParent);
        VillageCurrency primary = inherited.get(level.random.nextInt(inherited.size()));
        VillageCurrency left = primary;
        VillageCurrency right = primary;
        List<VillageCurrency> distinct = distinctCurrencies(inherited);
        if (distinct.size() > 1 && level.random.nextDouble() < EcologyConfig.villageHeterochromiaChance()) {
            VillageCurrency secondary = differentCurrency(distinct, primary, level.random);
            if (level.random.nextBoolean()) {
                left = secondary;
            } else {
                right = secondary;
            }
        }
        setEyeCurrencies(child, left, right, true);
    }

    public static VillageCurrency currency(Entity entity) {
        return tradeCurrencies(entity).getFirst();
    }

    public static List<VillageCurrency> tradeCurrencies(Entity entity) {
        if (entity instanceof VillageCurrencyHolder holder) {
            VillageCurrency left = availableCurrency(holder.ecology$getLeftEyeCurrency());
            VillageCurrency right = availableCurrency(holder.ecology$getRightEyeCurrency());
            return left == right ? List.of(left) : List.of(left, right);
        }
        return List.of(VillageCurrency.EMERALD);
    }

    public static VillageCurrency tradeCurrency(Villager villager, int offerIndex) {
        if (villager.level() instanceof ServerLevel level && EcologyConfig.villageCurrenciesEnabled()) {
            return villageCurrency(level, villager);
        }
        return currency(villager);
    }

    public static String describeCurrencies(Entity entity) {
        List<VillageCurrency> currencies = tradeCurrencies(entity);
        if (currencies.size() == 1) {
            return currencies.getFirst().serializedName();
        }
        return currencies.get(0).serializedName() + "/" + currencies.get(1).serializedName();
    }

    public static VillageCurrency assignCurrency(ServerLevel level, Villager villager) {
        if (!EcologyConfig.villageCurrenciesEnabled()) {
            setEyeCurrencies(villager, VillageCurrency.EMERALD, VillageCurrency.EMERALD, true);
            return VillageCurrency.EMERALD;
        }
        VillageCurrency currency = villageCurrency(level, villager);
        VillageCurrencyGenes.EyePair eyes = VillageCurrencyGenes.initialEyesFor(level.random, currency, eyeGeneCurrencies());
        setEyeCurrencies(villager, eyes.left(), eyes.right(), true);
        return currency;
    }

    public static BlockPos villageAnchor(ServerLevel level, Villager villager) {
        return anchorFor(level, villager);
    }

    public static BlockPos villageAnchor(ServerLevel level, BlockPos center) {
        return nearestPoi(level, center, PoiTypes.MEETING).orElse(center);
    }

    public static VillageCurrency villageCurrency(ServerLevel level, Villager villager) {
        return villageCurrency(level, anchorFor(level, villager));
    }

    public static VillageCurrency villageCurrency(ServerLevel level, BlockPos center) {
        if (!EcologyConfig.villageCurrenciesEnabled()) {
            return VillageCurrency.EMERALD;
        }
        BlockPos anchor = villageAnchor(level, center);
        return VillageCurrencyLedger.get(level).currencyFor(level, anchor);
    }

    public static VillageCurrency assignCurrency(ServerLevel level, Entity entity) {
        if (entity instanceof Villager villager) {
            return assignCurrency(level, villager);
        }
        if (!EcologyConfig.villageCurrenciesEnabled()) {
            setCurrency(entity, VillageCurrency.EMERALD);
            return VillageCurrency.EMERALD;
        }
        Optional<VillageCurrency> nearbyCurrency = nearbyVillagerCurrency(level, entity);
        if (nearbyCurrency.isPresent()) {
            VillageCurrency currency = nearbyCurrency.get();
            setCurrency(entity, currency);
            return currency;
        }
        return assignCurrency(level, entity, anchorForVillageEntity(level, entity));
    }

    private static VillageCurrency assignCurrency(ServerLevel level, Entity entity, BlockPos anchor) {
        if (!EcologyConfig.villageCurrenciesEnabled()) {
            setCurrency(entity, VillageCurrency.EMERALD);
            return VillageCurrency.EMERALD;
        }
        VillageCurrency currency = villageCurrency(level, anchor);
        setCurrency(entity, currency);
        return currency;
    }

    private static void setCurrency(Entity entity, VillageCurrency currency) {
        setEyeCurrencies(entity, currency, currency, true);
    }

    private static void setEyeCurrencies(Entity entity, VillageCurrency left, VillageCurrency right, boolean updateOffers) {
        left = availableCurrency(left);
        right = availableCurrency(right);
        boolean changed = false;
        if (entity instanceof VillageCurrencyHolder holder) {
            changed = holder.ecology$getLeftEyeCurrency() != left || holder.ecology$getRightEyeCurrency() != right;
            if (changed) {
                holder.ecology$setEyeCurrencies(left, right);
            }
        }
        if (changed && updateOffers && entity instanceof Villager villager) {
            convertOffers(villager);
        }
    }

    private static List<VillageCurrency> inheritedCurrencies(Villager parent, AgeableMob otherParent) {
        List<VillageCurrency> currencies = new ArrayList<>();
        addEyeCurrencies(currencies, parent);
        if (otherParent instanceof Villager otherVillager) {
            addEyeCurrencies(currencies, otherVillager);
        }
        if (currencies.isEmpty()) {
            currencies.add(VillageCurrency.EMERALD);
        }
        return currencies;
    }

    private static List<VillageCurrency> distinctCurrencies(List<VillageCurrency> currencies) {
        List<VillageCurrency> distinct = new ArrayList<>();
        for (VillageCurrency currency : currencies) {
            if (!distinct.contains(currency)) {
                distinct.add(currency);
            }
        }
        return distinct;
    }

    private static VillageCurrency differentCurrency(List<VillageCurrency> currencies, VillageCurrency primary, RandomSource random) {
        List<VillageCurrency> alternatives = currencies.stream()
                .filter(currency -> currency != primary)
                .toList();
        return alternatives.get(random.nextInt(alternatives.size()));
    }

    private static void addEyeCurrencies(List<VillageCurrency> currencies, Villager villager) {
        if (villager instanceof VillageCurrencyHolder holder) {
            currencies.add(availableCurrency(holder.ecology$getLeftEyeCurrency()));
            currencies.add(availableCurrency(holder.ecology$getRightEyeCurrency()));
        } else {
            currencies.add(VillageCurrency.EMERALD);
        }
    }

    private static BlockPos anchorFor(ServerLevel level, Villager villager) {
        return brainAnchor(level, villager, MemoryModuleType.MEETING_POINT)
                .or(() -> nearestPoi(level, villager.blockPosition(), PoiTypes.MEETING))
                .or(() -> brainAnchor(level, villager, MemoryModuleType.HOME))
                .or(() -> brainAnchor(level, villager, MemoryModuleType.JOB_SITE))
                .orElse(villager.blockPosition());
    }

    private static BlockPos anchorForVillageEntity(ServerLevel level, Entity entity) {
        return nearestPoi(level, entity.blockPosition(), PoiTypes.MEETING)
                .orElse(entity.blockPosition());
    }

    private static Optional<VillageCurrency> nearbyVillagerCurrency(ServerLevel level, Entity entity) {
        return level.getEntitiesOfClass(
                        Villager.class,
                        entity.getBoundingBox().inflate(ANCHOR_SEARCH_RADIUS),
                        villager -> villager.isAlive() && villager != entity)
                .stream()
                .min(Comparator.comparingDouble(entity::distanceToSqr))
                .map(villager -> villageCurrency(level, villager));
    }

    private static Optional<BlockPos> brainAnchor(ServerLevel level, Villager villager, MemoryModuleType<GlobalPos> memoryType) {
        return villager.getBrain().getMemory(memoryType)
                .filter(pos -> pos.dimension() == level.dimension())
                .map(GlobalPos::pos);
    }

    private static Optional<BlockPos> nearestPoi(ServerLevel level, BlockPos center, ResourceKey<PoiType> poiType) {
        return level.getPoiManager().findClosest(
                holder -> holder.is(poiType),
                center,
                ANCHOR_SEARCH_RADIUS,
                PoiManager.Occupancy.ANY);
    }

    public static VillageCurrency naturalCurrencyFor(ServerLevel level, BlockPos anchor) {
        int cellX = Math.floorDiv(anchor.getX(), VILLAGE_KEY_SIZE);
        int cellZ = Math.floorDiv(anchor.getZ(), VILLAGE_KEY_SIZE);
        long hash = 0xCBF29CE484222325L;
        hash = mix(hash, level.dimension().location().toString().hashCode());
        hash = mix(hash, cellX);
        hash = mix(hash, cellZ);
        List<VillageCurrency> currencies = naturalCurrencies();
        return currencies.get(Mth.positiveModulo((int)(hash ^ hash >>> 32), currencies.size()));
    }

    private static long mix(long hash, int value) {
        hash ^= value;
        return hash * 0x100000001B3L;
    }

    private static MerchantOffer convertOffer(MerchantOffer offer, VillageCurrency currency) {
        ItemCost costA = convertCost(offer.getItemCostA(), currency);
        Optional<ItemCost> costB = offer.getItemCostB().map(cost -> convertCost(cost, currency));
        ItemStack result = VillagePlayerTrades.isPlayerStocked(offer)
                ? offer.getResult().copy()
                : convertStack(offer.getResult(), currency);

        if (costA == offer.getItemCostA()
                && costB.equals(offer.getItemCostB())
                && ItemStack.matches(result, offer.getResult())) {
            return offer;
        }

        MerchantOffer converted = new MerchantOffer(
                costA,
                costB,
                result,
                offer.getUses(),
                offer.getMaxUses(),
                offer.getXp(),
                offer.getPriceMultiplier(),
                offer.getDemand());
        converted.setSpecialPriceDiff(offer.getSpecialPriceDiff());
        if (offer.isOutOfStock()) {
            converted.setToOutOfStock();
        }
        return converted;
    }

    private static ItemCost convertCost(ItemCost cost, VillageCurrency currency) {
        if (!isCurrencyItem(cost.itemStack())) {
            return cost;
        }
        Item item = currency.item().orElse(Items.EMERALD);
        if (cost.itemStack().is(item)) {
            return cost;
        }
        return new ItemCost(item.builtInRegistryHolder(), cost.count(), cost.components());
    }

    private static ItemStack convertStack(ItemStack stack, VillageCurrency currency) {
        if (!isCurrencyItem(stack)) {
            return stack.copy();
        }
        return new ItemStack(currency.item().orElse(Items.EMERALD), stack.getCount());
    }

    public static boolean setVillageCurrencyFromPayment(ServerLevel level, Player player, InteractionHand ledgerHand, BlockPos clickedPos) {
        if (!(level.getBlockState(clickedPos).getBlock() instanceof BellBlock)) {
            return false;
        }
        InteractionHand paymentHand = ledgerHand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack payment = player.getItemInHand(paymentHand);
        Optional<VillageCurrency> requestedCurrency = currencyFromItem(payment, availableCurrencies());
        if (requestedCurrency.isEmpty()) {
            return false;
        }
        if (!EcologyConfig.villageCurrenciesEnabled()) {
            player.displayClientMessage(Component.translatable("message.ecology.village.currency.disabled"), true);
            return true;
        }
        List<VillageCurrency> selectable = playerSelectableCurrencies();
        if (!selectable.contains(requestedCurrency.get())) {
            player.displayClientMessage(Component.translatable(
                    "message.ecology.village.currency.unavailable",
                    requestedCurrency.get().serializedName()), true);
            return true;
        }

        int cost = EcologyConfig.villageCurrencyChangeCost();
        if (!player.getAbilities().instabuild && payment.getCount() < cost) {
            player.displayClientMessage(Component.translatable(
                    "message.ecology.village.currency.insufficient",
                    cost,
                    currencyName(requestedCurrency.get())), true);
            return true;
        }

        BlockPos anchor = villageAnchor(level, clickedPos);
        VillageCurrencyLedger.get(level).setCurrency(anchor, requestedCurrency.get(), true);
        if (!player.getAbilities().instabuild && cost > 0) {
            payment.shrink(cost);
        }
        refreshNearbyOffers(level, anchor);
        player.displayClientMessage(Component.translatable(
                "message.ecology.village.currency.set",
                requestedCurrency.get().serializedName(),
                formatPos(anchor)), true);
        level.playSound(null, clickedPos, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 0.8F, 1.0F);
        return true;
    }

    public static boolean isCurrencyItem(ItemStack stack) {
        return currencyFromItem(stack, eligibleCurrencies()).isPresent();
    }

    public static boolean isEligibleCurrency(VillageCurrency currency) {
        return eligibleCurrencies().contains(currency);
    }

    public static List<VillageCurrency> eligibleCurrencies() {
        List<VillageCurrency> available = availableCurrencies();
        List<VillageCurrency> configured = configuredCurrencies(EcologyConfig.villageEligibleCurrencyNames(), available);
        List<VillageCurrency> blocked = configuredCurrencies(EcologyConfig.villageBlockedCurrencyNames(), List.of());
        List<VillageCurrency> currencies = configured.stream()
                .filter(currency -> !blocked.contains(currency))
                .toList();
        if (!currencies.isEmpty()) {
            return currencies;
        }
        return available.isEmpty() ? List.of(VillageCurrency.EMERALD) : available;
    }

    public static List<VillageCurrency> naturalCurrencies() {
        List<VillageCurrency> eligible = eligibleCurrencies();
        List<VillageCurrency> configured = configuredCurrencies(EcologyConfig.villageNaturalCurrencyNames(), eligible);
        List<VillageCurrency> currencies = configured.stream()
                .filter(eligible::contains)
                .toList();
        return currencies.isEmpty() ? eligible : currencies;
    }

    private static VillageCurrency availableCurrency(VillageCurrency currency) {
        return isEligibleCurrency(currency) ? currency : fallbackCurrency();
    }

    private static List<VillageCurrency> availableCurrencies() {
        return List.of(VillageCurrency.values()).stream()
                .filter(VillageCurrency::available)
                .toList();
    }

    private static List<VillageCurrency> playerSelectableCurrencies() {
        List<VillageCurrency> eligible = eligibleCurrencies();
        List<VillageCurrency> configured = configuredCurrencies(EcologyConfig.villagePlayerSelectableCurrencyNames(), eligible);
        List<VillageCurrency> currencies = configured.stream()
                .filter(eligible::contains)
                .toList();
        return currencies.isEmpty() ? eligible : currencies;
    }

    private static List<VillageCurrency> eyeGeneCurrencies() {
        return eligibleCurrencies();
    }

    private static Optional<VillageCurrency> currencyFromItem(ItemStack stack, List<VillageCurrency> candidates) {
        if (stack.isEmpty()) {
            return Optional.empty();
        }
        Item item = stack.getItem();
        return candidates.stream()
                .filter(currency -> currency.matches(item))
                .findFirst();
    }

    private static List<VillageCurrency> configuredCurrencies(List<String> names, List<VillageCurrency> fallbackWhenEmpty) {
        if (names.isEmpty()) {
            return fallbackWhenEmpty;
        }
        List<VillageCurrency> currencies = new ArrayList<>();
        for (String name : names) {
            VillageCurrency.optionalByName(name)
                    .filter(VillageCurrency::available)
                    .filter(currency -> !currencies.contains(currency))
                    .ifPresent(currencies::add);
        }
        return currencies;
    }

    private static VillageCurrency fallbackCurrency() {
        List<VillageCurrency> available = availableCurrencies();
        if (available.contains(VillageCurrency.EMERALD)) {
            return VillageCurrency.EMERALD;
        }
        return available.isEmpty() ? VillageCurrency.EMERALD : available.getFirst();
    }

    private static Component currencyName(VillageCurrency currency) {
        return currency.item()
                .map(item -> item.getDefaultInstance().getHoverName())
                .orElseGet(() -> Component.literal(currency.serializedName()));
    }

    private static void refreshNearbyOffers(ServerLevel level, BlockPos anchor) {
        level.getEntitiesOfClass(Villager.class, new AABB(anchor).inflate(ANCHOR_SEARCH_RADIUS))
                .forEach(VillageCurrencySystem::convertOffers);
    }

    private static String formatPos(BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }
}
