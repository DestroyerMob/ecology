package com.destroyermob.ecology.bee;

import net.minecraft.core.BlockPos;

public record BeeSearchArea(BlockPos min, BlockPos max, BeeRouteStopType type) {
    public static BeeSearchArea around(BlockPos center, int horizontalRadius, int verticalRadius, BeeRouteStopType type) {
        return new BeeSearchArea(
                center.offset(-horizontalRadius, -verticalRadius, -horizontalRadius),
                center.offset(horizontalRadius, verticalRadius, horizontalRadius),
                type);
    }
}
