package me.flashyreese.mods.nuit_interop.fabricskyboxes;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import me.flashyreese.mods.nuit.api.skyboxes.SkyboxRenderContext;
import me.flashyreese.mods.nuit.render.NuitRenderBackend;
import me.flashyreese.mods.nuit.util.Utils;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.EndFlashState;
import net.minecraft.client.renderer.RenderPipelines;
import org.joml.Matrix4f;

public class LegacyEndSkybox extends LegacyAbstractSkybox {
    private static final float MIN_END_FLASH_INTENSITY = 0.00001F;

    public static final Codec<LegacyEndSkybox> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            LegacyProperties.CODEC.fieldOf("properties").forGetter(skybox -> skybox.legacyProperties),
            LegacyConditions.CODEC.optionalFieldOf("conditions", LegacyConditions.DEFAULT).forGetter(skybox -> skybox.legacyConditions),
            LegacyDecorations.CODEC.optionalFieldOf("decorations", LegacyDecorations.DEFAULT).forGetter(skybox -> skybox.decorations)
    ).apply(instance, LegacyEndSkybox::new));

    public LegacyEndSkybox(LegacyProperties properties, LegacyConditions conditions, LegacyDecorations decorations) {
        super(properties, conditions, decorations);
    }

    @Override
    public void render(SkyboxRenderContext context) {
        context.applyFog();
        RenderPipeline pipeline = RenderPipelines.END_SKY;
        try (ByteBufferBuilder byteBufferBuilder = LegacyFsbRenderer.byteBufferBuilder(pipeline, 24)) {
            BufferBuilder builder = LegacyFsbRenderer.bufferBuilder(byteBufferBuilder, pipeline);
            for (int face = 0; face < 6; ++face) {
                int color = 0x282828 | ((int) (255.0F * this.alpha) << 24);
                Matrix4f matrix4f = Utils.getMatrixForRotatedFace(face);
                builder.addVertex(matrix4f, -100.0F, -100.0F, -100.0F).setUv(0.0F, 0.0F).setColor(color);
                builder.addVertex(matrix4f, -100.0F, -100.0F, 100.0F).setUv(0.0F, 16.0F).setColor(color);
                builder.addVertex(matrix4f, 100.0F, -100.0F, 100.0F).setUv(16.0F, 16.0F).setColor(color);
                builder.addVertex(matrix4f, 100.0F, -100.0F, -100.0F).setUv(16.0F, 0.0F).setColor(color);
            }

            GpuBufferSlice dynamicTransforms = NuitRenderBackend.createDynamicTransforms();
            LegacyFsbRenderer.drawTexturedMesh(pipeline, builder.buildOrThrow(), dynamicTransforms, context.endSkyTexture());
        }

        this.renderEndFlash(context);
        this.renderDecorations(context, context.skyModelViewStack());
    }

    private void renderEndFlash(SkyboxRenderContext context) {
        if (!(context.camera().entity().level() instanceof ClientLevel level)) {
            return;
        }

        EndFlashState endFlashState = level.endFlashState();
        if (endFlashState == null) {
            return;
        }

        float intensity = endFlashState.getIntensity(context.tickDelta()) * this.alpha;
        if (intensity > MIN_END_FLASH_INTENSITY) {
            context.renderEndFlash(intensity, endFlashState.getXAngle(), endFlashState.getYAngle());
        }
    }
}
