package me.flashyreese.mods.nuit_interop.sky;

import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.platform.GlStateManager;
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
import me.flashyreese.mods.nuit.api.skyboxes.Skybox;
import me.flashyreese.mods.nuit.components.UVRange;
import me.flashyreese.mods.nuit.mixin.SkyRendererAccessor;
import me.flashyreese.mods.nuit.render.NuitRenderBackend;
import me.flashyreese.mods.nuit.skybox.TextureRegistrar;
import me.flashyreese.mods.nuit_interop.config.NuitInteropConfig;
import me.flashyreese.mods.nuit_interop.fabricskyboxes.LegacyFsbRenderer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.List;
import java.util.stream.Stream;

public class OptiFineCustomSky implements Skybox, TextureRegistrar {
    private static final ResourceLocation DEFAULT_SUN = ResourceLocation.withDefaultNamespace("textures/environment/sun.png");
    private static final ResourceLocation DEFAULT_MOON_PHASES = ResourceLocation.withDefaultNamespace("textures/environment/moon_phases.png");

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
    public void render(SkyRendererAccessor skyRendererAccessor, PoseStack poseStack, Matrix4f projectionMatrix,
                       float tickDelta, Camera camera, boolean thickFog, Runnable fogCallback) {
        fogCallback.run();
        ClientLevel level = (ClientLevel) camera.getEntity().level();
        if (thickFog || this.hasBlindnessOrDarkness(camera)) {
            return;
        }

        FogType fogType = camera.getFluidInCamera();
        if (fogType == FogType.POWDER_SNOW || fogType == FogType.LAVA) {
            return;
        }

        Matrix4f modelViewMatrix = new Matrix4f(poseStack.last().pose());
        if (level.effects().skyType() == DimensionSpecialEffects.SkyType.END) {
            this.renderEndSky(skyRendererAccessor, modelViewMatrix, level, tickDelta);
        } else {
            this.renderOverworldSky(skyRendererAccessor, modelViewMatrix, projectionMatrix, level, tickDelta, camera);
        }
    }

