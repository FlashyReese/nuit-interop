package me.flashyreese.mods.nuit_interop.utils;

import com.mojang.serialization.Codec;
import me.flashyreese.mods.nuit.NuitClient;
import me.flashyreese.mods.nuit_interop.optifine.OptiFineSkyMath;
import net.minecraft.util.Mth;

import java.util.function.Function;

public final class Utils {
    public static int normalizeTickTime(int tickTime) {
        return OptiFineSkyMath.normalizeTime(tickTime);
    }

    public static <T> T warnIfDifferent(T initialValue, T finalValue, String message) {
        if (!initialValue.equals(finalValue) && NuitClient.config().generalSettings.debugMode) {
            NuitClient.getLogger().warn(message);
        }

        return finalValue;
    }

    public static Codec<Double> getClampedDouble(double min, double max) {
        if (min > max) {
            throw new UnsupportedOperationException("Maximum value was lesser than than the minimum value");
        } else {
            return Codec.DOUBLE.xmap(f -> Mth.clamp(f, min, max), Function.identity());
        }
    }
}
