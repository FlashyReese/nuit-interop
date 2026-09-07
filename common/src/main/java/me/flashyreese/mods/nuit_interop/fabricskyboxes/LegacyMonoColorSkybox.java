package me.flashyreese.mods.nuit_interop.fabricskyboxes;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import me.flashyreese.mods.nuit.components.Blend;
import me.flashyreese.mods.nuit.components.RGBA;
import me.flashyreese.mods.nuit.mixin.SkyRendererAccessor;
import me.flashyreese.mods.nuit.render.NuitRenderBackend;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;

public class LegacyMonoColorSkybox extends LegacyAbstractSkybox {
    public static final Codec<LegacyMonoColorSkybox> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            LegacyProperties.CODEC.fieldOf("properties").forGetter(skybox -> skybox.legacyProperties),
            LegacyConditions.CODEC.optionalFieldOf("conditions", LegacyConditions.DEFAULT).forGetter(skybox -> skybox.legacyConditions),
            LegacyDecorations.CODEC.optionalFieldOf("decorations", LegacyDecorations.DEFAULT).forGetter(skybox -> skybox.decorations),
            RGBA.CODEC.optionalFieldOf("color", RGBA.of()).forGetter(LegacyMonoColorSkybox::getColor),
            Blend.CODEC.optionalFieldOf("blend", Blend.normal()).forGetter(LegacyMonoColorSkybox::getBlend)
    ).apply(instance, LegacyMonoColorSkybox::new));

    private final RGBA color;
    private final Blend blend;

    public LegacyMonoColorSkybox(LegacyProperties properties, LegacyConditions conditions, LegacyDecorations decorations, RGBA color, Blend blend) {
        super(properties, conditions, decorations);
        this.color = color;
        this.blend = blend;
    }

    @Override
    public void render(SkyRendererAccessor skyRendererAccessor, PoseStack poseStack, Matrix4f projectionMatrix,
                       float tickDelta, Camera camera, boolean thickFog, Runnable fogCallback) {
        fogCallback.run();
        if (this.alpha <= 0.0F) {
            return;
        }

        Matrix4f modelViewMatrix = new Matrix4f(poseStack.last().pose());
        try {
            NuitRenderBackend.beginSkybox(this.blend, this.alpha, GameRenderer::getPositionColorShader);
            BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            for (int face = 0; face < 6; ++face) {
                Matrix4f matrix = new Matrix4f(modelViewMatrix).mul(LegacyFsbRenderer.getMatrixForRotatedFace(face));
                builder.addVertex(matrix, -100.0F, -100.0F, -100.0F).setColor(this.color.getRed(), this.color.getGreen(), this.color.getBlue(), this.color.getAlpha());
                builder.addVertex(matrix, -100.0F, -100.0F, 100.0F).setColor(this.color.getRed(), this.color.getGreen(), this.color.getBlue(), this.color.getAlpha());
                builder.addVertex(matrix, 100.0F, -100.0F, 100.0F).setColor(this.color.getRed(), this.color.getGreen(), this.color.getBlue(), this.color.getAlpha());
                builder.addVertex(matrix, 100.0F, -100.0F, -100.0F).setColor(this.color.getRed(), this.color.getGreen(), this.color.getBlue(), this.color.getAlpha());
            }
            NuitRenderBackend.draw(builder.buildOrThrow(), GameRenderer::getPositionColorShader);
        } finally {
            NuitRenderBackend.endSkybox();
        }
        this.renderDecorations(skyRendererAccessor, modelViewMatrix, projectionMatrix, tickDelta, camera, fogCallback);
    }

    public RGBA getColor() {
        return this.color;
    }

    public Blend getBlend() {
        return this.blend;
    }
}
