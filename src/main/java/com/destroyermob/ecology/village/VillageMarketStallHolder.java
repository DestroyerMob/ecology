package com.destroyermob.ecology.village;

import java.util.Optional;
import net.minecraft.core.GlobalPos;

public interface VillageMarketStallHolder {
    Optional<GlobalPos> ecology$getMarketStall();

    void ecology$setMarketStall(Optional<GlobalPos> stall);
}
