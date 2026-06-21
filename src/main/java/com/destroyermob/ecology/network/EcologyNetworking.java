package com.destroyermob.ecology.network;

import com.destroyermob.ecology.bee.EcologyBeeSystem;
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
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        Entity entity = player.level().getEntity(payload.entityId());
        if (entity instanceof Bee bee && bee.distanceToSqr(player) <= 64.0) {
            PacketDistributor.sendToPlayer(player, new BeeRoutePayload(payload.entityId(), EcologyBeeSystem.memory(bee).routePositions()));
        }
    }

    private static void handleRoutePayload(BeeRoutePayload payload, IPayloadContext context) {
        ClientBeeRouteCache.accept(payload, context.player().level().getGameTime());
    }
}
