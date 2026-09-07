package me.flashyreese.mods.nuit_interop.fabricskyboxes;

import com.mojang.blaze3d.vertex.PoseStack;
import me.flashyreese.mods.nuit.mixin.SkyRendererAccessor;
import net.minecraft.client.Camera;
import org.joml.Matrix4f;

public class LegacyDecorationsSkybox extends LegacyAbstractSkybox {
    public LegacyDecorationsSkybox(LegacyProperties properties, LegacyConditions conditions, LegacyDecorations decorations) {
        super(properties, conditions, decorations);
    }

    @Override
    public void render(SkyRendererAccessor skyRendererAccessor, PoseStack poseStack, Matrix4f projectionMatrix,
                       float tickDelta, Camera camera, boolean thickFog, Runnable fogCallback) {
        fogCallback.run();
        if (this.alpha <= 0.0F) {
            return;
        }

        this.renderDecorations(skyRendererAccessor, new Matrix4f(poseStack.last().pose()), projectionMatrix, tickDelta, camera, fogCallback);
    }
}
