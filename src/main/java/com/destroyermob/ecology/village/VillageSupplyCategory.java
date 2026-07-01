package com.destroyermob.ecology.village;

import java.util.Locale;
import java.util.Optional;

public enum VillageSupplyCategory {
    FOOD,
    WOOD,
    STONE,
    METAL,
    PAPER,
    CLOTH,
    TOOLS,
    MEDICINE,
    VALUABLES;

    private final String serializedName;

    VillageSupplyCategory() {
        this.serializedName = name().toLowerCase(Locale.ROOT);
    }

    public String serializedName() {
        return serializedName;
    }

    public String translationKey() {
        return "ecology.village.supply." + serializedName;
    }

    public static Optional<VillageSupplyCategory> byName(String name) {
        for (VillageSupplyCategory category : values()) {
            if (category.serializedName.equals(name)) {
                return Optional.of(category);
            }
        }
        return Optional.empty();
    }
}
