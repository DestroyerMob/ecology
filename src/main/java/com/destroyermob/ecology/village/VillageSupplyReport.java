package com.destroyermob.ecology.village;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;

public record VillageSupplyReport(
        BlockPos center,
        int ecologyScore,
        int villagerCount,
        int confinedVillagerCount,
        long simulatedTicks,
        Map<VillageSupplyCategory, Integer> supplies,
        Map<VillageSupplyCategory, Integer> dailyDeltas) {
    public VillageSupplyReport {
        center = center.immutable();
        supplies = Map.copyOf(supplies);
        dailyDeltas = Map.copyOf(dailyDeltas);
    }

    public int stock(VillageSupplyCategory category) {
        return supplies.getOrDefault(category, 0);
    }

    public int dailyDelta(VillageSupplyCategory category) {
        return dailyDeltas.getOrDefault(category, 0);
    }

    public List<VillageSupplyCategory> shortages() {
        List<VillageSupplyCategory> shortages = new ArrayList<>();
        for (VillageSupplyCategory category : VillageSupplyCategory.values()) {
            if (stock(category) < 30) {
                shortages.add(category);
            }
        }
        return shortages;
    }

    public List<VillageSupplyCategory> surpluses() {
        List<VillageSupplyCategory> surpluses = new ArrayList<>();
        for (VillageSupplyCategory category : VillageSupplyCategory.values()) {
            if (stock(category) >= 80) {
                surpluses.add(category);
            }
        }
        return surpluses;
    }
}
