package com.destroyermob.ecology.client;

import com.destroyermob.ecology.bee.BeeRouteStopType;
import com.destroyermob.ecology.bee.BeeSearchArea;
import com.destroyermob.ecology.network.BeeRouteRequestPayload;
import com.destroyermob.ecology.network.ClientBeeRouteCache;
import com.destroyermob.ecology.registry.EcologyItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public class WaxGogglesClientEvents {
    private static final int ROUTE_PARTICLE_INTERVAL_TICKS = 4;
    private static final int MAX_ROUTE_PARTICLES_PER_BURST = 96;
    private static final double ROUTE_PARTICLE_SPACING = 0.9;
    private static final double ROUTE_PARTICLE_HEIGHT = 0.15;
    private int hoveredBeeId = -1;
    private int requestCooldown;

    @SubscribeEvent
    public void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            hoveredBeeId = -1;
            return;
        }

        ClientBeeRouteCache.clearExpired(minecraft.level.getGameTime());
        GoggleMode goggleMode = goggleMode(minecraft.player.getItemBySlot(EquipmentSlot.HEAD));
        if (goggleMode == GoggleMode.NONE) {
            hoveredBeeId = -1;
            ClientBeeRouteCache.clearLock();
            return;
        }

        HitResult hitResult = minecraft.hitResult;
        if (hitResult instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof Bee bee) {
            hoveredBeeId = bee.getId();
        } else {
            hoveredBeeId = -1;
        }

        int lockedBeeId = ClientBeeRouteCache.lockedBeeId();
        if (hoveredBeeId >= 0 || lockedBeeId >= 0) {
            if (requestCooldown-- <= 0) {
                requestCooldown = 10;
                if (hoveredBeeId >= 0) {
                    PacketDistributor.sendToServer(new BeeRouteRequestPayload(hoveredBeeId));
                }
                if (lockedBeeId >= 0 && lockedBeeId != hoveredBeeId) {
                    PacketDistributor.sendToServer(new BeeRouteRequestPayload(lockedBeeId));
                }
            }
        } else {
            requestCooldown = 0;
        }

        if (minecraft.screen == null) {
            while (EcologyKeyMappings.LOCK_BEE_ROUTE.consumeClick()) {
                handleRouteLockKey(minecraft);
            }
        }

        if (goggleMode == GoggleMode.NORMAL
                && minecraft.level.getGameTime() % ROUTE_PARTICLE_INTERVAL_TICKS == 0) {
            spawnNextGoalParticles(minecraft, displayedBeeId(), displayedRoute(minecraft), displayedRouteIndex(minecraft));
        }
    }

    @SubscribeEvent
    public void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        if (goggleMode(minecraft.player.getItemBySlot(EquipmentSlot.HEAD)) != GoggleMode.DEBUG) {
            return;
        }

        List<BlockPos> route = displayedRoute(minecraft);
        List<BeeSearchArea> searchAreas = displayedSearchAreas(minecraft);
        boolean hasRoute = route != null && !route.isEmpty();
        boolean hasSearchAreas = searchAreas != null && !searchAreas.isEmpty();
        if (!hasRoute && !hasSearchAreas) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);

        MultiBufferSource.BufferSource buffer = minecraft.renderBuffers().bufferSource();
        VertexConsumer consumer = buffer.getBuffer(RenderType.lines());
        if (hasSearchAreas) {
            drawSearchAreas(poseStack, consumer, searchAreas);
        }
        if (hasRoute) {
            for (int i = 0; i < route.size(); i++) {
                BlockPos pos = route.get(i);
                float red = i % 2 == 0 ? 1.0F : 0.2F;
                float green = i % 2 == 0 ? 0.84F : 0.95F;
                float blue = i % 2 == 0 ? 0.15F : 0.25F;
                LevelRenderer.renderLineBox(poseStack, consumer, pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0, red, green, blue, 0.9F);
                if (i < route.size() - 1) {
                    drawLine(poseStack, consumer, Vec3.atCenterOf(pos), Vec3.atCenterOf(route.get(i + 1)), 1.0F, 0.76F, 0.05F, 0.95F);
                }
            }
        }
        buffer.endBatch(RenderType.lines());
        poseStack.popPose();
    }

    private void handleRouteLockKey(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }
        if (hoveredBeeId < 0) {
            if (ClientBeeRouteCache.hasLockedRoute()) {
                ClientBeeRouteCache.clearLock();
                minecraft.player.displayClientMessage(Component.translatable("message.ecology.bee_route_unlocked"), true);
            }
            return;
        }
        if (ClientBeeRouteCache.lockedBeeId() == hoveredBeeId) {
            ClientBeeRouteCache.clearLock();
            minecraft.player.displayClientMessage(Component.translatable("message.ecology.bee_route_unlocked"), true);
            return;
        }

        boolean replacingLockedRoute = ClientBeeRouteCache.hasLockedRoute();
        boolean lockedImmediately = ClientBeeRouteCache.lockCachedOrRequest(hoveredBeeId, minecraft.level.getGameTime());
        PacketDistributor.sendToServer(new BeeRouteRequestPayload(hoveredBeeId));
        minecraft.player.displayClientMessage(Component.translatable(
                routeLockMessage(lockedImmediately, replacingLockedRoute)), true);
    }

    private static String routeLockMessage(boolean lockedImmediately, boolean replacingLockedRoute) {
        if (lockedImmediately) {
            return replacingLockedRoute ? "message.ecology.bee_route_swapped" : "message.ecology.bee_route_locked";
        }
        return replacingLockedRoute ? "message.ecology.bee_route_swap_requested" : "message.ecology.bee_route_lock_requested";
    }

    private List<BlockPos> displayedRoute(Minecraft minecraft) {
        List<BlockPos> lockedRoute = ClientBeeRouteCache.lockedRoute();
        if (lockedRoute != null) {
            return lockedRoute;
        }
        return minecraft.level == null || hoveredBeeId < 0
                ? null
                : ClientBeeRouteCache.get(hoveredBeeId, minecraft.level.getGameTime());
    }

    private int displayedRouteIndex(Minecraft minecraft) {
        if (ClientBeeRouteCache.lockedBeeId() >= 0) {
            return ClientBeeRouteCache.lockedRouteIndex();
        }
        return minecraft.level == null || hoveredBeeId < 0
                ? 0
                : ClientBeeRouteCache.getRouteIndex(hoveredBeeId, minecraft.level.getGameTime());
    }

    private int displayedBeeId() {
        int lockedBeeId = ClientBeeRouteCache.lockedBeeId();
        return lockedBeeId >= 0 ? lockedBeeId : hoveredBeeId;
    }

    private List<BeeSearchArea> displayedSearchAreas(Minecraft minecraft) {
        List<BeeSearchArea> lockedSearchAreas = ClientBeeRouteCache.lockedSearchAreas();
        if (lockedSearchAreas != null) {
            return lockedSearchAreas;
        }
        return minecraft.level == null || hoveredBeeId < 0
                ? null
                : ClientBeeRouteCache.getSearchAreas(hoveredBeeId, minecraft.level.getGameTime());
    }

    private static GoggleMode goggleMode(ItemStack stack) {
        if (stack.is(EcologyItems.DEBUG_WAX_GOGGLES.get())) {
            return GoggleMode.DEBUG;
        }
        if (stack.is(EcologyItems.WAX_GOGGLES.get())) {
            return GoggleMode.NORMAL;
        }
        return GoggleMode.NONE;
    }

    private static void spawnNextGoalParticles(Minecraft minecraft, int beeId, List<BlockPos> route, int routeIndex) {
        if (minecraft.level == null || route == null || routeIndex < 0 || routeIndex >= route.size()) {
            return;
        }

        int spawned = 0;
        long gameTime = minecraft.level.getGameTime();
        double drift = (gameTime % 8) / 8.0;
        Vec3 start = routeLegStart(minecraft, beeId, route, routeIndex);
        if (start == null) {
            return;
        }
        Vec3 end = Vec3.atCenterOf(route.get(routeIndex)).add(0.0, ROUTE_PARTICLE_HEIGHT, 0.0);
        Vec3 delta = end.subtract(start);
        int steps = Math.max(2, (int) Math.ceil(delta.length() / ROUTE_PARTICLE_SPACING));
        for (int step = 0; step <= steps && spawned < MAX_ROUTE_PARTICLES_PER_BURST; step++) {
            double t = (step + drift) / (steps + 1.0);
            Vec3 pos = start.add(delta.scale(t));
            double bob = Math.sin((gameTime + step * 5L) * 0.35) * 0.035;
            minecraft.level.addParticle(
                    ParticleTypes.WAX_ON,
                    pos.x,
                    pos.y + bob,
                    pos.z,
                    0.0,
                    0.01,
                    0.0);
            if ((spawned + gameTime) % 5 == 0) {
                minecraft.level.addParticle(
                        ParticleTypes.FALLING_HONEY,
                        pos.x,
                        pos.y - 0.04,
                        pos.z,
                        0.0,
                        0.0,
                        0.0);
            }
            spawned++;
        }
    }

    private static Vec3 routeLegStart(Minecraft minecraft, int beeId, List<BlockPos> route, int routeIndex) {
        if (minecraft.level != null && beeId >= 0) {
            Entity entity = minecraft.level.getEntity(beeId);
            if (entity instanceof Bee) {
                return entity.position().add(0.0, entity.getBbHeight() * 0.5, 0.0);
            }
        }
        if (routeIndex <= 0) {
            return null;
        }
        return Vec3.atCenterOf(route.get(routeIndex - 1)).add(0.0, ROUTE_PARTICLE_HEIGHT, 0.0);
    }

    private static void drawSearchAreas(PoseStack poseStack, VertexConsumer consumer, List<BeeSearchArea> searchAreas) {
        for (BeeSearchArea searchArea : searchAreas) {
            float red = searchArea.type() == BeeRouteStopType.FLOWER ? 0.15F : 0.15F;
            float green = searchArea.type() == BeeRouteStopType.FLOWER ? 0.72F : 1.0F;
            float blue = searchArea.type() == BeeRouteStopType.FLOWER ? 1.0F : 0.35F;
            BlockPos min = searchArea.min();
            BlockPos max = searchArea.max();
            LevelRenderer.renderLineBox(
                    poseStack,
                    consumer,
                    min.getX(),
                    min.getY(),
                    min.getZ(),
                    max.getX() + 1.0,
                    max.getY() + 1.0,
                    max.getZ() + 1.0,
                    red,
                    green,
                    blue,
                    0.85F);
        }
    }

    private static void drawLine(PoseStack poseStack, VertexConsumer consumer, Vec3 start, Vec3 end, float red, float green, float blue, float alpha) {
        Vec3 normal = end.subtract(start).normalize();
        consumer.addVertex(poseStack.last(), (float) start.x, (float) start.y, (float) start.z)
                .setColor(red, green, blue, alpha)
                .setNormal(poseStack.last(), (float) normal.x, (float) normal.y, (float) normal.z);
        consumer.addVertex(poseStack.last(), (float) end.x, (float) end.y, (float) end.z)
                .setColor(red, green, blue, alpha)
                .setNormal(poseStack.last(), (float) normal.x, (float) normal.y, (float) normal.z);
    }

    private enum GoggleMode {
        NONE,
        NORMAL,
        DEBUG
    }
}
