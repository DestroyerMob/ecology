package com.destroyermob.ecology.client;

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
        if (!isWearingGoggles(minecraft.player.getItemBySlot(EquipmentSlot.HEAD))) {
            hoveredBeeId = -1;
            return;
        }

        HitResult hitResult = minecraft.hitResult;
        if (hitResult instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof Bee bee) {
            hoveredBeeId = bee.getId();
            if (requestCooldown-- <= 0) {
                requestCooldown = 10;
                PacketDistributor.sendToServer(new BeeRouteRequestPayload(hoveredBeeId));
            }
        } else {
            hoveredBeeId = -1;
            requestCooldown = 0;
        }
    }

    @SubscribeEvent
    public void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || hoveredBeeId < 0) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        List<BlockPos> route = ClientBeeRouteCache.get(hoveredBeeId, minecraft.level.getGameTime());
        if (route == null || route.isEmpty()) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);

        MultiBufferSource.BufferSource buffer = minecraft.renderBuffers().bufferSource();
        VertexConsumer consumer = buffer.getBuffer(RenderType.lines());
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
        buffer.endBatch(RenderType.lines());
        poseStack.popPose();
    }

    private static boolean isWearingGoggles(ItemStack stack) {
        return stack.is(EcologyItems.WAX_GOGGLES.get());
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
}
