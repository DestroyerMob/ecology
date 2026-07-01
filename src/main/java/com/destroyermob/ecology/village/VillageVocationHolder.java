package com.destroyermob.ecology.village;

import java.util.Optional;
import net.minecraft.world.entity.npc.VillagerProfession;

public interface VillageVocationHolder {
    Optional<VillagerProfession> ecology$getFirstParentProfession();

    Optional<VillagerProfession> ecology$getSecondParentProfession();

    void ecology$setParentProfessions(Optional<VillagerProfession> first, Optional<VillagerProfession> second);

    Optional<VillagerProfession> ecology$getDesiredProfession();

    void ecology$setDesiredProfession(Optional<VillagerProfession> profession);
}
