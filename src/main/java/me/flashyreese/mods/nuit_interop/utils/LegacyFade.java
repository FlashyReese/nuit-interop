package me.flashyreese.mods.nuit_interop.utils;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.amerebagatelle.mods.nuit.util.Utils;

public record LegacyFade(int startFadeIn, int endFadeIn, int startFadeOut, int endFadeOut, boolean alwaysOn) {
    public static final LegacyFade DEFAULT = new LegacyFade(0, 0, 0, 0, false);
    public static final Codec<LegacyFade> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("startFadeIn", 0).forGetter(LegacyFade::startFadeIn),
            Codec.INT.optionalFieldOf("endFadeIn", 0).forGetter(LegacyFade::endFadeIn),
            Codec.INT.optionalFieldOf("startFadeOut", 0).forGetter(LegacyFade::startFadeOut),
            Codec.INT.optionalFieldOf("endFadeOut", 0).forGetter(LegacyFade::endFadeOut),
            Codec.BOOL.optionalFieldOf("alwaysOn", false).forGetter(LegacyFade::alwaysOn)
    ).apply(instance, LegacyFade::new));

    public LegacyFade(int startFadeIn, int endFadeIn, int startFadeOut, int endFadeOut, boolean alwaysOn) {
        this.startFadeIn = normalizeAndWarnIfDifferent(startFadeIn, alwaysOn);
        this.endFadeIn = normalizeAndWarnIfDifferent(endFadeIn, alwaysOn);
        this.startFadeOut = normalizeAndWarnIfDifferent(startFadeOut, alwaysOn);
        this.endFadeOut = normalizeAndWarnIfDifferent(endFadeOut, alwaysOn);
        this.alwaysOn = alwaysOn;
    }

    private static int normalizeAndWarnIfDifferent(int time, boolean ignore) {
        if (ignore) {
            return time;
        } else {
            int normalized = Utils.normalizeTickTime(time);
            return Utils.warnIfDifferent(time, normalized, String.format("Provided time of %s has been normalized to %s", time, normalized));
        }
    }
}
