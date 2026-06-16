package me.flashyreese.mods.nuit_interop.fabricskyboxes;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import me.flashyreese.mods.nuit.components.Blend;
import me.flashyreese.mods.nuit.components.Texture;
import me.flashyreese.mods.nuit.components.UVRange;
import me.flashyreese.mods.nuit.render.NuitRenderBackend;
import me.flashyreese.mods.nuit.render.NuitRenderPipelines;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector4f;

public final class LegacyFsbRenderer {
    private LegacyFsbRenderer() {
    }

    public static RenderPipeline texturedPipeline(BlendFunction blendFunction) {
        return NuitRenderPipelines.texturedSkybox(blendFunction);
    }

    public static RenderPipeline monoPipeline(BlendFunction blendFunction) {
        return NuitRenderPipelines.monoColorSkybox(blendFunction);
    }

    public static GpuBufferSlice dynamicTransforms(Matrix4f modelViewMatrix, Blend blend, float alpha) {
        Vector4f colorModifier = blend.getColorModifier(alpha);
        return NuitRenderBackend.createDynamicTransforms(modelViewMatrix, colorModifier);
    }

    public static void drawTexturedMesh(RenderPipeline pipeline, MeshData meshData, GpuBufferSlice dynamicTransforms, Identifier textureId) {
        GpuTextureView textureView;
        GpuSampler sampler;
        try {
            AbstractTexture texture = Minecraft.getInstance().getTextureManager().getTexture(textureId);
            textureView = texture.getTextureView();
            sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        } catch (Throwable throwable) {
            try {
                meshData.close();
            } catch (Throwable suppressed) {
                throwable.addSuppressed(suppressed);
            }
            throw throwable;
        }

        NuitRenderBackend.draw(pipeline, meshData, dynamicTransforms, pass -> pass.bindTexture("Sampler0", textureView, sampler));
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

            drawTexturedMesh(pipeline, builder.buildOrThrow(), dynamicTransforms, textureId);
        }
    }

    public static void drawCelestialQuad(RenderPipeline pipeline, GpuBufferSlice dynamicTransforms, Identifier textureId, float size, float y, UVRange uvRange) {
        try (ByteBufferBuilder byteBufferBuilder = new ByteBufferBuilder(pipeline.getVertexFormat().getVertexSize() * 4)) {
            BufferBuilder builder = new BufferBuilder(byteBufferBuilder, pipeline.getVertexFormatMode(), pipeline.getVertexFormat());
            builder.addVertex(-size, y, -size).setUv(uvRange.minU(), uvRange.minV());
            builder.addVertex(size, y, -size).setUv(uvRange.maxU(), uvRange.minV());
            builder.addVertex(size, y, size).setUv(uvRange.maxU(), uvRange.maxV());
            builder.addVertex(-size, y, size).setUv(uvRange.minU(), uvRange.maxV());
            drawTexturedMesh(pipeline, builder.buildOrThrow(), dynamicTransforms, textureId);
        }
    }

    public static RenderPipeline celestialPipeline() {
        return RenderPipelines.CELESTIAL;
    }
}
