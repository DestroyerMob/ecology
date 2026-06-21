package com.destroyermob.ecology.bee;

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
    @SubscribeEvent
    public void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof Bee bee && !event.getLevel().isClientSide()) {
            EcologyBeeSystem.initializeBee(bee);
            BeeMemory memory = EcologyBeeSystem.memory(bee);
            if (!memory.ecologyGoalsAdded()) {
                bee.getGoalSelector().removeAllGoals(EcologyBeeEvents::isReplacedVanillaBeeGoal);
                bee.getGoalSelector().addGoal(1, new EcologyBeeGoals.ReturnHomeGoal(bee));
                bee.getGoalSelector().addGoal(2, new EcologyBeeGoals.WorkerRouteGoal(bee));
                bee.getGoalSelector().addGoal(2, new EcologyBeeGoals.DroneMatingGoal(bee));
                bee.getGoalSelector().addGoal(2, new EcologyBeeGoals.QueenMigrationGoal(bee));
                memory.setEcologyGoalsAdded(true);
            }
        }
    }

    @SubscribeEvent
    public void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Bee bee) || !(bee.level() instanceof ServerLevel level)) {
            return;
        }

        BeeMemory memory = EcologyBeeSystem.memory(bee);
        if (shouldRepairMemory(bee, memory)) {
            EcologyBeeSystem.initializeBee(bee);
        }
        if (memory.birthDay() >= 0 && EcologyBeeSystem.day(level) - memory.birthDay() >= memory.role().lifespanDays()) {
            bee.hurt(bee.damageSources().generic(), bee.getMaxHealth());
            return;
        }

        if (memory.returningHome() && memory.homeHive() == null) {
            memory.setReturningHome(false);
        }
        if (memory.role() == BeeRole.WORKER
                && memory.dailyComplete()
                && !memory.returningHome()
                && memory.homeHive() != null
                && !bee.isAngry()
                && !bee.hasStung()) {
            EcologyBeeSystem.sendHome(bee);
        }

        if (bee.tickCount % 10 == 0 && memory.role() == BeeRole.WORKER && !memory.dailyComplete() && !bee.hasStung()) {
            for (Player player : level.players()) {
                if (!player.isSpectator() && EcologyBeeSystem.isPlayerNearRoute(player, memory)) {
                    EcologyBeeSystem.markPathBlocked(bee, player);
                    break;
                }
            }
        }

        if (bee.getTarget() == null && memory.aggressionCause() != BeeAggressionCause.NONE) {
            memory.setAggressionCause(BeeAggressionCause.NONE);
        }
    }

    @SubscribeEvent
    public void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof Bee bee) || bee.level().isClientSide()) {
            return;
        }

        Entity attacker = event.getSource().getEntity();
        if (attacker instanceof LivingEntity livingAttacker) {
            EcologyBeeSystem.markDirectAttack(bee, livingAttacker);
        }
    }

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof Bee bee && bee.level() instanceof ServerLevel level) {
            EcologyBeeSystem.forgetAtHomeHive(level, EcologyBeeSystem.memory(bee));
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

    private static boolean shouldRepairMemory(Bee bee, BeeMemory memory) {
        if (memory.birthDay() < 0) {
            return true;
        }
        if (memory.homeHive() == null && bee.tickCount % 200 == 0) {
            return true;
        }
        return memory.homeHive() == null && bee.hasHive();
    }
}
