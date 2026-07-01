package com.destroyermob.ecology.mixin;

import com.destroyermob.ecology.EcologyConfig;
import com.destroyermob.ecology.bee.BeeMemory;
import com.destroyermob.ecology.bee.BeeRole;
import com.destroyermob.ecology.bee.EcologyBeeSystem;
import net.minecraft.client.model.BeeModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.animal.Bee;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BeeModel.class)
public abstract class BeeModelMixin {
    @Shadow
    @Final
    private ModelPart stinger;

    @Inject(method = "prepareMobModel(Lnet/minecraft/world/entity/animal/Bee;FFF)V", at = @At("TAIL"))
    private void ecology$hideNonQueenStinger(Bee bee, float limbSwing, float limbSwingAmount, float partialTick, CallbackInfo callback) {
        if (!EcologyConfig.advancedBeeSimulationEnabled()) {
            return;
        }
        BeeMemory memory = EcologyBeeSystem.memory(bee);
        if (memory.role() == BeeRole.WORKER || memory.role() == BeeRole.DRONE) {
            this.stinger.visible = false;
        }
    }
}
