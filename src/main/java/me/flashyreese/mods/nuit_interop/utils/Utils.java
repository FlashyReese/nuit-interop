package me.flashyreese.mods.nuit_interop.utils;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.amerebagatelle.mods.nuit.components.MinMaxEntry;
import net.minecraft.util.Identifier;
import net.minecraft.util.InvalidIdentifierException;

import java.io.InputStream;
import java.util.*;
import java.util.regex.Pattern;

public final class Utils {
    private static final Pattern OPTIFINE_RANGE_SEPARATOR = Pattern.compile("(\\d|\\))-(\\d|\\()");

    public static JsonObject convertOptiFineSkyProperties(ResourceManagerHelper resourceManagerHelper, Properties properties, Identifier propertiesIdentifier) {
        JsonObject jsonObject = new JsonObject();

        Identifier sourceTexture = parseSourceTexture(properties.getProperty("source", null), resourceManagerHelper, propertiesIdentifier);

        if (sourceTexture == null) {
            return null;
        }
        jsonObject.addProperty("source", sourceTexture.toString());

        // Blend
        if (properties.containsKey("blend")) {
            String blend = properties.getProperty("blend");
            if (blend != null) {
                jsonObject.addProperty("blend", blend);
            }
        }

        // Convert fade
        JsonObject fade = new JsonObject();
        if (properties.containsKey("startFadeIn") && properties.containsKey("endFadeIn") && properties.containsKey("endFadeOut")) {
            int startFadeIn = Objects.requireNonNull(Utils.toTickTime(properties.getProperty("startFadeIn"))).intValue();
            int endFadeIn = Objects.requireNonNull(Utils.toTickTime(properties.getProperty("endFadeIn"))).intValue();
            int endFadeOut = Objects.requireNonNull(Utils.toTickTime(properties.getProperty("endFadeOut"))).intValue();
            int startFadeOut;
            if (properties.containsKey("startFadeOut")) {
                startFadeOut = Objects.requireNonNull(Utils.toTickTime(properties.getProperty("startFadeOut"))).intValue();
            } else {
                startFadeOut = endFadeOut - (endFadeIn - startFadeIn);
                if (startFadeIn <= startFadeOut && endFadeIn >= startFadeOut) {
                    startFadeOut = endFadeOut;
                }
            }
            fade.addProperty("startFadeIn", Utils.normalizeTickTime(startFadeIn));
            fade.addProperty("endFadeIn", Utils.normalizeTickTime(endFadeIn));
            fade.addProperty("startFadeOut", Utils.normalizeTickTime(startFadeOut));
            fade.addProperty("endFadeOut", Utils.normalizeTickTime(endFadeOut));
        } else {
            fade.addProperty("alwaysOn", true);
        }
        jsonObject.add("fade", fade);

        // Speed
        if (properties.containsKey("speed")) {
            float speed = Float.parseFloat(properties.getProperty("speed", "1")) * -1;
            jsonObject.addProperty("speed", speed);
        }

        // Rotation
        if (properties.containsKey("rotate")) {
            boolean rotate = Boolean.parseBoolean(properties.getProperty("rotate", "true"));
            jsonObject.addProperty("rotate", rotate);
        }

        // Transition
        if (properties.containsKey("transition")) {
            int transition = Integer.parseInt(properties.getProperty("transition", "1"));
            jsonObject.addProperty("transition", transition);
        }

        // Axis
        JsonArray jsonAxis = new JsonArray();
        if (properties.containsKey("axis")) {
            String[] axis = properties.getProperty("axis").trim().replaceAll(" +", " ").split(" ");
            List<String> rev = Arrays.asList(axis);
            axis = rev.toArray(axis);
            for (String a : axis) {
                jsonAxis.add(Float.parseFloat(a));
            }
            jsonObject.add("axis", jsonAxis);
        }

        // Weather
        if (properties.containsKey("weather")) {
            String[] weathers = properties.getProperty("weather").split(" ");
            JsonArray jsonWeather = new JsonArray();
            if (weathers.length > 0) {
                for (String weather : weathers) {
                    jsonWeather.add(weather);
                }
            } else {
                jsonWeather.add("clear");
            }
            jsonObject.add("weathers", jsonWeather);
        }

        // Biomes
        if (properties.containsKey("biomes")) {
            String biomesString = properties.getProperty("biomes");
            if (biomesString.startsWith("!")) {
                jsonObject.addProperty("biomeInclusion", false);
                biomesString = biomesString.substring(1);
            }

            String[] biomes = biomesString.split(" ");
            if (biomes.length > 0) {
                JsonArray jsonBiomes = new JsonArray();
                for (String biome : biomes) {
                    jsonBiomes.add(biome);
                }
                jsonObject.add("biomes", jsonBiomes);
            }
        }

        // Heights
        if (properties.containsKey("heights")) {
            List<MinMaxEntry> minMaxEntries = Utils.parseMinMaxEntriesNegative(properties.getProperty("heights"));

            if (!minMaxEntries.isEmpty()) {
                JsonArray jsonYRanges = new JsonArray();
                minMaxEntries.forEach(minMaxEntry -> {
                    JsonObject minMax = new JsonObject();
                    minMax.addProperty("min", minMaxEntry.min());
                    minMax.addProperty("max", minMaxEntry.max());
                    jsonYRanges.add(minMax);
                });
                jsonObject.add("heights", jsonYRanges);
            }
        }

        // Days Loop -> Loop
        if (properties.containsKey("days")) {
            List<MinMaxEntry> minMaxEntries = Utils.parseMinMaxEntries(properties.getProperty("days"));

            if (!minMaxEntries.isEmpty()) {
                JsonObject loopObject = new JsonObject();

                JsonArray loopRange = new JsonArray();
                minMaxEntries.forEach(minMaxEntry -> {
                    JsonObject minMax = new JsonObject();
                    minMax.addProperty("min", minMaxEntry.min());
                    minMax.addProperty("max", minMaxEntry.max());
                    loopRange.add(minMax);
                });

                int value = 8;
                if (properties.containsKey("daysLoop")) {
                    value = Utils.parseInt(properties.getProperty("daysLoop"), 8);
                }
                loopObject.addProperty("days", value);

                loopObject.add("ranges", loopRange);

                jsonObject.add("loop", loopObject);
            }
        }

        return jsonObject;
    }

