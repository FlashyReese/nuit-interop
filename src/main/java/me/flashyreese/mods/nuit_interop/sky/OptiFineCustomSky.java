package me.flashyreese.mods.nuit_interop.sky;

import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.amerebagatelle.mods.nuit.api.skyboxes.Skybox;
import io.github.amerebagatelle.mods.nuit.mixin.SkyRendererAccessor;
import me.flashyreese.mods.nuit_interop.client.config.NuitInteropConfig;
import net.minecraft.block.enums.CameraSubmersionType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.world.World;
import org.joml.Matrix4f;

import java.util.List;

public class OptiFineCustomSky implements Skybox {
    public static final Codec<OptiFineCustomSky> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            OptiFineSkyLayer.CODEC.listOf().optionalFieldOf("layers", ImmutableList.of()).forGetter(OptiFineCustomSky::getLayers),
            World.CODEC.fieldOf("world").forGetter(OptiFineCustomSky::getWorldIdentifier)
    ).apply(instance, OptiFineCustomSky::new));

    private final List<OptiFineSkyLayer> layers;
    private final RegistryKey<World> worldIdentifier;

    private final MinecraftClient client = MinecraftClient.getInstance();
    private ClientWorld world = client.world;

    private boolean active = true;

    public OptiFineCustomSky(List<OptiFineSkyLayer> layers, RegistryKey<World> worldIdentifier) {
        this.layers = layers;
        this.worldIdentifier = worldIdentifier;
    }

    @Override
    public void render(SkyRendererAccessor skyRendererAccessor, MatrixStack matrixStack, float tickDelta, Camera camera, VertexConsumerProvider.Immediate immediate, Fog fog, Runnable fogCallback) {
        this.world = (ClientWorld) camera.getFocusedEntity().getEntityWorld();
        this.renderSky(skyRendererAccessor, matrixStack, tickDelta, camera, immediate, fog, fogCallback);
    }

    private void renderEndSky(MatrixStack matrices) {
        RenderSystem.enableBlend();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
        RenderSystem.setShaderTexture(0, SkyRendererAccessor.getEndSky());
        for (int i = 0; i < 6; ++i) {
            matrices.push();
            switch (i) {
                case 1 -> matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90.0f));
                case 2 -> matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90.0f));
                case 3 -> matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(180.0f));
                case 4 -> matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(90.0f));
                case 5 -> matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-90.0f));
            }

            Matrix4f matrix4f = matrices.peek().getPositionMatrix();
            VertexBuffer buffer = VertexBuffer.createAndUpload(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR, vertexConsumer -> {
                vertexConsumer.vertex(matrix4f, -100.0f, -100.0f, -100.0f).texture(0.0f, 0.0f).color(40, 40, 40, 255);
                vertexConsumer.vertex(matrix4f, -100.0f, -100.0f, 100.0f).texture(0.0f, 16.0f).color(40, 40, 40, 255);
                vertexConsumer.vertex(matrix4f, 100.0f, -100.0f, 100.0f).texture(16.0f, 16.0f).color(40, 40, 40, 255);
                vertexConsumer.vertex(matrix4f, 100.0f, -100.0f, -100.0f).texture(16.0f, 0.0f).color(40, 40, 40, 255);
            });

            buffer.bind();
            buffer.draw(RenderSystem.getModelViewMatrix(), RenderSystem.getProjectionMatrix(), RenderSystem.getShader());
            VertexBuffer.unbind();

            matrices.pop();
        }

        this.render(matrices, this.world, 0.0f);
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }


    public void renderSky(SkyRendererAccessor skyRendererAccessor, MatrixStack matrices, float tickDelta, Camera camera, VertexConsumerProvider.Immediate immediate, Fog fog, Runnable fogCallback) {
        fogCallback.run();
        CameraSubmersionType cameraSubmersionType = camera.getSubmersionType();
        if (cameraSubmersionType != CameraSubmersionType.POWDER_SNOW && cameraSubmersionType != CameraSubmersionType.LAVA) { // TODO: && !(worldRendererAccessor.hasBlindnessOrDarkness(camera))) {
            if (this.client.world.getDimensionEffects().getSkyType() == DimensionEffects.SkyType.END) {
                this.renderEndSky(matrices);
            } else if (this.client.world.getDimensionEffects().getSkyType() == DimensionEffects.SkyType.NORMAL) {
                int skyColor = this.world.getSkyColor(this.client.gameRenderer.getCamera().getPos(), tickDelta);
                float f = ColorHelper.getRedFloat(skyColor);
                float g = ColorHelper.getGreenFloat(skyColor);
                float h = ColorHelper.getBlueFloat(skyColor);
                RenderSystem.setShaderFog(fog);
                RenderSystem.depthMask(false);
                RenderSystem.setShaderColor(f, g, h, 1.0F);
                skyRendererAccessor.getTopSkyBuffer().bind();
                skyRendererAccessor.getTopSkyBuffer().draw(RenderLayer.getSky());
                VertexBuffer.unbind();
                RenderSystem.enableBlend();
                int sunriseOrSunsetColor = this.world.getDimensionEffects().getSkyColor(this.world.getSkyAngle(tickDelta));
                if (sunriseOrSunsetColor != -1) {
                    RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
                    RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                    matrices.push();
                    matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90.0F));
                    float i = MathHelper.sin(this.world.getSkyAngleRadians(tickDelta)) < 0.0F ? 180.0F : 0.0F;
                    matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(i));
                    matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(90.0F));
                    float sunriseOrSunsetR = ColorHelper.getRedFloat(sunriseOrSunsetColor);
                    float sunriseOrSunsetG = ColorHelper.getGreenFloat(sunriseOrSunsetColor);
                    float sunriseOrSunsetB = ColorHelper.getBlueFloat(sunriseOrSunsetColor);
                    float sunriseOrSunsetA = ColorHelper.getBlueFloat(sunriseOrSunsetColor);

                    Matrix4f matrix4f = matrices.peek().getPositionMatrix();
                    VertexBuffer buffer = VertexBuffer.createAndUpload(VertexFormat.DrawMode.TRIANGLE_FAN, VertexFormats.POSITION_COLOR, vertexConsumer -> {
                        vertexConsumer.vertex(matrix4f, 0.0F, 100.0F, 0.0F).color(sunriseOrSunsetR, sunriseOrSunsetG, sunriseOrSunsetB, sunriseOrSunsetA);
                        for (int n = 0; n <= 16; ++n) {
                            float o = (float) n * (float) (Math.PI * 2) / 16.0F;
                            float p = MathHelper.sin(o);
                            float q = MathHelper.cos(o);
                            vertexConsumer.vertex(matrix4f, p * 120.0F, q * 120.0F, -q * 40.0F * sunriseOrSunsetA).color(sunriseOrSunsetR, sunriseOrSunsetG, sunriseOrSunsetB, 0.0F);
                        }
                    });

                    buffer.bind();
                    buffer.draw(RenderSystem.getModelViewMatrix(), RenderSystem.getProjectionMatrix(), RenderSystem.getShader());
                    VertexBuffer.unbind();

                    matrices.pop();
                }

                RenderSystem.blendFuncSeparate(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE, GlStateManager.SrcFactor.ONE, GlStateManager.DstFactor.ZERO);
                matrices.push();
                float i = 1.0F - this.world.getRainGradient(tickDelta);
                RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, i);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-90.0F));
                this.render(matrices, this.world, tickDelta);
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(this.world.getSkyAngle(tickDelta) * 360.0F));
                Matrix4f matrix4f2 = matrices.peek().getPositionMatrix();

                RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX);
                RenderSystem.setShaderTexture(0, SkyRendererAccessor.getSun());
                VertexBuffer buffer = VertexBuffer.createAndUpload(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE, vertexConsumer -> {
                    final float k = 30.0F;
                    vertexConsumer.vertex(matrix4f2, -k, 100.0F, -k).texture(0.0F, 0.0F);
                    vertexConsumer.vertex(matrix4f2, k, 100.0F, -k).texture(1.0F, 0.0F);
                    vertexConsumer.vertex(matrix4f2, k, 100.0F, k).texture(1.0F, 1.0F);
                    vertexConsumer.vertex(matrix4f2, -k, 100.0F, k).texture(0.0F, 1.0F);
                });

                buffer.bind();
                buffer.draw(RenderSystem.getModelViewMatrix(), RenderSystem.getProjectionMatrix(), RenderSystem.getShader());
                VertexBuffer.unbind();

                RenderSystem.setShaderTexture(0, SkyRendererAccessor.getMoonPhases());
                int r = this.world.getMoonPhase();
                int s = r % 4;
                int m = r / 4 % 2;
                float t = (float) (s) / 4.0F;
                float o = (float) (m) / 2.0F;
                float p = (float) (s + 1) / 4.0F;
                float q = (float) (m + 1) / 2.0F;

                buffer = VertexBuffer.createAndUpload(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE, vertexConsumer -> {
                    final float v = 20.0F;
                    vertexConsumer.vertex(matrix4f2, -v, -100.0F, v).texture(p, q);
                    vertexConsumer.vertex(matrix4f2, v, -100.0F, v).texture(t, q);
                    vertexConsumer.vertex(matrix4f2, v, -100.0F, -v).texture(t, o);
                    vertexConsumer.vertex(matrix4f2, -v, -100.0F, -v).texture(p, o);
                });

                buffer.bind();
                buffer.draw(RenderSystem.getModelViewMatrix(), RenderSystem.getProjectionMatrix(), RenderSystem.getShader());
                VertexBuffer.unbind();

                float u = this.world.getStarBrightness(tickDelta) * i;
                if (u > 0.0F) {
                    RenderSystem.setShaderColor(u, u, u, u);
                    RenderSystem.setShaderFog(Fog.DUMMY);
                    skyRendererAccessor.getStarsBuffer().bind();
                    skyRendererAccessor.getStarsBuffer().draw(RenderLayer.getStars());
                    VertexBuffer.unbind();
                    fogCallback.run();
                }

                RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                RenderSystem.disableBlend();
                RenderSystem.defaultBlendFunc();
                matrices.pop();
                RenderSystem.setShaderColor(0.0F, 0.0F, 0.0F, 1.0F);
                double d = this.client.player.getCameraPosVec(tickDelta).y - this.world.getLevelProperties().getSkyDarknessHeight(this.world);
                if (d < 0.0) {
                    matrices.push();
                    matrices.translate(0.0F, 12.0F, 0.0F);
                    skyRendererAccessor.getBottomSkyBuffer().bind();
                    skyRendererAccessor.getBottomSkyBuffer().draw(RenderLayer.getSky());
                    VertexBuffer.unbind();
                    matrices.pop();
                }

                RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                RenderSystem.depthMask(true);
            }
        }
    }

    private boolean hasBlindnessOrDarkness(Camera camera) {
        Entity entity = camera.getFocusedEntity();
        if (entity instanceof LivingEntity livingEntity) {
            return livingEntity.hasStatusEffect(StatusEffects.BLINDNESS) || livingEntity.hasStatusEffect(StatusEffects.DARKNESS);
        }
        return false;
    }

    private void render(MatrixStack matrixStack, World world, float tickDelta) {
        int timeOfDay = (int) (world.getTimeOfDay() % 24000L);
        float skyAngle = world.getSkyAngle(tickDelta);
        float rainGradient = world.getRainGradient(tickDelta);
        float thunderGradient = world.getThunderGradient(tickDelta);

        if (rainGradient > 0.0F) {
            thunderGradient /= rainGradient;
        }

        for (OptiFineSkyLayer optiFineSkyLayer : this.layers) {
            if (optiFineSkyLayer.isActive(timeOfDay)) {
                optiFineSkyLayer.render(world, matrixStack, timeOfDay, skyAngle, rainGradient, thunderGradient);
            }
        }

        float f3 = 1.0F - rainGradient;
        OptiFineBlend.ADD.getBlendFunc().accept(f3);
    }

    @Override
    public void tick(ClientWorld clientWorld) {
        this.active = true;
        if (clientWorld.getRegistryKey() != this.worldIdentifier) {
            this.layers.forEach(layer -> layer.setConditionAlpha(-1.0F));
            this.active = false;
        } else {
            this.layers.forEach(layer -> layer.tick(clientWorld));
        }
    }

    @Override
    public boolean isActive() {
        return NuitInteropConfig.INSTANCE.interoperability && this.active;
    }

    public List<OptiFineSkyLayer> getLayers() {
        return layers;
    }

    public RegistryKey<World> getWorldIdentifier() {
        return worldIdentifier;
    }
}
