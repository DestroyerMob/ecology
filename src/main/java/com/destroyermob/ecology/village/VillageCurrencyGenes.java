package com.destroyermob.ecology.village;

import com.destroyermob.ecology.Ecology;
import com.destroyermob.ecology.EcologyConfig;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

public final class VillageCurrencyGenes extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new Gson();
    private static final String DIRECTORY = Ecology.MOD_ID + "/village_currency_genes";
    private static volatile Map<VillageCurrency, GeneProfile> profiles = defaultProfiles();

    public VillageCurrencyGenes() {
        super(GSON, DIRECTORY);
    }

    public static void registerReloadListener(AddReloadListenerEvent event) {
        event.addListener(new VillageCurrencyGenes());
    }

    public static int eyeColor(VillageCurrency currency) {
        GeneProfile profile = profiles.get(currency);
        return profile != null ? profile.eyeColor() : currency.eyeColor();
    }

    public static EyePair initialEyesFor(RandomSource random, VillageCurrency villageCurrency, List<VillageCurrency> availableGenes) {
        GeneProfile profile = profiles.get(villageCurrency);
        List<VillageCurrency> pool = profile == null ? fallbackGenes(villageCurrency, availableGenes) : filterAvailable(profile.defaultGenes(), availableGenes);
        if (pool.isEmpty()) {
            pool = fallbackGenes(villageCurrency, availableGenes);
        }

        VillageCurrency primary = pool.get(random.nextInt(pool.size()));
        VillageCurrency left = primary;
        VillageCurrency right = primary;
        List<VillageCurrency> distinct = distinct(pool);
        if (distinct.size() > 1 && random.nextDouble() < EcologyConfig.villageHeterochromiaChance()) {
            VillageCurrency secondary = differentCurrency(distinct, primary, random);
            if (random.nextBoolean()) {
                left = secondary;
            } else {
                right = secondary;
            }
        }
        return new EyePair(left, right);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> objects, ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<VillageCurrency, GeneProfile> loaded = new HashMap<>();
        objects.forEach((id, element) -> parseProfile(id, element).ifPresent(profile -> loaded.put(profile.currency(), profile)));
        profiles = Map.copyOf(loaded);
        Ecology.LOGGER.info("Loaded {} Ecology village currency gene profile(s)", profiles.size());
    }

    private static Optional<GeneProfile> parseProfile(ResourceLocation id, JsonElement element) {
        if (!element.isJsonObject()) {
            Ecology.LOGGER.warn("Skipping Ecology village currency gene profile {} because it is not a JSON object", id);
            return Optional.empty();
        }
        JsonObject json = element.getAsJsonObject();
        String currencyName = GsonHelper.getAsString(json, "currency", id.getPath());
        Optional<VillageCurrency> currency = VillageCurrency.optionalByName(currencyName);
        if (currency.isEmpty()) {
            Ecology.LOGGER.warn("Skipping Ecology village currency gene profile {} with unknown currency {}", id, currencyName);
            return Optional.empty();
        }

        int eyeColor = json.has("eye_color") ? parseColor(id, GsonHelper.getAsString(json, "eye_color"), currency.get().eyeColor()) : currency.get().eyeColor();
        List<VillageCurrency> defaultGenes = parseGenes(json, currency.get());
        return Optional.of(new GeneProfile(currency.get(), eyeColor, defaultGenes));
    }

    private static Map<VillageCurrency, GeneProfile> defaultProfiles() {
        Map<VillageCurrency, GeneProfile> defaults = new HashMap<>();
        for (VillageCurrency currency : VillageCurrency.values()) {
            defaults.put(currency, new GeneProfile(
                    currency,
                    currency.eyeColor(),
                    List.of(currency)));
        }
        return Map.copyOf(defaults);
    }

    private static int parseColor(ResourceLocation id, String value, int fallback) {
        String hex = value.trim().toLowerCase(Locale.ROOT);
        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        }
        if (hex.startsWith("0x")) {
            hex = hex.substring(2);
        }
        try {
            int rgb = (int)Long.parseLong(hex, 16);
            if (hex.length() <= 6) {
                rgb |= 0xFF000000;
            }
            return rgb;
        } catch (NumberFormatException exception) {
            Ecology.LOGGER.warn("Invalid eye_color '{}' in Ecology village currency gene profile {}; using fallback", value, id);
            return fallback;
        }
    }

    private static List<VillageCurrency> parseGenes(JsonObject json, VillageCurrency fallback) {
        if (!json.has("default_genes")) {
            return List.of(fallback);
        }
        List<VillageCurrency> genes = new ArrayList<>();
        for (JsonElement element : GsonHelper.getAsJsonArray(json, "default_genes")) {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
                continue;
            }
            VillageCurrency.optionalByName(element.getAsString()).ifPresent(genes::add);
        }
        return genes.isEmpty() ? List.of(fallback) : List.copyOf(genes);
    }

    private static List<VillageCurrency> filterAvailable(List<VillageCurrency> candidates, List<VillageCurrency> availableGenes) {
        return candidates.stream()
                .filter(availableGenes::contains)
                .toList();
    }

    private static List<VillageCurrency> fallbackGenes(VillageCurrency villageCurrency, List<VillageCurrency> availableGenes) {
        if (availableGenes.contains(villageCurrency)) {
            return List.of(villageCurrency);
        }
        return availableGenes.isEmpty() ? List.of(VillageCurrency.EMERALD) : availableGenes;
    }

    private static List<VillageCurrency> distinct(List<VillageCurrency> currencies) {
        List<VillageCurrency> distinct = new ArrayList<>();
        for (VillageCurrency currency : currencies) {
            if (!distinct.contains(currency)) {
                distinct.add(currency);
            }
        }
        return distinct;
    }

    private static VillageCurrency differentCurrency(List<VillageCurrency> currencies, VillageCurrency primary, RandomSource random) {
        List<VillageCurrency> alternatives = currencies.stream()
                .filter(currency -> currency != primary)
                .toList();
        return alternatives.get(random.nextInt(alternatives.size()));
    }

    public record EyePair(VillageCurrency left, VillageCurrency right) {
    }

    private record GeneProfile(
            VillageCurrency currency,
            int eyeColor,
            List<VillageCurrency> defaultGenes) {
    }
}
