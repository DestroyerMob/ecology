package com.destroyermob.ecology.bee;

import com.destroyermob.ecology.Ecology;
import com.destroyermob.ecology.EcologyConfig;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

public class EcologyBeeEvents {
    private static final Set<UUID> DISABLED_BEE_LOGIC = ConcurrentHashMap.newKeySet();

    @SubscribeEvent
    public void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof Bee bee) || event.getLevel().isClientSide() || !EcologyConfig.advancedBeeSimulationEnabled()) {
            return;
        }
        if (DISABLED_BEE_LOGIC.contains(bee.getUUID())) {
            return;
        }

        try {
            EcologyBeeSystem.initializeBee(bee);
            BeeMemory memory = EcologyBeeSystem.memory(bee);
            debug("Initialized Ecology bee memory for {} at {}", bee.getUUID(), bee.blockPosition());
            if (EcologyConfig.replaceVanillaBeeGoalsEnabled()) {
                bee.getGoalSelector().removeAllGoals(EcologyBeeEvents::isEcologyBeeGoal);
                bee.getGoalSelector().removeAllGoals(EcologyBeeEvents::isReplacedVanillaBeeGoal);
                if (memory.role() == BeeRole.DRONE) {
                    removeDroneCombatGoals(bee);
                    EcologyBeeSystem.ensureDroneHasNoStinger(bee, memory);
                }
                addGoalsForRole(bee, memory.role());
                memory.setEcologyGoalsAdded(true);
                debug("Added Ecology {} goals to {}", memory.role(), bee.getUUID());
            }
        } catch (RuntimeException exception) {
            disableBeeLogic(bee, "initialization", exception);
        }
    }

    @SubscribeEvent
    public void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Bee bee) || !(bee.level() instanceof ServerLevel level)
                || !EcologyConfig.advancedBeeSimulationEnabled()
                || DISABLED_BEE_LOGIC.contains(bee.getUUID())) {
            return;
        }

        try {
            tickBee(bee, level);
        } catch (RuntimeException exception) {
            disableBeeLogic(bee, "entity tick", exception);
        }
    }

    private static void tickBee(Bee bee, ServerLevel level) {
        BeeMemory memory = EcologyBeeSystem.memory(bee);
        if (memory.birthDay() < 0 || (EcologyConfig.replaceVanillaBeeGoalsEnabled() && shouldRepairMemory(bee, memory))) {
            EcologyBeeSystem.initializeBee(bee);
        }
        if (EcologyConfig.beeLifespanDeathEnabled()
                && memory.birthDay() >= 0
                && EcologyBeeSystem.ageDays(level, memory) >= EcologyBeeSystem.lifespanDays(level, memory)) {
            bee.hurt(bee.damageSources().generic(), bee.getMaxHealth());
            return;
        }
        EcologyBeeSystem.ensureDroneHasNoStinger(bee, memory);
        EcologyBeeSystem.clearSimulatorProtectedAggression(bee, memory);

        if (!EcologyConfig.replaceVanillaBeeGoalsEnabled()) {
            return;
        }

        if (memory.returningHome() && memory.homeHive() == null) {
            memory.setReturningHome(false);
        }
        if (memory.role() == BeeRole.WORKER
                && memory.dailyComplete()
                && memory.routeDay() == EcologyBeeSystem.day(level)
                && !memory.returningHome()
                && memory.homeHive() != null
                && !bee.isAngry()
                && !bee.hasStung()) {
            EcologyBeeSystem.sendHome(bee);
        }

        if (bee.tickCount % 10 == 0 && memory.role() == BeeRole.WORKER && !memory.dailyComplete() && !bee.hasStung()) {
            boolean playerNearRoute = false;
            for (Player player : level.players()) {
                if (!player.isSpectator() && EcologyBeeSystem.isPlayerNearRoute(player, memory)) {
                    playerNearRoute = true;
                    handleRouteObstruction(bee, memory, player);
                    break;
                }
            }
            if (!playerNearRoute && memory.routeAgitationTicks() > 0) {
                memory.setRouteAgitationTicks(memory.routeAgitationTicks() - 10);
            }
        }

        if (bee.getTarget() == null && memory.aggressionCause() != BeeAggressionCause.NONE) {
            memory.setAggressionCause(BeeAggressionCause.NONE);
        }
    }

    @SubscribeEvent
    public void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof Bee bee)
                || bee.level().isClientSide()
                || !EcologyConfig.advancedBeeSimulationEnabled()
                || !EcologyConfig.replaceVanillaBeeGoalsEnabled()
                || DISABLED_BEE_LOGIC.contains(bee.getUUID())) {
            return;
        }

        Entity attacker = event.getSource().getEntity();
        if (attacker instanceof LivingEntity livingAttacker) {
            try {
                BeeMemory memory = EcologyBeeSystem.memory(bee);
                if (memory.role() == BeeRole.DRONE) {
                    EcologyBeeSystem.ensureDroneHasNoStinger(bee, memory);
                    return;
                }
                if (livingAttacker instanceof Player player && EcologyBeeSystem.isHoldingHiveDaySimulator(player)) {
                    EcologyBeeSystem.clearSimulatorProtectedAggression(bee, memory);
                    return;
                }
                EcologyBeeSystem.markDirectAttack(bee, livingAttacker);
            } catch (RuntimeException exception) {
                disableBeeLogic(bee, "damage handling", exception);
            }
        }
    }

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof Bee bee
                && bee.level() instanceof ServerLevel level
                && EcologyConfig.advancedBeeSimulationEnabled()
                && EcologyConfig.hiveColonyTickingEnabled()
                && !DISABLED_BEE_LOGIC.contains(bee.getUUID())) {
            try {
                EcologyBeeSystem.forgetAtHomeHive(level, EcologyBeeSystem.memory(bee));
            } catch (RuntimeException exception) {
                disableBeeLogic(bee, "death handling", exception);
            }
        }
    }

    private static boolean isReplacedVanillaBeeGoal(Goal goal) {
        String name = goal.getClass().getName();
        return name.equals("net.minecraft.world.entity.animal.Bee$BeeEnterHiveGoal")
                || name.equals("net.minecraft.world.entity.animal.Bee$BeePollinateGoal")
                || name.equals("net.minecraft.world.entity.animal.Bee$BeeLocateHiveGoal")
                || name.equals("net.minecraft.world.entity.animal.Bee$BeeGoToHiveGoal")
                || name.equals("net.minecraft.world.entity.animal.Bee$BeeGoToKnownFlowerGoal")
                || name.equals("net.minecraft.world.entity.animal.Bee$BeeGrowCropGoal")
                || name.equals("net.minecraft.world.entity.ai.goal.BreedGoal");
    }

    private static boolean isVanillaBeeAttackGoal(Goal goal) {
        return goal.getClass().getName().equals("net.minecraft.world.entity.animal.Bee$BeeAttackGoal");
    }

    private static boolean isVanillaBeeAggressionTargetGoal(Goal goal) {
        String name = goal.getClass().getName();
        return name.equals("net.minecraft.world.entity.animal.Bee$BeeHurtByOtherGoal")
                || name.equals("net.minecraft.world.entity.animal.Bee$BeeBecomeAngryTargetGoal")
                || name.equals("net.minecraft.world.entity.ai.goal.target.ResetUniversalAngerTargetGoal");
    }

    private static boolean isEcologyBeeGoal(Goal goal) {
        return goal instanceof EcologyBeeGoals.ReturnHomeGoal
                || goal instanceof EcologyBeeGoals.WorkerRouteGoal
                || goal instanceof EcologyBeeGoals.DroneMatingGoal
                || goal instanceof EcologyBeeGoals.QueenMigrationGoal
                || goal instanceof EcologyBeeGoals.QueenReturnHomeGoal;
    }

    private static void addGoalsForRole(Bee bee, BeeRole role) {
        bee.getGoalSelector().addGoal(1, new EcologyBeeGoals.ReturnHomeGoal(bee));
        switch (role) {
            case WORKER -> bee.getGoalSelector().addGoal(2, new EcologyBeeGoals.WorkerRouteGoal(bee));
            case DRONE -> {
                if (EcologyConfig.droneMatingGoalEnabled()) {
                    bee.getGoalSelector().addGoal(2, new EcologyBeeGoals.DroneMatingGoal(bee));
                }
            }
            case QUEEN -> {
                if (EcologyConfig.queenMigrationGoalEnabled()) {
                    bee.getGoalSelector().addGoal(2, new EcologyBeeGoals.QueenMigrationGoal(bee));
                    bee.getGoalSelector().addGoal(3, new EcologyBeeGoals.QueenReturnHomeGoal(bee));
                }
            }
        }
    }

    private static void removeDroneCombatGoals(Bee bee) {
        bee.getGoalSelector().removeAllGoals(EcologyBeeEvents::isVanillaBeeAttackGoal);
        bee.targetSelector.removeAllGoals(EcologyBeeEvents::isVanillaBeeAggressionTargetGoal);
    }

    private static boolean shouldRepairMemory(Bee bee, BeeMemory memory) {
        if (memory.birthDay() < 0) {
            return true;
        }
        if (memory.homeHive() == null && bee.tickCount % 200 == 0) {
            return true;
        }
        return memory.homeHive() == null && bee.hasHive();
    }

    private static void handleRouteObstruction(Bee bee, BeeMemory memory, Player player) {
        int previous = memory.routeAgitationTicks();
        int threshold = bee.level() instanceof ServerLevel level
                ? EcologyBeeSystem.routeAgitationThreshold(level, memory)
                : EcologyConfig.ROUTE_AGITATION_ATTACK_TICKS.get();
        int next = Math.min(threshold, previous + 10);
        memory.setRouteAgitationTicks(next);
        if (previous == 0 || (next < threshold && next % 40 == 0)) {
            player.displayClientMessage(Component.translatable("message.ecology.bee_route_warning"), true);
        }
        if (next >= threshold) {
            EcologyBeeSystem.markPathBlocked(bee, player);
        }
    }

    private static void disableBeeLogic(Bee bee, String phase, RuntimeException exception) {
        DISABLED_BEE_LOGIC.add(bee.getUUID());
        Ecology.LOGGER.error("Disabled Ecology bee logic for {} after {} failure", bee.getUUID(), phase, exception);
    }

    private static void debug(String message, Object... args) {
        if (EcologyConfig.beeSystemDebugLoggingEnabled()) {
            Ecology.LOGGER.debug(message, args);
        }
    }
}
