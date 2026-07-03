package com.destroyermob.ecology.client;

import com.destroyermob.ecology.village.VillageCurrency;
import com.destroyermob.ecology.village.VillageCurrencyGenes;
import com.destroyermob.ecology.village.VillageCurrencyHolder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class VillagerEyeLayer extends RenderLayer<Villager, VillagerModel<Villager>> {
    private static final ResourceLocation WHITE_TEXTURE = ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    private static final float FACE_Z = -4.02F;
    private static final float EYE_Y0 = -4.0F;
    private static final float EYE_Y1 = -3.0F;
    private static final float LEFT_EYE_X0 = -2.0F;
    private static final float LEFT_EYE_X1 = -1.0F;
    private static final float RIGHT_EYE_X0 = 1.0F;
    private static final float RIGHT_EYE_X1 = 2.0F;

    public VillagerEyeLayer(RenderLayerParent<Villager, VillagerModel<Villager>> renderer) {
        super(renderer);
    }

    @Override
    public void render(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            Villager villager,
            float limbSwing,
            float limbSwingAmount,
            float partialTick,
            float ageInTicks,
            float netHeadYaw,
            float headPitch) {
        if (villager.isInvisible() || !(villager instanceof VillageCurrencyHolder holder)) {
            return;
        }

        VertexConsumer buffer = bufferSource.getBuffer(RenderType.entityCutoutNoCull(WHITE_TEXTURE));
        int overlay = LivingEntityRenderer.getOverlayCoords(villager, 0.0F);
        int eyeLight = Math.max(packedLight, LightTexture.FULL_BRIGHT);

        poseStack.pushPose();
        getParentModel().getHead().translateAndRotate(poseStack);
        renderEye(poseStack, buffer, overlay, eyeLight, holder.ecology$getLeftEyeCurrency(), LEFT_EYE_X0, LEFT_EYE_X1);
        renderEye(poseStack, buffer, overlay, eyeLight, holder.ecology$getRightEyeCurrency(), RIGHT_EYE_X0, RIGHT_EYE_X1);
        poseStack.popPose();
    }

    private static void renderEye(
            PoseStack poseStack,
            VertexConsumer buffer,
            int overlay,
            int packedLight,
            VillageCurrency currency,
            float minX,
            float maxX) {
        int color = VillageCurrencyGenes.eyeColor(currency);
        PoseStack.Pose pose = poseStack.last();
        vertex(buffer, pose, maxX, EYE_Y0, FACE_Z, color, overlay, packedLight, 1.0F, 0.0F);
        vertex(buffer, pose, minX, EYE_Y0, FACE_Z, color, overlay, packedLight, 0.0F, 0.0F);
        vertex(buffer, pose, minX, EYE_Y1, FACE_Z, color, overlay, packedLight, 0.0F, 1.0F);
        vertex(buffer, pose, maxX, EYE_Y1, FACE_Z, color, overlay, packedLight, 1.0F, 1.0F);
    }

    private static void vertex(
            VertexConsumer buffer,
            PoseStack.Pose pose,
            float x,
            float y,
            float z,
            int color,
            int overlay,
            int packedLight,
            float u,
            float v) {
        buffer.addVertex(pose, x / 16.0F, y / 16.0F, z / 16.0F)
                .setColor(color)
                .setUv(u, v)
                .setOverlay(overlay)
                .setLight(packedLight)
                .setNormal(pose, 0.0F, 0.0F, -1.0F);
    }
}
