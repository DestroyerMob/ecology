package com.destroyermob.ecology.village;

import java.util.List;
import net.minecraft.core.BlockPos;

public record VillageEcologyReport(
        BlockPos center,
        int score,
        VillageEcologyStatus status,
        int villagerCount,
        int golemCount,
        int bedCount,
        int cropCount,
        int matureCropCount,
        int flowerCount,
        int waterCount,
        int pathCount,
        int foodScore,
        int shelterScore,
        int safetyScore,
        int greenScore,
        int waterScore,
        int maintenanceScore,
        List<VillageEcologyIssue> issues) {
    public boolean hasVillageActivity() {
        return villagerCount > 0 || bedCount > 0 || cropCount > 0;
    }
}
