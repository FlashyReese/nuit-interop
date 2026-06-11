package me.flashyreese.mods.nuit_interop.fabricskyboxes;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap;
import me.flashyreese.mods.nuit.components.Fade;
import me.flashyreese.mods.nuit.components.Fog;
import me.flashyreese.mods.nuit.components.RGBA;
import me.flashyreese.mods.nuit.components.Rotation;

import java.util.Map;

public record LegacyProperties(int priority, LegacyFade fade, float minAlpha, float maxAlpha,
                               int transitionInDuration, int transitionOutDuration, boolean changeFog,
                               boolean changeFogDensity, RGBA fogColors, boolean renderSunSkyTint,
                               boolean renderInThickFog, LegacyRotation rotation) {
    public static final LegacyProperties DEFAULT = new LegacyProperties(0, LegacyFade.DEFAULT, 0.0F, 1.0F, 20, 20, false, false, RGBA.of(), true, true, LegacyRotation.DEFAULT);

    public static final Codec<LegacyProperties> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("priority", 0).forGetter(LegacyProperties::priority),
            LegacyFade.CODEC.fieldOf("fade").forGetter(LegacyProperties::fade),
            LegacyUtils.clampedFloat(0.0F, 1.0F).optionalFieldOf("minAlpha", 0.0F).forGetter(LegacyProperties::minAlpha),
            LegacyUtils.clampedFloat(0.0F, 1.0F).optionalFieldOf("maxAlpha", 1.0F).forGetter(LegacyProperties::maxAlpha),
            LegacyUtils.clampedInt(1, Integer.MAX_VALUE).optionalFieldOf("transitionInDuration", 20).forGetter(LegacyProperties::transitionInDuration),
            LegacyUtils.clampedInt(1, Integer.MAX_VALUE).optionalFieldOf("transitionOutDuration", 20).forGetter(LegacyProperties::transitionOutDuration),
            Codec.BOOL.optionalFieldOf("changeFog", false).forGetter(LegacyProperties::changeFog),
            Codec.BOOL.optionalFieldOf("changeFogDensity", false).forGetter(LegacyProperties::changeFogDensity),
            RGBA.CODEC.optionalFieldOf("fogColors", RGBA.of()).forGetter(LegacyProperties::fogColors),
            Codec.BOOL.optionalFieldOf("sunSkyTint", true).forGetter(LegacyProperties::renderSunSkyTint),
            Codec.BOOL.optionalFieldOf("inThickFog", true).forGetter(LegacyProperties::renderInThickFog),
            LegacyRotation.CODEC.optionalFieldOf("rotation", LegacyRotation.DEFAULT).forGetter(LegacyProperties::rotation)
    ).apply(instance, LegacyProperties::new));

    public LegacyProperties {
        if (minAlpha > maxAlpha) {
            throw new IllegalStateException("Maximum alpha is lower than the minimum alpha");
        }
    }

    public me.flashyreese.mods.nuit.components.Properties toNuitProperties() {
        Long2FloatOpenHashMap keyFrames = new Long2FloatOpenHashMap();
        long duration = this.fade.duration();
        if (!this.fade.keyFrames().isEmpty()) {
            keyFrames.putAll(this.fade.keyFrames());
        } else if (!this.fade.alwaysOn()) {
            keyFrames.put(this.fade.startFadeIn(), 0.0F);
            keyFrames.put(this.fade.endFadeIn(), 1.0F);
            keyFrames.put(this.fade.startFadeOut(), 1.0F);
            keyFrames.put(this.fade.endFadeOut(), 0.0F);
            duration = 24000L;
        }

        Fog fog = new Fog(this.fogColors.getRed(), this.fogColors.getGreen(), this.fogColors.getBlue(), this.changeFog, this.changeFogDensity, this.fogColors.getAlpha(), this.renderInThickFog);
        return new me.flashyreese.mods.nuit.components.Properties(this.priority, new Fade(duration, keyFrames), this.transitionInDuration, this.transitionOutDuration, fog, this.renderSunSkyTint, true, Rotation.of());
    }
}
