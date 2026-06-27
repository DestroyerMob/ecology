package com.destroyermob.ecology.village;

import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
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
    }
}
