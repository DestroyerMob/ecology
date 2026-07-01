package com.destroyermob.ecology.village;

import java.util.Optional;
import java.util.UUID;

public interface VillageHouseholdHolder {
    Optional<UUID> ecology$getHouseholdId();

    void ecology$setHouseholdId(Optional<UUID> householdId);

    Optional<UUID> ecology$getFirstParentId();

    Optional<UUID> ecology$getSecondParentId();

    void ecology$setParentIds(Optional<UUID> firstParentId, Optional<UUID> secondParentId);

    Optional<UUID> ecology$getPartnerId();

    void ecology$setPartnerId(Optional<UUID> partnerId);
}
