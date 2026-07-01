package com.destroyermob.ecology.village;

import com.destroyermob.ecology.EcologyConfig;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntSupplier;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.JigsawReplacementProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.entity.player.TradeWithVillagerEvent;

public final class VillageHouseholds {
    private static final String STORED_PLOT_CORNER_DIMENSION_TAG = "EcologyStoredHousePlotCornerDimension";
    private static final String STORED_PLOT_CORNER_POS_TAG = "EcologyStoredHousePlotCornerPos";
    private static final String STORED_PLOT_BANNER_COLOR_TAG = "EcologyStoredHousePlotBannerColor";
    private static final int VERTICAL_SCAN_RANGE = 8;
    private static final int HOME_SEARCH_RADIUS = 24;
    private static final int VACANT_HOME_SEARCH_RADIUS = 48;
    private static final int HOME_CLUSTER_RADIUS = 7;
    private static final int HOME_DEED_SEARCH_RADIUS = 4;
    private static final int HOME_DEED_HOME_RADIUS = HOME_CLUSTER_RADIUS + 6;
    private static final int HOME_DEED_SIGN_LINE_LENGTH = 15;
    private static final int CONSTRUCTION_SEARCH_RADIUS = 96;
    private static final int HOME_UPGRADE_SCAN_RADIUS = 5;
    private static final int HOME_UPGRADE_VERTICAL_RANGE = 3;
    private static final int MIN_PLOT_SIZE = 7;
    private static final int STARTING_SAVINGS = 8;
    private static final int MOVE_OUT_STARTING_SAVINGS = 6;
    private static final String HOME_DEED_HEADER = "Home of";
    private static final List<HouseTemplate> TEMPLATES = List.of(
            vanilla("plains_small_1", "plains", "plains_small_house_1", HouseSize.SMALL, 2),
            vanilla("plains_small_3", "plains", "plains_small_house_3", HouseSize.SMALL, 2),
            vanilla("plains_small_5", "plains", "plains_small_house_5", HouseSize.SMALL, 2),
            vanilla("plains_medium_1", "plains", "plains_medium_house_1", HouseSize.MEDIUM, 4),
            vanilla("plains_medium_2", "plains", "plains_medium_house_2", HouseSize.MEDIUM, 4),
            vanilla("plains_big_1", "plains", "plains_big_house_1", HouseSize.LARGE, 6),
            vanilla("desert_small_1", "desert", "desert_small_house_1", HouseSize.SMALL, 2),
            vanilla("desert_small_3", "desert", "desert_small_house_3", HouseSize.SMALL, 2),
            vanilla("desert_small_6", "desert", "desert_small_house_6", HouseSize.SMALL, 2),
            vanilla("desert_medium_1", "desert", "desert_medium_house_1", HouseSize.MEDIUM, 4),
            vanilla("desert_medium_2", "desert", "desert_medium_house_2", HouseSize.LARGE, 6),
            vanilla("savanna_small_1", "savanna", "savanna_small_house_1", HouseSize.SMALL, 2),
            vanilla("savanna_small_3", "savanna", "savanna_small_house_3", HouseSize.SMALL, 2),
            vanilla("savanna_small_5", "savanna", "savanna_small_house_5", HouseSize.SMALL, 2),
            vanilla("savanna_medium_1", "savanna", "savanna_medium_house_1", HouseSize.MEDIUM, 4),
            vanilla("savanna_medium_2", "savanna", "savanna_medium_house_2", HouseSize.LARGE, 6),
            vanilla("taiga_small_1", "taiga", "taiga_small_house_1", HouseSize.SMALL, 2),
            vanilla("taiga_small_2", "taiga", "taiga_small_house_2", HouseSize.SMALL, 2),
            vanilla("taiga_small_4", "taiga", "taiga_small_house_4", HouseSize.SMALL, 2),
            vanilla("taiga_medium_1", "taiga", "taiga_medium_house_1", HouseSize.MEDIUM, 4),
            vanilla("taiga_medium_3", "taiga", "taiga_medium_house_3", HouseSize.LARGE, 6),
            vanilla("snowy_small_1", "snowy", "snowy_small_house_1", HouseSize.SMALL, 2),
            vanilla("snowy_small_2", "snowy", "snowy_small_house_2", HouseSize.SMALL, 2),
            vanilla("snowy_small_4", "snowy", "snowy_small_house_4", HouseSize.SMALL, 2),
            vanilla("snowy_medium_1", "snowy", "snowy_medium_house_1", HouseSize.MEDIUM, 4),
            vanilla("snowy_medium_3", "snowy", "snowy_medium_house_3", HouseSize.LARGE, 6));

    private VillageHouseholds() {
    }

    public static boolean recordHousePlotCorner(ItemStack ledger, ServerLevel level, BlockPos clickedPos, InteractionHand ledgerHand, Player player) {
        if (!EcologyConfig.villageHouseConstructionEnabled()) {
            return false;
        }
        InteractionHand permitHand = ledgerHand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack permit = player.getItemInHand(permitHand);
        if (!(permit.getItem() instanceof BannerItem bannerItem)) {
            return false;
        }

        Optional<GlobalPos> villageAnchor = VillageRelocation.storedVillageAnchor(ledger);
        if (villageAnchor.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.ecology.village.household.plot.no_village"), true);
            return true;
        }
        if (!villageAnchor.get().dimension().equals(level.dimension())) {
            player.displayClientMessage(Component.translatable("message.ecology.village.household.plot.wrong_dimension"), true);
            return true;
        }

        Optional<GlobalPos> firstCorner = storedPlotCorner(ledger);
        if (firstCorner.isEmpty() || !firstCorner.get().dimension().equals(level.dimension())) {
            storePlotCorner(ledger, level, clickedPos, bannerItem.getColor());
            player.displayClientMessage(Component.translatable("message.ecology.village.household.plot.first", formatPos(clickedPos)), true);
            level.playSound(null, clickedPos, SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundSource.PLAYERS, 0.55F, 1.15F);
            return true;
        }

        BlockPos first = firstCorner.get().pos();
        BlockPos min = new BlockPos(Math.min(first.getX(), clickedPos.getX()), Math.min(first.getY(), clickedPos.getY()), Math.min(first.getZ(), clickedPos.getZ()));
        BlockPos max = new BlockPos(Math.max(first.getX(), clickedPos.getX()), Math.max(first.getY(), clickedPos.getY()), Math.max(first.getZ(), clickedPos.getZ()));
        DyeColor bannerColor = storedPlotBannerColor(ledger).orElse(bannerItem.getColor());
        if (!validatePlot(level, min, max, player)) {
            return true;
        }

        VillageHouseholdLedger.get(level).registerPlot(level, villageAnchor.get().pos(), min, max, bannerColor);
        clearStoredPlotCorner(ledger);
        if (!player.getAbilities().instabuild) {
            permit.shrink(1);
        }
        player.displayClientMessage(Component.translatable(
                "message.ecology.village.household.plot.registered",
                formatPos(min),
                formatPos(max)), true);
        level.playSound(null, clickedPos, SoundEvents.VILLAGER_YES, SoundSource.PLAYERS, 0.7F, 1.05F);
        return true;
    }

    public static void tickVillager(ServerLevel level, Villager villager) {
        if (!EcologyConfig.villageHouseholdsEnabled()) {
            return;
        }
        int interval = EcologyConfig.VILLAGE_HOUSEHOLD_UPDATE_INTERVAL_TICKS.get();
        if (villager.tickCount < 80 || Math.floorMod(villager.tickCount + villager.getId() * 43, interval) != 0) {
            return;
        }

        Optional<UUID> householdId = ensureHousehold(level, villager);
        if (householdId.isEmpty() || villager.isBaby()) {
            return;
        }
        UUID activeHousehold = householdId.get();
        if (tryMoveOut(level, villager, activeHousehold)) {
            ensureHomeDeed(level, activeHousehold);
            activeHousehold = householdId(villager).orElse(activeHousehold);
        }
        tickHouseConstruction(level, villager, activeHousehold);
        ensureHomeDeed(level, activeHousehold);
    }

