package com.destroyermob.ecology.network;

import com.destroyermob.ecology.EcologyConfig;
import com.destroyermob.ecology.bee.BeeMemory;
import com.destroyermob.ecology.bee.BeeSearchArea;
import com.destroyermob.ecology.bee.EcologyBeeSystem;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Bee;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class EcologyNetworking {
    private EcologyNetworking() {
    }

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1").optional();
        registrar.playToServer(BeeRouteRequestPayload.TYPE, BeeRouteRequestPayload.STREAM_CODEC, EcologyNetworking::handleRouteRequest);
        registrar.playToClient(BeeRoutePayload.TYPE, BeeRoutePayload.STREAM_CODEC, EcologyNetworking::handleRoutePayload);
    }

    private static void handleRouteRequest(BeeRouteRequestPayload payload, IPayloadContext context) {
        if (!EcologyConfig.advancedBeeSimulationEnabled()) {
            return;
        }
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        Entity entity = player.level().getEntity(payload.entityId());
        if (entity instanceof Bee bee && bee.distanceToSqr(player) <= 64.0) {
            PacketDistributor.sendToPlayer(player, routePayload(bee));
        }
    }

    public static void sendBeeRouteUpdate(Bee bee) {
        if (!EcologyConfig.advancedBeeSimulationEnabled()) {
            return;
        }
        PacketDistributor.sendToPlayersTrackingEntity(bee, routePayload(bee));
    }

    private static BeeRoutePayload routePayload(Bee bee) {
        BeeMemory memory = EcologyBeeSystem.memory(bee);
        return new BeeRoutePayload(
                bee.getId(),
                memory.routePositions(),
                memory.routeIndex(),
                searchAreas(memory));
    }

    private static List<BeeSearchArea> searchAreas(BeeMemory memory) {
        if (memory.lastSearchArea() == null) {
            return List.of();
        }
        return List.of(memory.lastSearchArea());
    }

    private static void handleRoutePayload(BeeRoutePayload payload, IPayloadContext context) {
        ClientBeeRouteCache.accept(payload, context.player().level().getGameTime());
    }
}
