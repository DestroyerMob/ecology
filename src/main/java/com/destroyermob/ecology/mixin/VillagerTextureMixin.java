package com.destroyermob.ecology.mixin;

import com.destroyermob.ecology.client.VillagerEyeLayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.VillagerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VillagerRenderer.class)
public abstract class VillagerTextureMixin {
    @Inject(method = "<init>", at = @At("TAIL"))
    private void ecology$addVillageEyeLayer(EntityRendererProvider.Context context, CallbackInfo callback) {
        ((VillagerRenderer)(Object)this).addLayer(new VillagerEyeLayer((VillagerRenderer)(Object)this));
    }
}
