package com.destroyermob.ecology.bee;

import com.destroyermob.ecology.registry.EcologyAttachments;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

public class EcologyBeeEvents {
    @SubscribeEvent
    public void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof Bee bee && !event.getLevel().isClientSide()) {
            EcologyBeeSystem.initializeBee(bee);
            BeeMemory memory = EcologyBeeSystem.memory(bee);
            if (!memory.ecologyGoalsAdded()) {
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

        EcologyBeeSystem.initializeBee(bee);
        BeeMemory memory = EcologyBeeSystem.memory(bee);
        if (memory.birthDay() >= 0 && EcologyBeeSystem.day(level) - memory.birthDay() >= memory.role().lifespanDays()) {
            bee.hurt(bee.damageSources().generic(), bee.getMaxHealth());
            return;
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
        bee.syncData(EcologyAttachments.BEE_MEMORY.get());
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
}
