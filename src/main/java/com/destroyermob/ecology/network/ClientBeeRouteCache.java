package com.destroyermob.ecology.network;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

public final class ClientBeeRouteCache {
    private static final Map<Integer, CachedRoute> ROUTES = new HashMap<>();
    private static final int NO_BEE = -1;
    private static int pendingLockBeeId = NO_BEE;
    @Nullable
    private static LockedRoute lockedRoute;

    private ClientBeeRouteCache() {
    }

    public static void accept(BeeRoutePayload payload, long gameTime) {
        List<BlockPos> route = List.copyOf(payload.route());
        ROUTES.put(payload.entityId(), new CachedRoute(route, gameTime + 60));
        if (pendingLockBeeId == payload.entityId()) {
            lock(payload.entityId(), route);
            pendingLockBeeId = NO_BEE;
        }
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

    public static boolean lockCachedOrRequest(int entityId, long gameTime) {
        List<BlockPos> route = get(entityId, gameTime);
        if (route != null) {
            lock(entityId, route);
            pendingLockBeeId = NO_BEE;
            return true;
        }
        pendingLockBeeId = entityId;
        return false;
    }

    public static void clearLock() {
        lockedRoute = null;
        pendingLockBeeId = NO_BEE;
    }

    public static boolean hasLockedRoute() {
        return lockedRoute != null;
    }

    public static int lockedBeeId() {
        return lockedRoute == null ? NO_BEE : lockedRoute.entityId;
    }

    @Nullable
    public static List<BlockPos> lockedRoute() {
        return lockedRoute == null ? null : lockedRoute.positions;
    }

    private static void lock(int entityId, List<BlockPos> route) {
        lockedRoute = new LockedRoute(entityId, List.copyOf(route));
    }

    private record CachedRoute(List<BlockPos> positions, long expiresAt) {
    }

    private record LockedRoute(int entityId, List<BlockPos> positions) {
    }
}
