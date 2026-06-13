package me.flashyreese.mods.nuit_interop.optifine;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import me.flashyreese.mods.nuit.components.RangeEntry;
import me.flashyreese.mods.nuit_interop.utils.ResourceManagerHelper;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Properties;
import java.util.Set;

public final class OptiFineSkyPropertiesConverter {
    private static final Set<String> SUPPORTED_WEATHER = Set.of("clear", "rain", "thunder");

    public static JsonObject convert(ResourceManagerHelper resourceManager, Properties properties, Identifier propertiesId) {
        Identifier sourceTexture = OptiFineSkyTextureResolver.resolve(properties.getProperty("source"), resourceManager, propertiesId);
        if (sourceTexture == null) {
            return null;
        }

        JsonObject layer = new JsonObject();
        layer.addProperty("source", sourceTexture.toString());

        copyString(properties, layer, "blend");
        if (!writeFade(properties, layer)) {
            return null;
        }
        if (!writeRotation(properties, layer)) {
            return null;
        }
        writeWeather(properties, layer);
        writeBiomes(properties, layer);
        writeHeights(properties, layer);
        if (!writeLoop(properties, layer)) {
            return null;
        }

        return layer;
    }

    private static void copyString(Properties properties, JsonObject target, String key) {
        if (properties.containsKey(key)) {
            String value = properties.getProperty(key);
            if (value != null) {
                target.addProperty(key, value);
            }
        }
    }

    private static boolean writeFade(Properties properties, JsonObject target) {
        boolean hasStartFadeIn = properties.containsKey("startFadeIn");
        boolean hasEndFadeIn = properties.containsKey("endFadeIn");
        boolean hasStartFadeOut = properties.containsKey("startFadeOut");
        boolean hasEndFadeOut = properties.containsKey("endFadeOut");

        JsonObject fade = new JsonObject();
        if (!hasStartFadeIn && !hasEndFadeIn && !hasStartFadeOut && !hasEndFadeOut) {
            fade.addProperty("alwaysOn", true);
            target.add("fade", fade);
            return true;
        }
        if (!hasStartFadeIn || !hasEndFadeIn || !hasEndFadeOut) {
            return false;
        }

        int startFadeIn = OptiFineSkyMath.parseClockTime(properties.getProperty("startFadeIn"));
        int endFadeIn = OptiFineSkyMath.parseClockTime(properties.getProperty("endFadeIn"));
        int endFadeOut = OptiFineSkyMath.parseClockTime(properties.getProperty("endFadeOut"));
        if (startFadeIn < 0 || endFadeIn < 0 || endFadeOut < 0) {
            return false;
        }

        int fadeInDuration = OptiFineSkyMath.normalizeTime(endFadeIn - startFadeIn);
        int startFadeOut = hasStartFadeOut ? OptiFineSkyMath.parseClockTime(properties.getProperty("startFadeOut")) : -1;
        if (startFadeOut < 0) {
            startFadeOut = OptiFineSkyMath.normalizeTime(endFadeOut - fadeInDuration);
            if (OptiFineSkyMath.containsTimeInclusive(startFadeOut, startFadeIn, endFadeIn)) {
                startFadeOut = endFadeIn;
            }
        }

        if (!hasValidFadeCycle(startFadeIn, endFadeIn, startFadeOut, endFadeOut, fadeInDuration)) {
            return false;
        }

        fade.addProperty("startFadeIn", OptiFineSkyMath.normalizeTime(startFadeIn));
        fade.addProperty("endFadeIn", OptiFineSkyMath.normalizeTime(endFadeIn));
        fade.addProperty("startFadeOut", OptiFineSkyMath.normalizeTime(startFadeOut));
        fade.addProperty("endFadeOut", OptiFineSkyMath.normalizeTime(endFadeOut));
        target.add("fade", fade);
        return true;
    }

    private static boolean hasValidFadeCycle(int startFadeIn, int endFadeIn, int startFadeOut, int endFadeOut, int fadeInDuration) {
        int fullCycle = fadeInDuration
                + OptiFineSkyMath.normalizeTime(startFadeOut - endFadeIn)
                + OptiFineSkyMath.normalizeTime(endFadeOut - startFadeOut)
                + OptiFineSkyMath.normalizeTime(startFadeIn - endFadeOut);
        return fullCycle == 0 || fullCycle == OptiFineSkyMath.DAY_TICKS;
    }

