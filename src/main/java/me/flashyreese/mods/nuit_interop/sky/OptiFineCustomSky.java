package me.flashyreese.mods.nuit_interop.sky;

import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.amerebagatelle.mods.nuit.api.skyboxes.Skybox;
import io.github.amerebagatelle.mods.nuit.mixin.SkyRendererAccessor;
import me.flashyreese.mods.nuit_interop.client.config.NuitInteropConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FogType;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

import java.util.List;
import java.util.Objects;

public class OptiFineCustomSky implements Skybox {
    public static final Codec<OptiFineCustomSky> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            OptiFineSkyLayer.CODEC.listOf().optionalFieldOf("layers", ImmutableList.of()).forGetter(OptiFineCustomSky::getLayers),
            Level.RESOURCE_KEY_CODEC.fieldOf("world").forGetter(OptiFineCustomSky::getWorldResourceKey)
    ).apply(instance, OptiFineCustomSky::new));

    private final List<OptiFineSkyLayer> layers;
    private final ResourceKey<Level> worldResourceKey;
    private boolean active = true;

    public OptiFineCustomSky(List<OptiFineSkyLayer> layers, ResourceKey<Level> worldResourceKey) {
        this.layers = layers;
        this.worldResourceKey = worldResourceKey;
    }

    @Override
    public void render(SkyRendererAccessor skyRendererAccessor, PoseStack poseStack, float tickDelta, Camera camera, MultiBufferSource.BufferSource bufferSource, FogParameters fogParameters) {
        this.renderSky(skyRendererAccessor, poseStack, tickDelta, camera, bufferSource, fogParameters);
    }

    private void renderEndSky(PoseStack poseStack, ClientLevel level) {
        RenderSystem.enableBlend();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(CoreShaders.POSITION_TEX_COLOR);
        RenderSystem.setShaderTexture(0, SkyRenderer.END_SKY_LOCATION);
        for (int i = 0; i < 6; ++i) {
            poseStack.pushPose();
            switch (i) {
                case 1 -> poseStack.mulPose(Axis.XP.rotationDegrees(90.0f));
                case 2 -> poseStack.mulPose(Axis.XP.rotationDegrees(-90.0f));
                case 3 -> poseStack.mulPose(Axis.XP.rotationDegrees(180.0f));
                case 4 -> poseStack.mulPose(Axis.ZP.rotationDegrees(90.0f));
                case 5 -> poseStack.mulPose(Axis.ZP.rotationDegrees(-90.0f));
            }

            Matrix4f matrix4f = poseStack.last().pose();
            BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            int color = ARGB.color(255, 40, 40, 40);
            builder.addVertex(matrix4f, -100.0F, -100.0F, -100.0F).setUv(0.0F, 0.0F).setColor(color);
            builder.addVertex(matrix4f, -100.0F, -100.0F, 100.0F).setUv(0.0F, 16.0F).setColor(color);
            builder.addVertex(matrix4f, 100.0F, -100.0F, 100.0F).setUv(16.0F, 16.0F).setColor(color);
            builder.addVertex(matrix4f, 100.0F, -100.0F, -100.0F).setUv(16.0F, 0.0F).setColor(color);
            BufferUploader.drawWithShader(builder.buildOrThrow());
            poseStack.popPose();
        }

        this.render(poseStack, level, 0.0F);
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    public void renderSky(SkyRendererAccessor skyRendererAccessor, PoseStack poseStack, float tickDelta, Camera camera, MultiBufferSource.BufferSource bufferSource, FogParameters fogParameters) {
        Minecraft minecraft = Minecraft.getInstance();
        Entity entity = camera.getEntity();
        ClientLevel level = Objects.requireNonNull((ClientLevel) entity.level());
        FogType cameraSubmersionType = camera.getFluidInCamera();
        if (cameraSubmersionType != FogType.POWDER_SNOW && cameraSubmersionType != FogType.LAVA && !(this.hasBlindnessOrDarkness(camera))) {
            if (level.effects().skyType() == DimensionSpecialEffects.SkyType.END) {
                this.renderEndSky(poseStack, level);
            } else if (level.effects().skyType() != DimensionSpecialEffects.SkyType.OVERWORLD) {
                return;
            }

            int skyColor = level.getSkyColor(minecraft.gameRenderer.getMainCamera().getPosition(), tickDelta);
            float timeOfDay = level.getTimeOfDay(tickDelta);
            float rainLevel = 1.0F - level.getRainLevel(tickDelta);
            int moonPhase = level.getMoonPhase();

            RenderSystem.depthMask(false);
            RenderSystem.setShaderFog(fogParameters);
            RenderSystem.setShaderColor(ARGB.redFloat(skyColor), ARGB.greenFloat(skyColor), ARGB.blueFloat(skyColor), 1.0F);
            skyRendererAccessor.getTopSkyBuffer().bind();
            skyRendererAccessor.getTopSkyBuffer().drawWithShader(RenderSystem.getModelViewStack(), RenderSystem.getProjectionMatrix(), RenderSystem.getShader());
            VertexBuffer.unbind();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

            RenderSystem.enableBlend();
            int sunriseOrSunsetColor = level.effects().getSunriseOrSunsetColor(level.getTimeOfDay(tickDelta));
            if (sunriseOrSunsetColor != -1) {
                RenderSystem.setShader(CoreShaders.POSITION_COLOR);
                RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                poseStack.pushPose();
                poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
                float i = Mth.sin(level.getSunAngle(tickDelta)) < 0.0F ? 180.0F : 0.0F;
                poseStack.mulPose(Axis.ZP.rotationDegrees(i));
                poseStack.mulPose(Axis.ZP.rotationDegrees(90.0F));
                float sunriseOrSunsetA = ARGB.blueFloat(sunriseOrSunsetColor);
                Matrix4f matrix4f = poseStack.last().pose();
                BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);
                builder.addVertex(matrix4f, 0.0F, 100.0F, 0.0F).setColor(sunriseOrSunsetColor);
                for (int n = 0; n <= 16; ++n) {
                    float o = (float) n * (float) (Math.PI * 2) / 16.0F;
                    float p = Mth.sin(o);
                    float q = Mth.cos(o);
                    builder.addVertex(matrix4f, p * 120.0F, q * 120.0F, -q * 40.0F * sunriseOrSunsetA).setColor(ARGB.color(sunriseOrSunsetColor, 0));
                }
                BufferUploader.drawWithShader(builder.buildOrThrow());
                poseStack.popPose();
            }

            RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);

            poseStack.pushPose();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, rainLevel);
            poseStack.mulPose(Axis.YP.rotationDegrees(-90.0F));
            this.render(poseStack, level, tickDelta);
            poseStack.mulPose(Axis.XP.rotationDegrees(timeOfDay * 360.0F));
            Matrix4f matrix4f2 = poseStack.last().pose();

            RenderSystem.setShader(CoreShaders.POSITION_TEX);
            {
                RenderSystem.setShaderTexture(0, SkyRendererAccessor.getSun());
                BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
                final float sunSize = 30.0F;
                builder.addVertex(matrix4f2, -sunSize, 100.0F, -sunSize).setUv(0.0F, 0.0F);
                builder.addVertex(matrix4f2, sunSize, 100.0F, -sunSize).setUv(1.0F, 0.0F);
                builder.addVertex(matrix4f2, sunSize, 100.0F, sunSize).setUv(1.0F, 1.0F);
                builder.addVertex(matrix4f2, -sunSize, 100.0F, sunSize).setUv(0.0F, 1.0F);
                BufferUploader.drawWithShader(builder.buildOrThrow());
            }

            {
                int s = moonPhase % 4;
                int m = moonPhase / 4 % 2;
                float t = (float) (s) / 4.0F;
                float o = (float) (m) / 2.0F;
                float p = (float) (s + 1) / 4.0F;
                float q = (float) (m + 1) / 2.0F;
                RenderSystem.setShaderTexture(0, SkyRendererAccessor.getMoonPhases());
                BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
                final float moonSize = 20.0F;
                builder.addVertex(matrix4f2, -moonSize, -100.0F, moonSize).setUv(p, q);
                builder.addVertex(matrix4f2, moonSize, -100.0F, moonSize).setUv(t, q);
                builder.addVertex(matrix4f2, moonSize, -100.0F, -moonSize).setUv(t, o);
                builder.addVertex(matrix4f2, -moonSize, -100.0F, -moonSize).setUv(p, o);
                BufferUploader.drawWithShader(builder.buildOrThrow());
            }

            float starBrightness = level.getStarBrightness(tickDelta) * rainLevel;
            if (starBrightness > 0.0F) {
                Matrix4fStack matrix4fStack = RenderSystem.getModelViewStack();
                matrix4fStack.pushMatrix();
                matrix4fStack.mul(poseStack.last().pose());
                RenderSystem.setShaderColor(starBrightness, starBrightness, starBrightness, starBrightness);
                RenderSystem.setShaderFog(FogParameters.NO_FOG);
                skyRendererAccessor.getStarsBuffer().bind();
                skyRendererAccessor.getStarsBuffer().drawWithShader(RenderSystem.getModelViewStack(), RenderSystem.getProjectionMatrix(), RenderSystem.getShader());
                VertexBuffer.unbind();
                RenderSystem.setShaderFog(fogParameters);
                RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                matrix4fStack.popMatrix();
            }

            RenderSystem.disableBlend();
            RenderSystem.defaultBlendFunc();
            poseStack.popPose();
            double eyeDepth = entity.getEyePosition(tickDelta).y - level.getLevelData().getHorizonHeight(level);
            if (eyeDepth < 0.0) {
                ((SkyRenderer) skyRendererAccessor).renderDarkDisc(poseStack);
            }

            RenderSystem.depthMask(true);
        }
    }

    private boolean hasBlindnessOrDarkness(Camera camera) {
        Entity entity = camera.getEntity();
        if (entity instanceof LivingEntity livingEntity) {
            return livingEntity.hasEffect(MobEffects.BLINDNESS) || livingEntity.hasEffect(MobEffects.DARKNESS);
        } else {
            return false;
        }
    }

    private void render(PoseStack poseStack, Level level, float tickDelta) {
        long timeOfDay = level.getDayTime();
        int clampedTimeOfDay = (int) (timeOfDay % 24000L);
        float skyAngle = level.getTimeOfDay(tickDelta);
        float rainGradient = level.getRainLevel(tickDelta);
        float thunderGradient = level.getThunderLevel(tickDelta);
        if (rainGradient > 0.0F) {
            thunderGradient /= rainGradient;
        }

        for (OptiFineSkyLayer optiFineSkyLayer : this.layers) {
            if (optiFineSkyLayer.isActive(timeOfDay, clampedTimeOfDay)) {
                optiFineSkyLayer.render(level, poseStack, clampedTimeOfDay, skyAngle, rainGradient, thunderGradient);
            }
        }

        OptiFineBlend.ADD.apply(1.0F - rainGradient);
    }

    @Override
    public void tick(ClientLevel clientLevel) {
        this.active = true;
        if (clientLevel.dimension() != this.worldResourceKey) {
            this.layers.forEach(layer -> layer.setConditionAlpha(-1.0F));
            this.active = false;
        } else {
            this.layers.forEach(layer -> layer.tick(clientLevel));
        }
    }

    @Override
    public boolean isActive() {
        return NuitInteropConfig.INSTANCE.interoperability && this.active;
    }

    public List<OptiFineSkyLayer> getLayers() {
        return layers;
    }

    public ResourceKey<Level> getWorldResourceKey() {
        return worldResourceKey;
    }
}
