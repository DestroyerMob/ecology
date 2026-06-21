package com.destroyermob.ecology.compat.jei;

import com.destroyermob.ecology.Ecology;
import com.destroyermob.ecology.registry.EcologyItems;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

@JeiPlugin
public class EcologyJeiPlugin implements IModPlugin {
    private static final ResourceLocation UID = Ecology.id("jei");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addItemStackInfo(
                new ItemStack(EcologyItems.WAX_GOGGLES.get()),
                Component.translatable("jei.ecology.wax_goggles.line1"),
                Component.translatable("jei.ecology.wax_goggles.line2"));
    }
}
