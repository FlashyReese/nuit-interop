package me.flashyreese.mods.nuit_interop.sky;

import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import me.flashyreese.mods.nuit.api.skyboxes.Skybox;
import me.flashyreese.mods.nuit.mixin.SkyRendererAccessor;
import me.flashyreese.mods.nuit_interop.config.NuitInteropConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.MoonPhase;
import org.joml.Matrix4fStack;

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
    public void render(SkyRendererAccessor skyRendererAccessor, Matrix4fStack matrix4fStack, float tickDelta, Camera camera, GpuBufferSlice fogParameters, MultiBufferSource.BufferSource bufferSource) {
        this.renderSky(skyRendererAccessor, matrix4fStack, tickDelta, camera, bufferSource, fogParameters);
    }

    private void renderEndSky(SkyRendererAccessor skyRendererAccessor, Matrix4fStack matrix4fStack, ClientLevel level, float tickDelta) {
        ((SkyRenderer) skyRendererAccessor).renderEndSky();
        this.renderLayers(matrix4fStack, level, tickDelta);
    }

    public void renderSky(SkyRendererAccessor skyRendererAccessor, Matrix4fStack matrix4fStack, float tickDelta, Camera camera, MultiBufferSource.BufferSource bufferSource, GpuBufferSlice fogParameters) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }

        RenderSystem.setShaderFog(fogParameters);
        if ("end".equals(level.dimensionType().skybox().getSerializedName())) {
            this.renderEndSky(skyRendererAccessor, matrix4fStack, level, tickDelta);
            return;
        }

        PoseStack poseStack = new PoseStack();
        float sunAngle = camera.attributeProbe().getValue(EnvironmentAttributes.SUN_ANGLE, tickDelta) * Mth.DEG_TO_RAD;
        float timeOfDay = getTimeOfDay(level, tickDelta);
        float rainLevel = 1.0F - level.getRainLevel(tickDelta);
        int sunriseOrSunsetColor = camera.attributeProbe().getValue(EnvironmentAttributes.SUNRISE_SUNSET_COLOR, tickDelta);
        MoonPhase moonPhase = camera.attributeProbe().getValue(EnvironmentAttributes.MOON_PHASE, tickDelta);
        float starBrightness = camera.attributeProbe().getValue(EnvironmentAttributes.STAR_BRIGHTNESS, tickDelta) * rainLevel;
        int skyColor = camera.attributeProbe().getValue(EnvironmentAttributes.SKY_COLOR, tickDelta);

        ((SkyRenderer) skyRendererAccessor).renderSkyDisc(skyColor);
        if (((sunriseOrSunsetColor >>> 24) & 0xFF) > 0) {
            ((SkyRenderer) skyRendererAccessor).renderSunriseAndSunset(poseStack, sunAngle, sunriseOrSunsetColor);
        }

        matrix4fStack.pushMatrix();
        matrix4fStack.rotate(Axis.YP.rotationDegrees(-90.0F));
        this.renderLayers(matrix4fStack, level, tickDelta);
        matrix4fStack.rotate(Axis.XP.rotationDegrees(timeOfDay * 360.0F));
        matrix4fStack.popMatrix();

        ((SkyRenderer) skyRendererAccessor).renderSunMoonAndStars(poseStack, sunAngle, sunAngle + Mth.PI, sunAngle, moonPhase, rainLevel, starBrightness);
        bufferSource.endBatch();

        if (minecraft.player != null && minecraft.player.getEyePosition(tickDelta).y - level.getLevelData().getHorizonHeight(level) < 0.0D) {
            ((SkyRenderer) skyRendererAccessor).renderDarkDisc();
        }
    }

    private void renderLayers(Matrix4fStack matrix4fStack, Level level, float tickDelta) {
        long timeOfDay = level.getDayTime();
        int clampedTimeOfDay = (int) (timeOfDay % 24000L);
        float skyAngle = getTimeOfDay(level, tickDelta);
        float rainGradient = level.getRainLevel(tickDelta);
        float thunderGradient = level.getThunderLevel(tickDelta);
        if (rainGradient > 0.0F) {
            thunderGradient /= rainGradient;
        }

        for (OptiFineSkyLayer optiFineSkyLayer : this.layers) {
            if (optiFineSkyLayer.isActive(timeOfDay, clampedTimeOfDay)) {
                optiFineSkyLayer.render(level, matrix4fStack, clampedTimeOfDay, skyAngle, rainGradient, thunderGradient);
            }
        }
    }

    private static float getTimeOfDay(Level level, float tickDelta) {
        return Mth.positiveModulo((level.getDayTime() + tickDelta) / 24000.0F, 1.0F);
    }

    @Override
    public void tick(ClientLevel clientLevel) {
        this.active = true;
        if (!clientLevel.dimension().equals(this.worldResourceKey)) {
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
        return this.layers;
    }

    public ResourceKey<Level> getWorldResourceKey() {
        return this.worldResourceKey;
    }

    public void close() {
    }
}
