package com.destroyermob.ecology.network;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

public final class ClientBeeRouteCache {
    private static final Map<Integer, CachedRoute> ROUTES = new HashMap<>();

    private ClientBeeRouteCache() {
    }

    public static void accept(BeeRoutePayload payload, long gameTime) {
        ROUTES.put(payload.entityId(), new CachedRoute(payload.route(), gameTime + 60));
    }

    @Nullable
    public static List<BlockPos> get(int entityId, long gameTime) {
        CachedRoute route = ROUTES.get(entityId);
        if (route == null) {
            return null;
        }
        if (route.expiresAt < gameTime) {
            ROUTES.remove(entityId);
            return null;
        }
        return route.positions;
    }

    public static void clearExpired(long gameTime) {
        ROUTES.entrySet().removeIf(entry -> entry.getValue().expiresAt < gameTime);
    }

    private record CachedRoute(List<BlockPos> positions, long expiresAt) {
    }
}
