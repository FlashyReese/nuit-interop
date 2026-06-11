package me.flashyreese.mods.nuit_interop.fabricskyboxes;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap;
import me.flashyreese.mods.nuit.util.CodecUtils;

import java.util.Map;

public record LegacyFade(int startFadeIn, int endFadeIn, int startFadeOut, int endFadeOut, boolean alwaysOn,
                         long duration, Map<Long, Float> keyFrames) {
    public static final LegacyFade DEFAULT = new LegacyFade(0, 0, 0, 0, false);
    public static final Codec<LegacyFade> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("startFadeIn", 0).forGetter(LegacyFade::startFadeIn),
            Codec.INT.optionalFieldOf("endFadeIn", 0).forGetter(LegacyFade::endFadeIn),
            Codec.INT.optionalFieldOf("startFadeOut", 0).forGetter(LegacyFade::startFadeOut),
            Codec.INT.optionalFieldOf("endFadeOut", 0).forGetter(LegacyFade::endFadeOut),
            Codec.BOOL.optionalFieldOf("alwaysOn", false).forGetter(LegacyFade::alwaysOn),
            Codec.LONG.optionalFieldOf("duration", 24000L).forGetter(LegacyFade::duration),
            CodecUtils.unboundedMapFixed(Long.class, LegacyUtils.clampedFloat(0.0F, 1.0F), Long2FloatOpenHashMap::new)
                    .optionalFieldOf("keyFrames", new Long2FloatOpenHashMap())
                    .forGetter(LegacyFade::keyFrames)
    ).apply(instance, LegacyFade::new));

    public LegacyFade(int startFadeIn, int endFadeIn, int startFadeOut, int endFadeOut, boolean alwaysOn) {
        this(startFadeIn, endFadeIn, startFadeOut, endFadeOut, alwaysOn, 24000L, new Long2FloatOpenHashMap());
    }

    public LegacyFade {
        duration = Math.max(1L, duration);
        if (!alwaysOn && keyFrames.isEmpty()) {
            startFadeIn = LegacyUtils.normalizeTickTime(startFadeIn);
            endFadeIn = LegacyUtils.normalizeTickTime(endFadeIn);
            startFadeOut = LegacyUtils.normalizeTickTime(startFadeOut);
            endFadeOut = LegacyUtils.normalizeTickTime(endFadeOut);
        }
    }
}
