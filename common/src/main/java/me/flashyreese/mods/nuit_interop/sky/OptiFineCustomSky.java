package me.flashyreese.mods.nuit_interop.sky;

import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.amerebagatelle.mods.nuit.api.skyboxes.Skybox;
import io.github.amerebagatelle.mods.nuit.mixin.LevelRendererAccessor;
import me.flashyreese.mods.nuit_interop.config.NuitInteropConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
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
    private ClientLevel world = client.level;

    private boolean active = true;

    public OptiFineCustomSky(List<OptiFineSkyLayer> layers, ResourceKey<Level> worldIdentifier) {
        this.layers = layers;
        this.worldIdentifier = worldIdentifier;
    }

    @Override
    public void render(LevelRendererAccessor worldRendererAccess, PoseStack matrixStack, Matrix4f matrix4f, float tickDelta, Camera camera, boolean thickFog, Runnable fogCallback) {
        this.world = (ClientLevel) camera.getEntity().level();
        this.renderSky(worldRendererAccess, matrixStack, matrix4f, tickDelta, camera, thickFog, fogCallback);
    }

    private void renderEndSky(PoseStack matrices) {
        RenderSystem.enableBlend();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, LevelRendererAccessor.getEndSky());
        Tesselator tessellator = Tesselator.getInstance();
        BufferBuilder bufferBuilder = tessellator.getBuilder();
        for (int i = 0; i < 6; ++i) {
            matrices.pushPose();
            if (i == 1) {
                matrices.mulPose(Axis.XP.rotationDegrees(90.0f));
            }
            if (i == 2) {
                matrices.mulPose(Axis.XP.rotationDegrees(-90.0f));
            }
            if (i == 3) {
                matrices.mulPose(Axis.XP.rotationDegrees(180.0f));
            }
            if (i == 4) {
                matrices.mulPose(Axis.ZP.rotationDegrees(90.0f));
            }
            if (i == 5) {
                matrices.mulPose(Axis.ZP.rotationDegrees(-90.0f));
            }
            Matrix4f matrix4f = matrices.last().pose();
            bufferBuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            bufferBuilder.vertex(matrix4f, -100.0f, -100.0f, -100.0f).uv(0.0f, 0.0f).color(40, 40, 40, 255).endVertex();
            bufferBuilder.vertex(matrix4f, -100.0f, -100.0f, 100.0f).uv(0.0f, 16.0f).color(40, 40, 40, 255).endVertex();
            bufferBuilder.vertex(matrix4f, 100.0f, -100.0f, 100.0f).uv(16.0f, 16.0f).color(40, 40, 40, 255).endVertex();
            bufferBuilder.vertex(matrix4f, 100.0f, -100.0f, -100.0f).uv(16.0f, 0.0f).color(40, 40, 40, 255).endVertex();
            tessellator.end();
            matrices.popPose();
        }
        this.render(matrices, this.world, 0.0f);
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    public void renderSky(LevelRendererAccessor worldRendererAccess, PoseStack matrices, Matrix4f projectionMatrix, float tickDelta, Camera camera, boolean thickFog, Runnable fogCallback) {
        fogCallback.run();
        if (!thickFog) {
            FogType cameraSubmersionType = camera.getFluidInCamera();
            if (cameraSubmersionType != FogType.POWDER_SNOW && cameraSubmersionType != FogType.LAVA && !this.hasBlindnessOrDarkness(camera)) {
                if (this.client.level.effects().skyType() == DimensionSpecialEffects.SkyType.END) {
                    this.renderEndSky(matrices);
                } else if (this.client.level.effects().skyType() == DimensionSpecialEffects.SkyType.NORMAL) {
                    Vec3 vec3d = this.world.getSkyColor(this.client.gameRenderer.getMainCamera().getPosition(), tickDelta);
                    float f = (float) vec3d.x;
                    float g = (float) vec3d.y;
                    float h = (float) vec3d.z;
                    FogRenderer.levelFogColor();
                    BufferBuilder bufferBuilder = Tesselator.getInstance().getBuilder();
                    RenderSystem.depthMask(false);
                    RenderSystem.setShaderColor(f, g, h, 1.0F);
                    ShaderInstance shaderProgram = RenderSystem.getShader();
                    worldRendererAccess.getLightSkyBuffer().bind();
                    worldRendererAccess.getLightSkyBuffer().drawWithShader(matrices.last().pose(), projectionMatrix, shaderProgram);
                    VertexBuffer.unbind();
                    RenderSystem.enableBlend();
                    float[] fs = this.world.effects().getSunriseColor(this.world.getTimeOfDay(tickDelta), tickDelta);
                    if (fs != null) {
                        RenderSystem.setShader(GameRenderer::getPositionColorShader);
                        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                        matrices.pushPose();
                        matrices.mulPose(Axis.XP.rotationDegrees(90.0F));
                        float i = Mth.sin(this.world.getSunAngle(tickDelta)) < 0.0F ? 180.0F : 0.0F;
                        matrices.mulPose(Axis.ZP.rotationDegrees(i));
                        matrices.mulPose(Axis.ZP.rotationDegrees(90.0F));
                        float j = fs[0];
                        float k = fs[1];
                        float l = fs[2];
                        Matrix4f matrix4f = matrices.last().pose();
                        bufferBuilder.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);
                        bufferBuilder.vertex(matrix4f, 0.0F, 100.0F, 0.0F).color(j, k, l, fs[3]).endVertex();
                        int m = 16;

                        for (int n = 0; n <= 16; ++n) {
                            float o = (float) n * (float) (Math.PI * 2) / 16.0F;
                            float p = Mth.sin(o);
                            float q = Mth.cos(o);
                            bufferBuilder.vertex(matrix4f, p * 120.0F, q * 120.0F, -q * 40.0F * fs[3]).color(fs[0], fs[1], fs[2], 0.0F).endVertex();
                        }

                        BufferUploader.drawWithShader(bufferBuilder.end());
                        matrices.popPose();
                    }

                    RenderSystem.blendFuncSeparate(
                            GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO
                    );
                    matrices.pushPose();
                    float i = 1.0F - this.world.getRainLevel(tickDelta);
                    RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, i);
                    matrices.mulPose(Axis.YP.rotationDegrees(-90.0F));
                    this.render(matrices, this.world, tickDelta);
                    matrices.mulPose(Axis.XP.rotationDegrees(this.world.getTimeOfDay(tickDelta) * 360.0F));
                    Matrix4f matrix4f2 = matrices.last().pose();
                    float k = 30.0F;
                    RenderSystem.setShader(GameRenderer::getPositionTexShader);
                    RenderSystem.setShaderTexture(0, LevelRendererAccessor.getSun());
                    bufferBuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
                    bufferBuilder.vertex(matrix4f2, -k, 100.0F, -k).uv(0.0F, 0.0F).endVertex();
                    bufferBuilder.vertex(matrix4f2, k, 100.0F, -k).uv(1.0F, 0.0F).endVertex();
                    bufferBuilder.vertex(matrix4f2, k, 100.0F, k).uv(1.0F, 1.0F).endVertex();
                    bufferBuilder.vertex(matrix4f2, -k, 100.0F, k).uv(0.0F, 1.0F).endVertex();
                    BufferUploader.drawWithShader(bufferBuilder.end());
                    k = 20.0F;
                    RenderSystem.setShaderTexture(0, LevelRendererAccessor.getMoonPhases());
                    int r = this.world.getMoonPhase();
                    int s = r % 4;
                    int m = r / 4 % 2;
                    float t = (float) (s + 0) / 4.0F;
                    float o = (float) (m + 0) / 2.0F;
                    float p = (float) (s + 1) / 4.0F;
                    float q = (float) (m + 1) / 2.0F;
                    bufferBuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
                    bufferBuilder.vertex(matrix4f2, -k, -100.0F, k).uv(p, q).endVertex();
                    bufferBuilder.vertex(matrix4f2, k, -100.0F, k).uv(t, q).endVertex();
                    bufferBuilder.vertex(matrix4f2, k, -100.0F, -k).uv(t, o).endVertex();
                    bufferBuilder.vertex(matrix4f2, -k, -100.0F, -k).uv(p, o).endVertex();
                    BufferUploader.drawWithShader(bufferBuilder.end());
                    float u = this.world.getStarBrightness(tickDelta) * i;
                    if (u > 0.0F) {
                        RenderSystem.setShaderColor(u, u, u, u);
                        FogRenderer.setupNoFog();
                        worldRendererAccess.getStarsBuffer().bind();
                        worldRendererAccess.getStarsBuffer().drawWithShader(matrices.last().pose(), projectionMatrix, GameRenderer.getPositionShader());
                        VertexBuffer.unbind();
                        fogCallback.run();
                    }

                    RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                    RenderSystem.disableBlend();
                    RenderSystem.defaultBlendFunc();
                    matrices.popPose();
                    RenderSystem.setShaderColor(0.0F, 0.0F, 0.0F, 1.0F);
                    double d = this.client.player.getEyePosition(tickDelta).y - this.world.getLevelData().getHorizonHeight(this.world);
                    if (d < 0.0) {
                        matrices.pushPose();
                        matrices.translate(0.0F, 12.0F, 0.0F);
                        worldRendererAccess.getDarkSkyBuffer().bind();
                        worldRendererAccess.getDarkSkyBuffer().drawWithShader(matrices.last().pose(), projectionMatrix, shaderProgram);
                        VertexBuffer.unbind();
                        matrices.popPose();
                    }

                    RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                    RenderSystem.depthMask(true);
                }
            }
        }
    }

    private boolean hasBlindnessOrDarkness(Camera camera) {
        Entity entity = camera.getEntity();
        if (entity instanceof LivingEntity livingEntity) {
            return livingEntity.hasEffect(MobEffects.BLINDNESS) || livingEntity.hasEffect(MobEffects.DARKNESS);
        }
        return false;
    }

    private void render(PoseStack matrixStack, Level world, float tickDelta) {
        int timeOfDay = (int) (world.getDayTime() % 24000L);
        float skyAngle = world.getTimeOfDay(tickDelta);
        float rainGradient = world.getRainLevel(tickDelta);
        float thunderGradient = world.getThunderLevel(tickDelta);

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
    public void tick(ClientLevel clientWorld) {
        this.active = true;
        if (clientWorld.dimension() != this.worldIdentifier) {
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

    public ResourceKey<Level> getWorldIdentifier() {
        return worldIdentifier;
    }
}
