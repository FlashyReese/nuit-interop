package me.flashyreese.mods.nuit_interop.sky;

import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.amerebagatelle.mods.nuit.components.Condition;
import io.github.amerebagatelle.mods.nuit.components.MinMaxEntry;
import io.github.amerebagatelle.mods.nuit.components.Weather;
import io.github.amerebagatelle.mods.nuit.util.Utils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.List;

public class OptiFineSkyLayer {
    private static final Codec<Vector3f> VEC_3_F = Codec.FLOAT.listOf().comapFlatMap((list) -> {
        if (list.size() < 3) {
            return DataResult.error(() -> "Incomplete number of elements in vector");
        }
        return DataResult.success(new Vector3f(list.get(0), list.get(1), list.get(2)));
    }, (vec) -> ImmutableList.of(vec.x(), vec.y(), vec.z()));

    private static final LegacyFade OPTIFINE_FADE = new LegacyFade(0, 0, 0, 0, true);

    public static final Codec<OptiFineSkyLayer> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("source").forGetter(OptiFineSkyLayer::getSource),
            Condition.create(ResourceLocation.CODEC).optionalFieldOf("biomes", Condition.of()).forGetter(OptiFineSkyLayer::getBiomes),
            MinMaxEntry.CODEC.listOf().optionalFieldOf("heights", ImmutableList.of()).forGetter(OptiFineSkyLayer::getHeights),
            OptiFineBlend.CODEC.optionalFieldOf("blend", OptiFineBlend.ADD).forGetter(OptiFineSkyLayer::getBlend),
            LegacyFade.CODEC.optionalFieldOf("fade", OPTIFINE_FADE).forGetter(OptiFineSkyLayer::getFade),
            Codec.BOOL.optionalFieldOf("rotate", false).forGetter(OptiFineSkyLayer::isRotate),
            Codec.FLOAT.optionalFieldOf("speed", 1.0F).forGetter(OptiFineSkyLayer::getSpeed),
            VEC_3_F.optionalFieldOf("axis", new Vector3f(1, 0, 0)).forGetter(OptiFineSkyLayer::getAxis),
            Loop.CODEC.optionalFieldOf("loop", Loop.DEFAULT).forGetter(OptiFineSkyLayer::getLoop),
            Codec.FLOAT.optionalFieldOf("transition", 1.0F).forGetter(OptiFineSkyLayer::getTransition),
            Weather.CODEC.listOf().optionalFieldOf("weathers", ImmutableList.of(Weather.CLEAR)).forGetter(OptiFineSkyLayer::getWeathers)
    ).apply(instance, OptiFineSkyLayer::new));

    private final ResourceLocation source;
    private final Condition<ResourceLocation> biomes;
    private final List<MinMaxEntry> heights;
    private final OptiFineBlend blend;
    private final LegacyFade fade;
    private final boolean rotate;
    private final float speed;
    private final Vector3f axis;
    private final Loop loop;
    private final float transition;
    private final List<Weather> weathers;
    public float conditionAlpha = -1;

    public OptiFineSkyLayer(ResourceLocation source, Condition<ResourceLocation> biomes, List<MinMaxEntry> heights, OptiFineBlend blend, LegacyFade fade, boolean rotate, float speed, Vector3f axis, Loop loop, float transition, List<Weather> weathers) {
        this.source = source;
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

    public void tick(Level world) {
        this.conditionAlpha = this.getPositionBrightness(world);
    }

    public void render(Level world, PoseStack matrixStack, int timeOfDay, float skyAngle, float rainGradient, float thunderGradient) {
        float weatherAlpha = this.getWeatherAlpha(rainGradient, thunderGradient);
        float fadeAlpha = this.getFadeAlpha(timeOfDay);
        float finalAlpha = Mth.clamp(this.conditionAlpha * weatherAlpha * fadeAlpha, 0.0F, 1.0F);

        if (!(finalAlpha < 1.0E-4F)) {
            RenderSystem.setShaderTexture(0, this.source);
            this.blend.getBlendFunc().accept(finalAlpha);
            matrixStack.pushPose();

            if (this.rotate) {
                float angle = getAngle(world, skyAngle);
                Quaternionf rotation = new Quaternionf();
                rotation.rotationAxis(angle, this.axis);
                matrixStack.mulPose(rotation);
            }

            Tesselator tessellator = Tesselator.getInstance();
            matrixStack.mulPose(Axis.XP.rotationDegrees(90.0F));
            matrixStack.mulPose(Axis.ZP.rotationDegrees(-90.0F));
            this.renderSide(matrixStack, tessellator, 4);
            matrixStack.pushPose();
            matrixStack.mulPose(Axis.XP.rotationDegrees(90.0F));
            this.renderSide(matrixStack, tessellator, 1);
            matrixStack.popPose();
            matrixStack.pushPose();
            matrixStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
            this.renderSide(matrixStack, tessellator, 0);
            matrixStack.popPose();
            matrixStack.mulPose(Axis.ZP.rotationDegrees(90.0F));
            this.renderSide(matrixStack, tessellator, 5);
            matrixStack.mulPose(Axis.ZP.rotationDegrees(90.0F));
            this.renderSide(matrixStack, tessellator, 2);
            matrixStack.mulPose(Axis.ZP.rotationDegrees(90.0F));
            this.renderSide(matrixStack, tessellator, 3);
            matrixStack.popPose();
        }
    }

    private void renderSide(PoseStack matrixStackIn, Tesselator tess, int side) {
        BufferBuilder bufferbuilder = tess.getBuilder();
        float f = (float) (side % 3) / 3.0F;
        float f1 = (float) (side / 3) / 2.0F;
        Matrix4f matrix4f = matrixStackIn.last().pose();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        bufferbuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        this.addVertex(matrix4f, bufferbuilder, -100.0F, -100.0F, -100.0F, f, f1);
        this.addVertex(matrix4f, bufferbuilder, -100.0F, -100.0F, 100.0F, f, f1 + 0.5F);
        this.addVertex(matrix4f, bufferbuilder, 100.0F, -100.0F, 100.0F, f + 0.33333334F, f1 + 0.5F);
        this.addVertex(matrix4f, bufferbuilder, 100.0F, -100.0F, -100.0F, f + 0.33333334F, f1);
        tess.end();
    }

    private void addVertex(Matrix4f matrix4f, BufferBuilder buffer, float x, float y, float z, float u, float v) {
        Vector4f vector4f = matrix4f.transform(new Vector4f(x, y, z, 1.0F));
        buffer.vertex(vector4f.x, vector4f.y, vector4f.z).uv(u, v).endVertex();
    }


    private float getAngle(Level world, float skyAngle) {
        float angleDayStart = 0.0F;

        if (this.speed != (float) Math.round(this.speed)) {
            long currentWorldDay = (world.getDayTime() + 18000L) / 24000L;
            double anglePerDay = this.speed % 1.0F;
            double currentAngle = (double) currentWorldDay * anglePerDay;
            angleDayStart = (float) (currentAngle % 1.0D);
        }

        return (-360.0F * (angleDayStart + skyAngle * this.speed)) * (float) Math.PI / 180.0F;
    }

    private boolean getConditionCheck(Level world) {
        Minecraft minecraftClient = Minecraft.getInstance();
        Entity cameraEntity = minecraftClient.getCameraEntity();

        if (cameraEntity == null) {
            return false;
        }

        BlockPos entityPos = cameraEntity.blockPosition();

        if (!this.biomes.getEntries().isEmpty()) {
            Biome currentBiome = world.getBiome(entityPos).value();

            if (this.biomes.isExcludes() == this.biomes.getEntries().contains(world.registryAccess().registryOrThrow(Registries.BIOME).getKey(currentBiome))) {
                return false;
            }
        }

        return this.heights == null || Utils.checkRanges(entityPos.getY(), this.heights, false);
    }

    private float getPositionBrightness(Level world) {
        if (this.biomes.getEntries().isEmpty() && this.heights.isEmpty()) {
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

        if (this.weathers.contains(Weather.CLEAR)) {
            weatherAlpha += f;
        }

        if (this.weathers.contains(Weather.RAIN)) {
            weatherAlpha += f1;
        }

        if (this.weathers.contains(Weather.THUNDER)) {
            weatherAlpha += thunderStrength;
        }

        return Mth.clamp(weatherAlpha, 0.0F, 1.0F);
    }

    private float getFadeAlpha(int timeOfDay) {
        if (!this.fade.isAlwaysOn()) {
            return Utils.calculateFadeAlphaValue(1.0F, 0.0F, timeOfDay, this.fade.getStartFadeIn(), this.fade.getEndFadeIn(), this.fade.getStartFadeOut(), this.fade.getEndFadeOut());
        }
        return 1.0F;
    }

    public boolean isActive(int timeOfDay) {
        if (!this.fade.isAlwaysOn() && Utils.isInTimeInterval(timeOfDay, this.fade.getEndFadeOut(), this.fade.getStartFadeIn())) {
            return false;
        } else {
            if (this.loop.getRanges() != null) {
                long adjustedTime = timeOfDay - (long) this.fade.getStartFadeIn();

                // Ensure adjustedTime is a non-negative value in the range of days
                while (adjustedTime < 0L) {
                    adjustedTime += 24000L * (int) this.loop.getDays();
                }

                int daysPassed = (int) (adjustedTime / 24000L);
                int currentDay = daysPassed % (int) this.loop.getDays();

                return Utils.checkRanges(currentDay, this.loop.getRanges(), false);
            }

            return true;
        }
    }

    public ResourceLocation getSource() {
        return source;
    }

    public Condition<ResourceLocation> getBiomes() {
        return biomes;
    }

    public List<MinMaxEntry> getHeights() {
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
