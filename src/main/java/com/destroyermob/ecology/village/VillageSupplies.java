package com.destroyermob.ecology.village;

import com.destroyermob.ecology.EcologyConfig;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.entity.player.TradeWithVillagerEvent;

public final class VillageSupplies {
    private static final int OFFER_REFRESH_TICKS = 100;
    private static final int DONATION_MAX_ITEMS = 32;

    private VillageSupplies() {
    }

    public static void tickVillager(ServerLevel level, Villager villager) {
        if (!EcologyConfig.villageSuppliesEnabled() || villager.isBaby()) {
            return;
        }

        BlockPos anchor = VillageCurrencySystem.villageAnchor(level, villager);
        updateAccount(level, anchor, false);
        if (villager.tickCount >= 60 && Math.floorMod(villager.tickCount + villager.getId(), OFFER_REFRESH_TICKS) == 0) {
            refreshOffers(villager);
        }
    }

    public static void prepareTrades(Villager villager) {
        if (!EcologyConfig.villageSuppliesEnabled() || !(villager.level() instanceof ServerLevel level) || villager.isBaby()) {
            return;
        }
        updateAccount(level, VillageCurrencySystem.villageAnchor(level, villager), false);
        refreshOffers(villager);
    }

    public static void onTrade(TradeWithVillagerEvent event) {
        AbstractVillager merchant = event.getAbstractVillager();
        if (!EcologyConfig.villageSuppliesEnabled()
                || !(merchant instanceof Villager villager)
                || !(villager.level() instanceof ServerLevel level)) {
            return;
        }

        Optional<TradeShape> shape = tradeShape(villager, event.getMerchantOffer());
        if (shape.isEmpty() || VillageWelfare.isConfined(villager)) {
            return;
        }
        if (VillagePlayerTrades.isPlayerStocked(event.getMerchantOffer())) {
            return;
        }

        BlockPos anchor = VillageCurrencySystem.villageAnchor(level, villager);
        VillageSupplyLedger.VillageSupplyAccount account = updateAccount(level, anchor, false);
        double amount = tradeSupplyAmount(event.getMerchantOffer(), shape.get());
        if (shape.get().villagerBuysFromPlayer()) {
            account.add(level, shape.get().category(), amount);
        } else {
            account.consume(level, shape.get().category(), amount);
        }
        refreshOffers(villager);
    }

    public static VillageSupplyReport report(ServerLevel level, BlockPos center) {
        BlockPos anchor = VillageCurrencySystem.villageAnchor(level, center);
        return updateAccount(level, anchor, true).report(anchor);
    }

    public static void sendReport(Player player, VillageSupplyReport report) {
        player.sendSystemMessage(Component.translatable("message.ecology.village.supply.header", report.center().toShortString()).withStyle(ChatFormatting.GOLD));
        if (report.simulatedTicks() >= 1200L) {
            player.sendSystemMessage(Component.translatable(
                    "message.ecology.village.supply.catchup",
                    String.format(Locale.ROOT, "%.1f", report.simulatedTicks() / 24000.0D)).withStyle(ChatFormatting.GRAY));
        }
        if (report.confinedVillagerCount() > 0) {
            player.sendSystemMessage(Component.translatable(
                    "message.ecology.village.supply.confined",
                    report.confinedVillagerCount()).withStyle(ChatFormatting.RED));
        }
        for (VillageSupplyCategory category : VillageSupplyCategory.values()) {
            int stock = report.stock(category);
            ChatFormatting style = stock < 30 ? ChatFormatting.RED : stock >= 80 ? ChatFormatting.GREEN : ChatFormatting.GRAY;
            player.sendSystemMessage(Component.translatable(
                    "message.ecology.village.supply.line",
                    Component.translatable(category.translationKey()),
                    stock,
                    signed(report.dailyDelta(category))).withStyle(style));
        }
        if (report.shortages().isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.ecology.village.supply.steady").withStyle(ChatFormatting.AQUA));
            return;
        }
        report.shortages().stream()
                .limit(4)
                .forEach(category -> player.sendSystemMessage(Component.translatable(
                        "message.ecology.village.supply.shortage",
                        Component.translatable(category.translationKey())).withStyle(ChatFormatting.YELLOW)));
    }

