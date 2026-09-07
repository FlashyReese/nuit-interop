package me.flashyreese.mods.nuit_interop.fabricskyboxes;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import me.flashyreese.mods.nuit.mixin.SkyRendererAccessor;
import me.flashyreese.mods.nuit.render.NuitRenderBackend;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;

public class LegacyEndSkybox extends LegacyAbstractSkybox {
    public static final Codec<LegacyEndSkybox> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            LegacyProperties.CODEC.fieldOf("properties").forGetter(skybox -> skybox.legacyProperties),
            LegacyConditions.CODEC.optionalFieldOf("conditions", LegacyConditions.DEFAULT).forGetter(skybox -> skybox.legacyConditions),
            LegacyDecorations.CODEC.optionalFieldOf("decorations", LegacyDecorations.DEFAULT).forGetter(skybox -> skybox.decorations)
    ).apply(instance, LegacyEndSkybox::new));

    public LegacyEndSkybox(LegacyProperties properties, LegacyConditions conditions, LegacyDecorations decorations) {
        super(properties, conditions, decorations);
    }

    @Override
    public void render(SkyRendererAccessor skyRendererAccessor, PoseStack poseStack, Matrix4f projectionMatrix,
                       float tickDelta, Camera camera, boolean thickFog, Runnable fogCallback) {
        fogCallback.run();
        if (this.alpha <= 0.0F) {
            return;
        }

        Matrix4f modelViewMatrix = new Matrix4f(poseStack.last().pose());
        RenderSystem.enableBlend();
        RenderSystem.depthMask(false);
        try {
            BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            int alpha = (int) (255.0F * this.alpha);
            for (int face = 0; face < 6; ++face) {
                Matrix4f matrix = new Matrix4f(modelViewMatrix).mul(LegacyFsbRenderer.getMatrixForRotatedFace(face));
                builder.addVertex(matrix, -100.0F, -100.0F, -100.0F).setUv(0.0F, 0.0F).setColor(40, 40, 40, alpha);
                builder.addVertex(matrix, -100.0F, -100.0F, 100.0F).setUv(0.0F, 16.0F).setColor(40, 40, 40, alpha);
                builder.addVertex(matrix, 100.0F, -100.0F, 100.0F).setUv(16.0F, 16.0F).setColor(40, 40, 40, alpha);
                builder.addVertex(matrix, 100.0F, -100.0F, -100.0F).setUv(16.0F, 0.0F).setColor(40, 40, 40, alpha);
            }
            NuitRenderBackend.drawTextured(builder.buildOrThrow(), GameRenderer::getPositionTexColorShader, SkyRendererAccessor.getEndSky());
        } finally {
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
        }
        this.renderDecorations(skyRendererAccessor, modelViewMatrix, projectionMatrix, tickDelta, camera, fogCallback);
    }
}
