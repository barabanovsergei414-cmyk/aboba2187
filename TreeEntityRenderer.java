package com.realistictrees.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.realistictrees.RealisticTrees;
import com.realistictrees.TreeConfig;
import com.realistictrees.client.model.TreeModelData;
import com.realistictrees.entity.CustomTreeEntity;
import com.realistictrees.wind.TreeWind;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;

public class TreeEntityRenderer extends EntityRenderer<CustomTreeEntity> {
    /** Наклон вершины дерева (доля высоты) при ветре силы 1 и настройке windStrength = 1. */
    private static final float TILT_BASE = 0.10f;

    public TreeEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0f;
    }

    @Override
    public boolean shouldRender(CustomTreeEntity entity, Frustum camera, double camX, double camY, double camZ) {
        double max = TreeConfig.CLIENT.renderDistance.get();
        if (entity.distanceToSqr(camX, camY, camZ) > max * max) return false;
        return super.shouldRender(entity, camera, camX, camY, camZ);
    }

    @Override
    public void render(CustomTreeEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        TreeModelData model = TreeModelData.get(CustomTreeEntity.modelName(entity.getTreeType()));
        if (model.parts.isEmpty()) return;

        float scale = entity.getTreeScale();
        boolean falling = entity.isFalling();
        float yaw = entity.getYRot();

        // --- ветер: смещение вершины в системе координат модели (в долях высоты) ---
        float ox = 0f, oz = 0f, flutter = 0f, t = 0f;
        boolean animate = false;
        if (!falling && TreeConfig.CLIENT.enableWind.get()) {
            double maxAnim = TreeConfig.CLIENT.animationDistance.get();
            if (this.entityRenderDispatcher.distanceToSqr(entity) < maxAnim * maxAnim && entity.level() != null) {
                animate = true;
                TreeWind.refresh(entity.level(), partialTick);
                float speed = TreeConfig.CLIENT.windSpeed.get().floatValue();
                float gustiness = TreeConfig.CLIENT.gustiness.get().floatValue();
                t = TreeWind.time * speed;
                float ph = entity.getWindPhase();

                float gust = 1f + gustiness * 0.6f * (Mth.sin(t * 0.7f + ph * 0.5f) * Mth.sin(t * 0.23f + ph));
                float osc = Mth.sin(t * 2.1f + ph);
                float amp = TILT_BASE * TreeConfig.CLIENT.windStrength.get().floatValue() * TreeWind.level;
                float along = amp * (0.8f * gust + 0.25f * osc);
                float across = amp * 0.3f * Mth.sin(t * 1.6f + ph * 2.3f);

                // мировой вектор ветра -> система координат модели (модель повёрнута на yaw вокруг Y)
                float a = yaw * Mth.DEG_TO_RAD;
                float c = Mth.cos(a), s = Mth.sin(a);
                float mdx = TreeWind.dirX * c - TreeWind.dirZ * s;
                float mdz = TreeWind.dirX * s + TreeWind.dirZ * c;
                ox = mdx * along - mdz * across;
                oz = mdz * along + mdx * across;

                flutter = TreeConfig.CLIENT.leafFlutter.get().floatValue() * 0.035f
                        * (0.3f + Math.min(TreeWind.level, 2f));
            }
        }

        poseStack.pushPose();
        if (falling) {
            float p = entity.getFallProgress(partialTick);
            float angle = 90f * p * p;
            float dirRad = entity.getFallYaw() * Mth.DEG_TO_RAD;
            float dx = Mth.cos(dirRad), dz = Mth.sin(dirRad);
            // ось (dz, 0, -dx): верх дерева наклоняется в сторону (dx, dz)
            poseStack.mulPose(new Quaternionf().rotationAxis(angle * Mth.DEG_TO_RAD, dz, 0f, -dx));
        }
        poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
        poseStack.scale(scale, scale, scale);

        PoseStack.Pose pose = poseStack.last();
        float H = model.height;
        float bendMul = H;

        for (TreeModelData.Part part : model.parts) {
            VertexConsumer vc = bufferSource.getBuffer(RenderType.entityCutoutNoCull(part.texture));
            boolean flutterHere = animate && part.foliage && flutter > 0f;
            float[] pos = part.pos;
            float[] nrm = part.nrm;
            float[] uv = part.uv;
            float[] bend = part.bend;
            int n = part.vertexCount;
            for (int i = 0; i < n; i += 3) {
                for (int k = 0; k < 4; k++) {
                    int idx = i + (k == 3 ? 2 : k);   // треугольник -> квад: последняя вершина дублируется
                    int j = idx * 3;
                    float x = pos[j], y = pos[j + 1], z = pos[j + 2];
                    if (animate) {
                        float b = bend[idx] * bendMul;
                        x += ox * b;
                        z += oz * b;
                        if (flutterHere) {
                            float ph = (x + z) * 1.9f + y * 2.3f;
                            float f = flutter * (0.4f + bend[idx]);
                            float sv = Mth.sin(t * 5.3f + ph);
                            x += sv * f;
                            z += Mth.sin(t * 4.1f + ph * 1.3f) * f;
                            y += sv * f * 0.5f;
                        }
                    }
                    vc.addVertex(pose, x, y, z)
                            .setColor(255, 255, 255, 255)
                            .setUv(uv[idx * 2], uv[idx * 2 + 1])
                            .setOverlay(OverlayTexture.NO_OVERLAY)
                            .setLight(packedLight)
                            .setNormal(pose, nrm[j], nrm[j + 1], nrm[j + 2]);
                }
            }
        }
        poseStack.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(CustomTreeEntity entity) {
        return ResourceLocation.fromNamespaceAndPath(RealisticTrees.MODID, "trees/missing.png");
    }
}
