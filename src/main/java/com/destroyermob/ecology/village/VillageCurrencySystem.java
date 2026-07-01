package com.destroyermob.ecology.village;

import com.destroyermob.ecology.EcologyConfig;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;

public final class VillageCurrencySystem {
    private static final int VILLAGE_KEY_SIZE = 96;
    private static final int ANCHOR_SEARCH_RADIUS = 48;
    private static final int CURRENCY_REFRESH_TICKS = 100;

    private VillageCurrencySystem() {
    }

    public static void tickVillager(ServerLevel level, Villager villager) {
        tickVillageEntity(level, villager);
    }

    public static void tickVillageEntity(ServerLevel level, Entity entity) {
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
        if (!(villager.level() instanceof ServerLevel level)) {
            return;
        }
        VillageCurrency currency = assignCurrency(level, villager);
        MerchantOffers offers = villager.getOffers();
        MerchantOffers convertedOffers = new MerchantOffers();
        boolean changed = false;

        for (MerchantOffer offer : offers) {
            MerchantOffer converted = convertOffer(offer, currency);
            convertedOffers.add(converted);
            changed |= converted != offer;
        }

        if (changed) {
            villager.setOffers(convertedOffers);
        }
    }

    public static void inheritCurrency(Villager child, Villager parent, AgeableMob otherParent) {
        VillageCurrency currency = availableCurrency(currency(parent));
        if (currency == VillageCurrency.EMERALD && otherParent instanceof Villager otherVillager) {
            currency = availableCurrency(currency(otherVillager));
        }
        setCurrency(child, currency);
    }

    public static VillageCurrency currency(Entity entity) {
        if (entity instanceof VillageCurrencyHolder holder) {
            return availableCurrency(holder.ecology$getVillageCurrency());
        }
        return VillageCurrency.EMERALD;
    }

    public static VillageCurrency assignCurrency(ServerLevel level, Villager villager) {
        return assignCurrency(level, villager, anchorFor(level, villager));
    }

    public static BlockPos villageAnchor(ServerLevel level, Villager villager) {
        return anchorFor(level, villager);
    }

    public static BlockPos villageAnchor(ServerLevel level, BlockPos center) {
        return nearestPoi(level, center, PoiTypes.MEETING).orElse(center);
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
        VillageCurrency currency = currencyFor(level, anchor);
        setCurrency(entity, currency);
        return currency;
    }

    private static void setCurrency(Entity entity, VillageCurrency currency) {
        currency = availableCurrency(currency);
        if (entity instanceof VillageCurrencyHolder holder && holder.ecology$getVillageCurrency() != currency) {
            holder.ecology$setVillageCurrency(currency);
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
                .map(villager -> assignCurrency(level, villager));
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

    private static VillageCurrency currencyFor(ServerLevel level, BlockPos anchor) {
        int cellX = Math.floorDiv(anchor.getX(), VILLAGE_KEY_SIZE);
        int cellZ = Math.floorDiv(anchor.getZ(), VILLAGE_KEY_SIZE);
        long hash = 0xCBF29CE484222325L;
        hash = mix(hash, level.dimension().location().toString().hashCode());
        hash = mix(hash, cellX);
        hash = mix(hash, cellZ);
        List<VillageCurrency> currencies = availableCurrencies();
        return currencies.get(Mth.positiveModulo((int)(hash ^ hash >>> 32), currencies.size()));
    }

    private static long mix(long hash, int value) {
        hash ^= value;
        return hash * 0x100000001B3L;
    }

    private static MerchantOffer convertOffer(MerchantOffer offer, VillageCurrency currency) {
        if (VillagePlayerTrades.isPlayerStocked(offer)) {
            return offer;
        }
        ItemCost costA = convertCost(offer.getItemCostA(), currency);
        Optional<ItemCost> costB = offer.getItemCostB().map(cost -> convertCost(cost, currency));
        ItemStack result = convertStack(offer.getResult(), currency);

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
        return new ItemCost(item.builtInRegistryHolder(), cost.count(), cost.components());
    }

    private static ItemStack convertStack(ItemStack stack, VillageCurrency currency) {
        if (!isCurrencyItem(stack)) {
            return stack.copy();
        }
        return new ItemStack(currency.item().orElse(Items.EMERALD), stack.getCount());
    }

    public static boolean isCurrencyItem(ItemStack stack) {
        return stack.is(Items.EMERALD)
                || VillageCurrency.RUBY.matches(stack.getItem())
                || VillageCurrency.SAPPHIRE.matches(stack.getItem());
    }

    private static VillageCurrency availableCurrency(VillageCurrency currency) {
        return currency.available() ? currency : VillageCurrency.EMERALD;
    }

    private static List<VillageCurrency> availableCurrencies() {
        return List.of(VillageCurrency.values()).stream()
                .filter(VillageCurrency::available)
                .toList();
    }
}