    private void renderEndSky(SkyRendererAccessor skyRendererAccessor, Matrix4f modelViewMatrix,
                              ClientLevel level, float tickDelta) {
        RenderSystem.depthMask(false);
        try {
            this.renderEndSkyTexture(modelViewMatrix);
            this.renderLayers(modelViewMatrix, level, tickDelta, level.getTimeOfDay(tickDelta));
        } finally {
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    private void renderOverworldSky(SkyRendererAccessor skyRendererAccessor, Matrix4f modelViewMatrix,
                                    Matrix4f projectionMatrix, ClientLevel level, float tickDelta, Camera camera) {
        Vec3 skyColor = level.getSkyColor(camera.getPosition(), tickDelta);
        FogRenderer.levelFogColor();
        RenderSystem.depthMask(false);
        RenderSystem.setShaderColor((float) skyColor.x, (float) skyColor.y, (float) skyColor.z, 1.0F);
        ShaderInstance shader = RenderSystem.getShader();
        skyRendererAccessor.getTopSkyBuffer().bind();
        skyRendererAccessor.getTopSkyBuffer().drawWithShader(modelViewMatrix, projectionMatrix, shader);
        VertexBuffer.unbind();
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        float celestialAngle = level.getTimeOfDay(tickDelta);
        float sunAngle = level.getSunAngle(tickDelta);
        float[] sunriseOrSunsetColor = level.effects().getSunriseColor(celestialAngle, tickDelta);
        if (sunriseOrSunsetColor != null) {
            this.renderSunriseAndSunset(modelViewMatrix, sunAngle, sunriseOrSunsetColor);
        }

        Matrix4f layerMatrix = new Matrix4f(modelViewMatrix).rotate(Axis.YP.rotationDegrees(-90.0F));
        this.renderLayers(layerMatrix, level, tickDelta, celestialAngle);
        this.renderSunMoon(modelViewMatrix, sunAngle, level.getMoonPhase(), 1.0F - level.getRainLevel(tickDelta));

        RenderSystem.setShaderColor(0.0F, 0.0F, 0.0F, 1.0F);
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && minecraft.player.getEyePosition(tickDelta).y - level.getLevelData().getHorizonHeight(level) < 0.0D) {
            Matrix4f darkDiscMatrix = new Matrix4f(modelViewMatrix).translate(0.0F, 12.0F, 0.0F);
            skyRendererAccessor.getBottomSkyBuffer().bind();
            skyRendererAccessor.getBottomSkyBuffer().drawWithShader(darkDiscMatrix, projectionMatrix, shader);
            VertexBuffer.unbind();
        }

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
    }

    private void renderSunriseAndSunset(Matrix4f modelViewMatrix, float sunAngle, float[] sunriseOrSunsetColor) {
        float zRotation = Mth.sin(sunAngle) < 0.0F ? 180.0F : 0.0F;
        Matrix4f sunriseModelViewMatrix = new Matrix4f(modelViewMatrix)
                .rotate(Axis.XP.rotationDegrees(90.0F))
                .rotate(Axis.ZP.rotationDegrees(zRotation + 90.0F));

        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);
        float alpha = sunriseOrSunsetColor[3];
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

    private void renderSunMoon(Matrix4f modelViewMatrix, float sunAngle, int moonPhase, float rainLevel) {
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO
        );
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, rainLevel);
        try {
            Matrix4f celestialMatrix = new Matrix4f(modelViewMatrix)
                    .rotate(Axis.YP.rotationDegrees(-90.0F))
                    .rotate(Axis.XP.rotation(sunAngle));
            LegacyFsbRenderer.drawCelestialQuad(celestialMatrix, DEFAULT_SUN, 30.0F, 100.0F, new UVRange(0.0F, 0.0F, 1.0F, 1.0F));

            int xCoord = moonPhase % 4;
            int yCoord = moonPhase / 4 % 2;
            float startX = xCoord / 4.0F;
            float startY = yCoord / 2.0F;
            float endX = (xCoord + 1) / 4.0F;
            float endY = (yCoord + 1) / 2.0F;
            LegacyFsbRenderer.drawCelestialQuad(celestialMatrix, DEFAULT_MOON_PHASES, 20.0F, -100.0F, new UVRange(endX, endY, startX, startY));
        } finally {
            NuitRenderBackend.endBlend();
        }
    }

    private void renderEndSkyTexture(Matrix4f modelViewMatrix) {
        RenderSystem.enableBlend();
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (int face = 0; face < 6; ++face) {
            Matrix4f matrix = new Matrix4f(modelViewMatrix).mul(LegacyFsbRenderer.getMatrixForRotatedFace(face));
            builder.addVertex(matrix, -100.0F, -100.0F, -100.0F).setUv(0.0F, 0.0F).setColor(40, 40, 40, 255);
            builder.addVertex(matrix, -100.0F, -100.0F, 100.0F).setUv(0.0F, 16.0F).setColor(40, 40, 40, 255);
            builder.addVertex(matrix, 100.0F, -100.0F, 100.0F).setUv(16.0F, 16.0F).setColor(40, 40, 40, 255);
            builder.addVertex(matrix, 100.0F, -100.0F, -100.0F).setUv(16.0F, 0.0F).setColor(40, 40, 40, 255);
        }
        NuitRenderBackend.drawTextured(builder.buildOrThrow(), GameRenderer::getPositionTexColorShader, SkyRendererAccessor.getEndSky());
    }

    private void renderLayers(Matrix4f modelViewMatrix, Level level, float tickDelta, float celestialAngle) {
        long timeOfDay = level.getDayTime();
        int clampedTimeOfDay = (int) (timeOfDay % 24000L);
        float rainGradient = level.getRainLevel(tickDelta);
        float thunderGradient = level.getThunderLevel(tickDelta);
        if (rainGradient > 0.0F) {
            thunderGradient /= rainGradient;
        }

        for (OptiFineSkyLayer layer : this.layers) {
            if (layer.isActive(timeOfDay, clampedTimeOfDay)) {
                layer.render(level, modelViewMatrix, clampedTimeOfDay, celestialAngle, rainGradient, thunderGradient);
            }
        }
    }

    private boolean hasBlindnessOrDarkness(Camera camera) {
        return camera.getEntity() instanceof LivingEntity livingEntity
                && (livingEntity.hasEffect(MobEffects.BLINDNESS) || livingEntity.hasEffect(MobEffects.DARKNESS));
    }

    @Override
    public void tick(ClientLevel clientLevel) {
        this.active = true;
        if (!OptiFineWorldMatcher.matches(
                this.worldResourceKey.location(),
                clientLevel.dimension().location(),
                clientLevel.effects().skyType(),
                clientLevel.dimensionType().ultraWarm()
        )) {
            this.layers.forEach(OptiFineSkyLayer::resetPositionAlpha);
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

    @Override
    public List<ResourceLocation> getTexturesToRegister() {
        return Stream.concat(this.layers.stream().map(OptiFineSkyLayer::getSource), Stream.of(DEFAULT_SUN, DEFAULT_MOON_PHASES))
                .distinct()
                .toList();
    }
}
