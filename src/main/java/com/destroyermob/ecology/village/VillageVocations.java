package com.destroyermob.ecology.village;

import com.destroyermob.ecology.Ecology;
import com.destroyermob.ecology.EcologyConfig;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.pathfinder.Path;

public final class VillageVocations {
    private static final int VOCATION_RETRY_TICKS = 100;
    private static final int JOB_SITE_SEARCH_RADIUS = 48;
    private static final int RANDOM_PROFESSION_WEIGHT = 8;
    private static final int FIRST_PARENT_WEIGHT = 45;
    private static final int SECOND_PARENT_WEIGHT = 25;
    private static final TagKey<VillagerProfession> ASSIGNABLE_PROFESSION_TAG = professionTag("assignable");
    private static final TagKey<VillagerProfession> LOW_FOOD_PROFESSION_TAG = professionTag("needs/low_food");
    private static final TagKey<VillagerProfession> LOW_SHELTER_PROFESSION_TAG = professionTag("needs/low_shelter");
    private static final TagKey<VillagerProfession> UNSAFE_PROFESSION_TAG = professionTag("needs/unsafe");
    private static final TagKey<VillagerProfession> LOW_GREEN_SPACE_PROFESSION_TAG = professionTag("needs/low_green_space");
    private static final TagKey<VillagerProfession> LOW_WATER_PROFESSION_TAG = professionTag("needs/low_water");
    private static final TagKey<VillagerProfession> LOW_MAINTENANCE_PROFESSION_TAG = professionTag("needs/low_maintenance");
    private static final List<VillagerProfession> VANILLA_ASSIGNABLE_PROFESSIONS = List.of(
            VillagerProfession.ARMORER,
            VillagerProfession.BUTCHER,
            VillagerProfession.CARTOGRAPHER,
            VillagerProfession.CLERIC,
            VillagerProfession.FARMER,
            VillagerProfession.FISHERMAN,
            VillagerProfession.FLETCHER,
            VillagerProfession.LEATHERWORKER,
            VillagerProfession.LIBRARIAN,
            VillagerProfession.MASON,
            VillagerProfession.SHEPHERD,
            VillagerProfession.TOOLSMITH,
            VillagerProfession.WEAPONSMITH);

    private VillageVocations() {
    }

    public static void tickVillager(ServerLevel level, Villager villager) {
        if (!EcologyConfig.villageVocationsEnabled()
                || villager.isBaby()
                || villager.getVillagerData().getProfession() == VillagerProfession.NITWIT) {
            return;
        }

        VillagerProfession current = villager.getVillagerData().getProfession();
        if (isAssignableProfession(current)) {
            if (hasValidJobSite(level, villager, current) || attachJobSite(level, villager, current)) {
                rememberDesiredProfession(villager, current);
                return;
            }
            clearUnstableProfession(level, villager);
            return;
        }
        if (current != VillagerProfession.NONE || villager.getVillagerXp() > 0) {
            return;
        }

        if (villager instanceof VillageVocationHolder holder && holder.ecology$getDesiredProfession().isPresent()) {
            if (applyProfession(level, villager, holder.ecology$getDesiredProfession().get())) {
                return;
            }
        }

        if (villager.tickCount < 40 || Math.floorMod(villager.tickCount + villager.getId(), VOCATION_RETRY_TICKS) != 0) {
            return;
        }
        assignIfNeeded(level, villager);
    }

    public static void assignOnJoin(ServerLevel level, Villager villager, boolean loadedFromDisk) {
        if (!loadedFromDisk) {
            assignIfNeeded(level, villager);
        }
    }

    public static boolean assignIfNeeded(ServerLevel level, Villager villager) {
        if (!canAssignProfession(villager)) {
            return false;
        }
        if (villager instanceof VillageVocationHolder holder && holder.ecology$getDesiredProfession().isPresent()) {
            if (applyProfession(level, villager, holder.ecology$getDesiredProfession().get())) {
                return true;
            }
        }

        Optional<VillagerProfession> chosen = chooseProfession(level, villager);
        if (chosen.isEmpty()) {
            return false;
        }
        VillagerProfession profession = chosen.get();
        rememberDesiredProfession(villager, profession);
        return applyProfession(level, villager, profession);
    }

