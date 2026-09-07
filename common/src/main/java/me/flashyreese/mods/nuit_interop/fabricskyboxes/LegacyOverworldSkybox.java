package me.flashyreese.mods.nuit_interop.fabricskyboxes;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.math.Axis;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import me.flashyreese.mods.nuit.api.skyboxes.SkyboxRenderContext;
import me.flashyreese.mods.nuit.render.NuitRenderBackend;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.util.Mth;
import net.minecraft.world.attribute.EnvironmentAttributes;
import org.joml.Matrix4f;
import org.joml.Vector4f;

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
    public void render(SkyboxRenderContext context) {
        context.applyFog();
        Matrix4f modelViewMatrix = new Matrix4f(context.skyModelViewStack());
        Camera camera = context.camera();
        float tickDelta = context.tickDelta();
        ClientLevel level = (ClientLevel) camera.entity().level();
        float sunAngle = camera.attributeProbe().getValue(EnvironmentAttributes.SUN_ANGLE, tickDelta) * Mth.DEG_TO_RAD;
        int sunriseOrSunsetColor = camera.attributeProbe().getValue(EnvironmentAttributes.SUNRISE_SUNSET_COLOR, tickDelta);
        int skyColor = camera.attributeProbe().getValue(EnvironmentAttributes.SKY_COLOR, tickDelta);

        int alphaColor = (skyColor & 0x00FFFFFF) | ((int) (this.alpha * 255.0F) << 24);
        context.renderSkyDisc(alphaColor);
        if (((sunriseOrSunsetColor >>> 24) & 0xFF) > 0) {
            this.renderSunriseAndSunset(modelViewMatrix, sunAngle, sunriseOrSunsetColor);
        }

        this.renderDecorations(context, modelViewMatrix);

        double eyeHeight = camera.entity().getEyePosition(tickDelta).y - level.getLevelData().getHorizonHeight(level);
        if (eyeHeight < 0.0D) {
            context.renderDarkDisc();
        }
    }

    private void renderSunriseAndSunset(Matrix4f modelViewMatrix, float sunAngle, int sunriseOrSunsetColor) {
        float zRotation = Mth.sin(sunAngle) < 0.0F ? 180.0F : 0.0F;
        Matrix4f sunriseModelViewMatrix = new Matrix4f(modelViewMatrix)
                .rotate(Axis.XP.rotationDegrees(90.0F))
                .rotate(Axis.ZP.rotationDegrees(zRotation + 90.0F));

        RenderPipeline pipeline = RenderPipelines.SUNRISE_SUNSET;
        try (ByteBufferBuilder byteBufferBuilder = LegacyFsbRenderer.byteBufferBuilder(pipeline, 18)) {
            BufferBuilder bufferBuilder = LegacyFsbRenderer.bufferBuilder(byteBufferBuilder, pipeline);
            int alpha = (int) (((sunriseOrSunsetColor >>> 24) & 0xFF) * this.alpha);
            int color = (sunriseOrSunsetColor & 0x00FFFFFF) | (alpha << 24);
            int transparentColor = color & 0x00FFFFFF;
            bufferBuilder.addVertex(0.0F, 100.0F, 0.0F).setColor(color);
            for (int i = 0; i <= 16; i++) {
                float angleRadians = (float) i * ((float) Math.PI * 2.0F) / 16.0F;
                float x = Mth.sin(angleRadians);
                float y = Mth.cos(angleRadians);
                float z = -y * 40.0F * (alpha / 255.0F);
                bufferBuilder.addVertex(x * 120.0F, y * 120.0F, z).setColor(transparentColor);
            }
            GpuBufferSlice dynamicTransforms = NuitRenderBackend.createDynamicTransforms(sunriseModelViewMatrix, new Vector4f(1.0F, 1.0F, 1.0F, 1.0F));
            NuitRenderBackend.draw(pipeline, bufferBuilder.buildOrThrow(), dynamicTransforms);
        }
    }
}
