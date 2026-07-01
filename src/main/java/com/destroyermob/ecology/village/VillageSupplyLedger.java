package com.destroyermob.ecology.village;

import com.destroyermob.ecology.Ecology;
import com.destroyermob.ecology.EcologyConfig;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public final class VillageSupplyLedger extends SavedData {
    private static final String DATA_NAME = Ecology.MOD_ID + "_village_supplies";
    private static final SavedData.Factory<VillageSupplyLedger> FACTORY =
            new SavedData.Factory<>(VillageSupplyLedger::new, VillageSupplyLedger::load);
    private static final int VILLAGE_KEY_SIZE = 96;

    private final Map<VillageKey, VillageSupplyAccount> accounts = new HashMap<>();

    public static VillageSupplyLedger get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public VillageSupplyAccount accountFor(BlockPos anchor) {
        return accounts.computeIfAbsent(VillageKey.from(anchor), ignored -> new VillageSupplyAccount());
    }

    @Override
    public CompoundTag save(CompoundTag compound, HolderLookup.Provider registries) {
        ListTag accountTags = new ListTag();
        accounts.forEach((key, account) -> {
            CompoundTag accountTag = new CompoundTag();
            accountTag.putInt("CellX", key.cellX());
            accountTag.putInt("CellZ", key.cellZ());
            account.save(accountTag);
            accountTags.add(accountTag);
        });
        compound.put("Accounts", accountTags);
        return compound;
    }

    private static VillageSupplyLedger load(CompoundTag compound, HolderLookup.Provider registries) {
        VillageSupplyLedger ledger = new VillageSupplyLedger();
        ListTag accountTags = compound.getList("Accounts", Tag.TAG_COMPOUND);
        for (int i = 0; i < accountTags.size(); i++) {
            CompoundTag accountTag = accountTags.getCompound(i);
            VillageKey key = new VillageKey(accountTag.getInt("CellX"), accountTag.getInt("CellZ"));
            ledger.accounts.put(key, VillageSupplyAccount.load(accountTag));
        }
        return ledger;
    }

    private record VillageKey(int cellX, int cellZ) {
        private static VillageKey from(BlockPos anchor) {
            return new VillageKey(Math.floorDiv(anchor.getX(), VILLAGE_KEY_SIZE), Math.floorDiv(anchor.getZ(), VILLAGE_KEY_SIZE));
        }
    }

    public static final class VillageSupplyAccount {
        private final EnumMap<VillageSupplyCategory, Double> supplies = new EnumMap<>(VillageSupplyCategory.class);
        private final EnumMap<VillageSupplyCategory, Double> dailyDeltas = new EnumMap<>(VillageSupplyCategory.class);
        private long lastUpdatedGameTime = -1L;
        private long lastSurveyGameTime = -1L;
        private long lastSimulatedTicks;
        private int ecologyScore = 50;
        private int villagerCount;
        private int confinedVillagerCount;

        private VillageSupplyAccount() {
            for (VillageSupplyCategory category : VillageSupplyCategory.values()) {
                supplies.put(category, 50.0D);
                dailyDeltas.put(category, 0.0D);
            }
        }

        public void simulateTo(ServerLevel level) {
            long now = level.getGameTime();
            lastSimulatedTicks = 0L;
            if (lastUpdatedGameTime < 0L) {
                lastUpdatedGameTime = now;
                setDirtyFrom(level);
                return;
            }

            long elapsed = now - lastUpdatedGameTime;
            if (elapsed <= 0L) {
                return;
            }

            long cappedElapsed = Math.min(elapsed, EcologyConfig.VILLAGE_SUPPLY_CATCHUP_DAYS.get() * 24000L);
            double days = cappedElapsed / 24000.0D;
            for (VillageSupplyCategory category : VillageSupplyCategory.values()) {
                double current = supplies.getOrDefault(category, 50.0D);
                double delta = dailyDeltas.getOrDefault(category, 0.0D);
                supplies.put(category, clamp(current + delta * days));
            }
            lastUpdatedGameTime = now;
            lastSimulatedTicks = cappedElapsed;
            setDirtyFrom(level);
        }

        public boolean needsUpdate(ServerLevel level, boolean force) {
            return force
                    || lastUpdatedGameTime < 0L
                    || level.getGameTime() - lastUpdatedGameTime >= EcologyConfig.VILLAGE_SUPPLY_UPDATE_INTERVAL_TICKS.get();
        }

        public boolean needsSurvey(ServerLevel level, boolean force) {
            return force
                    || lastSurveyGameTime < 0L
                    || level.getGameTime() - lastSurveyGameTime >= EcologyConfig.VILLAGE_SUPPLY_SURVEY_INTERVAL_TICKS.get();
        }

        public void refreshSurvey(ServerLevel level, VillageEcologyReport report, int confinedVillagers, Map<VillageSupplyCategory, Double> newDailyDeltas) {
            ecologyScore = report.score();
            villagerCount = report.villagerCount();
            confinedVillagerCount = confinedVillagers;
            if (lastSurveyGameTime < 0L) {
                seedFromEcology(report);
            }
            dailyDeltas.clear();
            for (VillageSupplyCategory category : VillageSupplyCategory.values()) {
                dailyDeltas.put(category, clampDelta(newDailyDeltas.getOrDefault(category, 0.0D)));
            }
            lastSurveyGameTime = level.getGameTime();
            setDirtyFrom(level);
        }

        public void add(ServerLevel level, VillageSupplyCategory category, double amount) {
            if (amount <= 0.0D) {
                return;
            }
            supplies.put(category, clamp(stock(category) + amount));
            setDirtyFrom(level);
        }

        public void consume(ServerLevel level, VillageSupplyCategory category, double amount) {
            if (amount <= 0.0D) {
                return;
            }
            supplies.put(category, clamp(stock(category) - amount));
            setDirtyFrom(level);
        }

        public int stockLevel(VillageSupplyCategory category) {
            return (int)Math.round(stock(category));
        }

        public VillageSupplyReport report(BlockPos center) {
            EnumMap<VillageSupplyCategory, Integer> stockReport = new EnumMap<>(VillageSupplyCategory.class);
            EnumMap<VillageSupplyCategory, Integer> deltaReport = new EnumMap<>(VillageSupplyCategory.class);
            for (VillageSupplyCategory category : VillageSupplyCategory.values()) {
                stockReport.put(category, stockLevel(category));
                deltaReport.put(category, (int)Math.round(dailyDeltas.getOrDefault(category, 0.0D)));
            }
            return new VillageSupplyReport(center, ecologyScore, villagerCount, confinedVillagerCount, lastSimulatedTicks, stockReport, deltaReport);
        }

        private double stock(VillageSupplyCategory category) {
            return supplies.getOrDefault(category, 50.0D);
        }

        private void seedFromEcology(VillageEcologyReport report) {
            supplies.put(VillageSupplyCategory.FOOD, clamp(25.0D + report.foodScore() * 0.65D));
            supplies.put(VillageSupplyCategory.WOOD, clamp(30.0D + report.greenScore() * 0.45D + report.maintenanceScore() * 0.20D));
            supplies.put(VillageSupplyCategory.STONE, clamp(30.0D + report.shelterScore() * 0.35D + report.maintenanceScore() * 0.25D));
            supplies.put(VillageSupplyCategory.METAL, clamp(22.0D + report.safetyScore() * 0.30D));
            supplies.put(VillageSupplyCategory.PAPER, clamp(25.0D + report.shelterScore() * 0.35D));
            supplies.put(VillageSupplyCategory.CLOTH, clamp(30.0D + report.greenScore() * 0.35D));
            supplies.put(VillageSupplyCategory.TOOLS, clamp(25.0D + report.maintenanceScore() * 0.35D + report.safetyScore() * 0.20D));
            supplies.put(VillageSupplyCategory.MEDICINE, clamp(25.0D + report.safetyScore() * 0.25D + report.greenScore() * 0.20D));
            supplies.put(VillageSupplyCategory.VALUABLES, clamp(20.0D + report.score() * 0.25D));
        }

        private void save(CompoundTag compound) {
            compound.putLong("LastUpdatedGameTime", lastUpdatedGameTime);
            compound.putLong("LastSurveyGameTime", lastSurveyGameTime);
            compound.putInt("EcologyScore", ecologyScore);
            compound.putInt("VillagerCount", villagerCount);
            compound.putInt("ConfinedVillagerCount", confinedVillagerCount);

            CompoundTag suppliesTag = new CompoundTag();
            CompoundTag dailyDeltasTag = new CompoundTag();
            for (VillageSupplyCategory category : VillageSupplyCategory.values()) {
                suppliesTag.putDouble(category.serializedName(), supplies.getOrDefault(category, 50.0D));
                dailyDeltasTag.putDouble(category.serializedName(), dailyDeltas.getOrDefault(category, 0.0D));
            }
            compound.put("Supplies", suppliesTag);
            compound.put("DailyDeltas", dailyDeltasTag);
        }

        private static VillageSupplyAccount load(CompoundTag compound) {
            VillageSupplyAccount account = new VillageSupplyAccount();
            account.lastUpdatedGameTime = compound.getLong("LastUpdatedGameTime");
            account.lastSurveyGameTime = compound.getLong("LastSurveyGameTime");
            account.ecologyScore = compound.getInt("EcologyScore");
            account.villagerCount = compound.getInt("VillagerCount");
            account.confinedVillagerCount = compound.getInt("ConfinedVillagerCount");

            CompoundTag suppliesTag = compound.getCompound("Supplies");
            CompoundTag dailyDeltasTag = compound.getCompound("DailyDeltas");
            for (VillageSupplyCategory category : VillageSupplyCategory.values()) {
                if (suppliesTag.contains(category.serializedName(), Tag.TAG_ANY_NUMERIC)) {
                    account.supplies.put(category, suppliesTag.getDouble(category.serializedName()));
                }
                if (dailyDeltasTag.contains(category.serializedName(), Tag.TAG_ANY_NUMERIC)) {
                    account.dailyDeltas.put(category, dailyDeltasTag.getDouble(category.serializedName()));
                }
            }
            return account;
        }

        private void setDirtyFrom(ServerLevel level) {
            VillageSupplyLedger.get(level).setDirty();
        }

        private static double clamp(double value) {
            return Math.max(0.0D, Math.min(100.0D, value));
        }

        private static double clampDelta(double value) {
            return Math.max(-35.0D, Math.min(35.0D, value));
        }
    }
}
