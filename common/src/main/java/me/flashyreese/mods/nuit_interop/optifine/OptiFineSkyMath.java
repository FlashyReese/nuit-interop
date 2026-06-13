package me.flashyreese.mods.nuit_interop.optifine;

import me.flashyreese.mods.nuit.components.RangeEntry;
import net.minecraft.util.Mth;

import java.util.List;

public final class OptiFineSkyMath {
    public static final int DAY_TICKS = 24000;
    private static final int NO_TIME = -1;

    public static int parseClockTime(String time) {
        if (time == null) {
            return NO_TIME;
        }

        String[] parts = time.split(":");
        if (parts.length != 2) {
            return NO_TIME;
        }

        int hour = parseInt(parts[0], NO_TIME);
        int minute = parseInt(parts[1], NO_TIME);
        if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
            return NO_TIME;
        }

        hour -= 6;
        if (hour < 0) {
            hour += 24;
        }

        return hour * 1000 + (int) ((double) minute / 60.0D * 1000.0D);
    }

    public static int normalizeTime(int time) {
        int result = time % DAY_TICKS;
        return result < 0 ? result + DAY_TICKS : result;
    }

    public static boolean containsTimeInclusive(int currentTime, int startTime, int endTime) {
        if (currentTime < 0 || currentTime >= DAY_TICKS) {
            throw new IllegalArgumentException("Time must be between 0 and 23999: " + currentTime);
        }

        if (startTime <= endTime) {
            return currentTime >= startTime && currentTime <= endTime;
        }
        return currentTime >= startTime || currentTime <= endTime;
    }

    public static int cyclicDistance(int startTime, int endTime) {
        return (endTime - startTime + DAY_TICKS) % DAY_TICKS;
    }

    public static float fadeAlpha(float maxAlpha, float minAlpha, int currentTime, int startFadeIn, int endFadeIn, int startFadeOut, int endFadeOut) {
        if (containsTimeInclusive(currentTime, endFadeIn, startFadeOut)) {
            return maxAlpha;
        }
        if (containsTimeInclusive(currentTime, startFadeIn, endFadeIn)) {
            int duration = cyclicDistance(startFadeIn, endFadeIn);
            int elapsed = cyclicDistance(startFadeIn, currentTime);
            return minAlpha + ((float) elapsed / duration) * (maxAlpha - minAlpha);
        }
        if (containsTimeInclusive(currentTime, startFadeOut, endFadeOut)) {
            int duration = cyclicDistance(startFadeOut, endFadeOut);
            int elapsed = cyclicDistance(startFadeOut, currentTime);
            return maxAlpha + ((float) elapsed / duration) * (minAlpha - maxAlpha);
        }
        return minAlpha;
    }

    public static boolean inAnyInclusiveRange(double value, List<RangeEntry> ranges) {
        for (RangeEntry range : ranges) {
            if (value >= range.min() && value <= range.max()) {
                return true;
            }
        }
        return false;
    }

    public static float smoothAlpha(float previousValue, float targetValue, float timeDeltaSec, float fadeSeconds) {
        if (timeDeltaSec <= 0.0F) {
            return previousValue;
        }

        float valueDelta = targetValue - previousValue;
        if (fadeSeconds > 0.0F && timeDeltaSec < fadeSeconds && Math.abs(valueDelta) > 1.0E-6F) {
            float updateCount = fadeSeconds / timeDeltaSec;
            float correction = 4.61F - 1.0F / (0.13F + updateCount / 10.0F);
            float blend = Mth.clamp(timeDeltaSec / fadeSeconds * correction, 0.0F, 1.0F);
            return previousValue + valueDelta * blend;
        }

        return targetValue;
    }

    static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception e) {
            return fallback;
        }
    }
}
