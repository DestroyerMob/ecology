package com.destroyermob.ecology.village;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.GlobalPos;

public interface VillageRelocationHolder {
    Optional<UUID> ecology$getRelocationGuide();

    void ecology$setRelocationGuide(Optional<UUID> guide);

    Optional<GlobalPos> ecology$getRelocationTarget();

    void ecology$setRelocationTarget(Optional<GlobalPos> target);
}
