package me.flashyreese.mods.nuit_interop.fabricskyboxes;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import me.flashyreese.mods.nuit.mixin.SkyRendererAccessor;
import me.flashyreese.mods.nuit.render.NuitRenderBackend;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public class LegacyOverworldSkybox extends LegacyAbstractSkybox {
    public static final Codec<LegacyOverworldSkybox> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            LegacyProperties.CODEC.fieldOf("properties").forGetter(skybox -> skybox.legacyProperties),
            LegacyConditions.CODEC.optionalFieldOf("conditions", LegacyConditions.DEFAULT).forGetter(skybox -> skybox.legacyConditions),
            LegacyDecorations.CODEC.optionalFieldOf("decorations", LegacyDecorations.DEFAULT).forGetter(skybox -> skybox.decorations)
    ).apply(instance, LegacyOverworldSkybox::new));

    public LegacyOverworldSkybox(LegacyProperties properties, LegacyConditions conditions, LegacyDecorations decorations) {
        super(properties, conditions, decorations);
    }

    @Override
    public void render(SkyRendererAccessor skyRendererAccessor, PoseStack poseStack, Matrix4f projectionMatrix,
                       float tickDelta, Camera camera, boolean thickFog, Runnable fogCallback) {
        fogCallback.run();
        ClientLevel level = (ClientLevel) camera.getEntity().level();
        Matrix4f modelViewMatrix = new Matrix4f(poseStack.last().pose());
        Vec3 skyColor = level.getSkyColor(camera.getPosition(), tickDelta);
        FogRenderer.levelFogColor();
        RenderSystem.depthMask(false);
        RenderSystem.setShaderColor((float) skyColor.x, (float) skyColor.y, (float) skyColor.z, this.alpha);
        ShaderInstance shader = RenderSystem.getShader();
        skyRendererAccessor.getTopSkyBuffer().bind();
        skyRendererAccessor.getTopSkyBuffer().drawWithShader(modelViewMatrix, projectionMatrix, shader);
        VertexBuffer.unbind();
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        float skyAngle = level.getTimeOfDay(tickDelta);
        float sunAngle = level.getSunAngle(tickDelta);
        float[] sunriseOrSunsetColor = level.effects().getSunriseColor(skyAngle, tickDelta);
        if (sunriseOrSunsetColor != null) {
            this.renderSunriseAndSunset(modelViewMatrix, sunAngle, sunriseOrSunsetColor);
        }

        this.renderDecorations(skyRendererAccessor, modelViewMatrix, projectionMatrix, tickDelta, camera, fogCallback);

        RenderSystem.depthMask(false);
        RenderSystem.setShaderColor(0.0F, 0.0F, 0.0F, 1.0F);
        double eyeHeight = camera.getEntity().getEyePosition(tickDelta).y - level.getLevelData().getHorizonHeight(level);
        if (eyeHeight < 0.0D) {
            Matrix4f darkDiscMatrix = new Matrix4f(modelViewMatrix).translate(0.0F, 12.0F, 0.0F);
            skyRendererAccessor.getBottomSkyBuffer().bind();
            skyRendererAccessor.getBottomSkyBuffer().drawWithShader(darkDiscMatrix, projectionMatrix, shader);
            VertexBuffer.unbind();
        }

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    private void renderSunriseAndSunset(Matrix4f modelViewMatrix, float sunAngle, float[] sunriseOrSunsetColor) {
        float zRotation = Mth.sin(sunAngle) < 0.0F ? 180.0F : 0.0F;
        Matrix4f sunriseModelViewMatrix = new Matrix4f(modelViewMatrix)
                .rotate(Axis.XP.rotationDegrees(90.0F))
                .rotate(Axis.ZP.rotationDegrees(zRotation + 90.0F));

        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);
        float alpha = sunriseOrSunsetColor[3] * this.alpha;
        builder.addVertex(sunriseModelViewMatrix, 0.0F, 100.0F, 0.0F)
                .setColor(sunriseOrSunsetColor[0], sunriseOrSunsetColor[1], sunriseOrSunsetColor[2], alpha);
        for (int vertex = 0; vertex <= 16; ++vertex) {
            float angleRadians = (float) vertex * Mth.TWO_PI / 16.0F;
            float x = Mth.sin(angleRadians);
            float y = Mth.cos(angleRadians);
            float z = -y * 40.0F * alpha;
            builder.addVertex(sunriseModelViewMatrix, x * 120.0F, y * 120.0F, z)
                    .setColor(sunriseOrSunsetColor[0], sunriseOrSunsetColor[1], sunriseOrSunsetColor[2], 0.0F);
        }
        NuitRenderBackend.draw(builder.buildOrThrow(), GameRenderer::getPositionColorShader);
    }
}
