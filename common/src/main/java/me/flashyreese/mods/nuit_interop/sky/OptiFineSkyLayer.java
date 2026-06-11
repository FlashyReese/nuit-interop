package me.flashyreese.mods.nuit_interop.sky;

import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import me.flashyreese.mods.nuit.components.RangeEntry;
import me.flashyreese.mods.nuit.components.Weather;
import me.flashyreese.mods.nuit.util.BufferUploader;
import me.flashyreese.mods.nuit.util.DynamicTransformsBuilder;
import me.flashyreese.mods.nuit.util.Utils;
import me.flashyreese.mods.nuit_interop.fabricskyboxes.LegacyFsbRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
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

public class OptiFineSkyLayer {
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
            Identifier.CODEC.listOf().optionalFieldOf("biomes", ImmutableList.of()).forGetter(OptiFineSkyLayer::getBiomes),
            RangeEntry.CODEC.listOf().optionalFieldOf("heights", ImmutableList.of()).forGetter(OptiFineSkyLayer::getHeights),
            OptiFineBlend.CODEC.optionalFieldOf("blend", OptiFineBlend.ADD).forGetter(OptiFineSkyLayer::getBlend),
            LegacyFade.CODEC.optionalFieldOf("fade", OPTIFINE_FADE).forGetter(OptiFineSkyLayer::getFade),
            Codec.BOOL.optionalFieldOf("rotate", false).forGetter(OptiFineSkyLayer::isRotate),
            Codec.FLOAT.optionalFieldOf("speed", 1.0F).forGetter(OptiFineSkyLayer::getSpeed),
            VEC_3_F.optionalFieldOf("axis", new Vector3f(1, 0, 0)).forGetter(OptiFineSkyLayer::getAxis),
            Loop.CODEC.optionalFieldOf("loop", Loop.DEFAULT).forGetter(OptiFineSkyLayer::getLoop),
            Codec.FLOAT.optionalFieldOf("transition", 1.0F).forGetter(OptiFineSkyLayer::getTransition),
            Weather.CODEC.listOf().optionalFieldOf("weathers", ImmutableList.of(Weather.NO_PRECIPITATION)).forGetter(OptiFineSkyLayer::getWeathers)
    ).apply(instance, OptiFineSkyLayer::new));

    private final Identifier source;
    private final boolean biomeInclusion;
    private final List<Identifier> biomes;
    private final List<RangeEntry> heights;
    private final OptiFineBlend blend;
    private final LegacyFade fade;
    private final boolean rotate;
    private final float speed;
    private final Vector3f axis;
    private final Loop loop;
    private final float transition;
    private final List<Weather> weathers;
    public float conditionAlpha = -1;

    public OptiFineSkyLayer(Identifier source, boolean biomeInclusion, List<Identifier> biomes, List<RangeEntry> heights, OptiFineBlend blend, LegacyFade fade, boolean rotate, float speed, Vector3f axis, Loop loop, float transition, List<Weather> weathers) {
        this.source = source;
        this.biomeInclusion = biomeInclusion;
        this.biomes = biomes;
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
        this.conditionAlpha = this.getPositionBrightness(level);
    }

    public void render(Level level, Matrix4fStack matrix4fStack, int timeOfDay, float skyAngle, float rainGradient, float thunderGradient) {
        float weatherAlpha = this.getWeatherAlpha(rainGradient, thunderGradient);
        float fadeAlpha = this.getFadeAlpha(timeOfDay);
        float finalAlpha = Mth.clamp(this.conditionAlpha * weatherAlpha * fadeAlpha, 0.0F, 1.0F);
        if (finalAlpha < 1.0E-4F) {
            return;
        }

        Vector4f colorModifier = this.blend.applyEquationAndGetColor(finalAlpha);
        GpuBufferSlice dynamicTransforms = DynamicTransformsBuilder.of().withShaderColor(colorModifier).build();
        RenderPipeline pipeline = LegacyFsbRenderer.texturedPipeline(this.blend.getBlendFunction());
        try (ByteBufferBuilder byteBufferBuilder = new ByteBufferBuilder(pipeline.getVertexFormat().getVertexSize() * 24)) {
            BufferBuilder builder = new BufferBuilder(byteBufferBuilder, pipeline.getVertexFormatMode(), pipeline.getVertexFormat());
            matrix4fStack.pushMatrix();
            try {
                if (this.rotate) {
                    matrix4fStack.rotate(Axis.of(this.axis).rotationDegrees(this.getAngle(level, skyAngle)));
                }

                matrix4fStack.rotate(Axis.XP.rotationDegrees(90.0F));
                matrix4fStack.rotate(Axis.ZP.rotationDegrees(-90.0F));
                this.renderSide(matrix4fStack, builder, 4);
                matrix4fStack.pushMatrix();
                matrix4fStack.rotate(Axis.XP.rotationDegrees(90.0F));
                this.renderSide(matrix4fStack, builder, 1);
                matrix4fStack.popMatrix();
                matrix4fStack.pushMatrix();
                matrix4fStack.rotate(Axis.XP.rotationDegrees(-90.0F));
                this.renderSide(matrix4fStack, builder, 0);
                matrix4fStack.popMatrix();
                matrix4fStack.rotate(Axis.ZP.rotationDegrees(90.0F));
                this.renderSide(matrix4fStack, builder, 5);
                matrix4fStack.rotate(Axis.ZP.rotationDegrees(90.0F));
                this.renderSide(matrix4fStack, builder, 2);
                matrix4fStack.rotate(Axis.ZP.rotationDegrees(90.0F));
                this.renderSide(matrix4fStack, builder, 3);
            } finally {
                matrix4fStack.popMatrix();
            }

            GpuTextureView textureView = Minecraft.getInstance().getTextureManager().getTexture(this.source).getTextureView();
            BufferUploader.drawWithShader(pipeline, builder.buildOrThrow(), pass -> {
                pass.setUniform("DynamicTransforms", dynamicTransforms);
                pass.bindTexture("Sampler0", textureView, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            });
        }
    }

    private void renderSide(Matrix4fStack matrix4fStack, BufferBuilder builder, int side) {
        float f = (float) (side % 3) / 3.0F;
        float f1 = (float) (side / 3) / 2.0F;
        Matrix4f matrix4f = new Matrix4f(matrix4fStack);
        builder.addVertex(matrix4f, -100.0F, -100.0F, -100.0F).setUv(f, f1);
        builder.addVertex(matrix4f, -100.0F, -100.0F, 100.0F).setUv(f, f1 + 0.5F);
        builder.addVertex(matrix4f, 100.0F, -100.0F, 100.0F).setUv(f + 0.33333334F, f1 + 0.5F);
        builder.addVertex(matrix4f, 100.0F, -100.0F, -100.0F).setUv(f + 0.33333334F, f1);
    }

    private float getAngle(Level level, float skyAngle) {
        float angleDayStart = 0.0F;
        if (this.speed != (float) Math.round(this.speed)) {
            long currentWorldDay = (level.getDayTime() + 18000L) / 24000L;
            double anglePerDay = this.speed % 1.0F;
            double currentAngle = (double) currentWorldDay * anglePerDay;
            angleDayStart = (float) (currentAngle % 1.0D);
        }

        return (-360.0F * (angleDayStart + skyAngle * this.speed));
    }

    private boolean getConditionCheck(Level level) {
        Minecraft minecraftClient = Minecraft.getInstance();
        Entity cameraEntity = minecraftClient.getCameraEntity();
        if (cameraEntity == null) {
            return false;
        }

        BlockPos entityPos = cameraEntity.getOnPos();
        if (!this.biomes.isEmpty()) {
            Holder<Biome> currentBiome = level.getBiome(entityPos);
            if (!currentBiome.isBound()) {
                return false;
            }

            if (!(this.biomeInclusion && this.biomes.contains(level.getBiome(cameraEntity.blockPosition()).unwrapKey().orElseThrow().identifier()))) {
                return false;
            }
        }

        return this.heights == null || Utils.checkRanges(entityPos.getY(), this.heights, false);
    }

    private float getPositionBrightness(Level world) {
        if (this.biomes.isEmpty() && this.heights.isEmpty()) {
            return 1.0F;
        }

        if (this.conditionAlpha == -1) {
            boolean conditionCheck = this.getConditionCheck(world);
            return conditionCheck ? 1.0F : 0.0F;
        }

        return Utils.calculateConditionAlphaValue(1.0F, 0.0F, this.conditionAlpha, (int) (this.transition * 20), this.getConditionCheck(world));
    }

    private float getWeatherAlpha(float rainStrength, float thunderStrength) {
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

    private float getFadeAlpha(int timeOfDay) {
        if (!this.fade.isAlwaysOn()) {
            return me.flashyreese.mods.nuit_interop.utils.Utils.calculateFadeAlphaValue(1.0F, 0.0F, timeOfDay, this.fade.getStartFadeIn(), this.fade.getEndFadeIn(), this.fade.getStartFadeOut(), this.fade.getEndFadeOut());
        }
        return 1.0F;
    }

    public boolean isActive(long timeOfDay, int clampedTimeOfDay) {
        if (!this.fade.isAlwaysOn() && me.flashyreese.mods.nuit_interop.utils.Utils.isInTimeInterval(clampedTimeOfDay, this.fade.getEndFadeOut(), this.fade.getStartFadeIn())) {
            return false;
        }
        if (this.loop.getRanges() != null) {
            long adjustedTime = timeOfDay - (long) this.fade.getStartFadeIn();
            while (adjustedTime < 0L) {
                adjustedTime += 24000L * (int) this.loop.getDays();
            }
            int daysPassed = (int) (adjustedTime / 24000L);
            int currentDay = daysPassed % (int) this.loop.getDays();
            return Utils.checkRanges(currentDay, this.loop.getRanges(), false);
        }
        return true;
    }

    public Identifier getSource() {
        return source;
    }

    public boolean isBiomeInclusion() {
        return biomeInclusion;
    }

    public List<Identifier> getBiomes() {
        return biomes;
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

    public void setConditionAlpha(float conditionAlpha) {
        this.conditionAlpha = conditionAlpha;
    }
}