    public static void recordBirth(ServerLevel level, Villager child, Villager parent, AgeableMob otherParent) {
        if (!EcologyConfig.villageHouseholdsEnabled() || !(child instanceof VillageHouseholdHolder childHolder)) {
            return;
        }

        Optional<UUID> parentHousehold = ensureHousehold(level, parent);
        if (parentHousehold.isEmpty()) {
            return;
        }
        UUID householdId = parentHousehold.get();
        childHolder.ecology$setHouseholdId(Optional.of(householdId));
        childHolder.ecology$setParentIds(Optional.of(parent.getUUID()), otherParent instanceof Villager otherVillager
                ? Optional.of(otherVillager.getUUID())
                : Optional.empty());
        childHolder.ecology$setPartnerId(Optional.empty());

        VillageHouseholdLedger ledger = VillageHouseholdLedger.get(level);
        VillageHouseholdLedger.HouseholdAccount account = ledger.accountFor(householdId);
        if (otherParent instanceof Villager otherVillager) {
            Optional<UUID> otherHousehold = ensureHousehold(level, otherVillager);
            if (otherHousehold.isPresent() && !otherHousehold.get().equals(householdId)) {
                ledger.merge(level, householdId, otherHousehold.get());
                if (otherVillager instanceof VillageHouseholdHolder otherHolder) {
                    otherHolder.ecology$setHouseholdId(Optional.of(householdId));
                }
            }
            if (parent instanceof VillageHouseholdHolder parentHolder) {
                parentHolder.ecology$setPartnerId(Optional.of(otherVillager.getUUID()));
            }
            if (otherVillager instanceof VillageHouseholdHolder otherHolder) {
                otherHolder.ecology$setPartnerId(Optional.of(parent.getUUID()));
            }
            if (account.home().isEmpty()) {
                homeFor(level, parent).or(() -> homeFor(level, otherVillager)).ifPresent(home -> account.setHome(level, home));
            }
        }
        account.touch(level);
        ensureHomeDeed(level, householdId);
    }

    public static void onAdulthood(ServerLevel level, Villager villager) {
        if (!EcologyConfig.villageHouseholdsEnabled()) {
            return;
        }
        ensureHousehold(level, villager).ifPresent(householdId -> tryMoveOut(level, villager, householdId));
    }

    public static void onTrade(TradeWithVillagerEvent event) {
        if (!EcologyConfig.villageHouseholdsEnabled()) {
            return;
        }
        AbstractVillager merchant = event.getAbstractVillager();
        if (!(merchant instanceof Villager villager) || !(villager.level() instanceof ServerLevel level) || villager.isBaby()) {
            return;
        }
        Optional<UUID> householdId = ensureHousehold(level, villager);
        if (householdId.isEmpty()) {
            return;
        }
        VillageHouseholdLedger.get(level)
                .accountFor(householdId.get())
                .addSavings(level, tradeIncome(event.getMerchantOffer()));
    }

    public static VillageHouseholdReport report(ServerLevel level, BlockPos center) {
        if (!EcologyConfig.villageHouseholdsEnabled()) {
            return new VillageHouseholdReport(center.immutable(), 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        }

        int radius = EcologyConfig.VILLAGE_ECOLOGY_RADIUS.get();
        AABB area = AABB.encapsulatingFullBlocks(
                center.offset(-radius, -VERTICAL_SCAN_RANGE, -radius),
                center.offset(radius, VERTICAL_SCAN_RANGE, radius));
        List<Villager> villagers = level.getEntitiesOfClass(Villager.class, area, Villager::isAlive);
        VillageHouseholdLedger ledger = VillageHouseholdLedger.get(level);
        Map<UUID, HouseholdSummary> summaries = new HashMap<>();

        for (Villager villager : villagers) {
            Optional<UUID> householdId = ensureHousehold(level, villager);
            if (householdId.isEmpty()) {
                continue;
            }
            HouseholdSummary summary = summaries.computeIfAbsent(householdId.get(), ignored -> new HouseholdSummary());
            if (villager.isBaby()) {
                summary.children++;
            } else {
                summary.adults++;
            }
            if (villager instanceof VillageHouseholdHolder holder && holder.ecology$getPartnerId().isPresent()) {
                summary.hasPartner = true;
            }
        }

        int childCount = summaries.values().stream().mapToInt(summary -> summary.children).sum();
        int pairedHouseholds = (int)summaries.values().stream()
                .filter(summary -> summary.hasPartner && summary.adults >= 2)
                .count();
        int adultChildrenAtHome = 0;
        for (Villager villager : villagers) {
            Optional<UUID> householdId = householdId(villager);
            if (householdId.isPresent() && isAdultChildLivingWithParent(villagers, villager, householdId.get())) {
                adultChildrenAtHome++;
            }
        }

        Set<BlockPos> claimedHomes = claimedHomes(ledger);
        int emptyHomes = countVacantHomes(level, center, claimedHomes, radius);
        PlotCounts plotCounts = plotCounts(ledger, center, Math.max(radius, CONSTRUCTION_SEARCH_RADIUS));
        int crowdedHouseholds = 0;
        int expansionReadyHouseholds = 0;
        int totalSavings = 0;
        for (Map.Entry<UUID, HouseholdSummary> entry : summaries.entrySet()) {
            VillageHouseholdLedger.HouseholdAccount account = ledger.accountFor(entry.getKey());
            HouseholdSummary summary = entry.getValue();
            int capacity = account.home().map(home -> bedCountNear(level, home)).orElse(0);
            boolean crowded = capacity < summary.memberCount();
            if (crowded) {
                crowdedHouseholds++;
                if (account.savings() >= EcologyConfig.VILLAGE_HOUSEHOLD_EXPANSION_READY_SAVINGS.get()) {
                    expansionReadyHouseholds++;
                }
            }
            totalSavings += account.savingsLevel();
        }

        return new VillageHouseholdReport(
                center.immutable(),
                summaries.size(),
                pairedHouseholds,
                childCount,
                adultChildrenAtHome,
                emptyHomes,
                plotCounts.available(),
                plotCounts.active(),
                plotCounts.completed(),
                crowdedHouseholds,
                expansionReadyHouseholds,
                totalSavings);
    }

    public static void sendReport(Player player, VillageHouseholdReport report) {
        player.sendSystemMessage(Component.translatable("message.ecology.village.household.header", report.center().toShortString()).withStyle(ChatFormatting.GOLD));
        player.sendSystemMessage(Component.translatable(
                "message.ecology.village.household.summary",
                report.householdCount(),
                report.pairedHouseholds(),
                report.childCount(),
                report.totalSavings()).withStyle(ChatFormatting.GRAY));
        if (report.emptyHomes() > 0) {
            player.sendSystemMessage(Component.translatable(
                    "message.ecology.village.household.empty_homes",
                    report.emptyHomes()).withStyle(ChatFormatting.AQUA));
        }
        if (report.approvedPlots() > 0 || report.activeConstruction() > 0) {
            player.sendSystemMessage(Component.translatable(
                    "message.ecology.village.household.plots",
                    report.approvedPlots(),
                    report.activeConstruction(),
                    report.completedConstructedHomes()).withStyle(ChatFormatting.GRAY));
        }
        if (report.adultChildrenAtHome() > 0) {
            player.sendSystemMessage(Component.translatable(
                    "message.ecology.village.household.move_out",
                    report.adultChildrenAtHome()).withStyle(ChatFormatting.YELLOW));
        }
        if (report.crowdedHouseholds() > 0) {
            player.sendSystemMessage(Component.translatable(
                    "message.ecology.village.household.crowded",
                    report.crowdedHouseholds()).withStyle(ChatFormatting.YELLOW));
        }
        if (report.expansionReadyHouseholds() > 0) {
            player.sendSystemMessage(Component.translatable(
                    "message.ecology.village.household.expansion_ready",
                    report.expansionReadyHouseholds()).withStyle(ChatFormatting.GREEN));
        }
        if (report.householdCount() == 0) {
            player.sendSystemMessage(Component.translatable("message.ecology.village.household.none").withStyle(ChatFormatting.GRAY));
        }
    }

    public static boolean inspectHomeDeed(ServerLevel level, BlockPos clickedPos, Player player) {
        if (!EcologyConfig.villageHouseholdsEnabled() || !isHomeDeedSign(level, clickedPos)) {
            return false;
        }
        Optional<HomeDeedTarget> target = householdHomeForDeed(level, clickedPos);
        if (target.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.ecology.village.household.deed.unclaimed"), true);
            return true;
        }
        sendHouseholdHistory(player, level, target.get());
        level.playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.7F, 1.05F);
        return true;
    }