    public static boolean donateOtherHand(ServerLevel level, Player player, InteractionHand ledgerHand, BlockPos center) {
        InteractionHand donationHand = ledgerHand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack donation = player.getItemInHand(donationHand);
        if (donation.isEmpty()) {
            return false;
        }
        Optional<VillageSupplyCategory> category = categoryForItem(donation)
                .or(() -> categoryFromItemPath(donation));
        if (category.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.ecology.village.supply.donate_unsupported"), true);
            return true;
        }

        int donatedItems = Math.min(DONATION_MAX_ITEMS, donation.getCount());
        int supplyValue = donationSupplyValue(donation, donatedItems);
        VillageSupplyLedger.VillageSupplyAccount account = updateAccount(level, VillageCurrencySystem.villageAnchor(level, center), false);
        account.add(level, category.get(), supplyValue);
        if (!player.getAbilities().instabuild) {
            donation.shrink(donatedItems);
        }

        player.displayClientMessage(Component.translatable(
                "message.ecology.village.supply.donated",
                donatedItems,
                Component.translatable(category.get().translationKey()),
                supplyValue), true);
        level.playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.5F, 1.2F);
        return true;
    }

    public static void refreshOffers(Villager villager) {
        if (!EcologyConfig.villageSuppliesEnabled() || !(villager.level() instanceof ServerLevel level)) {
            return;
        }

        VillageSupplyLedger.VillageSupplyAccount account = updateAccount(level, VillageCurrencySystem.villageAnchor(level, villager), false);
        MerchantOffers offers = villager.getOffers();
        MerchantOffers adjustedOffers = new MerchantOffers();
        boolean changed = false;
        for (MerchantOffer offer : offers) {
            if (VillagePlayerTrades.isPlayerStocked(offer)) {
                if (offer.isOutOfStock()) {
                    changed = true;
                } else {
                    adjustedOffers.add(offer);
                }
                continue;
            }
            MerchantOffer adjusted = adjustOffer(villager, account, offer);
            adjustedOffers.add(adjusted);
            changed |= adjusted != offer;
        }
        if (changed) {
            villager.setOffers(adjustedOffers);
        }
    }

    private static VillageSupplyLedger.VillageSupplyAccount updateAccount(ServerLevel level, BlockPos anchor, boolean forceSurvey) {
        VillageSupplyLedger ledger = VillageSupplyLedger.get(level);
        VillageSupplyLedger.VillageSupplyAccount account = ledger.accountFor(anchor);
        if (account.needsUpdate(level, forceSurvey)) {
            account.simulateTo(level);
        }
        if (account.needsSurvey(level, forceSurvey)) {
            VillageEcologyReport report = VillageEcology.survey(level, anchor);
            int confinedVillagers = VillageWelfare.confinedVillagerCount(level, anchor);
            account.refreshSurvey(level, report, confinedVillagers, dailyDeltas(level, anchor, report, confinedVillagers));
        }
        return account;
    }

    private static Map<VillageSupplyCategory, Double> dailyDeltas(ServerLevel level, BlockPos anchor, VillageEcologyReport report, int confinedVillagers) {
        EnumMap<VillageSupplyCategory, Double> deltas = new EnumMap<>(VillageSupplyCategory.class);
        for (VillageSupplyCategory category : VillageSupplyCategory.values()) {
            deltas.put(category, 0.0D);
        }

        add(deltas, VillageSupplyCategory.FOOD, report.foodScore() / 18.0D - report.villagerCount() * 1.4D);
        add(deltas, VillageSupplyCategory.WOOD, report.greenScore() / 28.0D + report.maintenanceScore() / 35.0D - report.villagerCount() * 0.18D);
        add(deltas, VillageSupplyCategory.STONE, report.shelterScore() / 32.0D + report.maintenanceScore() / 36.0D - report.villagerCount() * 0.16D);
        add(deltas, VillageSupplyCategory.METAL, report.safetyScore() / 45.0D - report.villagerCount() * 0.15D);
        add(deltas, VillageSupplyCategory.PAPER, report.shelterScore() / 42.0D - report.villagerCount() * 0.08D);
        add(deltas, VillageSupplyCategory.CLOTH, report.greenScore() / 36.0D - report.villagerCount() * 0.08D);
        add(deltas, VillageSupplyCategory.TOOLS, report.maintenanceScore() / 45.0D + report.safetyScore() / 70.0D - report.villagerCount() * 0.12D);
        add(deltas, VillageSupplyCategory.MEDICINE, report.greenScore() / 65.0D + report.safetyScore() / 65.0D - report.villagerCount() * 0.10D);
        add(deltas, VillageSupplyCategory.VALUABLES, report.score() / 85.0D - report.villagerCount() * 0.04D);
        if (confinedVillagers > 0) {
            add(deltas, VillageSupplyCategory.FOOD, -1.2D * confinedVillagers);
            add(deltas, VillageSupplyCategory.MEDICINE, -1.0D * confinedVillagers);
            add(deltas, VillageSupplyCategory.TOOLS, -0.6D * confinedVillagers);
            add(deltas, VillageSupplyCategory.VALUABLES, -0.8D * confinedVillagers);
        }

        Map<VillagerProfession, Integer> professions = professionCounts(level, anchor);
        professions.forEach((profession, count) -> addProfessionDeltas(deltas, profession, count));
        return deltas;
    }

    private static Map<VillagerProfession, Integer> professionCounts(ServerLevel level, BlockPos anchor) {
        Map<VillagerProfession, Integer> counts = new HashMap<>();
        int radius = EcologyConfig.VILLAGE_ECOLOGY_RADIUS.get();
        AABB area = AABB.encapsulatingFullBlocks(anchor.offset(-radius, -8, -radius), anchor.offset(radius, 8, radius));
        for (Villager villager : level.getEntitiesOfClass(Villager.class, area, villager -> villager.isAlive() && !villager.isBaby() && !VillageWelfare.isConfined(villager))) {
            VillagerProfession profession = villager.getVillagerData().getProfession();
            if (VillageVocations.isAssignableProfession(profession)) {
                counts.merge(profession, 1, Integer::sum);
            }
        }
        return counts;
    }

    private static void addProfessionDeltas(EnumMap<VillageSupplyCategory, Double> deltas, VillagerProfession profession, int count) {
        double workers = count;
        if (profession == VillagerProfession.FARMER) {
            add(deltas, VillageSupplyCategory.FOOD, 10.0D * workers);
            add(deltas, VillageSupplyCategory.PAPER, 0.8D * workers);
        } else if (profession == VillagerProfession.FISHERMAN) {
            add(deltas, VillageSupplyCategory.FOOD, 8.0D * workers);
        } else if (profession == VillagerProfession.BUTCHER) {
            add(deltas, VillageSupplyCategory.FOOD, 7.0D * workers);
        } else if (profession == VillagerProfession.FLETCHER) {
            add(deltas, VillageSupplyCategory.WOOD, 5.0D * workers);
            add(deltas, VillageSupplyCategory.TOOLS, 1.5D * workers);
        } else if (profession == VillagerProfession.SHEPHERD) {
            add(deltas, VillageSupplyCategory.CLOTH, 7.0D * workers);
        } else if (profession == VillagerProfession.LEATHERWORKER) {
            add(deltas, VillageSupplyCategory.CLOTH, 5.5D * workers);
            add(deltas, VillageSupplyCategory.TOOLS, 1.0D * workers);
        } else if (profession == VillagerProfession.LIBRARIAN) {
            add(deltas, VillageSupplyCategory.PAPER, 7.0D * workers);
            add(deltas, VillageSupplyCategory.VALUABLES, 0.7D * workers);
        } else if (profession == VillagerProfession.CARTOGRAPHER) {
            add(deltas, VillageSupplyCategory.PAPER, 6.0D * workers);
            add(deltas, VillageSupplyCategory.WOOD, -1.0D * workers);
        } else if (profession == VillagerProfession.MASON) {
            add(deltas, VillageSupplyCategory.STONE, 8.0D * workers);
            add(deltas, VillageSupplyCategory.TOOLS, -0.8D * workers);
        } else if (profession == VillagerProfession.ARMORER) {
            add(deltas, VillageSupplyCategory.TOOLS, 4.5D * workers);
            add(deltas, VillageSupplyCategory.METAL, -3.5D * workers);
        } else if (profession == VillagerProfession.TOOLSMITH) {
            add(deltas, VillageSupplyCategory.TOOLS, 5.0D * workers);
            add(deltas, VillageSupplyCategory.METAL, -2.5D * workers);
            add(deltas, VillageSupplyCategory.WOOD, -1.0D * workers);
        } else if (profession == VillagerProfession.WEAPONSMITH) {
            add(deltas, VillageSupplyCategory.TOOLS, 4.0D * workers);
            add(deltas, VillageSupplyCategory.METAL, -3.0D * workers);
        } else if (profession == VillagerProfession.CLERIC) {
            add(deltas, VillageSupplyCategory.MEDICINE, 6.0D * workers);
            add(deltas, VillageSupplyCategory.VALUABLES, -0.8D * workers);
        }
    }

    private static MerchantOffer adjustOffer(Villager villager, VillageSupplyLedger.VillageSupplyAccount account, MerchantOffer offer) {
        Optional<TradeShape> shape = tradeShape(villager, offer);
        int baseSpecialPriceDiff = baseSpecialPriceDiff(offer);
        int welfarePenalty = VillageWelfare.pricePenalty(villager);
        if (shape.isEmpty()) {
            clearOfferSupplyData(offer);
            if (welfarePenalty == 0 && offer.getSpecialPriceDiff() == baseSpecialPriceDiff) {
                return offer;
            }
            MerchantOffer adjusted = copyOffer(offer, offer.getMaxUses(), baseSpecialPriceDiff + welfarePenalty);
            markBaseSpecialPrice(adjusted, baseSpecialPriceDiff);
            return adjusted;
        }

        int baseMaxUses = baseMaxUses(offer, shape.get());
        boolean marketEligible = welfarePenalty == 0;
        int adjustedMaxUses = adjustedMaxUses(baseMaxUses, account.stockLevel(shape.get().category()), shape.get().villagerBuysFromPlayer(), marketEligible);
        int adjustedSpecialPriceDiff = baseSpecialPriceDiff + welfarePenalty;
        if (adjustedMaxUses == offer.getMaxUses() && adjustedSpecialPriceDiff == offer.getSpecialPriceDiff()) {
            markOffer(offer, baseMaxUses, baseSpecialPriceDiff, shape.get());
            return offer;
        }

        MerchantOffer adjusted = copyOffer(offer, adjustedMaxUses, adjustedSpecialPriceDiff);
        markOffer(adjusted, baseMaxUses, baseSpecialPriceDiff, shape.get());
        return adjusted;
    }

    private static int baseMaxUses(MerchantOffer offer, TradeShape shape) {
        if (offer instanceof VillageSupplyOfferHolder holder
                && holder.ecology$getBaseMaxUses() > 0
                && holder.ecology$getSupplyCategory().orElse(null) == shape.category()
                && holder.ecology$isSupplyBuyingOffer() == shape.villagerBuysFromPlayer()) {
            return holder.ecology$getBaseMaxUses();
        }
        return Math.max(1, offer.getMaxUses());
    }

    private static int baseSpecialPriceDiff(MerchantOffer offer) {
        if (offer instanceof VillageSupplyOfferHolder holder && holder.ecology$getBaseSpecialPriceDiff() != Integer.MIN_VALUE) {
            return holder.ecology$getBaseSpecialPriceDiff();
        }
        return offer.getSpecialPriceDiff();
    }

    private static void markOffer(MerchantOffer offer, int baseMaxUses, int baseSpecialPriceDiff, TradeShape shape) {
        if (offer instanceof VillageSupplyOfferHolder holder) {
            holder.ecology$setBaseMaxUses(baseMaxUses);
            holder.ecology$setBaseSpecialPriceDiff(baseSpecialPriceDiff);
            holder.ecology$setSupplyCategory(Optional.of(shape.category()));
            holder.ecology$setSupplyBuyingOffer(shape.villagerBuysFromPlayer());
        }
    }

    private static void markBaseSpecialPrice(MerchantOffer offer, int baseSpecialPriceDiff) {
        if (offer instanceof VillageSupplyOfferHolder holder) {
            holder.ecology$setBaseSpecialPriceDiff(baseSpecialPriceDiff);
        }
    }

    private static void clearOfferSupplyData(MerchantOffer offer) {
        if (offer instanceof VillageSupplyOfferHolder holder) {
            holder.ecology$setBaseMaxUses(-1);
            holder.ecology$setSupplyCategory(Optional.empty());
            holder.ecology$setSupplyBuyingOffer(false);
        }
    }

    private static int adjustedMaxUses(int baseMaxUses, int stock, boolean villagerBuysFromPlayer, boolean marketEligible) {
        if (!marketEligible) {
            return baseMaxUses;
        }
        if (villagerBuysFromPlayer) {
            if (stock < 25) {
                return Math.max(1, baseMaxUses + Math.max(1, baseMaxUses / 2));
            }
            if (stock >= 90) {
                return Math.max(1, baseMaxUses * 3 / 4);
            }
            return baseMaxUses;
        }
        if (stock < 15) {
            return Math.max(1, baseMaxUses / 4);
        }
        if (stock < 35) {
            return Math.max(1, baseMaxUses / 2);
        }
        if (stock >= 85) {
            return baseMaxUses + Math.max(1, baseMaxUses / 2);
        }
        if (stock >= 70) {
            return baseMaxUses + Math.max(1, baseMaxUses / 4);
        }
        return baseMaxUses;
    }

    private static MerchantOffer copyOffer(MerchantOffer offer, int maxUses, int specialPriceDiff) {
        MerchantOffer adjusted = new MerchantOffer(
                offer.getItemCostA(),
                offer.getItemCostB(),
                offer.getResult().copy(),
                offer.getUses(),
                maxUses,
                offer.getXp(),
                offer.getPriceMultiplier(),
                offer.getDemand());
        adjusted.setSpecialPriceDiff(specialPriceDiff);
        return adjusted;
    }

    private static Optional<TradeShape> tradeShape(Villager villager, MerchantOffer offer) {
        if (VillageCurrencySystem.isCurrencyItem(offer.getResult())) {
            Optional<VillageSupplyCategory> category = categoryForItem(offer.getItemCostA().itemStack())
                    .or(() -> offer.getItemCostB().flatMap(cost -> categoryForItem(cost.itemStack())))
                    .or(() -> categoryForProfession(villager.getVillagerData().getProfession()));
            return category.map(value -> new TradeShape(value, true));
        }
        Optional<VillageSupplyCategory> category = categoryForItem(offer.getResult())
                .or(() -> categoryForProfession(villager.getVillagerData().getProfession()));
        return category.map(value -> new TradeShape(value, false));
    }

    private static double tradeSupplyAmount(MerchantOffer offer, TradeShape shape) {
        if (shape.villagerBuysFromPlayer()) {
            int costCount = offer.getItemCostA().count() + offer.getItemCostB().map(cost -> cost.count()).orElse(0);
            return Math.min(14.0D, 3.0D + costCount / 4.0D);
        }
        return Math.min(12.0D, 1.5D + offer.getResult().getCount() / 2.0D);
    }

    private static Optional<VillageSupplyCategory> categoryForProfession(VillagerProfession profession) {
        if (profession == VillagerProfession.FARMER || profession == VillagerProfession.FISHERMAN || profession == VillagerProfession.BUTCHER) {
            return Optional.of(VillageSupplyCategory.FOOD);
        }
        if (profession == VillagerProfession.FLETCHER) {
            return Optional.of(VillageSupplyCategory.WOOD);
        }
        if (profession == VillagerProfession.MASON) {
            return Optional.of(VillageSupplyCategory.STONE);
        }
        if (profession == VillagerProfession.ARMORER || profession == VillagerProfession.TOOLSMITH || profession == VillagerProfession.WEAPONSMITH) {
            return Optional.of(VillageSupplyCategory.TOOLS);
        }
        if (profession == VillagerProfession.LIBRARIAN || profession == VillagerProfession.CARTOGRAPHER) {
            return Optional.of(VillageSupplyCategory.PAPER);
        }
        if (profession == VillagerProfession.SHEPHERD || profession == VillagerProfession.LEATHERWORKER) {
            return Optional.of(VillageSupplyCategory.CLOTH);
        }
        if (profession == VillagerProfession.CLERIC) {
            return Optional.of(VillageSupplyCategory.MEDICINE);
        }
        return Optional.empty();
    }

    private static Optional<VillageSupplyCategory> categoryForItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return Optional.empty();
        }
        if (stack.get(DataComponents.FOOD) != null || stack.is(ItemTags.FISHES) || stack.is(ItemTags.MEAT)) {
            return Optional.of(VillageSupplyCategory.FOOD);
        }
        if (stack.is(ItemTags.LOGS) || stack.is(ItemTags.PLANKS) || stack.is(ItemTags.SAPLINGS)
                || stack.is(Items.STICK) || stack.is(Items.BAMBOO)) {
            return Optional.of(VillageSupplyCategory.WOOD);
        }
        if (stack.is(ItemTags.STONE_CRAFTING_MATERIALS) || stack.is(ItemTags.STONE_TOOL_MATERIALS)
                || stack.is(ItemTags.STONE_BRICKS) || stack.is(Items.COBBLESTONE) || stack.is(Items.DEEPSLATE)
                || stack.is(Items.BRICK) || stack.is(Items.CLAY_BALL)) {
            return Optional.of(VillageSupplyCategory.STONE);
        }
        if (stack.is(Items.IRON_INGOT) || stack.is(Items.RAW_IRON) || stack.is(Items.IRON_NUGGET)
                || stack.is(Items.COPPER_INGOT) || stack.is(Items.RAW_COPPER)
                || stack.is(Items.GOLD_INGOT) || stack.is(Items.RAW_GOLD) || stack.is(Items.GOLD_NUGGET)
                || stack.is(ItemTags.IRON_ORES) || stack.is(ItemTags.COPPER_ORES) || stack.is(ItemTags.GOLD_ORES)
                || stack.is(ItemTags.COALS)) {
            return Optional.of(VillageSupplyCategory.METAL);
        }
        if (stack.is(Items.PAPER) || stack.is(Items.BOOK) || stack.is(Items.WRITABLE_BOOK)
                || stack.is(Items.WRITTEN_BOOK) || stack.is(ItemTags.LECTERN_BOOKS) || stack.is(ItemTags.BOOKSHELF_BOOKS)) {
            return Optional.of(VillageSupplyCategory.PAPER);
        }
        if (stack.is(ItemTags.WOOL) || stack.is(Items.STRING) || stack.is(Items.LEATHER)
                || stack.is(Items.RABBIT_HIDE) || stack.is(Items.FEATHER)) {
            return Optional.of(VillageSupplyCategory.CLOTH);
        }
        if (stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES) || stack.is(ItemTags.PICKAXES)
                || stack.is(ItemTags.SHOVELS) || stack.is(ItemTags.HOES) || stack.is(ItemTags.ARMOR_ENCHANTABLE)
                || stack.is(Items.FLINT) || stack.is(Items.ARROW)) {
            return Optional.of(VillageSupplyCategory.TOOLS);
        }
        if (stack.is(Items.HONEY_BOTTLE) || stack.is(Items.GLISTERING_MELON_SLICE) || stack.is(Items.GOLDEN_CARROT)
                || stack.is(Items.GOLDEN_APPLE) || stack.is(Items.SPIDER_EYE) || stack.is(Items.FERMENTED_SPIDER_EYE)
                || stack.is(Items.RABBIT_FOOT) || stack.is(Items.BLAZE_POWDER) || stack.is(Items.BONE_MEAL)) {
            return Optional.of(VillageSupplyCategory.MEDICINE);
        }
        if (VillageCurrencySystem.isCurrencyItem(stack) || stack.is(Items.DIAMOND) || stack.is(Items.LAPIS_LAZULI)
                || stack.is(Items.QUARTZ) || stack.is(Items.AMETHYST_SHARD) || stack.is(ItemTags.DIAMOND_ORES)
                || stack.is(ItemTags.EMERALD_ORES) || stack.is(ItemTags.LAPIS_ORES)) {
            return Optional.of(VillageSupplyCategory.VALUABLES);
        }
        return categoryFromItemPath(stack);
    }

    private static Optional<VillageSupplyCategory> categoryFromItemPath(ItemStack stack) {
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String path = key.getPath();
        if (path.contains("ruby") || path.contains("sapphire") || path.contains("gem") || path.contains("jewel")) {
            return Optional.of(VillageSupplyCategory.VALUABLES);
        }
        if (path.contains("ingot") || path.contains("nugget") || path.contains("raw_") || path.contains("ore")) {
            return Optional.of(VillageSupplyCategory.METAL);
        }
        if (path.contains("log") || path.contains("plank") || path.contains("wood")) {
            return Optional.of(VillageSupplyCategory.WOOD);
        }
        if (path.contains("stone") || path.contains("brick") || path.contains("slate")) {
            return Optional.of(VillageSupplyCategory.STONE);
        }
        if (path.contains("paper") || path.contains("book") || path.contains("map")) {
            return Optional.of(VillageSupplyCategory.PAPER);
        }
        if (path.contains("wool") || path.contains("cloth") || path.contains("leather") || path.contains("string")) {
            return Optional.of(VillageSupplyCategory.CLOTH);
        }
        if (path.contains("tool") || path.contains("sword") || path.contains("axe") || path.contains("pickaxe") || path.contains("shovel")) {
            return Optional.of(VillageSupplyCategory.TOOLS);
        }
        if (path.contains("medicine") || path.contains("potion") || path.contains("herb")) {
            return Optional.of(VillageSupplyCategory.MEDICINE);
        }
        return Optional.empty();
    }

    private static int donationSupplyValue(ItemStack stack, int itemCount) {
        int value = Math.max(1, itemCount);
        if (stack.is(Items.DIAMOND) || stack.is(Items.EMERALD) || stack.is(Items.GOLD_INGOT)
                || stack.is(Items.GOLDEN_APPLE) || stack.is(Items.ENCHANTED_GOLDEN_APPLE)) {
            value *= 3;
        } else if (stack.is(Items.IRON_INGOT) || stack.is(Items.COPPER_INGOT) || stack.is(Items.BOOK)
                || stack.is(Items.LEATHER) || stack.is(Items.HONEY_BOTTLE)) {
            value *= 2;
        }
        return Math.min(35, value);
    }

    private static String signed(int delta) {
        if (delta > 0) {
            return "+" + delta;
        }
        return Integer.toString(delta);
    }

    private static void add(EnumMap<VillageSupplyCategory, Double> deltas, VillageSupplyCategory category, double amount) {
        deltas.merge(category, amount, Double::sum);
    }

    private record TradeShape(VillageSupplyCategory category, boolean villagerBuysFromPlayer) {
    }
}
