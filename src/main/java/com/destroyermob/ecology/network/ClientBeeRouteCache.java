package com.destroyermob.ecology.network;

import com.destroyermob.ecology.bee.BeeSearchArea;
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
        List<BeeSearchArea> searchAreas = List.copyOf(payload.searchAreas());
        int routeIndex = Math.max(0, payload.routeIndex());
        ROUTES.put(payload.entityId(), new CachedRoute(route, routeIndex, searchAreas, gameTime + 60));
        if (pendingLockBeeId == payload.entityId()) {
            lock(payload.entityId(), route, routeIndex, searchAreas);
            pendingLockBeeId = NO_BEE;
        } else if (lockedBeeId() == payload.entityId()) {
            lock(payload.entityId(), route, routeIndex, searchAreas);
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

    @Nullable
    public static List<BeeSearchArea> getSearchAreas(int entityId, long gameTime) {
        CachedRoute route = ROUTES.get(entityId);
        if (route == null) {
            return null;
        }
        if (route.expiresAt < gameTime) {
            ROUTES.remove(entityId);
            return null;
        }
        return route.searchAreas;
    }

    public static int getRouteIndex(int entityId, long gameTime) {
        CachedRoute route = validCachedRoute(entityId, gameTime);
        return route == null ? 0 : route.routeIndex;
    }

    public static void clearExpired(long gameTime) {
        ROUTES.entrySet().removeIf(entry -> entry.getValue().expiresAt < gameTime);
    }

    public static boolean lockCachedOrRequest(int entityId, long gameTime) {
        CachedRoute route = validCachedRoute(entityId, gameTime);
        if (route != null) {
            lock(entityId, route.positions, route.routeIndex, route.searchAreas);
            pendingLockBeeId = NO_BEE;
            return true;
        }
        lockedRoute = null;
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

    public static int lockedRouteIndex() {
        return lockedRoute == null ? 0 : lockedRoute.routeIndex;
    }

    @Nullable
    public static List<BeeSearchArea> lockedSearchAreas() {
        return lockedRoute == null ? null : lockedRoute.searchAreas;
    }

    @Nullable
    private static CachedRoute validCachedRoute(int entityId, long gameTime) {
        CachedRoute route = ROUTES.get(entityId);
        if (route == null) {
            return null;
        }
        if (route.expiresAt < gameTime) {
            ROUTES.remove(entityId);
            return null;
        }
        return route;
    }

    private static void lock(int entityId, List<BlockPos> route, int routeIndex, List<BeeSearchArea> searchAreas) {
        lockedRoute = new LockedRoute(entityId, List.copyOf(route), routeIndex, List.copyOf(searchAreas));
    }

    private record CachedRoute(List<BlockPos> positions, int routeIndex, List<BeeSearchArea> searchAreas, long expiresAt) {
    }

    private record LockedRoute(int entityId, List<BlockPos> positions, int routeIndex, List<BeeSearchArea> searchAreas) {
    }
}
