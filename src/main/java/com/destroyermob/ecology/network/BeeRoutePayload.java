package com.destroyermob.ecology.network;

import com.destroyermob.ecology.Ecology;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record BeeRoutePayload(int entityId, List<BlockPos> route) implements CustomPacketPayload {
    public static final Type<BeeRoutePayload> TYPE = new Type<>(Ecology.id("bee_route"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BeeRoutePayload> STREAM_CODEC = StreamCodec.of(
            BeeRoutePayload::encode,
            BeeRoutePayload::decode);

    private static void encode(RegistryFriendlyByteBuf buffer, BeeRoutePayload payload) {
        buffer.writeVarInt(payload.entityId);
        buffer.writeVarInt(payload.route.size());
        for (BlockPos pos : payload.route) {
            buffer.writeBlockPos(pos);
        }
    }

    private static BeeRoutePayload decode(RegistryFriendlyByteBuf buffer) {
        int entityId = buffer.readVarInt();
        int count = buffer.readVarInt();
        List<BlockPos> route = new ArrayList<>(Math.min(count, 64));
        for (int i = 0; i < count && i < 64; i++) {
            route.add(buffer.readBlockPos());
        }
        for (int i = 64; i < count; i++) {
            buffer.readBlockPos();
        }
        return new BeeRoutePayload(entityId, List.copyOf(route));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
