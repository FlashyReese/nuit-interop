package me.flashyreese.mods.nuit_interop.sky;

import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.math.Axis;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import me.flashyreese.mods.nuit.components.RangeEntry;
import me.flashyreese.mods.nuit.components.Weather;
import me.flashyreese.mods.nuit.render.NuitRenderBackend;
import me.flashyreese.mods.nuit_interop.fabricskyboxes.LegacyFsbRenderer;
import me.flashyreese.mods.nuit_interop.optifine.OptiFineSkyMath;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.List;
import java.util.Locale;

public class OptiFineSkyLayer {
    private static final float MIN_VISIBLE_ALPHA = 0.0001F;
    private static final float UNINITIALIZED_ALPHA = -1.0F;
    private static final Codec<Vector3f> VEC_3_F = Codec.FLOAT.listOf().comapFlatMap((list) -> {
        if (list.size() < 3) {
            return DataResult.error(() -> "Incomplete number of elements in vector");
        } else {
            return DataResult.success(new Vector3f(list.get(0), list.get(1), list.get(2)));
        }
    }, (vec) -> ImmutableList.of(vec.x(), vec.y(), vec.z()));

    private static final LegacyFade OPTIFINE_FADE = new LegacyFade(0, 0, 0, 0, true);

    public static final Codec<OptiFineSkyLayer> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Identifier.CODEC.fieldOf("source").forGetter(OptiFineSkyLayer::getSource),
            Codec.BOOL.optionalFieldOf("biomeInclusion", true).forGetter(OptiFineSkyLayer::isBiomeInclusion),
            Codec.BOOL.optionalFieldOf("biomeCondition", false).forGetter(OptiFineSkyLayer::hasBiomeCondition),
            Identifier.CODEC.listOf().optionalFieldOf("biomes", ImmutableList.of()).forGetter(OptiFineSkyLayer::getBiomes),
            Codec.BOOL.optionalFieldOf("heightCondition", false).forGetter(OptiFineSkyLayer::hasHeightCondition),
            RangeEntry.CODEC.listOf().optionalFieldOf("heights", ImmutableList.of()).forGetter(OptiFineSkyLayer::getHeights),
            OptiFineBlend.CODEC.optionalFieldOf("blend", OptiFineBlend.ADD).forGetter(OptiFineSkyLayer::getBlend),
            LegacyFade.CODEC.optionalFieldOf("fade", OPTIFINE_FADE).forGetter(OptiFineSkyLayer::getFade),
            Codec.BOOL.optionalFieldOf("rotate", true).forGetter(OptiFineSkyLayer::isRotate),
            Codec.FLOAT.optionalFieldOf("speed", 1.0F).forGetter(OptiFineSkyLayer::getSpeed),
            VEC_3_F.optionalFieldOf("axis", new Vector3f(1, 0, 0)).forGetter(OptiFineSkyLayer::getAxis),
            Loop.CODEC.optionalFieldOf("loop", Loop.DEFAULT).forGetter(OptiFineSkyLayer::getLoop),
            Codec.FLOAT.optionalFieldOf("transition", 1.0F).forGetter(OptiFineSkyLayer::getTransition),
            Weather.CODEC.listOf().optionalFieldOf("weathers", ImmutableList.of(Weather.NO_PRECIPITATION)).forGetter(OptiFineSkyLayer::getWeathers)
    ).apply(instance, OptiFineSkyLayer::new));

    private final Identifier source;
    private final boolean biomeInclusion;
    private final boolean biomeCondition;
    private final List<Identifier> biomes;
    private final boolean heightCondition;
    private final List<RangeEntry> heights;
    private final OptiFineBlend blend;
    private final LegacyFade fade;
    private final boolean rotate;
    private final float speed;
    private final Vector3f axis;
    private final Loop loop;
    private final float transition;
    private final List<Weather> weathers;
    private float conditionAlpha = UNINITIALIZED_ALPHA;
    private long conditionAlphaLastUpdateMs = 0L;
    private Level lastLevel;

    public OptiFineSkyLayer(Identifier source, boolean biomeInclusion, boolean biomeCondition, List<Identifier> biomes, boolean heightCondition, List<RangeEntry> heights, OptiFineBlend blend, LegacyFade fade, boolean rotate, float speed, Vector3f axis, Loop loop, float transition, List<Weather> weathers) {
        this.source = source;
        this.biomeInclusion = biomeInclusion;
        this.biomeCondition = biomeCondition || !biomes.isEmpty();
        this.biomes = biomes;
        this.heightCondition = heightCondition || !heights.isEmpty();
        this.heights = heights;
        this.blend = blend;
        this.fade = fade;
        this.rotate = rotate;
        this.speed = speed;
        this.axis = axis;
        this.loop = loop;
        this.transition = transition;
        this.weathers = weathers;
    }

    public void tick(Level level) {
        if (this.lastLevel != level) {
            this.lastLevel = level;
            this.resetPositionAlpha();
        }
    }

    public void render(Level level, Matrix4fStack matrix4fStack, int timeOfDay, float skyAngle, float rainGradient, float thunderGradient) {
        float positionAlpha = this.getPositionAlpha(level);
        float weatherAlpha = this.computeWeatherAlpha(rainGradient, thunderGradient);
        float fadeAlpha = this.computeFadeAlpha(timeOfDay);
        float finalAlpha = Mth.clamp(positionAlpha * weatherAlpha * fadeAlpha, 0.0F, 1.0F);
        if (finalAlpha < MIN_VISIBLE_ALPHA) {
            return;
        }

        Vector4f colorModifier = this.blend.applyEquationAndGetColor(finalAlpha);
        RenderPipeline pipeline = LegacyFsbRenderer.texturedPipeline(this.blend.getBlendFunction());
        try (ByteBufferBuilder byteBufferBuilder = new ByteBufferBuilder(pipeline.getVertexFormat().getVertexSize() * 24)) {
            BufferBuilder builder = new BufferBuilder(byteBufferBuilder, pipeline.getVertexFormatMode(), pipeline.getVertexFormat());
            GpuBufferSlice dynamicTransforms;
            matrix4fStack.pushMatrix();
            try {
                this.applyLayerRotation(level, matrix4fStack, skyAngle);
                dynamicTransforms = this.createDynamicTransforms(matrix4fStack, colorModifier);
                this.buildSkyCube(builder);
            } finally {
                matrix4fStack.popMatrix();
            }

            LegacyFsbRenderer.drawTexturedMesh(pipeline, builder.buildOrThrow(), dynamicTransforms, this.source);
        }
    }

    private void applyLayerRotation(Level level, Matrix4fStack matrix4fStack, float skyAngle) {
        if (this.rotate) {
            matrix4fStack.rotate(Axis.of(this.axis).rotationDegrees(this.computeRotationDegrees(level, skyAngle)));
        }
    }

    private GpuBufferSlice createDynamicTransforms(Matrix4fStack matrix4fStack, Vector4f colorModifier) {
        return NuitRenderBackend.createDynamicTransforms(new Matrix4f(matrix4fStack), colorModifier);
    }

    private void buildSkyCube(BufferBuilder builder) {
        Matrix4fStack faceStack = new Matrix4fStack(8);
        faceStack.rotate(Axis.XP.rotationDegrees(90.0F));
        faceStack.rotate(Axis.ZP.rotationDegrees(-90.0F));
        this.renderSide(faceStack, builder, 4);
        faceStack.pushMatrix();
        faceStack.rotate(Axis.XP.rotationDegrees(90.0F));
        this.renderSide(faceStack, builder, 1);
        faceStack.popMatrix();
        faceStack.pushMatrix();
        faceStack.rotate(Axis.XP.rotationDegrees(-90.0F));
        this.renderSide(faceStack, builder, 0);
        faceStack.popMatrix();
        faceStack.rotate(Axis.ZP.rotationDegrees(90.0F));
        this.renderSide(faceStack, builder, 5);
        faceStack.rotate(Axis.ZP.rotationDegrees(90.0F));
        this.renderSide(faceStack, builder, 2);
        faceStack.rotate(Axis.ZP.rotationDegrees(90.0F));
        this.renderSide(faceStack, builder, 3);
    }

    private void renderSide(Matrix4fStack matrix4fStack, BufferBuilder builder, int side) {
        float minU = (float) (side % 3) / 3.0F;
        float minV = (float) (side / 3) / 2.0F;
        Matrix4f matrix4f = new Matrix4f(matrix4fStack);
        builder.addVertex(matrix4f, -100.0F, -100.0F, -100.0F).setUv(minU, minV);
        builder.addVertex(matrix4f, -100.0F, -100.0F, 100.0F).setUv(minU, minV + 0.5F);
        builder.addVertex(matrix4f, 100.0F, -100.0F, 100.0F).setUv(minU + 0.33333334F, minV + 0.5F);
        builder.addVertex(matrix4f, 100.0F, -100.0F, -100.0F).setUv(minU + 0.33333334F, minV);
    }

    private float computeRotationDegrees(Level level, float skyAngle) {
        float angleDayStart = 0.0F;
        if (this.speed != (float) Math.round(this.speed)) {
            long currentWorldDay = (level.getDefaultClockTime() + 18000L) / 24000L;
            double anglePerDay = this.speed % 1.0F;
            double currentAngle = (double) currentWorldDay * anglePerDay;
            angleDayStart = (float) (currentAngle % 1.0D);
        }

        return 360.0F * (angleDayStart + skyAngle * this.speed);
    }

    private boolean matchesPositionConditions(Level level) {
        Minecraft minecraftClient = Minecraft.getInstance();
        Entity cameraEntity = minecraftClient.getCameraEntity();
        if (cameraEntity == null) {
            return false;
        }

        BlockPos entityPos = cameraEntity.blockPosition();
        if (this.biomeCondition) {
            Holder<Biome> currentBiome = level.getBiome(entityPos);
            if (!currentBiome.isBound()) {
                return false;
            }

            ResourceKey<Biome> currentBiomeKey = currentBiome.unwrapKey().orElse(null);
            if (currentBiomeKey == null) {
                return false;
            }

            boolean matchesBiome = this.biomes.stream().anyMatch(biome -> matchesOptiFineBiome(biome, currentBiomeKey.identifier()));
            if (this.biomeInclusion != matchesBiome) {
                return false;
            }
        }

        if (this.heightCondition && !OptiFineSkyMath.inAnyInclusiveRange(entityPos.getY(), this.heights)) {
            return false;
        }

        return true;
    }

    private static boolean matchesOptiFineBiome(Identifier expected, Identifier actual) {
        if (expected.equals(actual)) {
            return true;
        }

        return expected.getNamespace().equals(actual.getNamespace()) && compactBiomePath(expected.getPath()).equals(compactBiomePath(actual.getPath()));
    }

    private static String compactBiomePath(String path) {
        return path.replace(" ", "").replace("_", "").toLowerCase(Locale.ROOT);
    }

    private float getPositionAlpha(Level world) {
        if (this.lastLevel != world) {
            this.lastLevel = world;
            this.resetPositionAlpha();
        }

        if (!this.biomeCondition && !this.heightCondition) {
            return 1.0F;
        }

        float targetAlpha = this.matchesPositionConditions(world) ? 1.0F : 0.0F;
        if (this.conditionAlpha == UNINITIALIZED_ALPHA) {
            this.conditionAlphaLastUpdateMs = System.currentTimeMillis();
            this.conditionAlpha = targetAlpha;
            return this.conditionAlpha;
        }

        long currentTimeMs = System.currentTimeMillis();
        float timeDeltaSec = (float) (currentTimeMs - this.conditionAlphaLastUpdateMs) / 1000.0F;
        this.conditionAlphaLastUpdateMs = currentTimeMs;
        this.conditionAlpha = OptiFineSkyMath.smoothAlpha(this.conditionAlpha, targetAlpha, timeDeltaSec, this.transition);
        return this.conditionAlpha;
    }

    private float computeWeatherAlpha(float rainStrength, float thunderStrength) {
        float f = 1.0F - rainStrength;
        float f1 = rainStrength - thunderStrength;
        float weatherAlpha = 0.0F;
        if (this.weathers.contains(Weather.NO_PRECIPITATION)) {
            weatherAlpha += f;
        }
        if (this.weathers.contains(Weather.WORLD_PRECIPITATION)) {
            weatherAlpha += f1;
        }
        if (this.weathers.contains(Weather.WORLD_THUNDERSTORM)) {
            weatherAlpha += thunderStrength;
        }
        return Mth.clamp(weatherAlpha, 0.0F, 1.0F);
    }

    private float computeFadeAlpha(int timeOfDay) {
        if (!this.fade.isAlwaysOn()) {
            return OptiFineSkyMath.fadeAlpha(1.0F, 0.0F, timeOfDay, this.fade.getStartFadeIn(), this.fade.getEndFadeIn(), this.fade.getStartFadeOut(), this.fade.getEndFadeOut());
        }
        return 1.0F;
    }

    public boolean isActive(long timeOfDay, int clampedTimeOfDay) {
        if (!this.fade.isAlwaysOn() && OptiFineSkyMath.containsTimeInclusive(clampedTimeOfDay, this.fade.getEndFadeOut(), this.fade.getStartFadeIn())) {
            return false;
        }
        if (!this.loop.getRanges().isEmpty()) {
            long adjustedTime = timeOfDay - (long) this.fade.getStartFadeIn();
            while (adjustedTime < 0L) {
                adjustedTime += 24000L * (int) this.loop.getDays();
            }
            int daysPassed = (int) (adjustedTime / 24000L);
            int currentDay = daysPassed % (int) this.loop.getDays();
            return OptiFineSkyMath.inAnyInclusiveRange(currentDay, this.loop.getRanges());
        }
        return true;
    }

    public Identifier getSource() {
        return source;
    }

    public boolean isBiomeInclusion() {
        return biomeInclusion;
    }

    public boolean hasBiomeCondition() {
        return biomeCondition;
    }

    public List<Identifier> getBiomes() {
        return biomes;
    }

    public boolean hasHeightCondition() {
        return heightCondition;
    }

    public List<RangeEntry> getHeights() {
        return heights;
    }

    public OptiFineBlend getBlend() {
        return blend;
    }

    public LegacyFade getFade() {
        return fade;
    }

    public boolean isRotate() {
        return rotate;
    }

    public float getSpeed() {
        return speed;
    }

    public Vector3f getAxis() {
        return axis;
    }

    public Loop getLoop() {
        return loop;
    }

    public float getTransition() {
        return transition;
    }

    public List<Weather> getWeathers() {
        return weathers;
    }

    public void resetPositionAlpha() {
        this.conditionAlpha = UNINITIALIZED_ALPHA;
        this.conditionAlphaLastUpdateMs = 0L;
    }
}
