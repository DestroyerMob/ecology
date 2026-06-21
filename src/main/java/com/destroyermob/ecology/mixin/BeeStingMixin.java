package com.destroyermob.ecology.mixin;

import com.destroyermob.ecology.Ecology;
import com.destroyermob.ecology.EcologyConfig;
import com.destroyermob.ecology.bee.BeeAggressionCause;
import com.destroyermob.ecology.bee.EcologyBeeSystem;
import com.destroyermob.ecology.bee.BeeMemory;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Bee.class)
public abstract class BeeStingMixin {
    @Inject(method = "doHurtTarget", at = @At("HEAD"), cancellable = true)
    private void ecology$olderDirectAttackDoesNotSpendStinger(Entity target, CallbackInfoReturnable<Boolean> callback) {
        if (!EcologyConfig.ENABLE_BEE_SYSTEM.get() || !EcologyConfig.REPLACE_VANILLA_BEE_GOALS.get()) {
            return;
        }
        Bee bee = (Bee) (Object) this;
        try {
            BeeMemory memory = EcologyBeeSystem.memory(bee);
            if (memory.aggressionCause() != BeeAggressionCause.DIRECT_ATTACK || EcologyBeeSystem.isFirstDay(bee)) {
                return;
            }

            DamageSource damageSource = bee.damageSources().mobAttack(bee);
            boolean hurt = target.hurt(damageSource, (float) ((int) bee.getAttributeValue(Attributes.ATTACK_DAMAGE)));
            EcologyBeeSystem.setStung(bee, false);
            callback.setReturnValue(hurt);
        } catch (RuntimeException exception) {
            Ecology.LOGGER.error("Ecology bee sting override failed; falling back to vanilla behavior", exception);
        }
    }
}
