package me.flashyreese.mods.nuit_interop.fabricskyboxes;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.vertex.PoseStack;
import me.flashyreese.mods.nuit.api.skyboxes.NuitSkybox;
import me.flashyreese.mods.nuit.api.skyboxes.SkyboxRenderContext;
import me.flashyreese.mods.nuit.api.skyboxes.SkyboxTextureProvider;
import me.flashyreese.mods.nuit.components.Conditions;
import me.flashyreese.mods.nuit.components.Properties;
import me.flashyreese.mods.nuit.render.NuitRenderBackend;
import me.flashyreese.mods.nuit_interop.config.NuitInteropConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.MoonPhase;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.material.FogType;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.lwjgl.opengl.GL46C;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public abstract class LegacyAbstractSkybox implements NuitSkybox, SkyboxTextureProvider {
    private static final Identifier DEFAULT_MOON_ATLAS = Identifier.withDefaultNamespace("textures/environment/celestial/moon_phases.png");
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

    protected final LegacyProperties legacyProperties;
    protected final LegacyConditions legacyConditions;
    protected final LegacyDecorations decorations;
    private final Properties nuitProperties;
    private final Conditions nuitConditions;
    protected float alpha;
    private float conditionAlpha = 0.0F;

    protected LegacyAbstractSkybox(LegacyProperties properties, LegacyConditions conditions, LegacyDecorations decorations) {
        this.legacyProperties = properties;
        this.legacyConditions = conditions;
        this.decorations = decorations;
        this.nuitProperties = properties.toNuitProperties();
        this.nuitConditions = conditions.toNuitConditions();
    }

    @Override
    public void tick(ClientLevel level) {
        this.updateAlpha(level);
    }

    @Override
    public void updateAlpha(ClientLevel level) {
        long currentTime = this.legacyProperties.fade().keyFrames().isEmpty()
                ? level.getDayTime() % 24000L
                : level.getDayTime() % this.legacyProperties.fade().duration();
        boolean condition = this.checkConditions();

        float fadeAlpha = 1.0F;
        if (this.legacyProperties.fade().alwaysOn()) {
            this.conditionAlpha = LegacyUtils.calculateConditionAlphaValue(1.0F, 0.0F, this.conditionAlpha, condition ? this.legacyProperties.transitionInDuration() : this.legacyProperties.transitionOutDuration(), condition);
        } else if (!this.legacyProperties.fade().keyFrames().isEmpty()) {
            fadeAlpha = LegacyUtils.calculateKeyFrameAlphaValue(this.legacyProperties.fade().keyFrames(), this.legacyProperties.fade().duration(), currentTime);
            int duration = condition ? this.legacyProperties.transitionInDuration() : this.legacyProperties.transitionOutDuration();
            this.conditionAlpha = LegacyUtils.calculateConditionAlphaValue(1.0F, 0.0F, this.conditionAlpha, duration, condition);
        } else {
            fadeAlpha = LegacyUtils.calculateFadeAlphaValue(1.0F, 0.0F, (int) currentTime, this.legacyProperties.fade().startFadeIn(), this.legacyProperties.fade().endFadeIn(), this.legacyProperties.fade().startFadeOut(), this.legacyProperties.fade().endFadeOut());
            int duration = condition ? this.legacyProperties.transitionInDuration() : this.legacyProperties.transitionOutDuration();
            this.conditionAlpha = LegacyUtils.calculateConditionAlphaValue(1.0F, 0.0F, this.conditionAlpha, duration, condition);
        }

        this.alpha = (fadeAlpha * this.conditionAlpha) * (this.legacyProperties.maxAlpha() - this.legacyProperties.minAlpha()) + this.legacyProperties.minAlpha();
        this.alpha = Mth.clamp(this.alpha, this.legacyProperties.minAlpha(), this.legacyProperties.maxAlpha());
    }

    protected boolean checkConditions() {
        return this.checkDimensions() && this.checkWorlds() && this.checkBiomes() && this.checkXRanges()
                && this.checkYRanges() && this.checkZRanges() && this.checkWeather() && this.checkEffects() && this.checkLoop();
    }

    protected boolean checkBiomes() {
        Minecraft client = Minecraft.getInstance();
        Objects.requireNonNull(client.level);
        Objects.requireNonNull(client.player);
        if (this.legacyConditions.biomes().isEmpty()) {
            return true;
        }

        boolean contains = this.legacyConditions.biomes().contains(client.level.registryAccess().lookupOrThrow(Registries.BIOME).getKey(client.level.getBiome(client.player.blockPosition()).value()));
        return this.legacyConditions.biomesExcluded() ^ contains;
    }

    protected boolean checkDimensions() {
        Minecraft client = Minecraft.getInstance();
        Objects.requireNonNull(client.level);
        if (this.legacyConditions.dimensions().isEmpty()) {
            return true;
        }

        return this.legacyConditions.dimensionsExcluded() ^ this.legacyConditions.dimensions().contains(client.level.dimension().identifier());
    }

    protected boolean checkWorlds() {
        Minecraft client = Minecraft.getInstance();
        Objects.requireNonNull(client.level);
        Identifier currentVanillaWorld = getVanillaWorldId(client.level.dimensionType().skybox());
        if (this.legacyConditions.worlds().isEmpty()) {
            return true;
        }

        boolean contains = this.legacyConditions.worlds().contains(currentVanillaWorld)
                || this.legacyConditions.worlds().contains(client.level.dimension().identifier());
        return this.legacyConditions.worldsExcluded() ^ contains;
    }

    private static Identifier getVanillaWorldId(DimensionType.Skybox skybox) {
        return switch (skybox) {
            case NONE -> Identifier.withDefaultNamespace("none");
            case OVERWORLD -> Identifier.withDefaultNamespace("overworld");
            case END -> Identifier.withDefaultNamespace("end");
        };
    }

    protected boolean checkEffects() {
        Minecraft client = Minecraft.getInstance();
        Objects.requireNonNull(client.level);
        Camera camera = client.gameRenderer.getMainCamera();

        if (this.legacyConditions.effects().isEmpty()) {
            boolean thickFog = client.gui.getBossOverlay().shouldCreateWorldFog();
            if (thickFog) {
                return this.legacyProperties.renderInThickFog();
            }

            FogType cameraSubmersionType = camera.getFluidInCamera();
            if (cameraSubmersionType == FogType.POWDER_SNOW || cameraSubmersionType == FogType.LAVA) {
                return false;
            }

            return !(camera.entity() instanceof LivingEntity livingEntity) || (!livingEntity.hasEffect(MobEffects.BLINDNESS) && !livingEntity.hasEffect(MobEffects.DARKNESS));
        }

        if (camera.entity() instanceof LivingEntity livingEntity) {
            boolean noneMatch = this.legacyConditions.effects().stream().noneMatch(identifier -> client.level.registryAccess().lookupOrThrow(Registries.MOB_EFFECT).get(identifier).isPresent()
                    && livingEntity.hasEffect(client.level.registryAccess().lookupOrThrow(Registries.MOB_EFFECT).wrapAsHolder(client.level.registryAccess().lookupOrThrow(Registries.MOB_EFFECT).get(identifier).get().value())));
            return this.legacyConditions.effectsExcluded() ^ noneMatch;
        }
        return true;
    }

    protected boolean checkXRanges() {
        return LegacyUtils.checkRangesInclusive(Objects.requireNonNull(Minecraft.getInstance().player).getX(), this.legacyConditions.xRanges(), this.legacyConditions.xRangesExcluded());
    }

    protected boolean checkYRanges() {
        return LegacyUtils.checkRangesInclusive(Objects.requireNonNull(Minecraft.getInstance().player).getY(), this.legacyConditions.yRanges(), this.legacyConditions.yRangesExcluded());
    }

    protected boolean checkZRanges() {
        return LegacyUtils.checkRangesInclusive(Objects.requireNonNull(Minecraft.getInstance().player).getZ(), this.legacyConditions.zRanges(), this.legacyConditions.zRangesExcluded());
    }

    protected boolean checkLoop() {
        if (this.legacyConditions.loop().ranges().isEmpty() || this.legacyConditions.loop().days() <= 0.0D) {
            return true;
        }

        double currentTime = Objects.requireNonNull(Minecraft.getInstance().level).getDayTime() - this.legacyProperties.fade().startFadeIn();
        double duration = 24000.0D * this.legacyConditions.loop().days();
        while (currentTime < 0.0D) {
            currentTime += duration;
        }

        double currentDay = (currentTime / 24000.0D) % this.legacyConditions.loop().days();
        return LegacyUtils.checkRangesInclusive(currentDay, this.legacyConditions.loop().ranges());
    }

    protected boolean checkWeather() {
        ClientLevel world = Objects.requireNonNull(Minecraft.getInstance().level);
        LocalPlayer player = Objects.requireNonNull(Minecraft.getInstance().player);
        Biome.Precipitation precipitation = world.getBiome(player.blockPosition()).value().getPrecipitationAt(player.blockPosition(), world.getSeaLevel());
        if (this.legacyConditions.weathers().isEmpty()) {
            return true;
        }

        boolean matches;
        if (this.legacyConditions.weathers().contains(LegacyWeather.THUNDER) && world.isThundering()) {
            matches = true;
        } else if (this.legacyConditions.weathers().contains(LegacyWeather.RAIN_THUNDER) && world.isThundering() && precipitation == Biome.Precipitation.RAIN) {
            matches = true;
        } else if (this.legacyConditions.weathers().contains(LegacyWeather.SNOW_THUNDER) && world.isThundering() && precipitation == Biome.Precipitation.SNOW) {
            matches = true;
        } else if (this.legacyConditions.weathers().contains(LegacyWeather.RAIN) && world.isRaining() && !world.isThundering()) {
            matches = true;
        } else if (this.legacyConditions.weathers().contains(LegacyWeather.SNOW) && world.isRaining() && precipitation == Biome.Precipitation.SNOW) {
            matches = true;
        } else if (this.legacyConditions.weathers().contains(LegacyWeather.BIOME_RAIN) && world.isRaining() && precipitation == Biome.Precipitation.RAIN) {
            matches = true;
        } else {
            matches = this.legacyConditions.weathers().contains(LegacyWeather.CLEAR) && !world.isRaining() && !world.isThundering();
        }
        return this.legacyConditions.weatherExcluded() ^ matches;
    }

    protected void renderDecorations(SkyboxRenderContext context, Matrix4fStack matrix4fStack) {
        if (!this.decorations.sunEnabled() && !this.decorations.moonEnabled() && !this.decorations.starsEnabled()) {
            return;
        }

        Camera camera = context.camera();
        float tickDelta = context.tickDelta();
        ClientLevel level = Objects.requireNonNull((ClientLevel) camera.entity().level());
        matrix4fStack.pushMatrix();
        try {
            this.decorations.rotation().apply(matrix4fStack, level);
            GpuBufferSlice dynamicTransforms = NuitRenderBackend.createDynamicTransforms(new Matrix4f(matrix4fStack), this.decorations.blend().getColorModifier(this.alpha));

            if (this.decorations.sunEnabled()) {
                LegacyFsbRenderer.drawCelestialQuad(LegacyFsbRenderer.celestialPipeline(), dynamicTransforms, this.decorations.sunTexture(), 30.0F, 100.0F, new me.flashyreese.mods.nuit.components.UVRange(0.0F, 0.0F, 1.0F, 1.0F));
            }

            if (this.decorations.moonEnabled()) {
                this.renderMoon(camera.attributeProbe().getValue(EnvironmentAttributes.MOON_PHASE, tickDelta), dynamicTransforms);
            }

            if (this.decorations.starsEnabled()) {
                PoseStack poseStack = new PoseStack();
                this.decorations.rotation().apply(poseStack, level);
                context.renderStars(camera.attributeProbe().getValue(EnvironmentAttributes.STAR_BRIGHTNESS, tickDelta), poseStack);
            }
        } finally {
            matrix4fStack.popMatrix();
            GL46C.glBlendEquation(GL46C.GL_FUNC_ADD);
        }
    }

    private void renderMoon(MoonPhase moonPhase, GpuBufferSlice dynamicTransforms) {
        Identifier moonTexture = this.decorations.moonTexture();
        boolean useDefaultMoonPhases = usesDefaultMoonPhases(moonTexture);
        Identifier texture = useDefaultMoonPhases ? DEFAULT_MOON_PHASES[moonPhase.index()] : moonTexture;
        float startX = 0.0F;
        float startY = 0.0F;
        float endX = 1.0F;
        float endY = 1.0F;

        if (!useDefaultMoonPhases) {
            int xCoord = moonPhase.index() % 4;
            int yCoord = moonPhase.index() / 4 % 2;
            startX = xCoord / 4.0F;
            startY = yCoord / 2.0F;
            endX = (xCoord + 1) / 4.0F;
            endY = (yCoord + 1) / 2.0F;
        }

        LegacyFsbRenderer.drawCelestialQuad(LegacyFsbRenderer.celestialPipeline(), dynamicTransforms, texture, 20.0F, -100.0F, new me.flashyreese.mods.nuit.components.UVRange(endX, endY, startX, startY));
    }

    protected static boolean usesDefaultMoonPhases(Identifier moonTexture) {
        return moonTexture.equals(DEFAULT_MOON_ATLAS) || moonTexture.equals(LegacyDecorations.MOON_PHASES);
    }

    protected static List<Identifier> getMoonTexturesToRegister(Identifier moonTexture) {
        return usesDefaultMoonPhases(moonTexture) ? List.of(DEFAULT_MOON_PHASES) : List.of(moonTexture);
    }

    @Override
    public List<Identifier> getTexturesToRegister() {
        List<Identifier> textures = new ArrayList<>();
        if (this.decorations.sunEnabled()) {
            textures.add(this.decorations.sunTexture());
        }
        if (this.decorations.moonEnabled()) {
            textures.addAll(getMoonTexturesToRegister(this.decorations.moonTexture()));
        }
        return textures.stream().distinct().toList();
    }

    @Override
    public Properties getProperties() {
        return this.nuitProperties;
    }

    @Override
    public Conditions getConditions() {
        return this.nuitConditions;
    }

    @Override
    public float getAlpha() {
        return this.alpha;
    }

    @Override
    public int getLayer() {
        return this.legacyProperties.priority();
    }

    @Override
    public boolean isActive() {
        return NuitInteropConfig.INSTANCE.interoperability && this.alpha != 0.0F;
    }
}