    public static Optional<GlobalPos> storedPlotCorner(ItemStack ledger) {
        CompoundTag root = ledger.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!root.contains(STORED_PLOT_CORNER_DIMENSION_TAG, 8) || !root.contains(STORED_PLOT_CORNER_POS_TAG, 99)) {
            return Optional.empty();
        }
        ResourceLocation dimension = ResourceLocation.tryParse(root.getString(STORED_PLOT_CORNER_DIMENSION_TAG));
        if (dimension == null) {
            return Optional.empty();
        }
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, dimension);
        return Optional.of(GlobalPos.of(key, BlockPos.of(root.getLong(STORED_PLOT_CORNER_POS_TAG))));
    }

    private static void tickHouseConstruction(ServerLevel level, Villager villager, UUID householdId) {
        if (!EcologyConfig.villageHouseConstructionEnabled() || !isWorkTime(level)) {
            return;
        }
        VillageHouseholdLedger ledger = VillageHouseholdLedger.get(level);
        Optional<VillageHouseholdLedger.HousePlot> activePlot = ledger.activePlotFor(householdId);
        if (activePlot.isPresent()) {
            advanceConstruction(level, villager, householdId, activePlot.get());
            return;
        }
        tryStartHouseConstruction(level, villager, householdId, true);
    }

    private static boolean tryStartHouseConstruction(ServerLevel level, Villager villager, UUID householdId) {
        return tryStartHouseConstruction(level, villager, householdId, true);
    }

    private static boolean tryStartHouseConstruction(ServerLevel level, Villager villager, UUID householdId, boolean allowExistingHomeUpgrade) {
        if (!EcologyConfig.villageHouseConstructionEnabled()) {
            return false;
        }
        VillageHouseholdLedger ledger = VillageHouseholdLedger.get(level);
        if (ledger.activePlotFor(householdId).isPresent()) {
            return false;
        }
        VillageHouseholdLedger.HouseholdAccount account = ledger.accountFor(householdId);
        int memberCount = householdMemberCount(level, villager.blockPosition(), householdId);
        int currentCapacity = account.home().map(home -> bedCountNear(level, home)).orElse(0);
        if (memberCount <= currentCapacity) {
            return false;
        }
        if (allowExistingHomeUpgrade && tryUpgradeExistingHome(level, villager, householdId, account, memberCount, currentCapacity)) {
            return true;
        }
        if (account.savings() < EcologyConfig.VILLAGE_HOUSE_CONSTRUCTION_SAVINGS_COST.get()) {
            return false;
        }

        BlockPos anchor = VillageCurrencySystem.villageAnchor(level, villager);
        Optional<ConstructionChoice> choice = chooseConstruction(level, villager, anchor, memberCount, ledger.availablePlotsNear(anchor, CONSTRUCTION_SEARCH_RADIUS));
        if (choice.isEmpty() || !account.spend(level, EcologyConfig.VILLAGE_HOUSE_CONSTRUCTION_SAVINGS_COST.get())) {
            return false;
        }
        choice.get().plot().assign(householdId, choice.get().template().id());
        ledger.setDirty();
        level.playSound(null, villager.blockPosition(), SoundEvents.UI_STONECUTTER_TAKE_RESULT, SoundSource.NEUTRAL, 0.65F, 1.0F);
        return true;
    }

    private static Optional<ConstructionChoice> chooseConstruction(
            ServerLevel level,
            Villager villager,
            BlockPos anchor,
            int memberCount,
            List<VillageHouseholdLedger.HousePlot> plots) {
        List<ConstructionChoice> choices = new ArrayList<>();
        List<HouseTemplate> templates = templatesForStyle(villageStyle(level, anchor));
        for (VillageHouseholdLedger.HousePlot plot : plots) {
            for (HouseTemplate template : templates) {
                if (plotFitsTemplate(level, plot, template)) {
                    int weight = template.weight(memberCount);
                    if (weight > 0) {
                        choices.add(new ConstructionChoice(plot, template, weight));
                    }
                }
            }
        }
        if (choices.isEmpty() && !"plains".equals(villageStyle(level, anchor))) {
            for (VillageHouseholdLedger.HousePlot plot : plots) {
                for (HouseTemplate template : templatesForStyle("plains")) {
                    if (plotFitsTemplate(level, plot, template)) {
                        int weight = template.weight(memberCount);
                        if (weight > 0) {
                            choices.add(new ConstructionChoice(plot, template, weight));
                        }
                    }
                }
            }
        }
        if (choices.isEmpty()) {
            return Optional.empty();
        }
        choices.sort(Comparator.comparingDouble(choice -> choice.plot().villageAnchor().distSqr(anchor)));
        int totalWeight = choices.stream().mapToInt(ConstructionChoice::weight).sum();
        int selected = villager.getRandom().nextInt(Math.max(1, totalWeight));
        for (ConstructionChoice choice : choices) {
            selected -= choice.weight();
            if (selected < 0) {
                return Optional.of(choice);
            }
        }
        return Optional.of(choices.get(0));
    }

    private static void advanceConstruction(ServerLevel level, Villager villager, UUID householdId, VillageHouseholdLedger.HousePlot plot) {
        Optional<HouseTemplate> template = templateById(plot.templateId().orElse(""));
        if (template.isEmpty() || plot.completed()) {
            return;
        }
        Optional<StructureTemplate> structure = structureTemplate(level, template.get());
        if (structure.isEmpty() || !plotFitsTemplate(level, plot, structure.get(), true)) {
            return;
        }

        int steps = constructionSteps(structure.get());
        if (plot.progress() < steps) {
            plot.advanceTo(plot.progress() + 1);
            VillageHouseholdLedger.get(level).setDirty();
            if (plot.progress() == 1 || plot.progress() == steps || plot.progress() % 3 == 0) {
                level.playSound(null, villager.blockPosition(), SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 0.45F, 0.9F + villager.getRandom().nextFloat() * 0.25F);
                villager.swing(InteractionHand.MAIN_HAND);
            }
            return;
        }

        BlockPos origin = buildOrigin(plot, structure.get());
        if (!placeVanillaHouse(level, origin, structure.get(), villager.getRandom())) {
            return;
        }
        Vec3i size = structure.get().getSize(Rotation.NONE);
        BlockPos center = origin.offset(size.getX() / 2, Math.min(2, Math.max(0, size.getY() - 1)), size.getZ() / 2);
        Optional<BlockPos> home = firstBedInStructure(level, origin, size);
        BlockPos upgradeCenter = home.orElse(center);
        int missingBeds = Math.max(0, template.get().targetBedCount() - bedCountNear(level, upgradeCenter));
        List<BedPlacement> addedBeds = placeAdditionalBeds(level, upgradeCenter, missingBeds);
        home = home.or(() -> addedBeds.stream().map(BedPlacement::foot).findFirst());
        placeHouseholdBanner(level, home.orElse(upgradeCenter), plot.bannerColor());
        plot.advanceTo(1);
        completeConstruction(level, householdId, plot, home.orElse(upgradeCenter), origin);
    }

    private static boolean placeVanillaHouse(ServerLevel level, BlockPos origin, StructureTemplate structure, RandomSource random) {
        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setRotation(Rotation.NONE)
                .setKnownShape(true)
                .setIgnoreEntities(true)
                .setFinalizeEntities(false)
                .addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK)
                .addProcessor(JigsawReplacementProcessor.INSTANCE);
        return structure.placeInWorld(level, origin, origin, settings, random, 18);
    }

    private static void completeConstruction(ServerLevel level, UUID householdId, VillageHouseholdLedger.HousePlot plot, BlockPos home, BlockPos origin) {
        plot.complete();
        canonicalBedFoot(level, home).ifPresent(pos -> {
            VillageHouseholdLedger.get(level).accountFor(householdId).setHome(level, pos);
            moveHouseholdHomeMemory(level, householdId, pos);
        });
        VillageHouseholdLedger.get(level).setDirty();
        ensureHomeDeed(level, householdId);
        level.playSound(null, origin, SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 0.75F, 1.0F);
    }

    private static int constructionSteps(StructureTemplate structure) {
        Vec3i size = structure.getSize(Rotation.NONE);
        int footprint = Math.max(1, size.getX() * size.getZ());
        int blocksPerStep = Math.max(1, EcologyConfig.VILLAGE_HOUSE_CONSTRUCTION_BLOCKS_PER_STEP.get());
        return Math.max(1, (footprint + blocksPerStep - 1) / blocksPerStep);
    }

    private static Optional<StructureTemplate> structureTemplate(ServerLevel level, HouseTemplate template) {
        return level.getServer().getStructureManager().get(template.location());
    }

    private static Optional<BlockPos> firstBedInStructure(ServerLevel level, BlockPos origin, Vec3i size) {
        for (BlockPos candidate : BlockPos.betweenClosed(
                origin.offset(-1, -1, -1),
                origin.offset(size.getX(), size.getY(), size.getZ()))) {
            Optional<BlockPos> foot = canonicalBedFoot(level, candidate);
            if (foot.isPresent()) {
                return foot;
            }
        }
        return Optional.empty();
    }

    private static void moveHouseholdHomeMemory(ServerLevel level, UUID householdId, BlockPos home) {
        int radius = EcologyConfig.VILLAGE_ECOLOGY_RADIUS.get() + CONSTRUCTION_SEARCH_RADIUS;
        AABB area = AABB.encapsulatingFullBlocks(home.offset(-radius, -VERTICAL_SCAN_RANGE, -radius), home.offset(radius, VERTICAL_SCAN_RANGE, radius));
        for (Villager villager : level.getEntitiesOfClass(Villager.class, area, Villager::isAlive)) {
            if (householdId(villager).filter(householdId::equals).isPresent()) {
                villager.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), home));
            }
        }
    }

    private static Optional<HouseTemplate> templateById(String templateId) {
        return TEMPLATES.stream().filter(template -> template.id().equals(templateId)).findFirst();
    }

    private static BlockPos buildOrigin(VillageHouseholdLedger.HousePlot plot, StructureTemplate structure) {
        Vec3i size = structure.getSize(Rotation.NONE);
        int x = plot.min().getX() + Math.max(0, (plot.width() - size.getX()) / 2);
        int z = plot.min().getZ() + Math.max(0, (plot.depth() - size.getZ()) / 2);
        return new BlockPos(x, plot.groundY() + 1, z);
    }

    private static boolean plotFitsTemplate(ServerLevel level, VillageHouseholdLedger.HousePlot plot, HouseTemplate template) {
        return structureTemplate(level, template)
                .filter(structure -> plotFitsTemplate(level, plot, structure, false))
                .isPresent();
    }

    private static boolean plotFitsTemplate(ServerLevel level, VillageHouseholdLedger.HousePlot plot, StructureTemplate structure, boolean allowExistingConstruction) {
        Vec3i size = structure.getSize(Rotation.NONE);
        if (plot.width() < size.getX() || plot.depth() < size.getZ()) {
            return false;
        }
        BlockPos origin = buildOrigin(plot, structure);
        for (BlockPos ground : BlockPos.betweenClosed(origin.below(), origin.offset(size.getX() - 1, -1, size.getZ() - 1))) {
            if (!level.hasChunkAt(ground) || level.getBlockState(ground).getCollisionShape(level, ground).isEmpty()) {
                return false;
            }
        }
        for (BlockPos pos : BlockPos.betweenClosed(origin, origin.offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1))) {
            if (!level.hasChunkAt(pos)) {
                return false;
            }
            BlockState state = level.getBlockState(pos);
            if (!canReplaceForConstruction(state) && !(allowExistingConstruction && isHouseConstructionBlock(state))) {
                return false;
            }
        }
        return true;
    }

    private static boolean validatePlot(ServerLevel level, BlockPos min, BlockPos max, Player player) {
        int width = max.getX() - min.getX() + 1;
        int depth = max.getZ() - min.getZ() + 1;
        int maxSize = EcologyConfig.VILLAGE_HOUSE_PLOT_MAX_SIZE.get();
        if (width < MIN_PLOT_SIZE || depth < MIN_PLOT_SIZE || width > maxSize || depth > maxSize) {
            player.displayClientMessage(Component.translatable("message.ecology.village.household.plot.size", MIN_PLOT_SIZE, maxSize), true);
            return false;
        }
        if (max.getY() - min.getY() > 2) {
            player.displayClientMessage(Component.translatable("message.ecology.village.household.plot.level"), true);
            return false;
        }
        VillageHouseholdLedger.HousePlot temporary = new VillageHouseholdLedger.HousePlot(UUID.randomUUID(), min, min, max, DyeColor.WHITE);
        if (TEMPLATES.stream().noneMatch(template -> plotFitsTemplate(level, temporary, template))) {
            player.displayClientMessage(Component.translatable("message.ecology.village.household.plot.blocked"), true);
            return false;
        }
        return true;
    }

    private static boolean canReplaceForConstruction(BlockState state) {
        return state.isAir()
                || state.is(Blocks.SHORT_GRASS)
                || state.is(Blocks.TALL_GRASS)
                || state.is(Blocks.FERN)
                || state.is(Blocks.LARGE_FERN)
                || state.is(BlockTags.FLOWERS)
                || state.is(BlockTags.SAPLINGS);
    }

    private static boolean isHouseConstructionBlock(BlockState state) {
        return state.is(Blocks.OAK_PLANKS)
                || state.is(Blocks.OAK_LOG)
                || state.is(Blocks.GLASS_PANE)
                || state.is(BlockTags.BEDS)
                || state.is(BlockTags.BANNERS);
    }

    private static boolean tryUpgradeExistingHome(
            ServerLevel level,
            Villager villager,
            UUID householdId,
            VillageHouseholdLedger.HouseholdAccount account,
            int memberCount,
            int currentCapacity) {
        Optional<BlockPos> home = account.home().flatMap(pos -> canonicalBedFoot(level, pos));
        if (home.isEmpty()) {
            return false;
        }
        int missingBeds = Math.min(
                Math.max(0, memberCount - currentCapacity),
                EcologyConfig.VILLAGE_HOME_UPGRADE_MAX_BEDS.get());
        if (missingBeds <= 0 || account.savings() < EcologyConfig.VILLAGE_HOME_UPGRADE_SAVINGS_COST.get()) {
            return false;
        }

        List<BedPlacement> placements = findBedPlacements(level, home.get(), missingBeds);
        if (placements.isEmpty() || !account.spend(level, EcologyConfig.VILLAGE_HOME_UPGRADE_SAVINGS_COST.get())) {
            return false;
        }
        placements.forEach(placement -> placeBed(level, placement));
        placeHouseholdBanner(level, home.get(), householdColor(householdId));
        ensureHomeDeed(level, householdId);
        account.setHome(level, home.get());
        moveHouseholdHomeMemory(level, householdId, home.get());
        level.playSound(null, villager.blockPosition(), SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 0.55F, 1.0F);
        return true;
    }

    private static List<BedPlacement> placeAdditionalBeds(ServerLevel level, BlockPos center, int missingBeds) {
        List<BedPlacement> placements = findBedPlacements(level, center, missingBeds);
        placements.forEach(placement -> placeBed(level, placement));
        return placements;
    }

    private static List<BedPlacement> findBedPlacements(ServerLevel level, BlockPos center, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        List<BedPlacement> placements = new ArrayList<>();
        Set<BlockPos> reserved = new HashSet<>();
        for (BlockPos candidate : BlockPos.betweenClosed(
                center.offset(-HOME_UPGRADE_SCAN_RADIUS, -HOME_UPGRADE_VERTICAL_RANGE, -HOME_UPGRADE_SCAN_RADIUS),
                center.offset(HOME_UPGRADE_SCAN_RADIUS, HOME_UPGRADE_VERTICAL_RANGE, HOME_UPGRADE_SCAN_RADIUS))) {
            if (!level.hasChunkAt(candidate) || candidate.distSqr(center) > HOME_UPGRADE_SCAN_RADIUS * HOME_UPGRADE_SCAN_RADIUS) {
                continue;
            }
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                BlockPos head = candidate.relative(facing);
                if (reserved.contains(candidate) || reserved.contains(head) || !canPlaceBed(level, candidate, facing)) {
                    continue;
                }
                BedPlacement placement = new BedPlacement(candidate.immutable(), facing);
                placements.add(placement);
                reserved.add(placement.foot());
                reserved.add(placement.head());
                if (placements.size() >= limit) {
                    return placements;
                }
            }
        }
        return placements;
    }

    private static boolean canPlaceBed(ServerLevel level, BlockPos foot, Direction facing) {
        BlockPos head = foot.relative(facing);
        return level.hasChunkAt(head)
                && canReplaceForHomeUpgrade(level.getBlockState(foot))
                && canReplaceForHomeUpgrade(level.getBlockState(head))
                && hasSolidFloor(level, foot)
                && hasSolidFloor(level, head)
                && hasShelter(level, foot)
                && hasShelter(level, head);
    }

    private static boolean canReplaceForHomeUpgrade(BlockState state) {
        return state.isAir() || state.is(Blocks.SHORT_GRASS) || state.is(Blocks.FERN) || state.is(BlockTags.FLOWERS);
    }

    private static boolean hasSolidFloor(ServerLevel level, BlockPos pos) {
        BlockPos below = pos.below();
        return level.hasChunkAt(below) && !level.getBlockState(below).getCollisionShape(level, below).isEmpty();
    }

    private static boolean hasShelter(ServerLevel level, BlockPos pos) {
        for (int y = 2; y <= 5; y++) {
            BlockPos roof = pos.above(y);
            if (level.hasChunkAt(roof) && !level.getBlockState(roof).getCollisionShape(level, roof).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static void placeBed(ServerLevel level, BedPlacement placement) {
        level.setBlock(placement.foot(), Blocks.RED_BED.defaultBlockState()
                .setValue(BedBlock.FACING, placement.facing())
                .setValue(BedBlock.PART, BedPart.FOOT), 3);
        level.setBlock(placement.head(), Blocks.RED_BED.defaultBlockState()
                .setValue(BedBlock.FACING, placement.facing())
                .setValue(BedBlock.PART, BedPart.HEAD), 3);
    }

    private static void placeHouseholdBanner(ServerLevel level, BlockPos center, DyeColor color) {
        if (hasHouseholdBannerNear(level, center)) {
            return;
        }
        for (BlockPos candidate : BlockPos.betweenClosed(center.offset(-3, -1, -3), center.offset(3, 2, 3))) {
            if (!level.hasChunkAt(candidate)
                    || !canReplaceForHomeUpgrade(level.getBlockState(candidate))
                    || !hasSolidFloor(level, candidate)) {
                continue;
            }
            BlockState banner = bannerBlock(color).defaultBlockState();
            if (banner.hasProperty(BannerBlock.ROTATION)) {
                banner = banner.setValue(BannerBlock.ROTATION, Math.floorMod(center.hashCode(), 16));
            }
            level.setBlock(candidate, banner, 3);
            return;
        }
    }

    private static void ensureHomeDeed(ServerLevel level, UUID householdId) {
        Optional<BlockPos> home = VillageHouseholdLedger.get(level)
                .accountFor(householdId)
                .home()
                .flatMap(pos -> canonicalBedFoot(level, pos));
        if (home.isEmpty()) {
            return;
        }
        Optional<BlockPos> deed = homeDeedNear(level, home.get());
        if (deed.isEmpty()) {
            deed = placeHomeDeed(level, home.get());
        }
        deed.ifPresent(pos -> updateHomeDeedSign(level, pos, householdMembersNear(level, home.get(), householdId)));
    }

    private static Optional<BlockPos> placeHomeDeed(ServerLevel level, BlockPos center) {
        for (BlockPos candidate : BlockPos.betweenClosed(
                center.offset(-HOME_DEED_SEARCH_RADIUS, -1, -HOME_DEED_SEARCH_RADIUS),
                center.offset(HOME_DEED_SEARCH_RADIUS, 2, HOME_DEED_SEARCH_RADIUS))) {
            if (!level.hasChunkAt(candidate)
                    || !canReplaceForHomeUpgrade(level.getBlockState(candidate))
                    || !hasSolidFloor(level, candidate)) {
                continue;
            }
            BlockState sign = Blocks.OAK_SIGN.defaultBlockState();
            if (sign.hasProperty(StandingSignBlock.ROTATION)) {
                sign = sign.setValue(StandingSignBlock.ROTATION, Math.floorMod(center.hashCode(), 16));
            }
            level.setBlock(candidate, sign, 3);
            return Optional.of(candidate.immutable());
        }
        return Optional.empty();
    }

    private static Optional<BlockPos> homeDeedNear(ServerLevel level, BlockPos center) {
        for (BlockPos candidate : BlockPos.betweenClosed(
                center.offset(-HOME_DEED_SEARCH_RADIUS, -2, -HOME_DEED_SEARCH_RADIUS),
                center.offset(HOME_DEED_SEARCH_RADIUS, 3, HOME_DEED_SEARCH_RADIUS))) {
            if (isHomeDeedSign(level, candidate)) {
                return Optional.of(candidate.immutable());
            }
        }
        return Optional.empty();
    }

    private static boolean isHomeDeedSign(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos)
                || !level.getBlockState(pos).is(BlockTags.ALL_SIGNS)
                || !(level.getBlockEntity(pos) instanceof SignBlockEntity sign)) {
            return false;
        }
        return HOME_DEED_HEADER.equals(sign.getFrontText().getMessage(0, false).getString());
    }

    private static void updateHomeDeedSign(ServerLevel level, BlockPos pos, List<Villager> members) {
        if (!(level.getBlockEntity(pos) instanceof SignBlockEntity sign)) {
            return;
        }
        List<String> names = members.stream().map(VillageHouseholds::villagerName).toList();
        SignText text = sign.getFrontText()
                .setColor(DyeColor.BLACK)
                .setMessage(0, Component.literal(HOME_DEED_HEADER));
        for (int line = 0; line < 3; line++) {
            text = text.setMessage(line + 1, Component.literal(homeDeedLine(names, line)));
        }
        sign.setText(text, true);
        sign.setWaxed(true);
        sign.setChanged();
        BlockState state = level.getBlockState(pos);
        level.sendBlockUpdated(pos, state, state, 3);
    }

    private static String homeDeedLine(List<String> names, int line) {
        if (names.isEmpty()) {
            return line == 0 ? "Household" : "";
        }
        if (line < 2 || names.size() <= 3) {
            return line < names.size() ? trimSignLine(names.get(line)) : "";
        }
        return "+" + (names.size() - 2) + " more";
    }

    private static String trimSignLine(String line) {
        if (line.length() <= HOME_DEED_SIGN_LINE_LENGTH) {
            return line;
        }
        return line.substring(0, HOME_DEED_SIGN_LINE_LENGTH - 1) + ".";
    }

    private static Optional<HomeDeedTarget> householdHomeForDeed(ServerLevel level, BlockPos deedPos) {
        HomeDeedTarget closest = null;
        double closestDistance = HOME_DEED_HOME_RADIUS * HOME_DEED_HOME_RADIUS;
        for (Map.Entry<UUID, VillageHouseholdLedger.HouseholdAccount> entry : VillageHouseholdLedger.get(level).accountEntries()) {
            Optional<BlockPos> home = entry.getValue().home().flatMap(pos -> canonicalBedFoot(level, pos));
            if (home.isEmpty()) {
                continue;
            }
            double distance = home.get().distSqr(deedPos);
            if (distance <= closestDistance) {
                closest = new HomeDeedTarget(entry.getKey(), home.get());
                closestDistance = distance;
            }
        }
        return Optional.ofNullable(closest);
    }

    private static void sendHouseholdHistory(Player player, ServerLevel level, HomeDeedTarget target) {
        List<Villager> members = householdMembersNear(level, target.home(), target.householdId());
        Map<UUID, String> knownNames = new HashMap<>();
        for (Villager member : members) {
            knownNames.put(member.getUUID(), villagerName(member));
        }
        VillageHouseholdLedger.HouseholdAccount account = VillageHouseholdLedger.get(level).accountFor(target.householdId());
        player.sendSystemMessage(Component.translatable(
                "message.ecology.village.household.deed.header",
                householdTitle(members, target.householdId())).withStyle(ChatFormatting.GOLD));
        player.sendSystemMessage(Component.translatable(
                "message.ecology.village.household.deed.home",
                formatPos(target.home()),
                account.savingsLevel()).withStyle(ChatFormatting.GRAY));
        if (members.isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.ecology.village.household.deed.no_members").withStyle(ChatFormatting.YELLOW));
            return;
        }
        player.sendSystemMessage(Component.translatable(
                "message.ecology.village.household.deed.occupants",
                formatNameList(members.stream().map(VillageHouseholds::villagerName).toList(), 6)).withStyle(ChatFormatting.AQUA));
        for (Villager member : members) {
            player.sendSystemMessage(Component.translatable(
                    "message.ecology.village.household.deed.member",
                    villagerName(member),
                    member.isBaby() ? "child" : "adult",
                    relationshipDescription(member, members, knownNames)).withStyle(ChatFormatting.GRAY));
        }
    }

    private static String relationshipDescription(Villager villager, List<Villager> members, Map<UUID, String> knownNames) {
        if (!(villager instanceof VillageHouseholdHolder holder)) {
            return "no recorded family links";
        }
        List<String> relationships = new ArrayList<>();
        holder.ecology$getPartnerId()
                .map(id -> nameFor(id, knownNames))
                .ifPresent(name -> relationships.add("partner of " + name));
        List<String> parents = new ArrayList<>();
        holder.ecology$getFirstParentId().map(id -> nameFor(id, knownNames)).ifPresent(parents::add);
        holder.ecology$getSecondParentId().map(id -> nameFor(id, knownNames)).ifPresent(parents::add);
        if (!parents.isEmpty()) {
            relationships.add("child of " + formatNameList(parents, 2));
        }
        List<String> children = members.stream()
                .filter(child -> hasParent(child, villager.getUUID()))
                .map(VillageHouseholds::villagerName)
                .toList();
        if (!children.isEmpty()) {
            relationships.add("parent of " + formatNameList(children, 4));
        }
        return relationships.isEmpty() ? "no recorded family links" : String.join("; ", relationships);
    }

    private static boolean hasParent(Villager child, UUID parentId) {
        if (!(child instanceof VillageHouseholdHolder holder)) {
            return false;
        }
        return holder.ecology$getFirstParentId().filter(parentId::equals).isPresent()
                || holder.ecology$getSecondParentId().filter(parentId::equals).isPresent();
    }

    private static List<Villager> householdMembersNear(ServerLevel level, BlockPos center, UUID householdId) {
        int radius = Math.max(HOME_SEARCH_RADIUS, EcologyConfig.VILLAGE_ECOLOGY_RADIUS.get());
        AABB area = AABB.encapsulatingFullBlocks(
                center.offset(-radius, -VERTICAL_SCAN_RANGE, -radius),
                center.offset(radius, VERTICAL_SCAN_RANGE, radius));
        List<Villager> members = new ArrayList<>(level.getEntitiesOfClass(Villager.class, area, Villager::isAlive)
                .stream()
                .filter(villager -> householdId(villager).filter(householdId::equals).isPresent())
                .toList());
        members.sort(Comparator.comparing((Villager member) -> member.isBaby()).thenComparing(VillageHouseholds::villagerName));
        return members;
    }

    private static String householdTitle(List<Villager> members, UUID householdId) {
        List<String> names = members.stream().map(VillageHouseholds::villagerName).toList();
        if (names.isEmpty()) {
            return "Household " + shortUuid(householdId);
        }
        return formatNameList(names, 2);
    }

    private static String villagerName(Villager villager) {
        String name = villager.getDisplayName().getString();
        return name.isBlank() ? villager.getName().getString() : name;
    }

    private static String nameFor(UUID id, Map<UUID, String> knownNames) {
        return knownNames.getOrDefault(id, "unknown " + shortUuid(id));
    }

    private static String shortUuid(UUID id) {
        return id.toString().substring(0, 8);
    }

    private static String formatNameList(List<String> names, int maxShown) {
        if (names.isEmpty()) {
            return "none";
        }
        int shown = Math.min(names.size(), maxShown);
        String joined = joinNames(names.subList(0, shown));
        if (names.size() > shown) {
            return joined + " +" + (names.size() - shown) + " more";
        }
        return joined;
    }

    private static String joinNames(List<String> names) {
        if (names.size() == 1) {
            return names.get(0);
        }
        if (names.size() == 2) {
            return names.get(0) + " and " + names.get(1);
        }
        return String.join(", ", names.subList(0, names.size() - 1)) + ", and " + names.get(names.size() - 1);
    }

    private static boolean hasHouseholdBannerNear(ServerLevel level, BlockPos center) {
        for (BlockPos candidate : BlockPos.betweenClosed(center.offset(-4, -2, -4), center.offset(4, 3, 4))) {
            if (level.hasChunkAt(candidate) && level.getBlockState(candidate).is(BlockTags.BANNERS)) {
                return true;
            }
        }
        return false;
    }

    private static DyeColor householdColor(UUID householdId) {
        DyeColor[] colors = DyeColor.values();
        return colors[Math.floorMod(householdId.hashCode(), colors.length)];
    }

    private static int householdMemberCount(ServerLevel level, BlockPos center, UUID householdId) {
        int radius = EcologyConfig.VILLAGE_ECOLOGY_RADIUS.get() + CONSTRUCTION_SEARCH_RADIUS;
        AABB area = AABB.encapsulatingFullBlocks(center.offset(-radius, -VERTICAL_SCAN_RANGE, -radius), center.offset(radius, VERTICAL_SCAN_RANGE, radius));
        return level.getEntitiesOfClass(Villager.class, area, Villager::isAlive)
                .stream()
                .filter(villager -> householdId(villager).filter(householdId::equals).isPresent())
                .mapToInt(ignored -> 1)
                .sum();
    }

    private static PlotCounts plotCounts(VillageHouseholdLedger ledger, BlockPos center, int radius) {
        int radiusSqr = radius * radius;
        int available = 0;
        int active = 0;
        int completed = 0;
        for (VillageHouseholdLedger.HousePlot plot : ledger.plots()) {
            if (plot.villageAnchor().distSqr(center) > radiusSqr && plot.min().distSqr(center) > radiusSqr) {
                continue;
            }
            if (plot.completed()) {
                completed++;
            } else if (plot.householdId().isPresent()) {
                active++;
            } else {
                available++;
            }
        }
        return new PlotCounts(available, active, completed);
    }

    private static void storePlotCorner(ItemStack ledger, ServerLevel level, BlockPos pos, DyeColor bannerColor) {
        CompoundTag root = ledger.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        root.putString(STORED_PLOT_CORNER_DIMENSION_TAG, level.dimension().location().toString());
        root.putLong(STORED_PLOT_CORNER_POS_TAG, pos.asLong());
        root.putInt(STORED_PLOT_BANNER_COLOR_TAG, bannerColor.ordinal());
        ledger.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
    }

    private static Optional<DyeColor> storedPlotBannerColor(ItemStack ledger) {
        CompoundTag root = ledger.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!root.contains(STORED_PLOT_BANNER_COLOR_TAG, 99)) {
            return Optional.empty();
        }
        int index = Math.max(0, Math.min(DyeColor.values().length - 1, root.getInt(STORED_PLOT_BANNER_COLOR_TAG)));
        return Optional.of(DyeColor.values()[index]);
    }

    private static void clearStoredPlotCorner(ItemStack ledger) {
        CompoundTag root = ledger.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        root.remove(STORED_PLOT_CORNER_DIMENSION_TAG);
        root.remove(STORED_PLOT_CORNER_POS_TAG);
        root.remove(STORED_PLOT_BANNER_COLOR_TAG);
        if (root.isEmpty()) {
            ledger.remove(DataComponents.CUSTOM_DATA);
        } else {
            ledger.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
        }
    }

    private static boolean isWorkTime(ServerLevel level) {
        long dayTime = level.getDayTime() % 24000L;
        return dayTime >= 2000L && dayTime <= 9000L;
    }

    private static String formatPos(BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }

    private static Optional<UUID> ensureHousehold(ServerLevel level, Villager villager) {
        if (!(villager instanceof VillageHouseholdHolder holder)) {
            return Optional.empty();
        }
        Optional<UUID> existing = holder.ecology$getHouseholdId();
        UUID householdId = existing.orElseGet(UUID::randomUUID);
        if (existing.isEmpty()) {
            holder.ecology$setHouseholdId(Optional.of(householdId));
            VillageHouseholdLedger.get(level).accountFor(householdId).addSavings(level, STARTING_SAVINGS);
        }

        VillageHouseholdLedger.HouseholdAccount account = VillageHouseholdLedger.get(level).accountFor(householdId);
        Optional<BlockPos> normalizedHome = account.home().flatMap(home -> canonicalBedFoot(level, home));
        if (normalizedHome.isPresent()) {
            normalizedHome.filter(home -> !account.home().filter(home::equals).isPresent())
                    .ifPresent(home -> account.setHome(level, home));
        } else {
            homeFor(level, villager).ifPresent(home -> account.setHome(level, home));
        }
        account.touch(level);
        return Optional.of(householdId);
    }

    private static Optional<BlockPos> homeFor(ServerLevel level, Villager villager) {
        Optional<BlockPos> rememberedHome = villager.getBrain().getMemory(MemoryModuleType.HOME)
                .filter(home -> home.dimension().equals(level.dimension()))
                .map(GlobalPos::pos)
                .flatMap(pos -> canonicalBedFoot(level, pos));
        if (rememberedHome.isPresent()) {
            return rememberedHome.map(BlockPos::immutable);
        }
        return nearestReachableBed(level, villager, HOME_SEARCH_RADIUS);
    }

    private static Optional<BlockPos> nearestReachableBed(ServerLevel level, Villager villager, int radius) {
        BlockPos origin = villager.blockPosition();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos candidate : BlockPos.betweenClosed(
                origin.offset(-radius, -VERTICAL_SCAN_RANGE, -radius),
                origin.offset(radius, VERTICAL_SCAN_RANGE, radius))) {
            Optional<BlockPos> bedFoot = canonicalBedFoot(level, candidate);
            if (bedFoot.isEmpty() || !bedFoot.get().equals(candidate)) {
                continue;
            }
            double distance = origin.distSqr(candidate);
            if (distance < bestDistance && canReach(villager, candidate)) {
                best = candidate.immutable();
                bestDistance = distance;
            }
        }
        return Optional.ofNullable(best);
    }

    private static boolean tryMoveOut(ServerLevel level, Villager villager, UUID currentHousehold) {
        if (!(villager instanceof VillageHouseholdHolder holder)
                || holder.ecology$getFirstParentId().isEmpty()
                || !isAdultChildLivingWithParent(level, villager, currentHousehold)) {
            return false;
        }

        VillageHouseholdLedger ledger = VillageHouseholdLedger.get(level);
        VillageHouseholdLedger.HouseholdAccount currentAccount = ledger.accountFor(currentHousehold);
        int moveOutCost = EcologyConfig.VILLAGE_HOUSEHOLD_MOVE_OUT_SAVINGS.get();
        if (currentAccount.savings() < moveOutCost) {
            return false;
        }

        BlockPos anchor = VillageCurrencySystem.villageAnchor(level, villager);
        Optional<BlockPos> vacantHome = findVacantHome(level, villager, anchor, claimedHomes(ledger));
        if (vacantHome.isEmpty()) {
            tryStartHouseConstruction(level, villager, currentHousehold, false);
            return false;
        }
        if (!currentAccount.spend(level, moveOutCost)) {
            return false;
        }

        UUID newHousehold = UUID.randomUUID();
        holder.ecology$setHouseholdId(Optional.of(newHousehold));
        holder.ecology$setPartnerId(Optional.empty());
        VillageHouseholdLedger.HouseholdAccount newAccount = ledger.accountFor(newHousehold);
        newAccount.setHome(level, vacantHome.get());
        newAccount.addSavings(level, MOVE_OUT_STARTING_SAVINGS);
        villager.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), vacantHome.get()));
        ensureHomeDeed(level, currentHousehold);
        ensureHomeDeed(level, newHousehold);
        level.playSound(null, villager.blockPosition(), SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 0.55F, 1.05F);
        return true;
    }

    private static Optional<BlockPos> findVacantHome(ServerLevel level, Villager villager, BlockPos anchor, Set<BlockPos> claimedHomes) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos candidate : BlockPos.betweenClosed(
                anchor.offset(-VACANT_HOME_SEARCH_RADIUS, -VERTICAL_SCAN_RANGE, -VACANT_HOME_SEARCH_RADIUS),
                anchor.offset(VACANT_HOME_SEARCH_RADIUS, VERTICAL_SCAN_RANGE, VACANT_HOME_SEARCH_RADIUS))) {
            Optional<BlockPos> bedFoot = canonicalBedFoot(level, candidate);
            if (bedFoot.isEmpty()
                    || !bedFoot.get().equals(candidate)
                    || isInClaimedHomeCluster(candidate, claimedHomes)) {
                continue;
            }
            double distance = villager.blockPosition().distSqr(candidate);
            if (distance < bestDistance && canReach(villager, candidate)) {
                best = candidate.immutable();
                bestDistance = distance;
            }
        }
        return Optional.ofNullable(best);
    }

    private static int countVacantHomes(ServerLevel level, BlockPos center, Set<BlockPos> claimedHomes, int radius) {
        int vacant = 0;
        Set<BlockPos> countedClusters = new HashSet<>();
        for (BlockPos candidate : BlockPos.betweenClosed(
                center.offset(-radius, -VERTICAL_SCAN_RANGE, -radius),
                center.offset(radius, VERTICAL_SCAN_RANGE, radius))) {
            Optional<BlockPos> bedFoot = canonicalBedFoot(level, candidate);
            if (bedFoot.isEmpty()
                    || !bedFoot.get().equals(candidate)
                    || isInClaimedHomeCluster(candidate, claimedHomes)
                    || isInClaimedHomeCluster(candidate, countedClusters)) {
                continue;
            }
            countedClusters.add(candidate.immutable());
            vacant++;
        }
        return vacant;
    }

    private static Set<BlockPos> claimedHomes(VillageHouseholdLedger ledger) {
        Set<BlockPos> homes = new HashSet<>();
        for (VillageHouseholdLedger.HouseholdAccount account : ledger.accounts()) {
            account.home().ifPresent(homes::add);
        }
        return homes;
    }

    private static int bedCountNear(ServerLevel level, BlockPos home) {
        int beds = 0;
        for (BlockPos candidate : BlockPos.betweenClosed(
                home.offset(-HOME_CLUSTER_RADIUS, -VERTICAL_SCAN_RANGE, -HOME_CLUSTER_RADIUS),
                home.offset(HOME_CLUSTER_RADIUS, VERTICAL_SCAN_RANGE, HOME_CLUSTER_RADIUS))) {
            Optional<BlockPos> bedFoot = canonicalBedFoot(level, candidate);
            if (bedFoot.isPresent() && bedFoot.get().equals(candidate)) {
                beds++;
            }
        }
        return beds;
    }

    private static Optional<BlockPos> canonicalBedFoot(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) {
            return Optional.empty();
        }
        BlockState state = level.getBlockState(pos);
        if (!state.is(BlockTags.BEDS)) {
            return Optional.empty();
        }
        Direction facing = state.getValue(BedBlock.FACING);
        BlockPos foot = state.getValue(BedBlock.PART) == BedPart.FOOT ? pos : pos.relative(facing.getOpposite());
        BlockState footState = level.getBlockState(foot);
        if (footState.is(BlockTags.BEDS) && footState.getValue(BedBlock.PART) == BedPart.FOOT) {
            return Optional.of(foot.immutable());
        }
        return Optional.empty();
    }

    private static boolean isInClaimedHomeCluster(BlockPos candidate, Set<BlockPos> claimedHomes) {
        int radiusSqr = HOME_CLUSTER_RADIUS * HOME_CLUSTER_RADIUS;
        for (BlockPos home : claimedHomes) {
            if (home.distSqr(candidate) <= radiusSqr) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAdultChildLivingWithParent(ServerLevel level, Villager villager, UUID householdId) {
        int radius = EcologyConfig.VILLAGE_ECOLOGY_RADIUS.get();
        AABB area = AABB.encapsulatingFullBlocks(
                villager.blockPosition().offset(-radius, -VERTICAL_SCAN_RANGE, -radius),
                villager.blockPosition().offset(radius, VERTICAL_SCAN_RANGE, radius));
        List<Villager> villagers = level.getEntitiesOfClass(Villager.class, area, Villager::isAlive);
        return isAdultChildLivingWithParent(villagers, villager, householdId);
    }

    private static boolean isAdultChildLivingWithParent(List<Villager> villagers, Villager villager, UUID householdId) {
        if (villager.isBaby() || !(villager instanceof VillageHouseholdHolder holder)) {
            return false;
        }
        Set<UUID> parentIds = new HashSet<>();
        holder.ecology$getFirstParentId().ifPresent(parentIds::add);
        holder.ecology$getSecondParentId().ifPresent(parentIds::add);
        if (parentIds.isEmpty()) {
            return false;
        }
        for (Villager candidate : villagers) {
            if (candidate != villager
                    && parentIds.contains(candidate.getUUID())
                    && householdId(candidate).filter(householdId::equals).isPresent()) {
                return true;
            }
        }
        return false;
    }

    private static Optional<UUID> householdId(Villager villager) {
        if (villager instanceof VillageHouseholdHolder holder) {
            return holder.ecology$getHouseholdId();
        }
        return Optional.empty();
    }

    private static boolean canReach(Villager villager, BlockPos target) {
        if (villager.blockPosition().distSqr(target) <= 9.0D) {
            return true;
        }
        Path path = villager.getNavigation().createPath(target, 1);
        return path != null && path.canReach();
    }

    private static double tradeIncome(MerchantOffer offer) {
        int currencyPaid = currencyValue(offer.getItemCostA())
                + offer.getItemCostB().map(VillageHouseholds::currencyValue).orElse(0);
        if (currencyPaid > 0) {
            return Math.min(24, Math.max(1, currencyPaid));
        }
        if (VillageCurrencySystem.isCurrencyItem(offer.getResult())) {
            return 1.5D;
        }
        return Math.max(1.0D, Math.min(6.0D, 1.0D + offer.getXp() / 2.0D));
    }

    private static int currencyValue(ItemCost cost) {
        ItemStack stack = cost.itemStack();
        return VillageCurrencySystem.isCurrencyItem(stack) ? cost.count() : 0;
    }

    private record ConstructionChoice(VillageHouseholdLedger.HousePlot plot, HouseTemplate template, int weight) {
    }

    private record PlotCounts(int available, int active, int completed) {
    }

    private record BedPlacement(BlockPos foot, Direction facing) {
        private BlockPos head() {
            return foot.relative(facing);
        }
    }

    private record HomeDeedTarget(UUID householdId, BlockPos home) {
    }

    private enum HouseSize {
        SMALL,
        MEDIUM,
        LARGE
    }

    private record HouseTemplate(String id, String style, ResourceLocation location, HouseSize size, int targetBedCount, IntSupplier baseWeight) {
        private int weight(int memberCount) {
            int weight = Math.max(0, baseWeight.getAsInt());
            if (memberCount >= 5 && targetBedCount >= 6) {
                weight += 30;
            } else if (memberCount >= 3 && targetBedCount >= 4) {
                weight += 20;
            } else if (memberCount <= 2 && targetBedCount <= 2) {
                weight += 12;
            }
            if (targetBedCount < memberCount) {
                weight /= 3;
            }
            return Math.max(0, weight);
        }
    }

    private static HouseTemplate vanilla(String id, String style, String name, HouseSize size, int targetBedCount) {
        return new HouseTemplate(
                id,
                style,
                ResourceLocation.withDefaultNamespace("village/" + style + "/houses/" + name),
                size,
                targetBedCount,
                weightSupplier(size));
    }

    private static IntSupplier weightSupplier(HouseSize size) {
        return switch (size) {
            case SMALL -> () -> EcologyConfig.VILLAGE_HOUSE_SMALL_WEIGHT.get();
            case MEDIUM -> () -> EcologyConfig.VILLAGE_HOUSE_MEDIUM_WEIGHT.get();
            case LARGE -> () -> EcologyConfig.VILLAGE_HOUSE_LARGE_WEIGHT.get();
        };
    }

    private static List<HouseTemplate> templatesForStyle(String style) {
        List<HouseTemplate> matches = TEMPLATES.stream()
                .filter(template -> template.style().equals(style))
                .toList();
        return matches.isEmpty() ? TEMPLATES.stream().filter(template -> template.style().equals("plains")).toList() : matches;
    }

    private static String villageStyle(ServerLevel level, BlockPos anchor) {
        var biome = level.getBiome(anchor);
        if (biome.is(BiomeTags.HAS_VILLAGE_DESERT)) {
            return "desert";
        }
        if (biome.is(BiomeTags.HAS_VILLAGE_SAVANNA) || biome.is(BiomeTags.IS_SAVANNA)) {
            return "savanna";
        }
        if (biome.is(BiomeTags.HAS_VILLAGE_SNOWY) || biome.value().coldEnoughToSnow(anchor)) {
            return "snowy";
        }
        if (biome.is(BiomeTags.HAS_VILLAGE_TAIGA) || biome.is(BiomeTags.IS_TAIGA)) {
            return "taiga";
        }
        return "plains";
    }

    private static Block bannerBlock(DyeColor color) {
        return switch (color) {
            case WHITE -> Blocks.WHITE_BANNER;
            case ORANGE -> Blocks.ORANGE_BANNER;
            case MAGENTA -> Blocks.MAGENTA_BANNER;
            case LIGHT_BLUE -> Blocks.LIGHT_BLUE_BANNER;
            case YELLOW -> Blocks.YELLOW_BANNER;
            case LIME -> Blocks.LIME_BANNER;
            case PINK -> Blocks.PINK_BANNER;
            case GRAY -> Blocks.GRAY_BANNER;
            case LIGHT_GRAY -> Blocks.LIGHT_GRAY_BANNER;
            case CYAN -> Blocks.CYAN_BANNER;
            case PURPLE -> Blocks.PURPLE_BANNER;
            case BLUE -> Blocks.BLUE_BANNER;
            case BROWN -> Blocks.BROWN_BANNER;
            case GREEN -> Blocks.GREEN_BANNER;
            case RED -> Blocks.RED_BANNER;
            case BLACK -> Blocks.BLACK_BANNER;
        };
    }

    private static final class HouseholdSummary {
        private int adults;
        private int children;
        private boolean hasPartner;

        private int memberCount() {
            return adults + children;
        }
    }
}
