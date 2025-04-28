package me.flashyreese.mods.nuit_interop.sky;

import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import me.flashyreese.mods.nuit.api.skyboxes.Skybox;
import me.flashyreese.mods.nuit.mixin.SkyRendererAccessor;
import me.flashyreese.mods.nuit_interop.config.NuitInteropConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.Level;
import org.joml.Matrix4f;

import java.util.List;

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
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        Matrix4f matrix4f = poseStack.last().pose();
        for (int i = 0; i < 6; ++i) {
            switch (i) {
                case 1 -> matrix4f.rotationX(1.5707964F);
                case 2 -> matrix4f.rotationX(-1.5707964F);
                case 3 -> matrix4f.rotationX(3.1415927F);
                case 4 -> matrix4f.rotationZ(1.5707964F);
                case 5 -> matrix4f.rotationZ(-1.5707964F);
            }

            int color = ARGB.color(255, 40, 40, 40);
            builder.addVertex(matrix4f, -100.0F, -100.0F, -100.0F).setUv(0.0F, 0.0F).setColor(color);
            builder.addVertex(matrix4f, -100.0F, -100.0F, 100.0F).setUv(0.0F, 16.0F).setColor(color);
            builder.addVertex(matrix4f, 100.0F, -100.0F, 100.0F).setUv(16.0F, 16.0F).setColor(color);
            builder.addVertex(matrix4f, 100.0F, -100.0F, -100.0F).setUv(16.0F, 0.0F).setColor(color);
        }
        RenderSystem.setShader(CoreShaders.POSITION_TEX_COLOR);
        RenderSystem.setShaderTexture(0, SkyRenderer.END_SKY_LOCATION);
        BufferUploader.drawWithShader(builder.buildOrThrow());
        this.render(poseStack, level, 0.0F);
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    public void renderSky(SkyRendererAccessor skyRendererAccessor, PoseStack poseStack, float tickDelta, Camera camera, MultiBufferSource.BufferSource bufferSource, FogParameters fogParameters) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = Minecraft.getInstance().level;
        DimensionSpecialEffects dimensionSpecialEffects = level.effects();
        DimensionSpecialEffects.SkyType skyType = dimensionSpecialEffects.skyType();
        RenderSystem.setShaderFog(fogParameters);
        if (skyType == DimensionSpecialEffects.SkyType.END) {
            this.renderEndSky(poseStack, level);
        } else {
            float sunAngle = level.getSunAngle(tickDelta);
            float timeOfDay = level.getTimeOfDay(tickDelta);
            float rainLevel = 1.0F - level.getRainLevel(tickDelta);
            float starBrightness = level.getStarBrightness(tickDelta) * rainLevel;
            int sunriseOrSunsetColor = dimensionSpecialEffects.getSunriseOrSunsetColor(timeOfDay);
            int moonPhase = level.getMoonPhase();
            int skyColor = level.getSkyColor(minecraft.gameRenderer.getMainCamera().getPosition(), tickDelta);
            ((SkyRenderer) skyRendererAccessor).renderSkyDisc(ARGB.redFloat(skyColor), ARGB.greenFloat(skyColor), ARGB.blueFloat(skyColor));
            if (dimensionSpecialEffects.isSunriseOrSunset(timeOfDay)) {
                ((SkyRenderer) skyRendererAccessor).renderSunriseAndSunset(poseStack, bufferSource, sunAngle, sunriseOrSunsetColor);
            }

            ((SkyRenderer) skyRendererAccessor).renderSunMoonAndStars(poseStack, bufferSource, timeOfDay, moonPhase, rainLevel, 0, fogParameters);
            bufferSource.endBatch();

            // Render Sky Layers
            RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
            poseStack.pushPose();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, rainLevel);
            poseStack.mulPose(Axis.YP.rotationDegrees(-90.0F));
            this.render(poseStack, level, tickDelta);
            poseStack.mulPose(Axis.XP.rotationDegrees(timeOfDay * 360.0F));
            poseStack.popPose();
            //

            if (minecraft.player.getEyePosition(tickDelta).y - level.getLevelData().getHorizonHeight(level) < 0.0) {
                ((SkyRenderer) skyRendererAccessor).renderDarkDisc(poseStack);
            }
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

        OptiFineBlend.ADD.getBlendFunc().accept(1.0F - rainGradient);
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