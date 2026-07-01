package com.destroyermob.ecology.mixin;

import com.destroyermob.ecology.village.VillageCurrencyHolder;
import net.minecraft.client.renderer.entity.VillagerRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(VillagerRenderer.class)
public abstract class VillagerTextureMixin {
    @Inject(method = "getTextureLocation(Lnet/minecraft/world/entity/npc/Villager;)Lnet/minecraft/resources/ResourceLocation;", at = @At("HEAD"), cancellable = true)
    private void ecology$villageCurrencyTexture(Villager villager, CallbackInfoReturnable<ResourceLocation> callback) {
        if (villager instanceof VillageCurrencyHolder holder) {
            callback.setReturnValue(holder.ecology$getVillageCurrency().villagerTexture(villager.isBaby()));
        }
    }
}