    public static Identifier parseSourceTexture(String source, ResourceManagerHelper resourceManagerHelper, Identifier propertiesId) {
        Identifier textureId;
        String namespace;
        String path;
        if (source == null) {
            namespace = propertiesId.getNamespace();
            path = propertiesId.getPath().replace(".properties", ".png");
        } else {
            if (source.startsWith("./")) {
                namespace = propertiesId.getNamespace();
                String fileName = propertiesId.getPath().split("/")[propertiesId.getPath().split("/").length - 1];
                path = propertiesId.getPath().replace(fileName, source.substring(2));
            } else {
                String[] parts = source.split("/", 3);
                if (parts.length == 3 && parts[0].equals("assets")) {
                    namespace = parts[1];
                    path = parts[2];
                } else {
                    Identifier sourceIdentifier = Identifier.tryParse(source);
                    if (sourceIdentifier != null) {
                        namespace = sourceIdentifier.getNamespace();
                        path = sourceIdentifier.getPath();
                    } else {
                        return null;
                    }
                }
            }
        }
        try {
            textureId = Identifier.of(namespace, path);
        } catch (InvalidIdentifierException e) {
            return null;
        }
        InputStream textureInputStream = resourceManagerHelper.getInputStream(textureId);
        if (textureInputStream == null) {
            return null;
        }
        return textureId;
    }

    public static Number toTickTime(String time) {
        String[] parts = time.split(":");
        if (parts.length != 2)
            return null;
        int h = Integer.parseInt(parts[0]);
        int m = Integer.parseInt(parts[1]);
        return h * 1000 + (m / 0.06F) - 6000;
    }

    public static int normalizeTickTime(int tickTime) {
        int result = tickTime % 24000;
        if (result < 0) {
            result += 24000;
        }
        return result;
    }

    public static List<MinMaxEntry> parseMinMaxEntries(String str) {
        List<MinMaxEntry> minMaxEntries = new ArrayList<>();
        String[] strings = str.split(" ,");

        for (String s : strings) {
            MinMaxEntry minMaxEntry = parseMinMaxEntry(s);

            if (minMaxEntry != null) {
                minMaxEntries.add(minMaxEntry);
            }
        }

        return minMaxEntries;
    }

    private static MinMaxEntry parseMinMaxEntry(String str) {
        if (str != null) {
            if (str.contains("-")) {
                String[] strings = str.split("-");
                if (strings.length == 2) {
                    int min = parseInt(strings[0], -1);
                    int max = parseInt(strings[1], -1);
                    if (min >= 0 && max >= 0) {
                        return new MinMaxEntry(min, max);
                    }
                }
            } else {
                int value = parseInt(str, -1);

                if (value >= 0) {
                    return new MinMaxEntry(value, value);
                }
            }
        }

        return null;
    }

    public static List<MinMaxEntry> parseMinMaxEntriesNegative(String str) {
        List<MinMaxEntry> minMaxEntries = new ArrayList<>();
        String[] strings = str.split(" ,");

        for (String s : strings) {
            MinMaxEntry minMaxEntry = parseMinMaxEntryNegative(s);

            if (minMaxEntry != null) {
                minMaxEntries.add(minMaxEntry);
            }
        }

        return minMaxEntries;
    }

    private static MinMaxEntry parseMinMaxEntryNegative(String str) {
        if (str != null) {
            String s = OPTIFINE_RANGE_SEPARATOR.matcher(str).replaceAll("$1=$2");

            if (s.contains("=")) {
                String[] strings = s.split("=");

                if (strings.length == 2) {
                    int j = parseInt(stripBrackets(strings[0]), Integer.MIN_VALUE);
                    int k = parseInt(stripBrackets(strings[1]), Integer.MIN_VALUE);

                    if (j != Integer.MIN_VALUE && k != Integer.MIN_VALUE) {
                        int min = Math.min(j, k);
                        int max = Math.max(j, k);
                        return new MinMaxEntry(min, max);
                    }
                }
            } else {
                int i = parseInt(stripBrackets(str), Integer.MIN_VALUE);

                if (i != Integer.MIN_VALUE) {
                    return new MinMaxEntry(i, i);
                }
            }
        }
        return null;
    }

    private static String stripBrackets(String str) {
        return str.startsWith("(") && str.endsWith(")") ? str.substring(1, str.length() - 1) : str;
    }

    public static int parseInt(String str, int defaultValue) {
        try {
            return Integer.parseInt(str);
        } catch (Exception e) {
            return defaultValue;
        }
    }
}
