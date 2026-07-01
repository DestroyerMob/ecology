package com.destroyermob.ecology.bee;

import java.util.Locale;
import java.util.Optional;
import net.minecraft.util.RandomSource;

public enum ColonyTrait {
    CALM,
    INDUSTRIOUS,
    HARDY,
    FERTILE,
    LONG_LIVED,
    RESTLESS;

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<ColonyTrait> byName(String name) {
        for (ColonyTrait trait : values()) {
            if (trait.serializedName().equals(name) || trait.name().equalsIgnoreCase(name)) {
                return Optional.of(trait);
            }
        }
        return Optional.empty();
    }

    public static ColonyTrait random(RandomSource random) {
        ColonyTrait[] traits = values();
        return traits[random.nextInt(traits.length)];
    }
}
