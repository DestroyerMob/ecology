package com.destroyermob.ecology.village;

import java.util.Optional;

public interface VillageSupplyOfferHolder {
    int ecology$getBaseMaxUses();

    void ecology$setBaseMaxUses(int baseMaxUses);

    int ecology$getBaseSpecialPriceDiff();

    void ecology$setBaseSpecialPriceDiff(int baseSpecialPriceDiff);

    Optional<VillageSupplyCategory> ecology$getSupplyCategory();

    void ecology$setSupplyCategory(Optional<VillageSupplyCategory> category);

    boolean ecology$isSupplyBuyingOffer();

    void ecology$setSupplyBuyingOffer(boolean buyingOffer);
}
