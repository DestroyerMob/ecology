package com.destroyermob.ecology.bee;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;

public final class BeeText {
    private BeeText() {
    }

    public static Component healthStatus(String serializedStatus) {
        return Component.translatable("ecology.health.status." + serializedStatus)
                .withStyle(healthStyle(serializedStatus));
    }

    public static Component healthStatus(ColonyHealthStatus status) {
        return healthStatus(status.name().toLowerCase());
    }

    public static ChatFormatting healthStyle(String serializedStatus) {
        return switch (serializedStatus) {
            case "thriving" -> ChatFormatting.GREEN;
            case "stable" -> ChatFormatting.AQUA;
            case "struggling" -> ChatFormatting.YELLOW;
            case "empty" -> ChatFormatting.DARK_GRAY;
            default -> ChatFormatting.RED;
        };
    }

    public static Component traitList(Iterable<ColonyTrait> traits) {
        Component result = Component.empty();
        boolean first = true;
        for (ColonyTrait trait : traits) {
            if (!first) {
                result = result.copy().append(Component.literal(", "));
            }
            result = result.copy().append(Component.translatable("ecology.trait." + trait.serializedName()));
            first = false;
        }
        return result;
    }

    public static Component traitList(ListTag traits) {
        Component result = Component.empty();
        for (int index = 0; index < traits.size(); index++) {
            if (index > 0) {
                result = result.copy().append(Component.literal(", "));
            }
            String traitName = traits.getString(index);
            result = result.copy().append(ColonyTrait.byName(traitName)
                    .<Component>map(trait -> Component.translatable("ecology.trait." + trait.serializedName()))
                    .orElse(Component.literal(traitName)));
        }
        return result;
    }
}
