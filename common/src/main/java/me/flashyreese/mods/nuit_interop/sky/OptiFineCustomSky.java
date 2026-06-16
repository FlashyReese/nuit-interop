package me.flashyreese.mods.nuit_interop.sky;

import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.math.Axis;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import me.flashyreese.mods.nuit.api.skyboxes.RenderableSkybox;
import me.flashyreese.mods.nuit.api.skyboxes.SkyboxRenderContext;
import me.flashyreese.mods.nuit.api.skyboxes.SkyboxTextureProvider;
import me.flashyreese.mods.nuit.components.UVRange;
import me.flashyreese.mods.nuit.render.NuitRenderBackend;
import me.flashyreese.mods.nuit.util.Utils;
import me.flashyreese.mods.nuit_interop.config.NuitInteropConfig;
import me.flashyreese.mods.nuit_interop.fabricskyboxes.LegacyFsbRenderer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.MoonPhase;
import net.minecraft.world.level.dimension.DimensionType;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector4f;

import java.util.List;
import java.util.stream.Stream;

public class OptiFineCustomSky implements RenderableSkybox, SkyboxTextureProvider {
    private static final Identifier DEFAULT_SUN = Identifier.withDefaultNamespace("textures/environment/celestial/sun.png");
    private static final Identifier[] DEFAULT_MOON_PHASES = new Identifier[]{
            Identifier.withDefaultNamespace("textures/environment/celestial/moon/full_moon.png"),
            Identifier.withDefaultNamespace("textures/environment/celestial/moon/waning_gibbous.png"),
            Identifier.withDefaultNamespace("textures/environment/celestial/moon/third_quarter.png"),
            Identifier.withDefaultNamespace("textures/environment/celestial/moon/waning_crescent.png"),
            Identifier.withDefaultNamespace("textures/environment/celestial/moon/new_moon.png"),
            Identifier.withDefaultNamespace("textures/environment/celestial/moon/waxing_crescent.png"),
            Identifier.withDefaultNamespace("textures/environment/celestial/moon/first_quarter.png"),
            Identifier.withDefaultNamespace("textures/environment/celestial/moon/waxing_gibbous.png")
    };

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

    private static float getCelestialAngle(Camera camera, float tickDelta) {
        return Mth.positiveModulo(camera.attributeProbe().getValue(EnvironmentAttributes.SUN_ANGLE, tickDelta) / 360.0F, 1.0F);
    }

    @Override
    public void render(SkyboxRenderContext context) {
        this.renderSky(context);
    }

    private void renderEndSky(SkyboxRenderContext context, Matrix4fStack matrix4fStack, ClientLevel level, float tickDelta, Camera camera) {
        this.renderEndSkyTexture(context);
        this.renderLayers(matrix4fStack, level, tickDelta, getCelestialAngle(camera, tickDelta));
    }

