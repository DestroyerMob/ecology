package com.destroyermob.ecology.network;

import com.destroyermob.ecology.Ecology;
import com.destroyermob.ecology.bee.BeeRouteStopType;
import com.destroyermob.ecology.bee.BeeSearchArea;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record BeeRoutePayload(int entityId, List<BlockPos> route, int routeIndex, List<BeeSearchArea> searchAreas) implements CustomPacketPayload {
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
        buffer.writeVarInt(payload.routeIndex);
        buffer.writeVarInt(payload.searchAreas.size());
        for (BeeSearchArea searchArea : payload.searchAreas) {
            buffer.writeBlockPos(searchArea.min());
            buffer.writeBlockPos(searchArea.max());
            buffer.writeVarInt(searchArea.type().ordinal());
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
        int routeIndex = buffer.readVarInt();
        int searchAreaCount = buffer.readVarInt();
        List<BeeSearchArea> searchAreas = new ArrayList<>(Math.min(searchAreaCount, 8));
        for (int i = 0; i < searchAreaCount; i++) {
            BlockPos min = buffer.readBlockPos();
            BlockPos max = buffer.readBlockPos();
            BeeRouteStopType type = routeStopType(buffer.readVarInt());
            if (i < 8) {
                searchAreas.add(new BeeSearchArea(min, max, type));
            }
        }
        return new BeeRoutePayload(entityId, List.copyOf(route), routeIndex, List.copyOf(searchAreas));
    }

    private static BeeRouteStopType routeStopType(int ordinal) {
        BeeRouteStopType[] values = BeeRouteStopType.values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : BeeRouteStopType.FLOWER;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
