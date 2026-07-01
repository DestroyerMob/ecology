package com.destroyermob.ecology.village;

import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.TradeWithVillagerEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public class VillageGolemEvents {
    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        VillageGolemConstruction.tick(event.getServer());
    }

    @SubscribeEvent
    public void onEntityJoinLevel(EntityJoinLevelEvent event) {
        Entity entity = event.getEntity();
        if (!event.getLevel().isClientSide()
                && event.loadedFromDisk()
                && VillageGolemConstruction.isOrphanedConstructionDisplay(entity)) {
            event.setCanceled(true);
        }
        if (!event.getLevel().isClientSide()
                && event.getLevel() instanceof ServerLevel level
                && entity instanceof Villager villager) {
            VillageVocations.assignOnJoin(level, villager, event.loadedFromDisk());
        }
    }

    @SubscribeEvent
    public void onVillagerTick(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof Villager villager && villager.level() instanceof ServerLevel level) {
            VillageCurrencySystem.tickVillager(level, villager);
            VillageVocations.tickVillager(level, villager);
            VillageRelocation.tickVillager(level, villager);
            VillageMarketStalls.tickVillager(level, villager);
            VillageWelfare.tickVillager(level, villager);
            VillageSupplies.tickVillager(level, villager);
            VillageEcology.tickVillager(level, villager);
        }
    }

    @SubscribeEvent
    public void onTradeWithVillager(TradeWithVillagerEvent event) {
        if (!VillagePlayerTrades.onTrade(event)) {
            VillageSupplies.onTrade(event);
        }
    }
}
