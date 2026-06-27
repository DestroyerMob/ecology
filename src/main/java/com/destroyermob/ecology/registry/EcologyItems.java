package com.destroyermob.ecology.registry;

import com.destroyermob.ecology.Ecology;
import com.destroyermob.ecology.item.BeeNestCuttingKnifeItem;
import com.destroyermob.ecology.item.BroodCombItem;
import com.destroyermob.ecology.item.CapturedWorkerBeeItem;
import com.destroyermob.ecology.item.HiveDaySimulatorItem;
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
    public static final DeferredItem<Item> DEBUG_WAX_GOGGLES = ITEMS.register("debug_wax_goggles", WaxGogglesItem::new);
    public static final DeferredItem<Item> HIVE_DAY_SIMULATOR = ITEMS.register("hive_day_simulator", HiveDaySimulatorItem::new);
    public static final DeferredItem<Item> BEEKEEPER_KNIFE = ITEMS.register("beekeeper_knife", BeeNestCuttingKnifeItem::new);
    public static final DeferredItem<Item> BROOD_COMB = ITEMS.register("brood_comb", BroodCombItem::new);
    public static final DeferredItem<Item> CAPTURED_WORKER_BEE = ITEMS.register("captured_worker_bee", CapturedWorkerBeeItem::new);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> ECOLOGY_TAB =
            CREATIVE_MODE_TABS.register("ecology", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.ecology"))
                    .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
                    .icon(() -> WAX_GOGGLES.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(WAX_GOGGLES.get());
                        output.accept(DEBUG_WAX_GOGGLES.get());
                        output.accept(HIVE_DAY_SIMULATOR.get());
                        output.accept(BEEKEEPER_KNIFE.get());
                        output.accept(BROOD_COMB.get());
                        output.accept(CAPTURED_WORKER_BEE.get());
                    })
                    .build());

    private EcologyItems() {
    }
}
