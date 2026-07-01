package com.destroyermob.ecology.village;

import java.util.Optional;
import net.minecraft.core.GlobalPos;

public interface VillageTradeboardHolder {
    Optional<GlobalPos> ecology$getTradeboard();

    void ecology$setTradeboard(Optional<GlobalPos> tradeboard);

    Optional<GlobalPos> ecology$getTradeInput();

    void ecology$setTradeInput(Optional<GlobalPos> input);

    Optional<GlobalPos> ecology$getTradeOutput();

    void ecology$setTradeOutput(Optional<GlobalPos> output);
}
