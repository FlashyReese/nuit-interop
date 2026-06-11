package me.flashyreese.mods.nuit_interop.fabricskyboxes;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import me.flashyreese.mods.nuit.components.Blend;
import me.flashyreese.mods.nuit.components.Texture;
import me.flashyreese.mods.nuit.components.UVRange;
import me.flashyreese.mods.nuit.mixin.RenderPipelinesAccessor;
import me.flashyreese.mods.nuit.util.BufferUploader;
import me.flashyreese.mods.nuit.util.DynamicTransformsBuilder;
import me.flashyreese.mods.nuit_interop.NuitInterop;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Function;

public final class LegacyFsbRenderer {
    private static final Function<BlendFunction, RenderPipeline> TEXTURED_PIPELINE_FACTORY = blendFunction -> {
        RenderPipeline.Builder builder = RenderPipeline.builder(RenderPipelinesAccessor.getMatricesProjectSnippet());
        builder.withLocation(Identifier.tryBuild(NuitInterop.MOD_ID, "pipeline/legacy_fsb_textured_skybox"));
        builder.withVertexShader("core/position_tex");
        builder.withFragmentShader("core/position_tex");
        builder.withDepthWrite(false);
        builder.withCull(false);
        if (blendFunction != null) {
            builder.withBlend(blendFunction);
        } else {
            builder.withoutBlend();
        }
        builder.withSampler("Sampler0");
        builder.withVertexFormat(DefaultVertexFormat.POSITION_TEX, VertexFormat.Mode.QUADS);
        return builder.build();
    };

    private static final Function<BlendFunction, RenderPipeline> MONO_PIPELINE_FACTORY = blendFunction -> {
        RenderPipeline.Builder builder = RenderPipeline.builder(RenderPipelinesAccessor.getMatricesProjectSnippet());
        builder.withLocation(Identifier.tryBuild(NuitInterop.MOD_ID, "pipeline/legacy_fsb_mono_skybox"));
        builder.withVertexShader("core/position_color");
        builder.withFragmentShader("core/position_color");
        builder.withDepthWrite(false);
        if (blendFunction != null) {
            builder.withBlend(blendFunction);
        } else {
            builder.withoutBlend();
        }
        builder.withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS);
        return builder.build();
    };

    private static final Map<BlendFunction, RenderPipeline> TEXTURED_PIPELINES = new IdentityHashMap<>();
    private static final Map<BlendFunction, RenderPipeline> MONO_PIPELINES = new IdentityHashMap<>();
    private static RenderPipeline texturedNoBlendPipeline;
    private static RenderPipeline monoNoBlendPipeline;

    private LegacyFsbRenderer() {
    }

    public static RenderPipeline texturedPipeline(BlendFunction blendFunction) {
        if (blendFunction == null) {
            if (texturedNoBlendPipeline == null) {
                texturedNoBlendPipeline = TEXTURED_PIPELINE_FACTORY.apply(null);
            }
            return texturedNoBlendPipeline;
        }
        return TEXTURED_PIPELINES.computeIfAbsent(blendFunction, TEXTURED_PIPELINE_FACTORY);
    }

    public static RenderPipeline monoPipeline(BlendFunction blendFunction) {
        if (blendFunction == null) {
            if (monoNoBlendPipeline == null) {
                monoNoBlendPipeline = MONO_PIPELINE_FACTORY.apply(null);
            }
            return monoNoBlendPipeline;
        }
        return MONO_PIPELINES.computeIfAbsent(blendFunction, MONO_PIPELINE_FACTORY);
    }

    public static GpuBufferSlice dynamicTransforms(Matrix4f modelViewMatrix, Blend blend, float alpha) {
        Vector4f colorModifier = blend.applyEquationAndGetColor(alpha);
        return DynamicTransformsBuilder.of()
                .withModelViewMatrix(modelViewMatrix)
                .withShaderColor(colorModifier)
                .build();
    }

    static void drawTexturedQuad(RenderPipeline pipeline, GpuBufferSlice dynamicTransforms, Matrix4f matrix4f, Texture texture) {
        drawTexturedQuad(pipeline, dynamicTransforms, matrix4f, texture.getTextureId(), texture.getUvRange());
    }

    static void drawTexturedQuad(RenderPipeline pipeline, GpuBufferSlice dynamicTransforms, Matrix4f matrix4f, Identifier textureId, UVRange uvRange) {
        try (ByteBufferBuilder byteBufferBuilder = new ByteBufferBuilder(pipeline.getVertexFormat().getVertexSize() * 4)) {
            BufferBuilder builder = new BufferBuilder(byteBufferBuilder, pipeline.getVertexFormatMode(), pipeline.getVertexFormat());
            builder.addVertex(matrix4f, -100.0F, -100.0F, -100.0F).setUv(uvRange.minU(), uvRange.minV());
            builder.addVertex(matrix4f, -100.0F, -100.0F, 100.0F).setUv(uvRange.minU(), uvRange.maxV());
            builder.addVertex(matrix4f, 100.0F, -100.0F, 100.0F).setUv(uvRange.maxU(), uvRange.maxV());
            builder.addVertex(matrix4f, 100.0F, -100.0F, -100.0F).setUv(uvRange.maxU(), uvRange.minV());

            GpuTextureView textureView = Minecraft.getInstance().getTextureManager().getTexture(textureId).getTextureView();
            BufferUploader.drawWithShader(pipeline, builder.buildOrThrow(), pass -> {
                pass.setUniform("DynamicTransforms", dynamicTransforms);
                pass.bindTexture("Sampler0", textureView, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            });
        }
    }

    static void drawCelestialQuad(RenderPipeline pipeline, GpuBufferSlice dynamicTransforms, Identifier textureId, float size, float y, UVRange uvRange) {
        try (ByteBufferBuilder byteBufferBuilder = new ByteBufferBuilder(pipeline.getVertexFormat().getVertexSize() * 4)) {
            BufferBuilder builder = new BufferBuilder(byteBufferBuilder, pipeline.getVertexFormatMode(), pipeline.getVertexFormat());
            builder.addVertex(-size, y, -size).setUv(uvRange.minU(), uvRange.minV());
            builder.addVertex(size, y, -size).setUv(uvRange.maxU(), uvRange.minV());
            builder.addVertex(size, y, size).setUv(uvRange.maxU(), uvRange.maxV());
            builder.addVertex(-size, y, size).setUv(uvRange.minU(), uvRange.maxV());
            GpuTextureView textureView = Minecraft.getInstance().getTextureManager().getTexture(textureId).getTextureView();
            BufferUploader.drawWithShader(pipeline, builder.buildOrThrow(), pass -> {
                pass.setUniform("DynamicTransforms", dynamicTransforms);
                pass.bindTexture("Sampler0", textureView, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            });
        }
    }

    public static RenderPipeline celestialPipeline() {
        return RenderPipelines.CELESTIAL;
    }
}
