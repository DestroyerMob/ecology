package com.destroyermob.ecology.network;

import com.destroyermob.ecology.Ecology;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record BeeRouteRequestPayload(int entityId) implements CustomPacketPayload {
    public static final Type<BeeRouteRequestPayload> TYPE = new Type<>(Ecology.id("bee_route_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BeeRouteRequestPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> buffer.writeVarInt(payload.entityId),
            buffer -> new BeeRouteRequestPayload(buffer.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
