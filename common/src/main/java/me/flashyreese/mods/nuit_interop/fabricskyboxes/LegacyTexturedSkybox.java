package me.flashyreese.mods.nuit_interop.fabricskyboxes;

import com.mojang.blaze3d.vertex.PoseStack;
import me.flashyreese.mods.nuit.components.Blend;
import me.flashyreese.mods.nuit.mixin.SkyRendererAccessor;
import me.flashyreese.mods.nuit.render.NuitRenderBackend;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
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
    public final void render(SkyRendererAccessor skyRendererAccessor, PoseStack poseStack, Matrix4f projectionMatrix,
                             float tickDelta, Camera camera, boolean thickFog, Runnable fogCallback) {
        fogCallback.run();
        if (this.alpha <= 0.0F) {
            return;
        }

        ClientLevel level = Objects.requireNonNull(Minecraft.getInstance().level);
        Matrix4f modelViewMatrix = this.rotation.apply(new Matrix4f(poseStack.last().pose()), level);
        try {
            NuitRenderBackend.beginSkybox(this.blend, this.alpha, GameRenderer::getPositionTexShader);
            this.renderTexturedSkybox(modelViewMatrix);
        } finally {
            NuitRenderBackend.endSkybox();
        }
        this.renderDecorations(skyRendererAccessor, modelViewMatrix, projectionMatrix, tickDelta, camera, fogCallback);
    }

    protected abstract void renderTexturedSkybox(Matrix4f modelViewMatrix);

    public Blend getBlend() {
        return this.blend;
    }
}