    private static boolean writeRotation(Properties properties, JsonObject target) {
        if (properties.containsKey("speed")) {
            float speed = OptiFineSkyPropertyParser.parseFloat(properties.getProperty("speed"), 1.0F);
            if (speed < 0.0F) {
                return false;
            }
            target.addProperty("speed", speed);
        }
        if (properties.containsKey("rotate")) {
            target.addProperty("rotate", OptiFineSkyPropertyParser.parseBoolean(properties.getProperty("rotate"), true));
        }
        if (properties.containsKey("transition")) {
            target.addProperty("transition", OptiFineSkyPropertyParser.parseFloat(properties.getProperty("transition"), 1.0F));
        }
        if (properties.containsKey("axis")) {
            float[] axis = OptiFineSkyPropertyParser.parseAxis(properties.getProperty("axis"));
            if (axis != null) {
                JsonArray convertedAxis = new JsonArray();
                convertedAxis.add(axis[2]);
                convertedAxis.add(axis[1]);
                convertedAxis.add(-axis[0]);
                target.add("axis", convertedAxis);
            }
        }
        return true;
    }

    private static void writeWeather(Properties properties, JsonObject target) {
        if (!properties.containsKey("weather")) {
            return;
        }

        JsonArray weather = new JsonArray();
        OptiFineSkyPropertyParser.tokens(properties.getProperty("weather"), " ").stream()
                .filter(SUPPORTED_WEATHER::contains)
                .forEach(weather::add);
        target.add("weathers", weather);
    }

    private static void writeBiomes(Properties properties, JsonObject target) {
        if (!properties.containsKey("biomes")) {
            return;
        }

        String rawBiomes = properties.getProperty("biomes", "").trim();
        target.addProperty("biomeCondition", true);
        if (rawBiomes.startsWith("!")) {
            target.addProperty("biomeInclusion", false);
            rawBiomes = rawBiomes.substring(1);
        }

        JsonArray biomes = new JsonArray();
        for (String biomeName : OptiFineSkyPropertyParser.tokens(rawBiomes, " ")) {
            Identifier biomeId = OptiFineSkyPropertyParser.parseBiomeId(biomeName);
            if (biomeId != null) {
                biomes.add(biomeId.toString());
            }
        }
        target.add("biomes", biomes);
    }

    private static void writeHeights(Properties properties, JsonObject target) {
        if (!properties.containsKey("heights")) {
            return;
        }

        List<RangeEntry> ranges = OptiFineSkyPropertyParser.parseRanges(properties.getProperty("heights"), true);
        if (ranges == null) {
            return;
        }

        target.addProperty("heightCondition", true);
        target.add("heights", rangesToJson(ranges));
    }

    private static boolean writeLoop(Properties properties, JsonObject target) {
        if (!properties.containsKey("days")) {
            return true;
        }

        List<RangeEntry> ranges = OptiFineSkyPropertyParser.parseRanges(properties.getProperty("days"), false);
        if (ranges == null) {
            return true;
        }

        int daysLoop = 8;
        if (properties.containsKey("daysLoop")) {
            daysLoop = OptiFineSkyMath.parseInt(properties.getProperty("daysLoop"), 8);
            if (daysLoop <= 0) {
                return false;
            }
        }

        JsonObject loop = new JsonObject();
        loop.addProperty("days", daysLoop);
        loop.add("ranges", rangesToJson(ranges));
        target.add("loop", loop);
        return true;
    }

    private static JsonArray rangesToJson(List<RangeEntry> ranges) {
        JsonArray jsonRanges = new JsonArray();
        for (RangeEntry range : ranges) {
            JsonObject jsonRange = new JsonObject();
            jsonRange.addProperty("min", range.min());
            jsonRange.addProperty("max", range.max());
            jsonRanges.add(jsonRange);
        }
        return jsonRanges;
    }
}
