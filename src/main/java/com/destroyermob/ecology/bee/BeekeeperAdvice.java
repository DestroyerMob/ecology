package com.destroyermob.ecology.bee;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;

public final class BeekeeperAdvice {
    private static final int MAX_LINES = 4;

    private BeekeeperAdvice() {
    }

    public static Component focus(ServerLevel level, BeehiveBlockEntity hive, ColonyData colony, ColonyHealth health) {
        long day = EcologyBeeSystem.colonyDay(level, colony);
        BeekeeperFocus focus = focusFor(level, hive, colony, health, day);
        return Component.translatable("ecology.beekeeper_focus." + focus.key);
    }

    public static List<Component> forHive(ServerLevel level, BeehiveBlockEntity hive, ColonyData colony, ColonyHealth health) {
        List<Component> advice = new ArrayList<>();
        addHealthAdvice(advice, health);

        long day = EcologyBeeSystem.colonyDay(level, colony);
        if (ColonySwarming.isReady(level, hive.getBlockPos(), colony, day)) {
            advice.add(Component.translatable("ecology.advice.swarm_ready"));
        }
        if (health.issues().isEmpty() && health.supportsPollinationBonus()) {
            advice.add(Component.translatable("ecology.advice.stable"));
        }
        if (colony.hasQueenExcluder(day)) {
            advice.add(Component.translatable("ecology.advice.queen_excluder"));
        }

        if (advice.size() <= MAX_LINES) {
            return advice;
        }
        return List.copyOf(advice.subList(0, MAX_LINES));
    }

    private static void addHealthAdvice(List<Component> advice, ColonyHealth health) {
        for (ColonyHealthIssue issue : health.issues()) {
            switch (issue) {
                case NO_COLONY -> advice.add(Component.translatable("ecology.advice.no_colony"));
                case ABANDONED -> advice.add(Component.translatable("ecology.advice.abandoned"));
                case DOOMED -> advice.add(Component.translatable("ecology.advice.doomed"));
                case MISSING_QUEEN -> advice.add(Component.translatable("ecology.advice.missing_queen"));
                case NO_WORKERS -> advice.add(Component.translatable("ecology.advice.no_workers"));
                case LOW_WORKERS -> advice.add(Component.translatable("ecology.advice.low_workers"));
                case OVERCROWDED -> advice.add(Component.translatable("ecology.advice.overcrowded"));
                case INBRED -> advice.add(Component.translatable("ecology.advice.inbred"));
                case AGING_QUEEN -> advice.add(Component.translatable("ecology.advice.aging_queen"));
                case UNMATED_QUEEN -> advice.add(Component.translatable("ecology.advice.unmated_queen"));
                case LOW_FORAGE -> advice.add(Component.translatable("ecology.advice.low_forage"));
                case SWARM_READY -> {
                    // Added after issue processing so it can mention nearby empty hives and excluders once.
                }
            }
        }
    }

    private static BeekeeperFocus focusFor(ServerLevel level, BeehiveBlockEntity hive, ColonyData colony, ColonyHealth health, long day) {
        if (health.issues().contains(ColonyHealthIssue.NO_COLONY)) {
            return BeekeeperFocus.RELOCATION;
        }
        if (health.issues().contains(ColonyHealthIssue.ABANDONED)
                || health.issues().contains(ColonyHealthIssue.DOOMED)
                || health.issues().contains(ColonyHealthIssue.MISSING_QUEEN)
                || health.issues().contains(ColonyHealthIssue.NO_WORKERS)) {
            return BeekeeperFocus.RECOVERY;
        }
        if (ColonySwarming.isReady(level, hive.getBlockPos(), colony, day)
                || health.issues().contains(ColonyHealthIssue.OVERCROWDED)
                || health.issues().contains(ColonyHealthIssue.INBRED)
                || health.issues().contains(ColonyHealthIssue.AGING_QUEEN)) {
            return BeekeeperFocus.BREEDING;
        }
        if (health.issues().contains(ColonyHealthIssue.LOW_WORKERS)
                || health.issues().contains(ColonyHealthIssue.LOW_FORAGE)
                || health.issues().contains(ColonyHealthIssue.UNMATED_QUEEN)) {
            return BeekeeperFocus.GROWTH;
        }
        if (health.supportsPollinationBonus()) {
            return BeekeeperFocus.PRODUCTION;
        }
        return BeekeeperFocus.MONITORING;
    }

    private enum BeekeeperFocus {
        RELOCATION("relocation"),
        RECOVERY("recovery"),
        GROWTH("growth"),
        BREEDING("breeding"),
        PRODUCTION("production"),
        MONITORING("monitoring");

        private final String key;

        BeekeeperFocus(String key) {
            this.key = key;
        }
    }
}
