package me.flashyreese.mods.nuit_interop.sky;

import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
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

import java.util.List;

public class OptiFineCustomSky implements Skybox {
    public static final Codec<OptiFineCustomSky> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            OptiFineSkyLayer.CODEC.listOf().optionalFieldOf("layers", ImmutableList.of()).forGetter(OptiFineCustomSky::getLayers),
            Level.RESOURCE_KEY_CODEC.fieldOf("world").forGetter(OptiFineCustomSky::getWorldIdentifier)
    ).apply(instance, OptiFineCustomSky::new));

    private final List<OptiFineSkyLayer> layers;
    private final ResourceKey<Level> worldIdentifier;

    private final Minecraft client = Minecraft.getInstance();
    private ClientLevel level = client.level;

    private boolean active = true;

    public OptiFineCustomSky(List<OptiFineSkyLayer> layers, ResourceKey<Level> worldIdentifier) {
        this.layers = layers;
        this.worldIdentifier = worldIdentifier;
    }

    @Override
    public void render(SkyRendererAccessor skyRendererAccessor, PoseStack poseStack, float tickDelta, Camera camera, MultiBufferSource.BufferSource bufferSource, FogParameters fogParameters, Runnable fogCallback) {
        this.level = (ClientLevel) camera.getEntity().getCommandSenderWorld();
        this.renderSky(skyRendererAccessor, poseStack, tickDelta, camera, bufferSource, fogParameters, fogCallback);
    }

    private void renderEndSky(PoseStack matrices) {
        RenderSystem.enableBlend();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(CoreShaders.POSITION_TEX_COLOR);
        RenderSystem.setShaderTexture(0, SkyRendererAccessor.getEndSky());
        for (int i = 0; i < 6; ++i) {
            matrices.pushPose();
            switch (i) {
                case 1 -> matrices.mulPose(Axis.XP.rotationDegrees(90.0f));
                case 2 -> matrices.mulPose(Axis.XP.rotationDegrees(-90.0f));
                case 3 -> matrices.mulPose(Axis.XP.rotationDegrees(180.0f));
                case 4 -> matrices.mulPose(Axis.ZP.rotationDegrees(90.0f));
                case 5 -> matrices.mulPose(Axis.ZP.rotationDegrees(-90.0f));
            }

            Matrix4f matrix4f = matrices.last().pose();
            VertexBuffer buffer = VertexBuffer.uploadStatic(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR, vertexConsumer -> {
                vertexConsumer.addVertex(matrix4f, -100.0f, -100.0f, -100.0f).setUv(0.0f, 0.0f).setColor(40, 40, 40, 255);
                vertexConsumer.addVertex(matrix4f, -100.0f, -100.0f, 100.0f).setUv(0.0f, 16.0f).setColor(40, 40, 40, 255);
                vertexConsumer.addVertex(matrix4f, 100.0f, -100.0f, 100.0f).setUv(16.0f, 16.0f).setColor(40, 40, 40, 255);
                vertexConsumer.addVertex(matrix4f, 100.0f, -100.0f, -100.0f).setUv(16.0f, 0.0f).setColor(40, 40, 40, 255);
            });

            buffer.bind();
            buffer.drawWithShader(RenderSystem.getModelViewMatrix(), RenderSystem.getProjectionMatrix(), RenderSystem.getShader());
            VertexBuffer.unbind();

            matrices.popPose();
        }

        this.render(matrices, this.level, 0.0F);
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    public void renderSky(SkyRendererAccessor skyRendererAccessor, PoseStack poseStack, float tickDelta, Camera camera, MultiBufferSource.BufferSource bufferSource, FogParameters fogParameters, Runnable fogCallback) {
        fogCallback.run();
        FogType cameraSubmersionType = camera.getFluidInCamera();
        if (cameraSubmersionType != FogType.POWDER_SNOW && cameraSubmersionType != FogType.LAVA && !(this.hasBlindnessOrDarkness(camera))) {
            if (this.client.level.effects().skyType() == DimensionSpecialEffects.SkyType.END) {
                this.renderEndSky(poseStack);
            } else if (this.client.level.effects().skyType() == DimensionSpecialEffects.SkyType.OVERWORLD) {
                int skyColor = this.level.getSkyColor(this.client.gameRenderer.getMainCamera().getPosition(), tickDelta);
                float f = ARGB.redFloat(skyColor);
                float g = ARGB.greenFloat(skyColor);
                float h = ARGB.blueFloat(skyColor);
                RenderSystem.setShaderFog(fogParameters);
                RenderSystem.depthMask(false);
                RenderSystem.setShaderColor(f, g, h, 1.0F);
                skyRendererAccessor.getTopSkyBuffer().bind();
                skyRendererAccessor.getTopSkyBuffer().drawWithRenderType(RenderType.sky());
                VertexBuffer.unbind();
                RenderSystem.enableBlend();
                int sunriseOrSunsetColor = this.level.effects().getSunriseOrSunsetColor(this.level.getTimeOfDay(tickDelta));
                if (sunriseOrSunsetColor != -1) {
                    RenderSystem.setShader(CoreShaders.POSITION_COLOR);
                    RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                    poseStack.pushPose();
                    poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
                    float i = Mth.sin(this.level.getSunAngle(tickDelta)) < 0.0F ? 180.0F : 0.0F;
                    poseStack.mulPose(Axis.ZP.rotationDegrees(i));
                    poseStack.mulPose(Axis.ZP.rotationDegrees(90.0F));
                    float sunriseOrSunsetR = ARGB.redFloat(sunriseOrSunsetColor);
                    float sunriseOrSunsetG = ARGB.greenFloat(sunriseOrSunsetColor);
                    float sunriseOrSunsetB = ARGB.blueFloat(sunriseOrSunsetColor);
                    float sunriseOrSunsetA = ARGB.blueFloat(sunriseOrSunsetColor);

                    Matrix4f matrix4f = poseStack.last().pose();
                    VertexBuffer buffer = VertexBuffer.uploadStatic(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR, vertexConsumer -> {
                        vertexConsumer.addVertex(matrix4f, 0.0F, 100.0F, 0.0F).setColor(sunriseOrSunsetR, sunriseOrSunsetG, sunriseOrSunsetB, sunriseOrSunsetA);
                        for (int n = 0; n <= 16; ++n) {
                            float o = (float) n * (float) (Math.PI * 2) / 16.0F;
                            float p = Mth.sin(o);
                            float q = Mth.cos(o);
                            vertexConsumer.addVertex(matrix4f, p * 120.0F, q * 120.0F, -q * 40.0F * sunriseOrSunsetA).setColor(sunriseOrSunsetR, sunriseOrSunsetG, sunriseOrSunsetB, 0.0F);
                        }
                    });

                    buffer.bind();
                    buffer.drawWithShader(RenderSystem.getModelViewMatrix(), RenderSystem.getProjectionMatrix(), RenderSystem.getShader());
                    VertexBuffer.unbind();

                    poseStack.popPose();
                }

                RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
                poseStack.pushPose();
                float i = 1.0F - this.level.getRainLevel(tickDelta);
                RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, i);
                poseStack.mulPose(Axis.YP.rotationDegrees(-90.0F));
                this.render(poseStack, this.level, tickDelta);
                poseStack.mulPose(Axis.XP.rotationDegrees(this.level.getTimeOfDay(tickDelta) * 360.0F));
                Matrix4f matrix4f2 = poseStack.last().pose();

                RenderSystem.setShader(CoreShaders.POSITION_TEX);
                RenderSystem.setShaderTexture(0, SkyRendererAccessor.getSun());
                VertexBuffer buffer = VertexBuffer.uploadStatic(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX, vertexConsumer -> {
                    final float k = 30.0F;
                    vertexConsumer.addVertex(matrix4f2, -k, 100.0F, -k).setUv(0.0F, 0.0F);
                    vertexConsumer.addVertex(matrix4f2, k, 100.0F, -k).setUv(1.0F, 0.0F);
                    vertexConsumer.addVertex(matrix4f2, k, 100.0F, k).setUv(1.0F, 1.0F);
                    vertexConsumer.addVertex(matrix4f2, -k, 100.0F, k).setUv(0.0F, 1.0F);
                });

                buffer.bind();
                buffer.drawWithShader(RenderSystem.getModelViewMatrix(), RenderSystem.getProjectionMatrix(), RenderSystem.getShader());
                VertexBuffer.unbind();

                RenderSystem.setShaderTexture(0, SkyRendererAccessor.getMoonPhases());
                int r = this.level.getMoonPhase();
                int s = r % 4;
                int m = r / 4 % 2;
                float t = (float) (s) / 4.0F;
                float o = (float) (m) / 2.0F;
                float p = (float) (s + 1) / 4.0F;
                float q = (float) (m + 1) / 2.0F;

                buffer = VertexBuffer.uploadStatic(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX, vertexConsumer -> {
                    final float v = 20.0F;
                    vertexConsumer.addVertex(matrix4f2, -v, -100.0F, v).setUv(p, q);
                    vertexConsumer.addVertex(matrix4f2, v, -100.0F, v).setUv(t, q);
                    vertexConsumer.addVertex(matrix4f2, v, -100.0F, -v).setUv(t, o);
                    vertexConsumer.addVertex(matrix4f2, -v, -100.0F, -v).setUv(p, o);
                });

                buffer.bind();
                buffer.drawWithShader(RenderSystem.getModelViewMatrix(), RenderSystem.getProjectionMatrix(), RenderSystem.getShader());
                VertexBuffer.unbind();

                float u = this.level.getStarBrightness(tickDelta) * i;
                if (u > 0.0F) {
                    RenderSystem.setShaderColor(u, u, u, u);
                    RenderSystem.setShaderFog(FogParameters.NO_FOG);
                    skyRendererAccessor.getStarsBuffer().bind();
                    skyRendererAccessor.getStarsBuffer().drawWithRenderType(RenderType.stars());
                    VertexBuffer.unbind();
                    fogCallback.run();
                }

                RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                RenderSystem.disableBlend();
                RenderSystem.defaultBlendFunc();
                poseStack.popPose();
                RenderSystem.setShaderColor(0.0F, 0.0F, 0.0F, 1.0F);
                double d = this.client.player.getEyePosition(tickDelta).y - this.level.getLevelData().getHorizonHeight(this.level);
                if (d < 0.0) {
                    poseStack.pushPose();
                    poseStack.translate(0.0F, 12.0F, 0.0F);
                    skyRendererAccessor.getBottomSkyBuffer().bind();
                    skyRendererAccessor.getBottomSkyBuffer().drawWithRenderType(RenderType.sky());
                    VertexBuffer.unbind();
                    poseStack.popPose();
                }

                RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                RenderSystem.depthMask(true);
            }
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

    private void render(PoseStack matrixStack, Level level, float tickDelta) {
        int timeOfDay = (int) (level.getTimeOfDay(tickDelta) % 24000L);
        float skyAngle = level.getTimeOfDay(tickDelta);
        float rainGradient = level.getRainLevel(tickDelta);
        float thunderGradient = level.getThunderLevel(tickDelta);

        if (rainGradient > 0.0F) {
            thunderGradient /= rainGradient;
        }

        for (OptiFineSkyLayer optiFineSkyLayer : this.layers) {
            if (optiFineSkyLayer.isActive(timeOfDay)) {
                optiFineSkyLayer.render(level, matrixStack, timeOfDay, skyAngle, rainGradient, thunderGradient);
            }
        }

        float f3 = 1.0F - rainGradient;
        OptiFineBlend.ADD.getBlendFunc().accept(f3);
    }

    @Override
    public void tick(ClientLevel clientLevel) {
        this.active = true;
        if (clientLevel.dimension() != this.worldIdentifier) {
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

    public ResourceKey<Level> getWorldIdentifier() {
        return worldIdentifier;
    }
}
