package com.destroyermob.ecology.village;

import com.destroyermob.ecology.EcologyConfig;
import com.destroyermob.ecology.network.VillageLedgerPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public final class VillageLedgerReports {
    private static final int PAGE_BODY_LINES = 12;

    private VillageLedgerReports() {
    }

    public static void openVillage(ServerLevel level, ServerPlayer player, ItemStack ledger, BlockPos center) {
        PacketDistributor.sendToPlayer(player, new VillageLedgerPayload(villagePages(level, ledger, center)));
        level.playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.7F, 1.05F);
    }

    public static void openVillager(ServerLevel level, ServerPlayer player, Villager villager) {
        PacketDistributor.sendToPlayer(player, new VillageLedgerPayload(villagerPages(level, villager)));
        level.playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.7F, 1.0F);
    }

    public static List<String> villagePages(ServerLevel level, ItemStack ledger, BlockPos center) {
        List<String> pages = new ArrayList<>();
        if (!EcologyConfig.villageEcologyEnabled()) {
            pages.add(page("Village Ledger", List.of("Village ecology is disabled in the config.")));
            return pages;
        }

        VillageEcologyReport ecology = VillageEcology.survey(level, center);
        pages.add(page("Village Ledger", List.of(
                "Survey: " + formatPos(ecology.center()),
                "Status: " + titleCase(ecology.status().name()),
                "Score: " + ecology.score() + "/100",
                "",
                "Villagers: " + ecology.villagerCount(),
                "Golems: " + ecology.golemCount(),
                "Guards: " + ecology.guardCount(),
                "Beds: " + ecology.bedCount(),
                "",
                ecology.issues().isEmpty()
                        ? "This village is balanced."
                        : "Needs: " + issueSummary(ecology.issues()))));

        pages.add(page("Ecology Scores", List.of(
                "Food: " + ecology.foodScore(),
                "Shelter: " + ecology.shelterScore(),
                "Safety: " + ecology.safetyScore(),
                "Green space: " + ecology.greenScore(),
                "Water: " + ecology.waterScore(),
                "Upkeep: " + ecology.maintenanceScore(),
                "",
                "Crops: " + ecology.cropCount(),
                "Mature crops: " + ecology.matureCropCount(),
                "Flowers: " + ecology.flowerCount(),
                "Water blocks: " + ecology.waterCount(),
                "Path blocks: " + ecology.pathCount())));

        if (EcologyConfig.villageSuppliesEnabled()) {
            pages.add(supplyPage(level, center));
        }
        if (EcologyConfig.villageHouseholdsEnabled()) {
            pages.add(householdPage(level, center));
        }
        pages.add(setupPage(ledger));
        pages.add(helpPage());
        return pages;
    }

    public static List<String> villagerPages(ServerLevel level, Villager villager) {
        List<String> pages = new ArrayList<>();
        VillagerProfession profession = villager.getVillagerData().getProfession();
        pages.add(page("Villager Ledger", List.of(
                "Name: " + villager.getDisplayName().getString(),
                "Position: " + formatPos(villager.blockPosition()),
                "Profession: " + professionLabel(profession),
                "Level: " + villager.getVillagerData().getLevel(),
                "XP: " + villager.getVillagerXp(),
                "Currency: " + VillageCurrencySystem.currency(villager).serializedName(),
                desiredProfession(villager)
                        .map(desired -> "Desired job: " + professionLabel(desired))
                        .orElse("Desired job: none recorded"),
                "",
                villager.isBaby() ? "This villager is a child." : "This villager is an adult.")));

        pages.add(page("Access", List.of(
                "Home: " + memoryPos(level, villager, MemoryModuleType.HOME).map(VillageLedgerReports::formatPos).orElse("-"),
                "Job: " + memoryPos(level, villager, MemoryModuleType.JOB_SITE).map(VillageLedgerReports::formatPos).orElse("-"),
                "Meeting: " + memoryPos(level, villager, MemoryModuleType.MEETING_POINT).map(VillageLedgerReports::formatPos).orElse("-"),
                "",
                "Welfare: " + (VillageWelfare.isConfined(villager) ? "confined" : "settled"),
                "Pressure: " + confinementPressure(villager),
                "Reason: " + VillageWelfare.diagnosticReasonText(level, villager),
                "",
                VillageWelfare.isConfined(villager)
                        ? "Advice: restore paths to home and meeting space."
                        : "Advice: access looks healthy.")));

        pages.add(villagerHouseholdPage(level, villager));
        pages.add(villagerWorkPage(level, villager));
        return pages;
    }

    private static String supplyPage(ServerLevel level, BlockPos center) {
        VillageSupplyReport report = VillageSupplies.report(level, center);
        List<String> lines = new ArrayList<>();
        if (report.simulatedTicks() >= 1200L) {
            lines.add("Caught up " + String.format(Locale.ROOT, "%.1f", report.simulatedTicks() / 24000.0D) + " day(s).");
            lines.add("");
        }
        if (report.confinedVillagerCount() > 0) {
            lines.add("Confined traders: " + report.confinedVillagerCount());
            lines.add("");
        }
        for (VillageSupplyCategory category : VillageSupplyCategory.values()) {
            lines.add(titleCase(category.serializedName()) + ": " + report.stock(category) + " (" + signed(report.dailyDelta(category)) + "/day)");
        }
        if (!report.shortages().isEmpty()) {
            lines.add("");
            lines.add("Short: " + categorySummary(report.shortages()));
        }
        return page("Village Supplies", lines);
    }

    private static String householdPage(ServerLevel level, BlockPos center) {
        VillageHouseholdReport report = VillageHouseholds.report(level, center);
        return page("Households", List.of(
                "Households: " + report.householdCount(),
                "Partnered: " + report.pairedHouseholds(),
                "Children: " + report.childCount(),
                "Adult children at home: " + report.adultChildrenAtHome(),
                "Total savings: " + report.totalSavings(),
                "",
                "Empty homes: " + report.emptyHomes(),
                "Approved plots: " + report.approvedPlots(),
                "Active builds: " + report.activeConstruction(),
                "Built homes: " + report.completedConstructedHomes(),
                "",
                report.crowdedHouseholds() > 0
                        ? "Crowded homes: " + report.crowdedHouseholds()
                        : "No crowded homes reported."));
    }

    private static String setupPage(ItemStack ledger) {
        return page("Marked Setup", List.of(
                "Village: " + formatStored(VillageRelocation.storedVillageAnchor(ledger)),
                "Stall: " + formatStored(VillageMarketStalls.storedStall(ledger)),
                "Tradeboard: " + formatStored(VillagePlayerTrades.storedTradeboard(ledger)),
                "Input: " + formatStored(VillagePlayerTrades.storedTradeInput(ledger)),
                "Output: " + formatStored(VillagePlayerTrades.storedTradeOutput(ledger)),
                "House plot: " + formatStored(VillageHouseholds.storedPlotCorner(ledger)),
                "",
                "Crouch-use marks things.",
                "Use on a villager to assign a complete setup.",
                "Crouch-use a villager to inspect."));
    }

    private static String helpPage() {
        return page("Village Notes", List.of(
                "Villagers need reachable homes, meeting space, and work.",
                "",
                "Tradeboards use stocked input and output inventories.",
                "",
                "Households save currency from trades. Crowded homes can add beds or fund approved plots.",
                "",
                "Vocation choice favors parents, village needs, and reachable job sites."));
    }

    private static String villagerHouseholdPage(ServerLevel level, Villager villager) {
        if (!(villager instanceof VillageHouseholdHolder holder) || holder.ecology$getHouseholdId().isEmpty()) {
            return page("Household", List.of("No household record yet."));
        }
        UUID householdId = holder.ecology$getHouseholdId().get();
        VillageHouseholdLedger.HouseholdAccount account = VillageHouseholdLedger.get(level).accountFor(householdId);
        List<String> lines = new ArrayList<>();
        lines.add("Household: " + shortUuid(householdId));
        lines.add("Home: " + account.home().map(VillageLedgerReports::formatPos).orElse("-"));
        lines.add("Savings: " + account.savingsLevel());
        holder.ecology$getPartnerId().ifPresent(partner -> lines.add("Partner: " + shortUuid(partner)));
        String parents = formatIds(holder.ecology$getFirstParentId(), holder.ecology$getSecondParentId());
        if (!parents.isBlank()) {
            lines.add("Parents: " + parents);
        }
        return page("Household", lines);
    }

    private static String villagerWorkPage(ServerLevel level, Villager villager) {
        List<String> lines = new ArrayList<>();
        Optional<BlockPos> stall = VillageMarketStalls.assignedStall(level, villager);
        lines.add("Stall: " + stall.map(VillageLedgerReports::formatPos).orElse("-"));
        if (stall.isPresent()) {
            lines.add("Stall path: " + (VillageMarketStalls.canReachAssignedStall(level, villager) ? "reachable" : "blocked"));
        }
        if (villager instanceof VillageTradeboardHolder holder) {
            lines.add("");
            lines.add("Tradeboard: " + formatStored(holder.ecology$getTradeboard()));
            lines.add("Input: " + formatStored(holder.ecology$getTradeInput()));
            lines.add("Output: " + formatStored(holder.ecology$getTradeOutput()));
        }
        if (VillageRelocation.isRelocating(villager) && villager instanceof VillageRelocationHolder holder) {
            lines.add("");
            lines.add("Relocating to: " + formatStored(holder.ecology$getRelocationTarget()));
        }
        return page("Work", lines);
    }

    private static String page(String title, List<String> lines) {
        List<String> pageLines = new ArrayList<>();
        pageLines.add(title);
        pageLines.add("");
        lines.stream()
                .limit(PAGE_BODY_LINES)
                .forEach(pageLines::add);
        return String.join("\n", pageLines);
    }

    private static Optional<VillagerProfession> desiredProfession(Villager villager) {
        return villager instanceof VillageVocationHolder holder ? holder.ecology$getDesiredProfession() : Optional.empty();
    }

    private static int confinementPressure(Villager villager) {
        return villager instanceof VillageWelfareHolder holder ? holder.ecology$getConfinementPressure() : 0;
    }

    private static Optional<BlockPos> memoryPos(ServerLevel level, Villager villager, MemoryModuleType<GlobalPos> memoryType) {
        return villager.getBrain().getMemory(memoryType)
                .filter(pos -> pos.dimension().equals(level.dimension()))
                .map(GlobalPos::pos);
    }

    private static String professionLabel(VillagerProfession profession) {
        String name = VillageVocations.professionName(profession);
        return name.startsWith("minecraft:") ? titleCase(name.substring("minecraft:".length())) : name;
    }

    private static String issueSummary(List<VillageEcologyIssue> issues) {
        return issues.stream()
                .limit(4)
                .map(issue -> titleCase(issue.name()))
                .reduce((first, second) -> first + ", " + second)
                .orElse("none");
    }

    private static String categorySummary(List<VillageSupplyCategory> categories) {
        return categories.stream()
                .limit(4)
                .map(category -> titleCase(category.serializedName()))
                .reduce((first, second) -> first + ", " + second)
                .orElse("none");
    }

    private static String formatStored(Optional<GlobalPos> stored) {
        return stored.map(pos -> formatPos(pos.pos())).orElse("-");
    }

    private static String formatIds(Optional<UUID> first, Optional<UUID> second) {
        if (first.isPresent() && second.isPresent()) {
            return shortUuid(first.get()) + ", " + shortUuid(second.get());
        }
        return first.map(VillageLedgerReports::shortUuid)
                .orElseGet(() -> second.map(VillageLedgerReports::shortUuid).orElse(""));
    }

    private static String shortUuid(UUID id) {
        return id.toString().substring(0, 8);
    }

    private static String signed(int value) {
        return value > 0 ? "+" + value : Integer.toString(value);
    }

    private static String titleCase(String value) {
        String normalized = value.toLowerCase(Locale.ROOT).replace('_', ' ');
        if (normalized.isBlank()) {
            return normalized;
        }
        String[] words = normalized.split(" ");
        for (int i = 0; i < words.length; i++) {
            if (!words[i].isBlank()) {
                words[i] = words[i].substring(0, 1).toUpperCase(Locale.ROOT) + words[i].substring(1);
            }
        }
        return String.join(" ", words);
    }

    private static String formatPos(BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }
}