    public static void prepareForTrading(ServerLevel level, Villager villager) {
        if (!EcologyConfig.villageVocationsEnabled() || villager.isBaby()) {
            return;
        }
        VillagerProfession current = villager.getVillagerData().getProfession();
        if (current == VillagerProfession.NITWIT) {
            return;
        }
        if (current == VillagerProfession.NONE) {
            assignIfNeeded(level, villager);
            return;
        }
        if (villager.getVillagerXp() == 0
                && villager.getVillagerData().getLevel() <= 1
                && !hasValidJobSite(level, villager, current)
                && !attachJobSite(level, villager, current)) {
            clearUnstableProfession(level, villager);
        }
    }

    public static void inheritParentProfessions(Villager child, Villager parent, AgeableMob otherParent) {
        if (!EcologyConfig.villageVocationsEnabled() || !(child instanceof VillageVocationHolder holder)) {
            return;
        }
        Optional<VillagerProfession> first = professionForInheritance(parent);
        Optional<VillagerProfession> second = otherParent instanceof Villager villager
                ? professionForInheritance(villager)
                : Optional.empty();
        holder.ecology$setParentProfessions(first, second);
    }

    public static boolean isAssignableProfession(VillagerProfession profession) {
        return profession != VillagerProfession.NONE && profession != VillagerProfession.NITWIT;
    }

    public static String professionName(VillagerProfession profession) {
        ResourceLocation key = BuiltInRegistries.VILLAGER_PROFESSION.getKey(profession);
        return key == null ? "" : key.toString();
    }

    public static Optional<VillagerProfession> professionByName(String name) {
        ResourceLocation location = ResourceLocation.tryParse(name);
        if (location == null) {
            return Optional.empty();
        }
        return BuiltInRegistries.VILLAGER_PROFESSION.getOptional(location);
    }

    private static boolean canAssignProfession(Villager villager) {
        return EcologyConfig.villageVocationsEnabled()
                && !villager.isBaby()
                && villager.getVillagerData().getProfession() == VillagerProfession.NONE
                && villager.getVillagerXp() == 0;
    }

    private static void rememberDesiredProfession(Villager villager, VillagerProfession profession) {
        if (villager instanceof VillageVocationHolder holder && isAssignableProfession(profession)) {
            holder.ecology$setDesiredProfession(Optional.of(profession));
        }
    }

    private static boolean applyProfession(ServerLevel level, Villager villager, VillagerProfession profession) {
        if (!isAssignableProfession(profession)) {
            return false;
        }
        if (!hasValidJobSite(level, villager, profession) && !attachJobSite(level, villager, profession)) {
            return false;
        }
        if (villager.getVillagerData().getProfession() == profession) {
            return true;
        }
        villager.setVillagerData(villager.getVillagerData().setProfession(profession));
        villager.refreshBrain(level);
        if (profession.workSound() != null) {
            villager.playSound(profession.workSound(), 0.45F, 1.0F);
        }
        return true;
    }

    private static Optional<VillagerProfession> professionForInheritance(Villager villager) {
        VillagerProfession profession = villager.getVillagerData().getProfession();
        if (isAssignableProfession(profession)) {
            return Optional.of(profession);
        }
        if (villager instanceof VillageVocationHolder holder) {
            return holder.ecology$getDesiredProfession();
        }
        return Optional.empty();
    }

