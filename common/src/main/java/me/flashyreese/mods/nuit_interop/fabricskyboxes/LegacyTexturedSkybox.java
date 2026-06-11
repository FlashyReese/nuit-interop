package me.flashyreese.mods.nuit_interop.fabricskyboxes;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import me.flashyreese.mods.nuit.components.Blend;
import me.flashyreese.mods.nuit.mixin.SkyRendererAccessor;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.lwjgl.opengl.GL46C;

import java.util.Objects;

public abstract class LegacyTexturedSkybox extends LegacyAbstractSkybox {
    protected final Blend blend;
    protected final LegacyRotation rotation;

    protected LegacyTexturedSkybox(LegacyProperties properties, LegacyConditions conditions, LegacyDecorations decorations, Blend blend) {
        super(properties, conditions, decorations);
        this.blend = blend;
        this.rotation = properties.rotation();
    }

    @Override
    public final void render(SkyRendererAccessor skyRendererAccessor, Matrix4fStack matrix4fStack, float tickDelta, Camera camera, GpuBufferSlice fogParameters, MultiBufferSource.BufferSource bufferSource) {
        if (this.alpha <= 0.0F) {
            return;
        }

        ClientLevel level = Objects.requireNonNull(Minecraft.getInstance().level);
        matrix4fStack.pushMatrix();
        try {
            this.rotation.apply(matrix4fStack, level);
            RenderPipeline pipeline = LegacyFsbRenderer.texturedPipeline(this.blend.getBlendFunction());
            GpuBufferSlice dynamicTransforms = LegacyFsbRenderer.dynamicTransforms(new Matrix4f(matrix4fStack), this.blend, this.alpha);
            this.renderTexturedSkybox(skyRendererAccessor, matrix4fStack, tickDelta, camera, fogParameters, bufferSource, pipeline, dynamicTransforms);
            this.renderDecorations(skyRendererAccessor, matrix4fStack, tickDelta, camera);
        } finally {
            matrix4fStack.popMatrix();
            GL46C.glBlendEquation(GL46C.GL_FUNC_ADD);
        }
    }

    protected abstract void renderTexturedSkybox(SkyRendererAccessor skyRendererAccessor, Matrix4fStack matrix4fStack, float tickDelta, Camera camera, GpuBufferSlice fogParameters, MultiBufferSource.BufferSource bufferSource, RenderPipeline pipeline, GpuBufferSlice dynamicTransforms);

    public Blend getBlend() {
        return this.blend;
    }
}
