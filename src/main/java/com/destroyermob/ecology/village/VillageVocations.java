package com.destroyermob.ecology.village;

import com.destroyermob.ecology.EcologyConfig;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;

public final class VillageVocations {
    private static final int VOCATION_RETRY_TICKS = 100;
    private static final int RANDOM_PROFESSION_WEIGHT = 8;
    private static final int FIRST_PARENT_WEIGHT = 45;
    private static final int SECOND_PARENT_WEIGHT = 25;
    private static final List<VillagerProfession> ASSIGNABLE_PROFESSIONS = List.of(
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
            rememberDesiredProfession(villager, current);
            return;
        }
        if (current != VillagerProfession.NONE || villager.getVillagerXp() > 0) {
            return;
        }

        if (villager instanceof VillageVocationHolder holder && holder.ecology$getDesiredProfession().isPresent()) {
            applyProfession(level, villager, holder.ecology$getDesiredProfession().get());
            return;
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
            applyProfession(level, villager, holder.ecology$getDesiredProfession().get());
            return true;
        }

        VillagerProfession profession = chooseProfession(level, villager);
        rememberDesiredProfession(villager, profession);
        applyProfession(level, villager, profession);
        return true;
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

    private static void applyProfession(ServerLevel level, Villager villager, VillagerProfession profession) {
        if (!isAssignableProfession(profession) || villager.getVillagerData().getProfession() == profession) {
            return;
        }
        villager.setVillagerData(villager.getVillagerData().setProfession(profession));
        if (profession.workSound() != null) {
            villager.playSound(profession.workSound(), 0.45F, 1.0F);
        }
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

    private static VillagerProfession chooseProfession(ServerLevel level, Villager villager) {
        LinkedHashMap<VillagerProfession, Integer> weights = new LinkedHashMap<>();
        ASSIGNABLE_PROFESSIONS.forEach(profession -> addWeight(weights, profession, RANDOM_PROFESSION_WEIGHT));

        if (villager instanceof VillageVocationHolder holder) {
            holder.ecology$getFirstParentProfession()
                    .filter(VillageVocations::isAssignableProfession)
                    .ifPresent(profession -> addWeight(weights, profession, FIRST_PARENT_WEIGHT));
            holder.ecology$getSecondParentProfession()
                    .filter(VillageVocations::isAssignableProfession)
                    .ifPresent(profession -> addWeight(weights, profession, SECOND_PARENT_WEIGHT));
        }

        VillageEcologyReport report = VillageEcology.survey(level, villager.blockPosition());
        for (VillageEcologyIssue issue : report.issues()) {
            addNeedWeights(weights, issue);
        }

        return selectWeighted(weights, villager.getRandom());
    }

    private static void addNeedWeights(Map<VillagerProfession, Integer> weights, VillageEcologyIssue issue) {
        switch (issue) {
            case NO_VILLAGERS -> {
            }
            case LOW_FOOD -> {
                addWeight(weights, VillagerProfession.FARMER, 34);
                addWeight(weights, VillagerProfession.FISHERMAN, 14);
                addWeight(weights, VillagerProfession.BUTCHER, 10);
            }
            case LOW_SHELTER -> {
                addWeight(weights, VillagerProfession.MASON, 24);
                addWeight(weights, VillagerProfession.CARTOGRAPHER, 8);
                addWeight(weights, VillagerProfession.LIBRARIAN, 6);
            }
            case UNSAFE -> {
                addWeight(weights, VillagerProfession.ARMORER, 22);
                addWeight(weights, VillagerProfession.WEAPONSMITH, 22);
                addWeight(weights, VillagerProfession.TOOLSMITH, 14);
                addWeight(weights, VillagerProfession.CLERIC, 8);
            }
            case LOW_GREEN_SPACE -> {
                addWeight(weights, VillagerProfession.SHEPHERD, 18);
                addWeight(weights, VillagerProfession.FARMER, 12);
                addWeight(weights, VillagerProfession.FLETCHER, 8);
            }
            case LOW_WATER -> {
                addWeight(weights, VillagerProfession.FISHERMAN, 28);
                addWeight(weights, VillagerProfession.FARMER, 8);
            }
            case LOW_MAINTENANCE -> {
                addWeight(weights, VillagerProfession.MASON, 18);
                addWeight(weights, VillagerProfession.TOOLSMITH, 12);
                addWeight(weights, VillagerProfession.LEATHERWORKER, 8);
            }
        }
    }

    private static void addWeight(Map<VillagerProfession, Integer> weights, VillagerProfession profession, int weight) {
        if (isAssignableProfession(profession) && weight > 0) {
            weights.merge(profession, weight, Integer::sum);
        }
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
}
