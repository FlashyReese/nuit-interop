package me.flashyreese.mods.nuit_interop.fabricskyboxes;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexBuffer;
import me.flashyreese.mods.nuit.api.skyboxes.NuitSkybox;
import me.flashyreese.mods.nuit.components.Conditions;
import me.flashyreese.mods.nuit.components.Properties;
import me.flashyreese.mods.nuit.mixin.SkyRendererAccessor;
import me.flashyreese.mods.nuit.render.NuitRenderBackend;
import me.flashyreese.mods.nuit.skybox.TextureRegistrar;
import me.flashyreese.mods.nuit_interop.config.NuitInteropConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.material.FogType;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public abstract class LegacyAbstractSkybox implements NuitSkybox, TextureRegistrar {
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

        ResourceLocation biome = client.level.getBiome(client.player.blockPosition()).unwrapKey().orElseThrow().location();
        boolean contains = this.legacyConditions.biomes().contains(biome);
        return this.legacyConditions.biomesExcluded() ^ contains;
    }

    protected boolean checkDimensions() {
        Minecraft client = Minecraft.getInstance();
        Objects.requireNonNull(client.level);
        if (this.legacyConditions.dimensions().isEmpty()) {
            return true;
        }

        return this.legacyConditions.dimensionsExcluded() ^ this.legacyConditions.dimensions().contains(client.level.dimension().location());
    }

    protected boolean checkWorlds() {
        Minecraft client = Minecraft.getInstance();
        Objects.requireNonNull(client.level);
        ResourceLocation currentVanillaWorld = me.flashyreese.mods.nuit.util.Utils.getVanillaSkyboxId(client.level.effects().skyType());
        if (this.legacyConditions.worlds().isEmpty()) {
            return true;
        }

        boolean contains = this.legacyConditions.worlds().contains(currentVanillaWorld)
                || this.legacyConditions.worlds().contains(client.level.dimension().location())
                || this.legacyConditions.worlds().contains(client.level.dimensionType().effectsLocation());
        return this.legacyConditions.worldsExcluded() ^ contains;
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

            return !(camera.getEntity() instanceof LivingEntity livingEntity) || (!livingEntity.hasEffect(MobEffects.BLINDNESS) && !livingEntity.hasEffect(MobEffects.DARKNESS));
        }

        if (camera.getEntity() instanceof LivingEntity livingEntity) {
            boolean noneMatch = this.legacyConditions.effects().stream().noneMatch(resourceLocation -> {
                var registry = client.level.registryAccess().registryOrThrow(Registries.MOB_EFFECT);
                var effect = registry.get(resourceLocation);
                return effect != null && livingEntity.hasEffect(registry.wrapAsHolder(effect));
            });
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
        Biome.Precipitation precipitation = world.getBiome(player.blockPosition()).value().getPrecipitationAt(player.blockPosition());
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

    protected void renderDecorations(SkyRendererAccessor skyRendererAccessor, Matrix4f modelViewMatrix,
                                     Matrix4f projectionMatrix, float tickDelta, Camera camera,
                                     Runnable fogCallback) {
        if (!this.decorations.sunEnabled() && !this.decorations.moonEnabled() && !this.decorations.starsEnabled()) {
            return;
        }

        ClientLevel level = Objects.requireNonNull((ClientLevel) camera.getEntity().level());
        Matrix4f decorationMatrix = this.decorations.rotation().apply(new Matrix4f(modelViewMatrix), level);
        try {
            NuitRenderBackend.beginSkybox(this.decorations.blend(), this.alpha, GameRenderer::getPositionTexShader);

            if (this.decorations.sunEnabled()) {
                LegacyFsbRenderer.drawCelestialQuad(decorationMatrix, this.decorations.sunTexture(), 30.0F, 100.0F, new me.flashyreese.mods.nuit.components.UVRange(0.0F, 0.0F, 1.0F, 1.0F));
            }

            if (this.decorations.moonEnabled()) {
                this.renderMoon(level.getMoonPhase(), decorationMatrix);
            }

            if (this.decorations.starsEnabled()) {
                this.renderStars(skyRendererAccessor, level, decorationMatrix, projectionMatrix, tickDelta, fogCallback);
            }
        } finally {
            NuitRenderBackend.endSkybox();
        }
    }

    private void renderMoon(int moonPhase, Matrix4f modelViewMatrix) {
        ResourceLocation moonTexture = this.decorations.moonTexture();
        int xCoord = moonPhase % 4;
        int yCoord = moonPhase / 4 % 2;
        float startX = xCoord / 4.0F;
        float startY = yCoord / 2.0F;
        float endX = (xCoord + 1) / 4.0F;
        float endY = (yCoord + 1) / 2.0F;
        LegacyFsbRenderer.drawCelestialQuad(modelViewMatrix, moonTexture, 20.0F, -100.0F, new me.flashyreese.mods.nuit.components.UVRange(endX, endY, startX, startY));
    }

    private void renderStars(SkyRendererAccessor skyRendererAccessor, ClientLevel level, Matrix4f modelViewMatrix,
                             Matrix4f projectionMatrix, float tickDelta, Runnable fogCallback) {
        float brightness = level.getStarBrightness(tickDelta) * this.alpha;
        if (brightness <= 0.0F) {
            return;
        }

        RenderSystem.setShader(GameRenderer::getPositionShader);
        RenderSystem.setShaderColor(brightness, brightness, brightness, brightness);
        FogRenderer.setupNoFog();
        try {
            skyRendererAccessor.getStarsBuffer().bind();
            skyRendererAccessor.getStarsBuffer().drawWithShader(modelViewMatrix, projectionMatrix, RenderSystem.getShader());
        } finally {
            try {
                VertexBuffer.unbind();
            } finally {
                fogCallback.run();
            }
        }
    }

    protected static List<ResourceLocation> getMoonTexturesToRegister(ResourceLocation moonTexture) {
        return List.of(moonTexture);
    }

    @Override
    public List<ResourceLocation> getTexturesToRegister() {
        List<ResourceLocation> textures = new ArrayList<>();
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
