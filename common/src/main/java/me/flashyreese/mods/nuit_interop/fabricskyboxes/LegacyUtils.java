package me.flashyreese.mods.nuit_interop.fabricskyboxes;

import com.mojang.blaze3d.platform.DestFactor;
import com.mojang.blaze3d.platform.SourceFactor;
import com.mojang.serialization.Codec;
import me.flashyreese.mods.nuit.components.RangeEntry;
import me.flashyreese.mods.nuit.components.UVRange;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Tuple;
import net.minecraft.util.Mth;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

final class LegacyUtils {
    private LegacyUtils() {
    }

    static Codec<Integer> clampedInt(int min, int max) {
        if (min > max) {
            throw new UnsupportedOperationException("Maximum value was lesser than than the minimum value");
        }
        return Codec.INT.xmap(value -> Mth.clamp(value, min, max), Function.identity());
    }

    static Codec<Float> clampedFloat(float min, float max) {
        if (min > max) {
            throw new UnsupportedOperationException("Maximum value was lesser than than the minimum value");
        }
        return Codec.FLOAT.xmap(value -> Mth.clamp(value, min, max), Function.identity());
    }

    static Codec<Double> clampedDouble(double min, double max) {
        if (min > max) {
            throw new UnsupportedOperationException("Maximum value was lesser than than the minimum value");
        }
        return Codec.DOUBLE.xmap(value -> Mth.clamp(value, min, max), Function.identity());
    }

    static int normalizeTickTime(long tickTime) {
        long result = tickTime % 24000L;
        return (int) (result >= 0 ? result : result + 24000L);
    }

    static boolean isInTimeInterval(int currentTime, int startTime, int endTime) {
        if (startTime <= endTime) {
            return currentTime >= startTime && currentTime <= endTime;
        }
        return currentTime >= startTime || currentTime <= endTime;
    }

    static int cyclicTimeDistance(int startTime, int endTime) {
        return (endTime - startTime + 24000) % 24000;
    }

    static float calculateFadeAlphaValue(float maxAlpha, float minAlpha, int currentTime, int startFadeIn, int endFadeIn, int startFadeOut, int endFadeOut) {
        if (isInTimeInterval(currentTime, endFadeIn, startFadeOut)) {
            return maxAlpha;
        } else if (isInTimeInterval(currentTime, startFadeIn, endFadeIn)) {
            int duration = Math.max(1, cyclicTimeDistance(startFadeIn, endFadeIn));
            int passed = cyclicTimeDistance(startFadeIn, currentTime);
            return minAlpha + ((float) passed / duration) * (maxAlpha - minAlpha);
        } else if (isInTimeInterval(currentTime, startFadeOut, endFadeOut)) {
            int duration = Math.max(1, cyclicTimeDistance(startFadeOut, endFadeOut));
            int passed = cyclicTimeDistance(startFadeOut, currentTime);
            return maxAlpha + ((float) passed / duration) * (minAlpha - maxAlpha);
        }
        return minAlpha;
    }

    static float calculateKeyFrameAlphaValue(Map<Long, Float> keyFrames, long duration, long currentTime) {
        Optional<Tuple<Long, Long>> closestKeyframes = me.flashyreese.mods.nuit.util.Utils.findClosestKeyframes(keyFrames, currentTime);
        if (closestKeyframes.isEmpty()) {
            return 1.0F;
        }

        Tuple<Long, Long> frames = closestKeyframes.get();
        return me.flashyreese.mods.nuit.util.Utils.calculateInterpolatedAlpha(
                currentTime,
                duration,
                frames.getA(),
                frames.getB(),
                keyFrames.get(frames.getA()),
                keyFrames.get(frames.getB())
        );
    }

    static float calculateConditionAlphaValue(float maxAlpha, float minAlpha, float lastAlpha, int duration, boolean in) {
        if (duration == 0) {
            return lastAlpha;
        } else if (in && maxAlpha == lastAlpha) {
            return maxAlpha;
        } else if (!in && lastAlpha == minAlpha) {
            return minAlpha;
        }

        float alphaChange = (maxAlpha - minAlpha) / duration;
        float result = in ? lastAlpha + alphaChange : lastAlpha - alphaChange;
        return Mth.clamp(result, minAlpha, maxAlpha);
    }

    static boolean checkRangesInclusive(double value, List<RangeEntry> rangeEntries) {
        if (rangeEntries.isEmpty()) {
            return true;
        }

        float floatValue = (float) value;
        for (RangeEntry rangeEntry : rangeEntries) {
            if (floatValue >= rangeEntry.min() && floatValue <= rangeEntry.max()) {
                return true;
            }
        }
        return false;
    }

    static boolean checkRangesInclusive(double value, List<RangeEntry> rangeEntries, boolean excludes) {
        if (rangeEntries.isEmpty()) {
            return true;
        }

        return excludes ^ checkRangesInclusive(value, rangeEntries);
    }

    static double calculateRotation(double rotationSpeed, int timeShift, boolean skyboxRotation, ClientLevel level) {
        if (rotationSpeed == 0.0D) {
            return 0.0D;
        }

        long timeOfDay = level.getDayTime() + timeShift;
        double rotationFraction = timeOfDay / (24000.0D / rotationSpeed);
        double skyAngle = Mth.positiveModulo(rotationFraction, 1.0D);
        return 360.0D * skyAngle;
    }

    static UVRange withUv(UVRange ignored, float minU, float minV, float maxU, float maxV) {
        return new UVRange(minU, minV, maxU, maxV);
    }
}
