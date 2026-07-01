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
    EMERALD("emerald", List.of()),
    RUBY("ruby", List.of(
            tag(Ecology.id("village_currency/ruby")),
            tag(ResourceLocation.fromNamespaceAndPath("c", "gems/ruby")),
            tag(ResourceLocation.fromNamespaceAndPath("forge", "gems/ruby")))),
    SAPPHIRE("sapphire", List.of(
            tag(Ecology.id("village_currency/sapphire")),
            tag(ResourceLocation.fromNamespaceAndPath("c", "gems/sapphire")),
            tag(ResourceLocation.fromNamespaceAndPath("forge", "gems/sapphire"))));

    private final String serializedName;
    private final List<TagKey<Item>> itemTags;

    VillageCurrency(String serializedName, List<TagKey<Item>> itemTags) {
        this.serializedName = serializedName;
        this.itemTags = itemTags;
    }

    public String serializedName() {
        return serializedName;
    }

    public Optional<Item> item() {
        if (this == EMERALD) {
            return Optional.of(Items.EMERALD);
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
        if (this == EMERALD) {
            return item == Items.EMERALD;
        }
        for (TagKey<Item> tag : itemTags) {
            if (item.builtInRegistryHolder().is(tag)) {
                return true;
            }
        }
        return false;
    }

    public ResourceLocation villagerTexture(boolean baby) {
        return switch (this) {
            case EMERALD -> ResourceLocation.withDefaultNamespace("textures/entity/villager/villager.png");
            case RUBY -> Ecology.id(baby
                    ? "textures/entity/villager/villager_ruby_baby.png"
                    : "textures/entity/villager/villager_ruby.png");
            case SAPPHIRE -> Ecology.id(baby
                    ? "textures/entity/villager/villager_sapphire_baby.png"
                    : "textures/entity/villager/villager_sapphire.png");
        };
    }

    public ResourceLocation guardTexture(boolean steveModel) {
        return switch (this) {
            case EMERALD -> ResourceLocation.fromNamespaceAndPath(
                    "guardvillagers",
                    steveModel ? "textures/entity/guard/guard_steve.png" : "textures/entity/guard/guard.png");
            case RUBY -> Ecology.id(steveModel
                    ? "textures/entity/guard/guard_steve_ruby.png"
                    : "textures/entity/guard/guard_ruby.png");
            case SAPPHIRE -> Ecology.id(steveModel
                    ? "textures/entity/guard/guard_steve_sapphire.png"
                    : "textures/entity/guard/guard_sapphire.png");
        };
    }

    public static VillageCurrency byName(String name) {
        String normalized = name.toLowerCase(Locale.ROOT);
        for (VillageCurrency currency : values()) {
            if (currency.serializedName.equals(normalized)) {
                return currency;
            }
        }
        return EMERALD;
    }

    private static TagKey<Item> tag(ResourceLocation location) {
        return TagKey.create(Registries.ITEM, location);
    }
}
