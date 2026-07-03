package com.destroyermob.ecology.village;

import com.destroyermob.ecology.Ecology;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public enum VillageCurrency {
    EMERALD("emerald", Items.EMERALD, List.of()),
    RUBY("ruby", null, List.of(
            tag(Ecology.id("village_currency/ruby")),
            tag(ResourceLocation.fromNamespaceAndPath("c", "gems/ruby")),
            tag(ResourceLocation.fromNamespaceAndPath("forge", "gems/ruby")))),
    SAPPHIRE("sapphire", null, List.of(
            tag(Ecology.id("village_currency/sapphire")),
            tag(ResourceLocation.fromNamespaceAndPath("c", "gems/sapphire")),
            tag(ResourceLocation.fromNamespaceAndPath("forge", "gems/sapphire"))));

    private final String serializedName;
    private final Item defaultItem;
    private final List<TagKey<Item>> itemTags;

    VillageCurrency(String serializedName, Item defaultItem, List<TagKey<Item>> itemTags) {
        this.serializedName = serializedName;
        this.defaultItem = defaultItem;
        this.itemTags = itemTags;
    }

    public String serializedName() {
        return serializedName;
    }

    public Optional<Item> item() {
        if (defaultItem != null) {
            return Optional.of(defaultItem);
        }
        return itemTags.stream()
                .flatMap(tag -> BuiltInRegistries.ITEM.getTag(tag).stream())
                .flatMap(named -> named.stream())
                .map(Holder::value)
                .findFirst();
    }

    public boolean available() {
        return item().isPresent();
    }

    public boolean matches(Item item) {
        if (defaultItem != null && item == defaultItem) {
            return true;
        }
        for (TagKey<Item> tag : itemTags) {
            if (item.builtInRegistryHolder().is(tag)) {
                return true;
            }
        }
        return false;
    }

    public int eyeColor() {
        return switch (this) {
            case EMERALD -> 0xFF009611;
            case RUBY -> 0xFF960400;
            case SAPPHIRE -> 0xFF000896;
        };
    }

    public Optional<ResourceLocation> guardTexture(boolean steveModel) {
        return switch (this) {
            case RUBY -> Optional.of(Ecology.id(steveModel
                    ? "textures/entity/guard/guard_steve_ruby.png"
                    : "textures/entity/guard/guard_ruby.png"));
            case SAPPHIRE -> Optional.of(Ecology.id(steveModel
                    ? "textures/entity/guard/guard_steve_sapphire.png"
                    : "textures/entity/guard/guard_sapphire.png"));
            case EMERALD -> Optional.empty();
        };
    }

    public static VillageCurrency byName(String name) {
        return optionalByName(name).orElse(EMERALD);
    }

    public static Optional<VillageCurrency> optionalByName(String name) {
        String normalized = normalizeName(name);
        for (VillageCurrency currency : values()) {
            if (currency.serializedName.equals(normalized)
                    || currency.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                return Optional.of(currency);
            }
        }
        return Optional.empty();
    }

    private static String normalizeName(String name) {
        String normalized = name.toLowerCase(Locale.ROOT).trim();
        int namespaceSeparator = normalized.indexOf(':');
        if (namespaceSeparator >= 0 && namespaceSeparator + 1 < normalized.length()) {
            normalized = normalized.substring(namespaceSeparator + 1);
        }
        return normalized;
    }

    private static TagKey<Item> tag(ResourceLocation location) {
        return TagKey.create(Registries.ITEM, location);
    }
}
