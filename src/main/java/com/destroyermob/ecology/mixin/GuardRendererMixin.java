package com.destroyermob.ecology.mixin;

import com.destroyermob.ecology.village.VillageCurrency;
import com.destroyermob.ecology.village.VillageCurrencyHolder;
import java.lang.reflect.InvocationTargetException;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "tallestegg.guardvillagers.client.renderer.GuardRenderer", remap = false)
public abstract class GuardRendererMixin {
    @Inject(
            method = "getTextureLocation(Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/resources/ResourceLocation;",
            at = @At("HEAD"),
            cancellable = true)
    private void ecology$guardVillageCurrencyTexture(Entity entity, CallbackInfoReturnable<ResourceLocation> callback) {
        if (entity instanceof VillageCurrencyHolder holder) {
            VillageCurrency currency = holder.ecology$getVillageCurrency();
            if (currency != VillageCurrency.EMERALD) {
                callback.setReturnValue(currency.guardTexture(ecology$guardsUseSteveTexture()));
            }
        }
    }

    @Unique
    private static boolean ecology$guardsUseSteveTexture() {
        try {
            Class<?> guardConfig = Class.forName(
                    "tallestegg.guardvillagers.configuration.GuardConfig",
                    false,
                    GuardRendererMixin.class.getClassLoader());
            Object clientConfig = guardConfig.getField("CLIENT").get(null);
            Object guardSteve = clientConfig.getClass().getField("GuardSteve").get(clientConfig);
            Object value = guardSteve.getClass().getMethod("get").invoke(guardSteve);
            return value instanceof Boolean enabled && enabled;
        } catch (ClassNotFoundException
                | NoSuchFieldException
                | NoSuchMethodException
                | IllegalAccessException
                | InvocationTargetException
                | LinkageError ignored) {
            return false;
        }
    }
}
