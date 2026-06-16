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
import org.joml.Matrix4fStack;

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
        Matrix4fStack matrix4fStack = context.skyModelViewStack();
        Camera camera = context.camera();
        float tickDelta = context.tickDelta();
        ClientLevel level = (ClientLevel) camera.entity().level();
        float sunAngle = camera.attributeProbe().getValue(EnvironmentAttributes.SUN_ANGLE, tickDelta) * Mth.DEG_TO_RAD;
        int sunriseOrSunsetColor = camera.attributeProbe().getValue(EnvironmentAttributes.SUNRISE_SUNSET_COLOR, tickDelta);
        int skyColor = camera.attributeProbe().getValue(EnvironmentAttributes.SKY_COLOR, tickDelta);

        int alphaColor = (skyColor & 0x00FFFFFF) | ((int) (this.alpha * 255.0F) << 24);
        context.renderSkyDisc(alphaColor);
        if (((sunriseOrSunsetColor >>> 24) & 0xFF) > 0) {
            this.renderSunriseAndSunset(matrix4fStack, sunAngle, sunriseOrSunsetColor);
        }

        this.renderDecorations(context, matrix4fStack);

        double eyeHeight = camera.entity().getEyePosition(tickDelta).y - level.getLevelData().getHorizonHeight(level);
        if (eyeHeight < 0.0D) {
            context.renderDarkDisc();
        }
    }

    private void renderSunriseAndSunset(Matrix4fStack matrix4fStack, float sunAngle, int sunriseOrSunsetColor) {
        matrix4fStack.pushMatrix();
        try {
            matrix4fStack.rotate(Axis.XP.rotationDegrees(90.0F));
            float zRotation = Mth.sin(sunAngle) < 0.0F ? 180.0F : 0.0F;
            matrix4fStack.rotate(Axis.ZP.rotationDegrees(zRotation));
            matrix4fStack.rotate(Axis.ZP.rotationDegrees(90.0F));

            RenderPipeline pipeline = RenderPipelines.SUNRISE_SUNSET;
            try (ByteBufferBuilder byteBufferBuilder = new ByteBufferBuilder(pipeline.getVertexFormat().getVertexSize() * 17)) {
                BufferBuilder bufferBuilder = new BufferBuilder(byteBufferBuilder, pipeline.getVertexFormatMode(), pipeline.getVertexFormat());
                int alpha = (int) (((sunriseOrSunsetColor >>> 24) & 0xFF) * this.alpha);
                int color = (sunriseOrSunsetColor & 0x00FFFFFF) | (alpha << 24);
                int transparentColor = color & 0x00FFFFFF;
                bufferBuilder.addVertex(matrix4fStack, 0.0F, 100.0F, 0.0F).setColor(color);
                for (int i = 0; i <= 16; i++) {
                    float angleRadians = (float) i * ((float) Math.PI * 2.0F) / 16.0F;
                    float x = Mth.sin(angleRadians);
                    float y = Mth.cos(angleRadians);
                    float z = -y * 40.0F * (alpha / 255.0F);
                    bufferBuilder.addVertex(matrix4fStack, x * 120.0F, y * 120.0F, z).setColor(transparentColor);
                }
                GpuBufferSlice dynamicTransforms = NuitRenderBackend.createDynamicTransforms();
                NuitRenderBackend.draw(pipeline, bufferBuilder.buildOrThrow(), dynamicTransforms);
            }
        } finally {
            matrix4fStack.popMatrix();
        }
    }
}
