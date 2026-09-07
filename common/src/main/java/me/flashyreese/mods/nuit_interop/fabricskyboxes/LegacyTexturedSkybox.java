package me.flashyreese.mods.nuit_interop.fabricskyboxes;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import me.flashyreese.mods.nuit.api.skyboxes.SkyboxRenderContext;
import me.flashyreese.mods.nuit.components.Blend;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import org.joml.Matrix4f;

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
        Matrix4f modelViewMatrix = this.rotation.apply(new Matrix4f(context.skyModelViewStack()), level);
        RenderPipeline pipeline = LegacyFsbRenderer.texturedPipeline(this.blend.getBlendFunction());
        GpuBufferSlice dynamicTransforms = LegacyFsbRenderer.dynamicTransforms(modelViewMatrix, this.blend, this.alpha);
        this.renderTexturedSkybox(context, modelViewMatrix, pipeline, dynamicTransforms);
        this.renderDecorations(context, modelViewMatrix);
    }

    protected abstract void renderTexturedSkybox(SkyboxRenderContext context, Matrix4f modelViewMatrix, RenderPipeline pipeline, GpuBufferSlice dynamicTransforms);

    public Blend getBlend() {
        return this.blend;
    }
}
