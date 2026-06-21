package me.flashyreese.mods.nuit_interop.fabricskyboxes;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import me.flashyreese.mods.nuit.api.skyboxes.SkyboxRenderContext;
import me.flashyreese.mods.nuit.components.Blend;
import me.flashyreese.mods.nuit.components.RGBA;
import me.flashyreese.mods.nuit.render.NuitRenderBackend;
import me.flashyreese.mods.nuit.util.Utils;
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
    public void render(SkyboxRenderContext context) {
        context.applyFog();
        if (this.alpha <= 0.0F) {
            return;
        }

        RenderPipeline pipeline = LegacyFsbRenderer.monoPipeline(this.blend.getBlendFunction());
        GpuBufferSlice dynamicTransforms = NuitRenderBackend.createDynamicTransforms(new Matrix4f(context.skyModelViewStack()), this.blend.getColorModifier(this.alpha));
        try (ByteBufferBuilder byteBufferBuilder = LegacyFsbRenderer.byteBufferBuilder(pipeline, 24)) {
            BufferBuilder builder = LegacyFsbRenderer.bufferBuilder(byteBufferBuilder, pipeline);
            for (int face = 0; face < 6; ++face) {
                Matrix4f matrix4f = Utils.getMatrixForRotatedFace(face);
                builder.addVertex(matrix4f, -100.0F, -100.0F, -100.0F).setColor(this.color.getRed(), this.color.getGreen(), this.color.getBlue(), this.color.getAlpha());
                builder.addVertex(matrix4f, -100.0F, -100.0F, 100.0F).setColor(this.color.getRed(), this.color.getGreen(), this.color.getBlue(), this.color.getAlpha());
                builder.addVertex(matrix4f, 100.0F, -100.0F, 100.0F).setColor(this.color.getRed(), this.color.getGreen(), this.color.getBlue(), this.color.getAlpha());
                builder.addVertex(matrix4f, 100.0F, -100.0F, -100.0F).setColor(this.color.getRed(), this.color.getGreen(), this.color.getBlue(), this.color.getAlpha());
            }
            NuitRenderBackend.draw(pipeline, builder.buildOrThrow(), dynamicTransforms);
        } finally {
            this.renderDecorations(context, context.skyModelViewStack());
        }
    }

    public RGBA getColor() {
        return this.color;
    }

    public Blend getBlend() {
        return this.blend;
    }
}
