package me.flashyreese.mods.nuit_interop.fabricskyboxes;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import it.unimi.dsi.fastutil.ints.Int2LongArrayMap;
import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap;
import me.flashyreese.mods.nuit.api.skyboxes.Skybox;
import me.flashyreese.mods.nuit.components.Blend;
import me.flashyreese.mods.nuit.components.Blender;
import me.flashyreese.mods.nuit.components.RangeEntry;
import me.flashyreese.mods.nuit.components.RGBA;
import me.flashyreese.mods.nuit.components.Texture;
import me.flashyreese.mods.nuit.components.UVRange;
import net.minecraft.resources.Identifier;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3i;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class LegacyFabricSkyBoxesParser {
    private static final Map<String, Codec<? extends Skybox>> CODECS = Map.of(
            "monocolor", LegacyMonoColorSkybox.CODEC,
            "overworld", LegacyOverworldSkybox.CODEC,
            "end", LegacyEndSkybox.CODEC,
            "square-textured", LegacySquareTexturedSkybox.CODEC,
            "single-sprite-square-textured", LegacySingleSpriteSquareTexturedSkybox.CODEC,
            "animated-square-textured", LegacyAnimatedSquareTexturedSkybox.CODEC,
            "single-sprite-animated-square-textured", LegacySingleSpriteAnimatedSquareTexturedSkybox.CODEC,
            "multi-textured", LegacyMultiTextureSkybox.CODEC,
            "multi-texture", LegacyMultiTextureSkybox.CODEC
    );

    public static Optional<Skybox> parse(Identifier sourceId, JsonObject jsonObject) {
        String type = normalizeType(getString(jsonObject, "type", null));
        if (type == null) {
            return Optional.empty();
        }

        int schemaVersion = getInt(jsonObject, "schemaVersion", 1);
        if (schemaVersion == 1) {
            if (isFlatLegacy(jsonObject)) {
                return parseFlatLegacy(type, jsonObject);
            }
            return parseStructuredLegacyV1(type, jsonObject);
        }

        if (isFlatLegacy(jsonObject)) {
            return parseFlatLegacy(type, jsonObject);
        }

        if (schemaVersion != 2) {
            return Optional.empty();
        }

        Codec<? extends Skybox> codec = CODECS.get(type);
        if (codec == null) {
            return Optional.empty();
        }

        return Optional.of(codec.decode(JsonOps.INSTANCE, jsonObject).getOrThrow().getFirst());
    }

    private static Optional<Skybox> parseStructuredLegacyV1(String type, JsonObject jsonObject) {
        LegacyProperties properties = readV1Properties(jsonObject);
        LegacyConditions conditions = readV1Conditions(jsonObject);
        Blend blend = readBlend(jsonObject, Blend.normal());

        return switch (type) {
            case "square-textured" -> Optional.of(new LegacySingleSpriteSquareTexturedSkybox(
                    properties,
                    conditions,
                    LegacyDecorations.DEFAULT,
                    blend,
                    new Texture(readIdentifier(jsonObject, "texture"))
            ));
            case "multi-textured", "multi-texture" -> Optional.of(new LegacyMultiTextureSkybox(
                    properties,
                    conditions,
                    LegacyDecorations.DEFAULT,
                    blend,
                    readV1Animations(jsonObject.get("animatableTextures"))
            ));
            case "decorations" -> Optional.of(new LegacyDecorationsSkybox(
                    properties,
                    conditions,
                    readV1Decorations(jsonObject, properties.rotation(), blend)
            ));
            case "monocolor" -> Optional.of(new LegacyMonoColorSkybox(
                    properties,
                    conditions,
                    LegacyDecorations.DEFAULT,
                    readColor(jsonObject.get("color"), RGBA.of()),
                    blend
            ));
            default -> Optional.empty();
        };
    }

    private static Optional<Skybox> parseFlatLegacy(String type, JsonObject jsonObject) {
        return switch (type) {
            case "monocolor" -> Optional.of(new LegacyMonoColorSkybox(
                    readFlatProperties(jsonObject),
                    readFlatConditions(jsonObject),
                    LegacyDecorations.DEFAULT,
                    new RGBA(getFloat(jsonObject, "red", 0.0F), getFloat(jsonObject, "green", 0.0F), getFloat(jsonObject, "blue", 0.0F), getFloat(jsonObject, "alpha", 1.0F)),
                    Blend.normal()
            ));
            case "square-textured" -> Optional.of(new LegacySquareTexturedSkybox(
                    readFlatProperties(jsonObject),
                    readFlatConditions(jsonObject),
                    LegacyDecorations.DEFAULT,
                    new Blend(getBoolean(jsonObject, "shouldBlend", false) ? "add" : "", Blender.normal()),
                    new LegacyTextures(
                            new Texture(readIdentifier(jsonObject, "texture_north")),
                            new Texture(readIdentifier(jsonObject, "texture_south")),
                            new Texture(readIdentifier(jsonObject, "texture_east")),
                            new Texture(readIdentifier(jsonObject, "texture_west")),
                            new Texture(readIdentifier(jsonObject, "texture_top")),
                            new Texture(readIdentifier(jsonObject, "texture_bottom"))
                    )
            ));
            default -> Optional.empty();
        };
    }

    private static LegacyProperties readFlatProperties(JsonObject jsonObject) {
        float maxAlpha = getFloat(jsonObject, "maxAlpha", 1.0F);
        float transitionSpeed = getFloat(jsonObject, "transitionSpeed", 0.05F);
        int transitionDuration = transitionSpeed <= 0.0F ? 20 : Math.max(1, (int) (maxAlpha / transitionSpeed));
        LegacyRotation rotation = new LegacyRotation(true, new Vector3f(), readVector3f(jsonObject, "axis"), new Vector3i(), 0.0F, 1.0F, 0.0F);
        return new LegacyProperties(
                0,
                new LegacyFade(getInt(jsonObject, "startFadeIn", 0), getInt(jsonObject, "endFadeIn", 0), getInt(jsonObject, "startFadeOut", 0), getInt(jsonObject, "endFadeOut", 0), false),
                getFloat(jsonObject, "minAlpha", 0.0F),
                maxAlpha,
                transitionDuration,
                transitionDuration,
                getBoolean(jsonObject, "changeFog", false),
                false,
                new RGBA(getFloat(jsonObject, "fogRed", 0.0F), getFloat(jsonObject, "fogGreen", 0.0F), getFloat(jsonObject, "fogBlue", 0.0F), 0.0F),
                true,
                true,
                rotation
        );
    }

    private static LegacyConditions readFlatConditions(JsonObject jsonObject) {
        List<Identifier> biomes = readIdentifiers(jsonObject.get("biomes"));
        List<Identifier> worlds = readIdentifiers(jsonObject.get("dimensions"));
        List<LegacyWeather> weathers = readWeather(jsonObject.get("weather"));
        List<RangeEntry> yRanges = readRanges(jsonObject.get("heightRanges"));
        return new LegacyConditions(biomes, worlds, List.of(), List.of(), weathers, List.of(), yRanges, List.of(), LegacyLoop.DEFAULT);
    }

    private static LegacyProperties readV1Properties(JsonObject jsonObject) {
        JsonObject properties = getObject(jsonObject, "properties");
        if (properties == null) {
            return LegacyProperties.DEFAULT;
        }

        return new LegacyProperties(
                getInt(properties, "layer", getInt(properties, "priority", 0)),
                readV1Fade(properties.get("fade")),
                getFloat(properties, "minAlpha", 0.0F),
                getFloat(properties, "maxAlpha", 1.0F),
                getInt(properties, "transitionInDuration", 20),
                getInt(properties, "transitionOutDuration", 20),
                getBoolean(properties, "changeFog", false),
                getBoolean(properties, "changeFogDensity", false),
                readColor(properties.get("fogColors"), RGBA.of()),
                getBoolean(properties, "sunSkyTint", true),
                getBoolean(properties, "inThickFog", true),
                readV1Rotation(properties.get("rotation"))
        );
    }

    private static LegacyFade readV1Fade(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return new LegacyFade(0, 0, 0, 0, true);
        }

        JsonObject fade = element.getAsJsonObject();
        if (getBoolean(fade, "alwaysOn", false)) {
            return new LegacyFade(0, 0, 0, 0, true);
        }

        if (fade.has("keyFrames")) {
            Long2FloatOpenHashMap keyFrames = new Long2FloatOpenHashMap();
            JsonObject keyFrameObject = getObject(fade, "keyFrames");
            if (keyFrameObject != null) {
                for (Map.Entry<String, JsonElement> entry : keyFrameObject.entrySet()) {
                    keyFrames.put(Long.parseLong(entry.getKey()), entry.getValue().getAsFloat());
                }
            }
            return new LegacyFade(0, 0, 0, 0, false, getLong(fade, "duration", 24000L), keyFrames);
        }

        return new LegacyFade(
                getInt(fade, "startFadeIn", 0),
                getInt(fade, "endFadeIn", 0),
                getInt(fade, "startFadeOut", 0),
                getInt(fade, "endFadeOut", 0),
                false
        );
    }

    private static LegacyRotation readV1Rotation(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return LegacyRotation.DEFAULT;
        }

        JsonObject rotation = element.getAsJsonObject();
        JsonObject mappingObject = getObject(rotation, "mapping");
        JsonObject axisObject = getObject(rotation, "axis");
        if (mappingObject != null || axisObject != null) {
            return LegacyRotation.mapped(
                    getBoolean(rotation, "skyboxRotation", true),
                    readQuaternionKeyframes(mappingObject),
                    readQuaternionKeyframes(axisObject),
                    getLong(rotation, "duration", 24000L),
                    getFloat(rotation, "speed", 1.0F)
            );
        }

        return new LegacyRotation(
                getBoolean(rotation, "skyboxRotation", true),
                readVector3f(rotation, "static"),
                readVector3f(rotation, "axis"),
                readVector3i(rotation, "timeShift"),
                getFloat(rotation, "rotationSpeedX", 0.0F),
                getFloat(rotation, "rotationSpeedY", 0.0F),
                getFloat(rotation, "rotationSpeedZ", 0.0F)
        );
    }

    private static Map<Long, Quaternionf> readQuaternionKeyframes(JsonObject object) {
        if (object == null) {
            return Map.of();
        }

        Map<Long, Quaternionf> keyframes = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            Vector3f vector = readVector3f(entry.getValue());
            keyframes.put(Long.parseLong(entry.getKey()), new Quaternionf()
                    .rotateLocalX((float) Math.toRadians(vector.x()))
                    .rotateLocalY((float) Math.toRadians(vector.y()))
                    .rotateLocalZ((float) Math.toRadians(vector.z())));
        }
        return keyframes;
    }

    private static LegacyConditions readV1Conditions(JsonObject jsonObject) {
        JsonObject conditions = getObject(jsonObject, "conditions");
        if (conditions == null) {
            return LegacyConditions.DEFAULT;
        }

        ConditionData<Identifier> biomes = readIdentifierCondition(conditions, "biomes");
        ConditionData<Identifier> worlds = readIdentifierCondition(conditions, "worlds");
        ConditionData<Identifier> dimensions = readIdentifierCondition(conditions, "dimensions");
        ConditionData<Identifier> effects = readIdentifierCondition(conditions, "effects");
        ConditionData<LegacyWeather> weather = readWeatherCondition(conditions, "weather");
        ConditionData<RangeEntry> xRanges = readRangeCondition(conditions, "xRanges");
        ConditionData<RangeEntry> yRanges = readRangeCondition(conditions, "yRanges");
        ConditionData<RangeEntry> zRanges = readRangeCondition(conditions, "zRanges");

        return new LegacyConditions(
                biomes.entries(),
                worlds.entries(),
                dimensions.entries(),
                effects.entries(),
                weather.entries(),
                xRanges.entries(),
                yRanges.entries(),
                zRanges.entries(),
                readLoop(conditions.get("loop")),
                biomes.excludes(),
                worlds.excludes(),
                dimensions.excludes(),
                effects.excludes(),
                weather.excludes(),
                xRanges.excludes(),
                yRanges.excludes(),
                zRanges.excludes()
        );
    }

    private static LegacyLoop readLoop(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return LegacyLoop.DEFAULT;
        }

        JsonObject loop = element.getAsJsonObject();
        return new LegacyLoop(getFloat(loop, "days", 0.0F), readRanges(loop.get("ranges")));
    }

    private static LegacyDecorations readV1Decorations(JsonObject jsonObject, LegacyRotation rotation, Blend blend) {
        return new LegacyDecorations(
                readIdentifier(jsonObject, "sun", LegacyDecorations.SUN),
                readIdentifier(jsonObject, "moon", LegacyDecorations.MOON_PHASES),
                getBoolean(jsonObject, "showSun", false),
                getBoolean(jsonObject, "showMoon", false),
                getBoolean(jsonObject, "showStars", false),
                rotation,
                blend
        );
    }

    private static List<LegacyAnimation> readV1Animations(JsonElement element) {
        List<LegacyAnimation> animations = new ArrayList<>();
        if (element == null || !element.isJsonArray()) {
            return animations;
        }

        for (JsonElement entry : element.getAsJsonArray()) {
            if (!entry.isJsonObject()) {
                continue;
            }

            JsonObject animation = entry.getAsJsonObject();
            animations.add(new LegacyAnimation(
                    new Texture(readIdentifier(animation, "texture")),
                    readUvRange(animation.get("uvRange")),
                    getInt(animation, "gridColumns", 1),
                    getInt(animation, "gridRows", 1),
                    getInt(animation, "duration", 1),
                    getBoolean(animation, "interpolate", true),
                    readFrameDurations(animation.get("frameDuration"))
            ));
        }
        return animations;
    }

    private static Map<Integer, Long> readFrameDurations(JsonElement element) {
        Int2LongArrayMap durations = new Int2LongArrayMap();
        if (element == null || !element.isJsonObject()) {
            return durations;
        }

        for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
            durations.put(Integer.parseInt(entry.getKey()), entry.getValue().getAsLong());
        }
        return durations;
    }

    private static boolean isFlatLegacy(JsonObject jsonObject) {
        return !jsonObject.has("properties") && (jsonObject.has("startFadeIn") || jsonObject.has("texture_north") || jsonObject.has("shouldBlend") || jsonObject.has("heightRanges"));
    }

    private static String normalizeType(String type) {
        if (type == null) {
            return null;
        }
        String normalized = type.toLowerCase(Locale.ROOT).replace('_', '-');
        int separator = normalized.indexOf(':');
        return separator >= 0 ? normalized.substring(separator + 1) : normalized;
    }

    private static Identifier readIdentifier(JsonObject jsonObject, String name) {
        return readIdentifier(jsonObject, name, Identifier.withDefaultNamespace("missingno"));
    }

    private static Identifier readIdentifier(JsonObject jsonObject, String name, Identifier defaultValue) {
        return Identifier.tryParse(getString(jsonObject, name, defaultValue.toString()));
    }

    private static List<Identifier> readIdentifiers(JsonElement element) {
        List<Identifier> identifiers = new ArrayList<>();
        if (element == null || element.isJsonNull()) {
            return identifiers;
        }
        if (element.isJsonObject() && element.getAsJsonObject().has("entries")) {
            return readIdentifiers(element.getAsJsonObject().get("entries"));
        }
        if (element.isJsonArray()) {
            for (JsonElement entry : element.getAsJsonArray()) {
                identifiers.add(Identifier.tryParse(entry.getAsString()));
            }
        } else if (element.isJsonPrimitive()) {
            identifiers.add(Identifier.tryParse(element.getAsString()));
        }
        return identifiers;
    }

    private static List<LegacyWeather> readWeather(JsonElement element) {
        List<LegacyWeather> weathers = new ArrayList<>();
        if (element == null || element.isJsonNull()) {
            return weathers;
        }
        if (element.isJsonObject() && element.getAsJsonObject().has("entries")) {
            return readWeather(element.getAsJsonObject().get("entries"));
        }
        if (element.isJsonArray()) {
            for (JsonElement entry : element.getAsJsonArray()) {
                weathers.add(LegacyWeather.fromString(entry.getAsString()));
            }
        } else if (element.isJsonPrimitive()) {
            weathers.add(LegacyWeather.fromString(element.getAsString()));
        }
        return weathers;
    }

    private static List<RangeEntry> readRanges(JsonElement element) {
        List<RangeEntry> ranges = new ArrayList<>();
        if (element == null || element.isJsonNull()) {
            return ranges;
        }
        if (element.isJsonObject() && element.getAsJsonObject().has("entries")) {
            return readRanges(element.getAsJsonObject().get("entries"));
        }
        if (!element.isJsonArray()) {
            return ranges;
        }
        for (JsonElement entry : element.getAsJsonArray()) {
            if (entry.isJsonArray()) {
                JsonArray range = entry.getAsJsonArray();
                if (range.size() >= 2) {
                    ranges.add(new RangeEntry(range.get(0).getAsFloat(), range.get(1).getAsFloat()));
                }
            } else if (entry.isJsonObject()) {
                JsonObject range = entry.getAsJsonObject();
                ranges.add(new RangeEntry(getFloat(range, "min", 0.0F), getFloat(range, "max", 0.0F)));
            }
        }
        return ranges;
    }

    private static ConditionData<Identifier> readIdentifierCondition(JsonObject jsonObject, String name) {
        JsonElement element = jsonObject.get(name);
        return new ConditionData<>(readIdentifiers(element), readExcludes(element));
    }

    private static ConditionData<LegacyWeather> readWeatherCondition(JsonObject jsonObject, String name) {
        JsonElement element = jsonObject.get(name);
        return new ConditionData<>(readWeather(element), readExcludes(element));
    }

    private static ConditionData<RangeEntry> readRangeCondition(JsonObject jsonObject, String name) {
        JsonElement element = jsonObject.get(name);
        return new ConditionData<>(readRanges(element), readExcludes(element));
    }

    private static boolean readExcludes(JsonElement element) {
        return element != null && element.isJsonObject() && getBoolean(element.getAsJsonObject(), "excludes", false);
    }

    private static UVRange readUvRange(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return new UVRange(0.0F, 0.0F, 1.0F, 1.0F);
        }

        JsonObject uvRange = element.getAsJsonObject();
        return new UVRange(
                getFloat(uvRange, "minU", 0.0F),
                getFloat(uvRange, "minV", 0.0F),
                getFloat(uvRange, "maxU", 1.0F),
                getFloat(uvRange, "maxV", 1.0F)
        );
    }

    private static RGBA readColor(JsonElement element, RGBA defaultValue) {
        if (element == null || !element.isJsonObject()) {
            return defaultValue;
        }

        JsonObject color = element.getAsJsonObject();
        return new RGBA(
                getFloat(color, "red", defaultValue.getRed()),
                getFloat(color, "green", defaultValue.getGreen()),
                getFloat(color, "blue", defaultValue.getBlue()),
                getFloat(color, "alpha", defaultValue.getAlpha())
        );
    }

    private static Blend readBlend(JsonObject jsonObject, Blend defaultValue) {
        JsonElement element = jsonObject.get("blend");
        if (element == null || element.isJsonNull()) {
            return defaultValue;
        }
        return Blend.CODEC.decode(JsonOps.INSTANCE, element).getOrThrow().getFirst();
    }

    private static Vector3f readVector3f(JsonObject jsonObject, String name) {
        if (!jsonObject.has(name) || !jsonObject.get(name).isJsonArray()) {
            return new Vector3f();
        }
        return readVector3f(jsonObject.get(name));
    }

    private static Vector3f readVector3f(JsonElement element) {
        if (element == null || !element.isJsonArray()) {
            return new Vector3f();
        }
        JsonArray array = element.getAsJsonArray();
        return new Vector3f(
                array.size() > 0 ? array.get(0).getAsFloat() : 0.0F,
                array.size() > 1 ? array.get(1).getAsFloat() : 0.0F,
                array.size() > 2 ? array.get(2).getAsFloat() : 0.0F
        );
    }

    private static Vector3i readVector3i(JsonObject jsonObject, String name) {
        if (!jsonObject.has(name) || !jsonObject.get(name).isJsonArray()) {
            return new Vector3i();
        }

        JsonArray array = jsonObject.getAsJsonArray(name);
        return new Vector3i(
                array.size() > 0 ? array.get(0).getAsInt() : 0,
                array.size() > 1 ? array.get(1).getAsInt() : 0,
                array.size() > 2 ? array.get(2).getAsInt() : 0
        );
    }

    private static JsonObject getObject(JsonObject jsonObject, String name) {
        return jsonObject.has(name) && jsonObject.get(name).isJsonObject() ? jsonObject.getAsJsonObject(name) : null;
    }

    private static String getString(JsonObject jsonObject, String name, String defaultValue) {
        if (jsonObject.has(name) && jsonObject.get(name).isJsonPrimitive()) {
            return jsonObject.get(name).getAsString();
        }
        return defaultValue;
    }

    private static int getInt(JsonObject jsonObject, String name, int defaultValue) {
        if (jsonObject.has(name) && jsonObject.get(name).isJsonPrimitive()) {
            try {
                return jsonObject.get(name).getAsInt();
            } catch (NumberFormatException ignored) {
            }
        }
        return defaultValue;
    }

    private static long getLong(JsonObject jsonObject, String name, long defaultValue) {
        if (jsonObject.has(name) && jsonObject.get(name).isJsonPrimitive()) {
            try {
                return jsonObject.get(name).getAsLong();
            } catch (NumberFormatException ignored) {
            }
        }
        return defaultValue;
    }

    private static float getFloat(JsonObject jsonObject, String name, float defaultValue) {
        if (jsonObject.has(name) && jsonObject.get(name).isJsonPrimitive()) {
            try {
                return jsonObject.get(name).getAsFloat();
            } catch (NumberFormatException ignored) {
            }
        }
        return defaultValue;
    }

    private static boolean getBoolean(JsonObject jsonObject, String name, boolean defaultValue) {
        if (jsonObject.has(name) && jsonObject.get(name).isJsonPrimitive()) {
            return jsonObject.get(name).getAsBoolean();
        }
        return defaultValue;
    }

    private record ConditionData<T>(List<T> entries, boolean excludes) {
    }
}
