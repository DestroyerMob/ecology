package com.destroyermob.ecology.village;

import com.destroyermob.ecology.Ecology;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.TradeWithVillagerEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public class VillageGolemEvents {
    private final Set<String> disabledVillageSystems = new HashSet<>();

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        runVillageSystem("golem_construction", null, () -> VillageGolemConstruction.tick(event.getServer()));
        runVillageSystem("worksites", null, () -> VillageWorksites.tick(event.getServer()));
        runVillageSystem("construction_crews", null, () -> VillageConstructionCrews.tick(event.getServer()));
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
            runVillageSystem("vocations_join", villager, () -> VillageVocations.assignOnJoin(level, villager, event.loadedFromDisk()));
        }
        if (!event.getLevel().isClientSide()
                && event.getLevel() instanceof ServerLevel level
                && entity instanceof ServerPlayer player) {
            runVillageSystem("zones_join", null, () -> VillageZones.refreshAndResolveCenter(level, player.blockPosition(), player.chunkPosition(), true));
        }
    }

    @SubscribeEvent
    public void onEntityEnteringSection(EntityEvent.EnteringSection event) {
        if (!event.didChunkChange() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        runVillageSystem("zones_chunk", null, () -> VillageZones.onPlayerChangedChunk(player.serverLevel(), player, player.chunkPosition()));
    }

    @SubscribeEvent
    public void onVillagerTick(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof Villager villager && villager.level() instanceof ServerLevel level) {
            runVillageSystem("currency", villager, () -> VillageCurrencySystem.tickVillager(level, villager));
            runVillageSystem("vocations", villager, () -> VillageVocations.tickVillager(level, villager));
            runVillageSystem("relocation", villager, () -> VillageRelocation.tickVillager(level, villager));
            runVillageSystem("market_stalls", villager, () -> VillageMarketStalls.tickVillager(level, villager));
            runVillageSystem("welfare", villager, () -> VillageWelfare.tickVillager(level, villager));
            runVillageSystem("households", villager, () -> VillageHouseholds.tickVillager(level, villager));
            runVillageSystem("supplies", villager, () -> VillageSupplies.tickVillager(level, villager));
            runVillageSystem("ecology", villager, () -> VillageEcology.tickVillager(level, villager));
        }
    }

    @SubscribeEvent
    public void onTradeWithVillager(TradeWithVillagerEvent event) {
        runVillageSystem("household_trades", null, () -> VillageHouseholds.onTrade(event));
        boolean handled = runVillageSystem("player_trades", null, () -> VillagePlayerTrades.onTrade(event), false);
        if (!handled) {
            runVillageSystem("supply_trades", null, () -> VillageSupplies.onTrade(event));
        }
    }

    private void runVillageSystem(String system, Villager villager, Runnable action) {
        runVillageSystem(system, villager, () -> {
            action.run();
            return true;
        }, false);
    }

    private boolean runVillageSystem(String system, Villager villager, VillageSystemAction action, boolean fallback) {
        if (disabledVillageSystems.contains(system)) {
            return fallback;
        }
        try {
            return action.run();
        } catch (Exception | LinkageError exception) {
            disabledVillageSystems.add(system);
            if (villager == null) {
                Ecology.LOGGER.error("Disabled Ecology village {} system after failure", system, exception);
            } else {
                Ecology.LOGGER.error(
                        "Disabled Ecology village {} system after failure on villager {} at {}",
                        system,
                        villager.getUUID(),
                        villager.blockPosition(),
                        exception);
            }
            return fallback;
        }
    }

    @FunctionalInterface
    private interface VillageSystemAction {
        boolean run();
    }
}
