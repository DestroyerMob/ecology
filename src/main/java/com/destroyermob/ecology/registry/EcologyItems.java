package com.destroyermob.ecology.registry;

import com.destroyermob.ecology.Ecology;
import com.destroyermob.ecology.bee.ApiaryTreatment;
import com.destroyermob.ecology.item.ApiaryTreatmentItem;
import com.destroyermob.ecology.item.BeeNestCuttingKnifeItem;
import com.destroyermob.ecology.item.BeekeepersJournalItem;
import com.destroyermob.ecology.item.BroodCombItem;
import com.destroyermob.ecology.item.CapturedWorkerBeeItem;
import com.destroyermob.ecology.item.HiveDaySimulatorItem;
import com.destroyermob.ecology.item.InspectionTrayItem;
import com.destroyermob.ecology.item.QueenCellItem;
import com.destroyermob.ecology.item.SwarmLureItem;
import com.destroyermob.ecology.item.VillageLedgerItem;
import com.destroyermob.ecology.item.WaxGogglesItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.BlockItem;
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
    public static final DeferredItem<Item> BEEKEEPERS_JOURNAL = ITEMS.register("beekeepers_journal", BeekeepersJournalItem::new);
    public static final DeferredItem<Item> VILLAGE_LEDGER = ITEMS.register("village_ledger", VillageLedgerItem::new);
    public static final DeferredItem<BlockItem> TRADEBOARD = ITEMS.registerSimpleBlockItem(EcologyBlocks.TRADEBOARD);
    public static final DeferredItem<Item> INSPECTION_TRAY = ITEMS.register("inspection_tray", InspectionTrayItem::new);
    public static final DeferredItem<Item> HIVE_DAY_SIMULATOR = ITEMS.register("hive_day_simulator", HiveDaySimulatorItem::new);
    public static final DeferredItem<Item> BEEKEEPER_KNIFE = ITEMS.register("beekeeper_knife", BeeNestCuttingKnifeItem::new);
    public static final DeferredItem<Item> BROOD_COMB = ITEMS.register("brood_comb", BroodCombItem::new);
    public static final DeferredItem<Item> CAPTURED_WORKER_BEE = ITEMS.register("captured_worker_bee", CapturedWorkerBeeItem::new);
    public static final DeferredItem<Item> QUEEN_CELL = ITEMS.register("queen_cell", QueenCellItem::new);
    public static final DeferredItem<Item> SWARM_LURE = ITEMS.register("swarm_lure", SwarmLureItem::new);
    public static final DeferredItem<Item> APIARY_SMOKER = ITEMS.register("apiary_smoker", () -> new ApiaryTreatmentItem(ApiaryTreatment.SMOKE, false));
    public static final DeferredItem<Item> HIVE_STAND = ITEMS.register("hive_stand", () -> new ApiaryTreatmentItem(ApiaryTreatment.HIVE_STAND, true));
    public static final DeferredItem<Item> QUEEN_EXCLUDER = ITEMS.register("queen_excluder", () -> new ApiaryTreatmentItem(ApiaryTreatment.QUEEN_EXCLUDER, true));
    public static final DeferredItem<Item> BROOD_FRAME = ITEMS.register("brood_frame", () -> new ApiaryTreatmentItem(ApiaryTreatment.BROOD_FRAME, true));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> ECOLOGY_TAB =
            CREATIVE_MODE_TABS.register("ecology", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.ecology"))
                    .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
                    .icon(() -> WAX_GOGGLES.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(WAX_GOGGLES.get());
                        output.accept(DEBUG_WAX_GOGGLES.get());
                        output.accept(BEEKEEPERS_JOURNAL.get());
                        output.accept(VILLAGE_LEDGER.get());
                        output.accept(TRADEBOARD.get());
                        output.accept(INSPECTION_TRAY.get());
                        output.accept(HIVE_DAY_SIMULATOR.get());
                        output.accept(BEEKEEPER_KNIFE.get());
                        output.accept(BROOD_COMB.get());
                        output.accept(CAPTURED_WORKER_BEE.get());
                        output.accept(QUEEN_CELL.get());
                        output.accept(SWARM_LURE.get());
                        output.accept(APIARY_SMOKER.get());
                        output.accept(HIVE_STAND.get());
                        output.accept(QUEEN_EXCLUDER.get());
                        output.accept(BROOD_FRAME.get());
                    })
                    .build());

    private EcologyItems() {
    }
}