    private static Optional<VillagerProfession> chooseProfession(ServerLevel level, Villager villager) {
        LinkedHashMap<VillagerProfession, Integer> weights = new LinkedHashMap<>();
        assignableProfessions().forEach(profession -> addWeight(weights, profession, RANDOM_PROFESSION_WEIGHT));

        if (villager instanceof VillageVocationHolder holder) {
            holder.ecology$getFirstParentProfession()
                    .filter(VillageVocations::isAssignableProfession)
                    .ifPresent(profession -> addWeight(weights, profession, FIRST_PARENT_WEIGHT));
            holder.ecology$getSecondParentProfession()
                    .filter(VillageVocations::isAssignableProfession)
                    .ifPresent(profession -> addWeight(weights, profession, SECOND_PARENT_WEIGHT));
        }

        VillageEcologyReport report = VillageEcology.surveyCached(level, villager.blockPosition());
        for (VillageEcologyIssue issue : report.issues()) {
            addNeedWeights(weights, issue);
        }

        weights.entrySet().removeIf(entry -> !hasAvailableJobSite(level, villager, entry.getKey()));
        if (weights.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(selectWeighted(weights, villager.getRandom()));
    }

    private static void addNeedWeights(Map<VillagerProfession, Integer> weights, VillageEcologyIssue issue) {
        switch (issue) {
            case NO_VILLAGERS -> {
            }
            case LOW_FOOD -> {
                addTaggedNeedWeights(weights, LOW_FOOD_PROFESSION_TAG, 12);
                addWeight(weights, VillagerProfession.FARMER, 34);
                addWeight(weights, VillagerProfession.FISHERMAN, 14);
                addWeight(weights, VillagerProfession.BUTCHER, 10);
            }
            case LOW_SHELTER -> {
                addTaggedNeedWeights(weights, LOW_SHELTER_PROFESSION_TAG, 12);
                addWeight(weights, VillagerProfession.MASON, 24);
                addWeight(weights, VillagerProfession.CARTOGRAPHER, 8);
                addWeight(weights, VillagerProfession.LIBRARIAN, 6);
            }
            case UNSAFE -> {
                addTaggedNeedWeights(weights, UNSAFE_PROFESSION_TAG, 12);
                addWeight(weights, VillagerProfession.ARMORER, 22);
                addWeight(weights, VillagerProfession.WEAPONSMITH, 22);
                addWeight(weights, VillagerProfession.TOOLSMITH, 14);
                addWeight(weights, VillagerProfession.CLERIC, 8);
            }
            case LOW_GREEN_SPACE -> {
                addTaggedNeedWeights(weights, LOW_GREEN_SPACE_PROFESSION_TAG, 12);
                addWeight(weights, VillagerProfession.SHEPHERD, 18);
                addWeight(weights, VillagerProfession.FARMER, 12);
                addWeight(weights, VillagerProfession.FLETCHER, 8);
            }
            case LOW_WATER -> {
                addTaggedNeedWeights(weights, LOW_WATER_PROFESSION_TAG, 12);
                addWeight(weights, VillagerProfession.FISHERMAN, 28);
                addWeight(weights, VillagerProfession.FARMER, 8);
            }
            case LOW_MAINTENANCE -> {
                addTaggedNeedWeights(weights, LOW_MAINTENANCE_PROFESSION_TAG, 12);
                addWeight(weights, VillagerProfession.MASON, 18);
                addWeight(weights, VillagerProfession.TOOLSMITH, 12);
                addWeight(weights, VillagerProfession.LEATHERWORKER, 8);
            }
        }
    }

    private static List<VillagerProfession> assignableProfessions() {
        List<VillagerProfession> tagged = BuiltInRegistries.VILLAGER_PROFESSION.stream()
                .filter(VillageVocations::isAssignableProfession)
                .filter(profession -> professionInTag(profession, ASSIGNABLE_PROFESSION_TAG))
                .toList();
        return tagged.isEmpty() ? VANILLA_ASSIGNABLE_PROFESSIONS : tagged;
    }

    private static void addTaggedNeedWeights(Map<VillagerProfession, Integer> weights, TagKey<VillagerProfession> tag, int weight) {
        assignableProfessions().stream()
                .filter(profession -> professionInTag(profession, tag))
                .forEach(profession -> addWeight(weights, profession, weight));
    }

    private static void addWeight(Map<VillagerProfession, Integer> weights, VillagerProfession profession, int weight) {
        if (canConsiderProfession(profession) && weight > 0) {
            weights.merge(profession, weight, Integer::sum);
        }
    }

    private static boolean canConsiderProfession(VillagerProfession profession) {
        return assignableProfessions().contains(profession);
    }

    private static boolean professionInTag(VillagerProfession profession, TagKey<VillagerProfession> tag) {
        return BuiltInRegistries.VILLAGER_PROFESSION.wrapAsHolder(profession).is(tag);
    }

    private static TagKey<VillagerProfession> professionTag(String path) {
        return TagKey.create(Registries.VILLAGER_PROFESSION, Ecology.id(path));
    }

    private static VillagerProfession selectWeighted(LinkedHashMap<VillagerProfession, Integer> weights, RandomSource random) {
        int totalWeight = weights.values().stream().mapToInt(Integer::intValue).sum();
        if (totalWeight <= 0) {
            return VillagerProfession.FARMER;
        }
        int selected = random.nextInt(totalWeight);
        for (Map.Entry<VillagerProfession, Integer> entry : weights.entrySet()) {
            selected -= entry.getValue();
            if (selected < 0) {
                return entry.getKey();
            }
        }
        return VillagerProfession.FARMER;
    }

    private static boolean hasAvailableJobSite(ServerLevel level, Villager villager, VillagerProfession profession) {
        return level.getPoiManager()
                .findAllClosestFirstWithType(
                        profession.acquirableJobSite(),
                        pos -> true,
                        villager.blockPosition(),
                        JOB_SITE_SEARCH_RADIUS,
                        PoiManager.Occupancy.HAS_SPACE)
                .map(pair -> pair.getSecond().immutable())
                .filter(pos -> canReach(villager, pos))
                .findFirst()
                .isPresent();
    }

    private static boolean hasValidJobSite(ServerLevel level, Villager villager, VillagerProfession profession) {
        return villager.getBrain().getMemory(MemoryModuleType.JOB_SITE)
                .filter(jobSite -> jobSite.dimension().equals(level.dimension()))
                .map(GlobalPos::pos)
                .filter(pos -> level.getPoiManager().exists(pos, profession.heldJobSite()))
                .isPresent();
    }

    private static boolean attachJobSite(ServerLevel level, Villager villager, VillagerProfession profession) {
        Optional<BlockPos> jobSite = level.getPoiManager()
                .findAllClosestFirstWithType(
                        profession.acquirableJobSite(),
                        pos -> true,
                        villager.blockPosition(),
                        JOB_SITE_SEARCH_RADIUS,
                        PoiManager.Occupancy.HAS_SPACE)
                .map(pair -> pair.getSecond().immutable())
                .filter(pos -> canReach(villager, pos))
                .findFirst();
        if (jobSite.isEmpty()) {
            return false;
        }
        Optional<BlockPos> claimed = level.getPoiManager().take(
                profession.acquirableJobSite(),
                (type, pos) -> pos.equals(jobSite.get()),
                jobSite.get(),
                1);
        if (claimed.isEmpty()) {
            return false;
        }
        villager.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), claimed.get().immutable()));
        return true;
    }

    private static boolean canReach(Villager villager, BlockPos target) {
        if (villager.blockPosition().distSqr(target) <= 9.0D) {
            return true;
        }
        Path path = villager.getNavigation().createPath(target, 1);
        return path != null && path.canReach();
    }

    private static void clearUnstableProfession(ServerLevel level, Villager villager) {
        if (villager.getVillagerData().getProfession() == VillagerProfession.NONE || villager.getVillagerXp() > 0) {
            return;
        }
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.NONE));
        villager.refreshBrain(level);
    }
}
