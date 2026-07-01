package com.destroyermob.ecology.village;

import com.destroyermob.ecology.Ecology;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.DyeColor;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public final class VillageHouseholdLedger extends SavedData {
    private static final String DATA_NAME = Ecology.MOD_ID + "_village_households";
    private static final SavedData.Factory<VillageHouseholdLedger> FACTORY =
            new SavedData.Factory<>(VillageHouseholdLedger::new, VillageHouseholdLedger::load);

    private final Map<UUID, HouseholdAccount> accounts = new HashMap<>();
    private final Map<UUID, HousePlot> plots = new HashMap<>();

    public static VillageHouseholdLedger get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public HouseholdAccount accountFor(UUID householdId) {
        return accounts.computeIfAbsent(householdId, ignored -> new HouseholdAccount());
    }

    public Collection<HouseholdAccount> accounts() {
        return accounts.values();
    }

    public Collection<Map.Entry<UUID, HouseholdAccount>> accountEntries() {
        return Collections.unmodifiableCollection(accounts.entrySet());
    }

    public Collection<HousePlot> plots() {
        return Collections.unmodifiableCollection(plots.values());
    }

    public HousePlot registerPlot(ServerLevel level, BlockPos villageAnchor, BlockPos min, BlockPos max, DyeColor bannerColor) {
        HousePlot plot = new HousePlot(UUID.randomUUID(), villageAnchor.immutable(), min.immutable(), max.immutable(), bannerColor);
        plots.put(plot.id(), plot);
        setDirtyFrom(level);
        return plot;
    }

    public List<HousePlot> availablePlotsNear(BlockPos anchor, int radius) {
        int radiusSqr = radius * radius;
        List<HousePlot> available = new ArrayList<>();
        for (HousePlot plot : plots.values()) {
            if (!plot.completed()
                    && plot.householdId().isEmpty()
                    && plot.villageAnchor().distSqr(anchor) <= radiusSqr) {
                available.add(plot);
            }
        }
        return available;
    }

    public Optional<HousePlot> activePlotFor(UUID householdId) {
        return plots.values().stream()
                .filter(plot -> !plot.completed())
                .filter(plot -> plot.householdId().filter(householdId::equals).isPresent())
                .findFirst();
    }

    public void merge(ServerLevel level, UUID targetId, UUID sourceId) {
        if (targetId.equals(sourceId)) {
            return;
        }
        HouseholdAccount target = accountFor(targetId);
        HouseholdAccount source = accounts.remove(sourceId);
        if (source == null) {
            return;
        }
        target.savings += source.savings;
        if (target.home.isEmpty()) {
            target.home = source.home;
        }
        target.lastSeenGameTime = Math.max(target.lastSeenGameTime, source.lastSeenGameTime);
        plots.values().forEach(plot -> plot.householdId()
                .filter(sourceId::equals)
                .ifPresent(ignored -> plot.assign(targetId, plot.templateId().orElse(""))));
        setDirtyFrom(level);
    }

    @Override
    public CompoundTag save(CompoundTag compound, HolderLookup.Provider registries) {
        ListTag accountTags = new ListTag();
        accounts.forEach((householdId, account) -> {
            CompoundTag accountTag = new CompoundTag();
            accountTag.putUUID("HouseholdId", householdId);
            account.save(accountTag);
            accountTags.add(accountTag);
        });
        compound.put("Households", accountTags);
        ListTag plotTags = new ListTag();
        plots.forEach((plotId, plot) -> {
            CompoundTag plotTag = new CompoundTag();
            plot.save(plotTag);
            plotTags.add(plotTag);
        });
        compound.put("HousePlots", plotTags);
        return compound;
    }

    private static VillageHouseholdLedger load(CompoundTag compound, HolderLookup.Provider registries) {
        VillageHouseholdLedger ledger = new VillageHouseholdLedger();
        ListTag accountTags = compound.getList("Households", Tag.TAG_COMPOUND);
        for (int i = 0; i < accountTags.size(); i++) {
            CompoundTag accountTag = accountTags.getCompound(i);
            if (accountTag.hasUUID("HouseholdId")) {
                ledger.accounts.put(accountTag.getUUID("HouseholdId"), HouseholdAccount.load(accountTag));
            }
        }
        ListTag plotTags = compound.getList("HousePlots", Tag.TAG_COMPOUND);
        for (int i = 0; i < plotTags.size(); i++) {
            HousePlot.load(plotTags.getCompound(i)).ifPresent(plot -> ledger.plots.put(plot.id(), plot));
        }
        return ledger;
    }

    private void setDirtyFrom(ServerLevel level) {
        VillageHouseholdLedger.get(level).setDirty();
    }

    public static final class HouseholdAccount {
        private Optional<BlockPos> home = Optional.empty();
        private double savings;
        private long lastSeenGameTime = -1L;

        private HouseholdAccount() {
        }

        public Optional<BlockPos> home() {
            return home;
        }

        public void setHome(ServerLevel level, BlockPos home) {
            this.home = Optional.of(home.immutable());
            touch(level);
        }

        public void clearHome(ServerLevel level) {
            if (home.isPresent()) {
                home = Optional.empty();
                touch(level);
            }
        }

        public double savings() {
            return savings;
        }

        public int savingsLevel() {
            return (int)Math.round(savings);
        }

        public void addSavings(ServerLevel level, double amount) {
            if (amount <= 0.0D) {
                return;
            }
            savings = Math.min(1000.0D, savings + amount);
            touch(level);
        }

        public boolean spend(ServerLevel level, double amount) {
            if (amount <= 0.0D) {
                touch(level);
                return true;
            }
            if (savings < amount) {
                return false;
            }
            savings -= amount;
            touch(level);
            return true;
        }

        public void touch(ServerLevel level) {
            lastSeenGameTime = level.getGameTime();
            VillageHouseholdLedger.get(level).setDirty();
        }

        private void save(CompoundTag compound) {
            home.ifPresent(pos -> compound.putLong("Home", pos.asLong()));
            compound.putDouble("Savings", savings);
            compound.putLong("LastSeenGameTime", lastSeenGameTime);
        }

        private static HouseholdAccount load(CompoundTag compound) {
            HouseholdAccount account = new HouseholdAccount();
            if (compound.contains("Home", Tag.TAG_ANY_NUMERIC)) {
                account.home = Optional.of(BlockPos.of(compound.getLong("Home")));
            }
            if (compound.contains("Savings", Tag.TAG_ANY_NUMERIC)) {
                account.savings = compound.getDouble("Savings");
            }
            if (compound.contains("LastSeenGameTime", Tag.TAG_ANY_NUMERIC)) {
                account.lastSeenGameTime = compound.getLong("LastSeenGameTime");
            }
            return account;
        }
    }

    public static final class HousePlot {
        private final UUID id;
        private final BlockPos villageAnchor;
        private final BlockPos min;
        private final BlockPos max;
        private final DyeColor bannerColor;
        private Optional<UUID> householdId = Optional.empty();
        private Optional<String> templateId = Optional.empty();
        private int progress;
        private boolean completed;

        HousePlot(UUID id, BlockPos villageAnchor, BlockPos min, BlockPos max, DyeColor bannerColor) {
            this.id = id;
            this.villageAnchor = villageAnchor.immutable();
            this.min = min.immutable();
            this.max = max.immutable();
            this.bannerColor = bannerColor;
        }

        public UUID id() {
            return id;
        }

        public BlockPos villageAnchor() {
            return villageAnchor;
        }

        public BlockPos min() {
            return min;
        }

        public BlockPos max() {
            return max;
        }

        public DyeColor bannerColor() {
            return bannerColor;
        }

        public Optional<UUID> householdId() {
            return householdId;
        }

        public Optional<String> templateId() {
            return templateId;
        }

        public int progress() {
            return progress;
        }

        public boolean completed() {
            return completed;
        }

        public int width() {
            return max.getX() - min.getX() + 1;
        }

        public int depth() {
            return max.getZ() - min.getZ() + 1;
        }

        public int groundY() {
            return min.getY();
        }

        public void assign(UUID householdId, String templateId) {
            this.householdId = Optional.of(householdId);
            this.templateId = templateId == null || templateId.isBlank() ? Optional.empty() : Optional.of(templateId);
        }

        public void advanceTo(int progress) {
            this.progress = Math.max(this.progress, progress);
        }

        public void complete() {
            completed = true;
        }

        private void save(CompoundTag compound) {
            compound.putUUID("PlotId", id);
            compound.putLong("VillageAnchor", villageAnchor.asLong());
            compound.putLong("Min", min.asLong());
            compound.putLong("Max", max.asLong());
            compound.putInt("BannerColor", bannerColor.ordinal());
            householdId.ifPresent(id -> compound.putUUID("HouseholdId", id));
            templateId.ifPresent(template -> compound.putString("Template", template));
            compound.putInt("Progress", progress);
            compound.putBoolean("Completed", completed);
        }

        private static Optional<HousePlot> load(CompoundTag compound) {
            if (!compound.hasUUID("PlotId")
                    || !compound.contains("VillageAnchor", Tag.TAG_ANY_NUMERIC)
                    || !compound.contains("Min", Tag.TAG_ANY_NUMERIC)
                    || !compound.contains("Max", Tag.TAG_ANY_NUMERIC)) {
                return Optional.empty();
            }
            int colorIndex = Math.max(0, Math.min(DyeColor.values().length - 1, compound.getInt("BannerColor")));
            HousePlot plot = new HousePlot(
                    compound.getUUID("PlotId"),
                    BlockPos.of(compound.getLong("VillageAnchor")),
                    BlockPos.of(compound.getLong("Min")),
                    BlockPos.of(compound.getLong("Max")),
                    DyeColor.values()[colorIndex]);
            if (compound.hasUUID("HouseholdId")) {
                plot.householdId = Optional.of(compound.getUUID("HouseholdId"));
            }
            if (compound.contains("Template", Tag.TAG_STRING)) {
                plot.templateId = Optional.of(compound.getString("Template"));
            }
            if (compound.contains("Progress", Tag.TAG_ANY_NUMERIC)) {
                plot.progress = compound.getInt("Progress");
            }
            plot.completed = compound.getBoolean("Completed");
            return Optional.of(plot);
        }
    }
}
