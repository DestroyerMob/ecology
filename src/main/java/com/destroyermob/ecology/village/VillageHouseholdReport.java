package com.destroyermob.ecology.village;

import net.minecraft.core.BlockPos;

public record VillageHouseholdReport(
        BlockPos center,
        int householdCount,
        int pairedHouseholds,
        int childCount,
        int adultChildrenAtHome,
        int emptyHomes,
        int approvedPlots,
        int activeConstruction,
        int completedConstructedHomes,
        int crowdedHouseholds,
        int expansionReadyHouseholds,
        int totalSavings) {
}
