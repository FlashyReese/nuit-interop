package me.flashyreese.mods.nuit_interop.fabricskyboxes;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import me.flashyreese.mods.nuit.api.skyboxes.SkyboxRenderContext;
import me.flashyreese.mods.nuit.components.Blend;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
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
    public final void render(SkyboxRenderContext context) {
        context.applyFog();
        if (this.alpha <= 0.0F) {
            return;
        }

        ClientLevel level = Objects.requireNonNull(Minecraft.getInstance().level);
        Matrix4fStack matrix4fStack = context.skyModelViewStack();
        matrix4fStack.pushMatrix();
        try {
            this.rotation.apply(matrix4fStack, level);
            RenderPipeline pipeline = LegacyFsbRenderer.texturedPipeline(this.blend.getBlendFunction());
            GpuBufferSlice dynamicTransforms = LegacyFsbRenderer.dynamicTransforms(new Matrix4f(matrix4fStack), this.blend, this.alpha);
            this.renderTexturedSkybox(context, matrix4fStack, pipeline, dynamicTransforms);
            this.renderDecorations(context, matrix4fStack);
        } finally {
            matrix4fStack.popMatrix();
            GL46C.glBlendEquation(GL46C.GL_FUNC_ADD);
        }
    }

    protected abstract void renderTexturedSkybox(SkyboxRenderContext context, Matrix4fStack matrix4fStack, RenderPipeline pipeline, GpuBufferSlice dynamicTransforms);

    public Blend getBlend() {
        return this.blend;
    }
}