    public void renderSky(SkyboxRenderContext context) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }

        context.applyFog();
        Matrix4fStack matrix4fStack = context.skyModelViewStack();
        Camera camera = context.camera();
        float tickDelta = context.tickDelta();
        if (level.dimensionType().skybox() == DimensionType.Skybox.END) {
            this.renderEndSky(context, matrix4fStack, level, tickDelta, camera);
            return;
        }

        float sunAngleDegrees = camera.attributeProbe().getValue(EnvironmentAttributes.SUN_ANGLE, tickDelta);
        float sunAngle = sunAngleDegrees * Mth.DEG_TO_RAD;
        float celestialAngle = Mth.positiveModulo(sunAngleDegrees / 360.0F, 1.0F);
        float rainLevel = 1.0F - level.getRainLevel(tickDelta);
        int sunriseOrSunsetColor = camera.attributeProbe().getValue(EnvironmentAttributes.SUNRISE_SUNSET_COLOR, tickDelta);
        MoonPhase moonPhase = camera.attributeProbe().getValue(EnvironmentAttributes.MOON_PHASE, tickDelta);
        int skyColor = camera.attributeProbe().getValue(EnvironmentAttributes.SKY_COLOR, tickDelta);

        context.renderSkyDisc(skyColor);
        if (((sunriseOrSunsetColor >>> 24) & 0xFF) > 0) {
            this.renderSunriseAndSunset(matrix4fStack, sunAngle, sunriseOrSunsetColor);
        }

        matrix4fStack.pushMatrix();
        matrix4fStack.rotate(Axis.YP.rotationDegrees(-90.0F));
        this.renderLayers(matrix4fStack, level, tickDelta, celestialAngle);
        matrix4fStack.popMatrix();

        this.renderSunMoon(matrix4fStack, sunAngle, moonPhase, rainLevel);

        if (minecraft.player != null && minecraft.player.getEyePosition(tickDelta).y - level.getLevelData().getHorizonHeight(level) < 0.0D) {
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
                bufferBuilder.addVertex(matrix4fStack, 0.0F, 100.0F, 0.0F).setColor(sunriseOrSunsetColor);
                int transparentColor = sunriseOrSunsetColor & 0x00FFFFFF;
                float alpha = ((sunriseOrSunsetColor >>> 24) & 0xFF) / 255.0F;
                for (int i = 0; i <= 16; i++) {
                    float angleRadians = (float) i * Mth.TWO_PI / 16.0F;
                    float x = Mth.sin(angleRadians);
                    float y = Mth.cos(angleRadians);
                    float z = -y * 40.0F * alpha;
                    bufferBuilder.addVertex(matrix4fStack, x * 120.0F, y * 120.0F, z).setColor(transparentColor);
                }
                GpuBufferSlice dynamicTransforms = NuitRenderBackend.createDynamicTransforms();
                NuitRenderBackend.draw(pipeline, bufferBuilder.buildOrThrow(), dynamicTransforms);
            }
        } finally {
            matrix4fStack.popMatrix();
        }
    }

    private void renderSunMoon(Matrix4fStack matrix4fStack, float sunAngle, MoonPhase moonPhase, float rainLevel) {
        matrix4fStack.pushMatrix();
        try {
            matrix4fStack.rotate(Axis.YP.rotationDegrees(-90.0F));
            matrix4fStack.pushMatrix();
            try {
                matrix4fStack.rotate(Axis.XP.rotation(sunAngle));
                GpuBufferSlice dynamicTransforms = NuitRenderBackend.createDynamicTransforms(new Matrix4f(matrix4fStack), new Vector4f(1.0F, 1.0F, 1.0F, rainLevel));
                LegacyFsbRenderer.drawCelestialQuad(LegacyFsbRenderer.celestialPipeline(), dynamicTransforms, DEFAULT_SUN, 30.0F, 100.0F, new UVRange(0.0F, 0.0F, 1.0F, 1.0F));
            } finally {
                matrix4fStack.popMatrix();
            }

            matrix4fStack.pushMatrix();
            try {
                matrix4fStack.rotate(Axis.XP.rotation(sunAngle + Mth.PI));
                GpuBufferSlice dynamicTransforms = NuitRenderBackend.createDynamicTransforms(new Matrix4f(matrix4fStack), new Vector4f(1.0F, 1.0F, 1.0F, rainLevel));
                LegacyFsbRenderer.drawCelestialQuad(LegacyFsbRenderer.celestialPipeline(), dynamicTransforms, DEFAULT_MOON_PHASES[moonPhase.index()], 20.0F, 100.0F, new UVRange(0.0F, 0.0F, 1.0F, 1.0F));
            } finally {
                matrix4fStack.popMatrix();
            }
        } finally {
            matrix4fStack.popMatrix();
        }
    }

    private void renderEndSkyTexture(SkyboxRenderContext context) {
        RenderPipeline pipeline = RenderPipelines.END_SKY;
        try (ByteBufferBuilder byteBufferBuilder = new ByteBufferBuilder(pipeline.getVertexFormat().getVertexSize() * 24)) {
            BufferBuilder builder = new BufferBuilder(byteBufferBuilder, pipeline.getVertexFormatMode(), pipeline.getVertexFormat());
            for (int face = 0; face < 6; ++face) {
                Matrix4f matrix4f = Utils.getMatrixForRotatedFace(face);
                builder.addVertex(matrix4f, -100.0F, -100.0F, -100.0F).setUv(0.0F, 0.0F).setColor(0xFF282828);
                builder.addVertex(matrix4f, -100.0F, -100.0F, 100.0F).setUv(0.0F, 16.0F).setColor(0xFF282828);
                builder.addVertex(matrix4f, 100.0F, -100.0F, 100.0F).setUv(16.0F, 16.0F).setColor(0xFF282828);
                builder.addVertex(matrix4f, 100.0F, -100.0F, -100.0F).setUv(16.0F, 0.0F).setColor(0xFF282828);
            }

            GpuBufferSlice dynamicTransforms = NuitRenderBackend.createDynamicTransforms();
            LegacyFsbRenderer.drawTexturedMesh(pipeline, builder.buildOrThrow(), dynamicTransforms, context.endSkyTexture());
        }
    }

    private void renderLayers(Matrix4fStack matrix4fStack, Level level, float tickDelta, float celestialAngle) {
        long timeOfDay = level.getDayTime();
        int clampedTimeOfDay = (int) (timeOfDay % 24000L);
        float rainGradient = level.getRainLevel(tickDelta);
        float thunderGradient = level.getThunderLevel(tickDelta);
        if (rainGradient > 0.0F) {
            thunderGradient /= rainGradient;
        }

        for (OptiFineSkyLayer optiFineSkyLayer : this.layers) {
            if (optiFineSkyLayer.isActive(timeOfDay, clampedTimeOfDay)) {
                optiFineSkyLayer.render(level, matrix4fStack, clampedTimeOfDay, celestialAngle, rainGradient, thunderGradient);
            }
        }
    }

    @Override
    public void tick(ClientLevel clientLevel) {
        this.active = true;
        if (!clientLevel.dimension().equals(this.worldResourceKey)) {
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
    public List<Identifier> getTexturesToRegister() {
        return Stream.concat(this.layers.stream().map(OptiFineSkyLayer::getSource), Stream.concat(Stream.of(DEFAULT_SUN), Stream.of(DEFAULT_MOON_PHASES)))
                .distinct()
                .toList();
    }
}
