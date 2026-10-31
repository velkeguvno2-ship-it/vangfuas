package com.example.desertvanguard.client;

import com.example.desertvanguard.DesertVanguardMod;
import com.example.desertvanguard.entity.DesertVanguardEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;

/** Renders the baked PMX mesh (models/entity/desert_vanguard.mesh), one render type per material/texture. */
public class DesertVanguardRenderer extends EntityRenderer<DesertVanguardEntity> {

    private static final ResourceLocation MESH =
            new ResourceLocation(DesertVanguardMod.MODID, "models/entity/desert_vanguard.mesh");

    // Same order as the texture list in the PMX: skeleton, armor, glaive, shield
    private static final ResourceLocation[] TEXTURES = {
            tex("skeleton"), tex("desert_armor"), tex("desert_weapon"), tex("desert_shield")
    };

    private static ResourceLocation tex(String name) {
        return new ResourceLocation(DesertVanguardMod.MODID, "textures/entity/desert_vanguard/" + name + ".png");
    }

    private record Part(int texture, int triangles, float[] data) {}

    private final Part[] parts;

    public DesertVanguardRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.5F;
        this.parts = loadMesh(context.getResourceManager());
    }

    private static Part[] loadMesh(ResourceManager manager) {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(manager.getResourceOrThrow(MESH).open()))) {
            Part[] result = new Part[in.readInt()];
            for (int p = 0; p < result.length; p++) {
                int texture = in.readInt();
                int tris = in.readInt();
                float[] data = new float[tris * 3 * 8];
                for (int i = 0; i < data.length; i++) data[i] = in.readFloat();
                result[p] = new Part(texture, tris, data);
            }
            return result;
        } catch (IOException e) {
            throw new RuntimeException("Could not load " + MESH, e);
        }
    }

    @Override
    public void render(DesertVanguardEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();

        float bodyYaw = Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot);
        poseStack.mulPose(Axis.YP.rotationDegrees(-bodyYaw));

        if (entity.deathTime > 0) {
            float f = ((float) entity.deathTime + partialTick - 1.0F) / 20.0F * 1.6F;
            f = Math.min(Mth.sqrt(f), 1.0F);
            poseStack.mulPose(Axis.ZP.rotationDegrees(f * 90.0F));
        }

        int overlay = LivingEntityRenderer.getOverlayCoords(entity, 0.0F);
        PoseStack.Pose pose = poseStack.last();
        Matrix4f matrix = pose.pose();
        Matrix3f normal = pose.normal();

        for (Part part : parts) {
            VertexConsumer vc = buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURES[part.texture()]));
            float[] d = part.data();
            for (int t = 0; t < part.triangles(); t++) {
                int base = t * 24;
                // entity render types use QUADS, so the last vertex of the triangle is emitted twice
                emit(vc, matrix, normal, d, base, overlay, packedLight);
                emit(vc, matrix, normal, d, base + 8, overlay, packedLight);
                emit(vc, matrix, normal, d, base + 16, overlay, packedLight);
                emit(vc, matrix, normal, d, base + 16, overlay, packedLight);
            }
        }

        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    private static void emit(VertexConsumer vc, Matrix4f m, Matrix3f n, float[] d, int o, int overlay, int light) {
        vc.vertex(m, d[o], d[o + 1], d[o + 2])
                .color(255, 255, 255, 255)
                .uv(d[o + 6], d[o + 7])
                .overlayCoords(overlay)
                .uv2(light)
                .normal(n, d[o + 3], d[o + 4], d[o + 5])
                .endVertex();
    }

    @Override
    public ResourceLocation getTextureLocation(DesertVanguardEntity entity) {
        return TEXTURES[0];
    }
}
