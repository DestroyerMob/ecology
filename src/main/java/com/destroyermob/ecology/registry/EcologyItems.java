package com.destroyermob.ecology.registry;

import com.destroyermob.ecology.Ecology;
import com.destroyermob.ecology.item.WaxGogglesItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class EcologyItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Ecology.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Ecology.MOD_ID);

    public static final DeferredItem<Item> WAX_GOGGLES = ITEMS.register("wax_goggles", WaxGogglesItem::new);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> ECOLOGY_TAB =
            CREATIVE_MODE_TABS.register("ecology", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.ecology"))
                    .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
                    .icon(() -> WAX_GOGGLES.get().getDefaultInstance())
                    .displayItems((parameters, output) -> output.accept(WAX_GOGGLES.get()))
                    .build());

    private EcologyItems() {
    }
}
