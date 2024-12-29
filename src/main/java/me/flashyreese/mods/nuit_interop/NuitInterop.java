package me.flashyreese.mods.nuit_interop;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import io.github.amerebagatelle.mods.nuit.SkyboxManager;
import io.github.amerebagatelle.mods.nuit.api.skyboxes.Skybox;
import io.github.amerebagatelle.mods.nuit.components.MinMaxEntry;
import me.flashyreese.mods.nuit_interop.client.config.NuitInteropConfig;
import me.flashyreese.mods.nuit_interop.client.config.NuitInteropMode;
import me.flashyreese.mods.nuit_interop.mixin.SkyboxManagerAccessor;
import me.flashyreese.mods.nuit_interop.sky.OptiFineCustomSky;
import me.flashyreese.mods.nuit_interop.utils.BlenderUtil;
import me.flashyreese.mods.nuit_interop.utils.ResourceManagerHelper;
import me.flashyreese.mods.nuit_interop.utils.Utils;
import net.minecraft.ResourceLocationException;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NuitInterop {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String OPTIFINE_SKY_PARENT = "optifine/sky";
    private static final Pattern OPTIFINE_SKY_PATTERN = Pattern.compile("optifine/sky/(?<world>\\w+)/(?<name>\\w+).properties$");
    private static final String MCPATCHER_SKY_PARENT = "mcpatcher/sky";
    private static final Pattern MCPATCHER_SKY_PATTERN = Pattern.compile("mcpatcher/sky/(?<world>\\w+)/(?<name>\\w+).properties$");
    private static NuitInterop INSTANCE;
    private final Map<ResourceLocation, String> convertedSkyMap = new HashMap<>();
    private final Logger logger = LoggerFactory.getLogger("Nuit-Interop");

    public static NuitInterop getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new NuitInterop();
        }

        return INSTANCE;
    }

    public Map<ResourceLocation, String> getConvertedSkyMap() {
        return convertedSkyMap;
    }

    public void inject(ResourceManager manager) {
        this.convertedSkyMap.clear();
        if (NuitInteropConfig.INSTANCE.interoperability) {
            if (NuitInteropConfig.INSTANCE.preferNuitNative) {
                if (!((SkyboxManagerAccessor) SkyboxManager.getInstance()).getSkyboxes().isEmpty()) {
                    this.logger.info("Nuit Native is preferred and existing skyboxes already detected! No longer converting MCP/OptiFine formats!");
                    return;
                }
            } else {
                this.logger.warn("Nuit-Interop is preventing native Nuit resource packs from loading!");
            }

            this.logger.warn("Removing existing Nuit skies...");
            this.logger.warn("Nuit-Interop is converting MCPatcher/OptiFine custom skies resource packs! Any visual bugs are likely caused by Nuit-Interop. Please do not report these issues to Nuit nor Resource Pack creators!");
            SkyboxManager.getInstance().clearSkyboxes();
            this.logger.info("Looking for OptiFine/MCPatcher Skies...");
            this.convert(new ResourceManagerHelper(manager));
        }
    }

    public void convert(ResourceManagerHelper managerAccessor) {
        if (NuitInteropConfig.INSTANCE.processOptiFine) {
            this.convertNamespace(managerAccessor, OPTIFINE_SKY_PARENT, OPTIFINE_SKY_PATTERN);
        }

        if (NuitInteropConfig.INSTANCE.processMCPatcher) {
            this.convertNamespace(managerAccessor, MCPATCHER_SKY_PARENT, MCPATCHER_SKY_PATTERN);
        }
    }

    /**
     * Converts a specific namespace
     *
     * @param resourceManagerHelper The resource manager helper
     * @param skyParent             The parent namespace
     * @param pattern               The pattern for namespace
     */
    private void convertNamespace(ResourceManagerHelper resourceManagerHelper, String skyParent, Pattern pattern) {
        AtomicBoolean hasGeneratedOverworldSky = new AtomicBoolean();
        AtomicBoolean hasGeneratedEndSky = new AtomicBoolean();
        final JsonArray overworldLayers = new JsonArray();
        final JsonArray endLayers = new JsonArray();
        resourceManagerHelper.searchIn(skyParent)
                .filter(id -> id.getPath().endsWith(".properties"))
                .sorted(Comparator.comparing(ResourceLocation::getPath, (id1, id2) -> {
                    // Sorting for older versions of Nuit without priority
                    Matcher matcherId1 = pattern.matcher(id1);
                    Matcher matcherId2 = pattern.matcher(id2);
                    if (matcherId1.find() && matcherId2.find()) {
                        int id1No = Utils.parseInt(matcherId1.group("name").replace("sky", ""), -1);
                        int id2No = Utils.parseInt(matcherId2.group("name").replace("sky", ""), -1);
                        if (id1No >= 0 && id2No >= 0) {
                            return id1No - id2No;
                        }
                    }
                    return 0;
                }))
                .forEach(id -> {
                    Matcher matcher = pattern.matcher(id.getPath());
                    if (matcher.find()) {
                        String world = matcher.group("world");
                        String name = matcher.group("name");
                        if (world == null || name == null) {
                            return;
                        }

                        if (name.equals("moon_phases") || name.equals("sun")) {
                            this.logger.info("Skipping {}, moon_phases/sun aren't supported!", id);
                            return;
                        }

                        this.logger.info("Converting {} to Nuit Format...", id);

                        InputStream inputStream = resourceManagerHelper.getInputStream(id);
                        if (inputStream == null) {
                            if (NuitInteropConfig.INSTANCE.debugMode) {
                                this.logger.error("Error trying to read namespaced identifier: {}", id);
                            }

                            return;
                        }

                        Properties properties = new Properties();
                        try {
                            properties.load(inputStream);
                        } catch (IOException e) {
                            if (NuitInteropConfig.INSTANCE.debugMode) {
                                this.logger.error("Error trying to read namespaced identifier: {}", id);
                            }

                            return;
                        } finally {
                            try {
                                inputStream.close();
                            } catch (IOException e) {
                                if (NuitInteropConfig.INSTANCE.debugMode) {
                                    this.logger.error("Error trying to close input stream at namespaced identifier: {}", id);
                                }
                            }
                        }

                        if (NuitInteropConfig.INSTANCE.mode == NuitInteropMode.CONVERSION) {
                            if (!hasGeneratedOverworldSky.get() && world.equals("world0")) {
                                this.generateSky("minecraft:overworld", "overworld");
                                this.generateOverworldDecorations();
                                hasGeneratedOverworldSky.set(true);
                            }

                            if (!hasGeneratedEndSky.get() && world.equals("world1")) {
                                this.generateSky("minecraft:the_end", "end");
                                hasGeneratedEndSky.set(true);
                            }

                            this.convert(resourceManagerHelper, skyParent, name, id, properties, world);
                        } else if (NuitInteropConfig.INSTANCE.mode == NuitInteropMode.NATIVE) {
                            JsonObject json = Utils.convertOptiFineSkyProperties(resourceManagerHelper, properties, id);
                            if (json != null) {
                                if (world.equals("world0")) {
                                    overworldLayers.add(json);
                                } else if (world.equals("world1")) {
                                    endLayers.add(json);
                                }
                            }
                        }

                    }
                });

        if (NuitInteropConfig.INSTANCE.mode == NuitInteropMode.NATIVE) {
            if (!overworldLayers.isEmpty()) {
                JsonObject overworldJson = new JsonObject();
                overworldJson.addProperty("schemaVersion", 2);
                overworldJson.addProperty("type", "optifine-custom-sky");
                overworldJson.add("layers", overworldLayers);
                overworldJson.addProperty("world", "minecraft:overworld");

                Skybox skybox = OptiFineCustomSky.CODEC.decode(JsonOps.INSTANCE, overworldJson).getOrThrow().getFirst();
                SkyboxManager.getInstance().addSkybox(ResourceLocation.fromNamespaceAndPath("nuit-interop", "native-optifine-custom-sky-overworld"), skybox);
            }

            if (!endLayers.isEmpty()) {
                JsonObject endJson = new JsonObject();
                endJson.addProperty("schemaVersion", 2);
                endJson.addProperty("type", "optifine-custom-sky");
                endJson.add("layers", endLayers);
                endJson.addProperty("world", "minecraft:the_end");

                Skybox skybox = OptiFineCustomSky.CODEC.decode(JsonOps.INSTANCE, endJson).getOrThrow().getFirst();
                SkyboxManager.getInstance().addSkybox(ResourceLocation.fromNamespaceAndPath("nuit-interop", "native-optifine-custom-sky-end"), skybox);
            }
        }
    }

    /**
     * Converts one MCPatcher file to Nuit format.
     *
     * @param propertiesId The OptiFine metadata file identifier.
     * @param properties   The MCPatcher properties file.
     * @param world        The world name
     */
    private void convert(ResourceManagerHelper resourceManagerHelper, String skyParent, String skyName, ResourceLocation propertiesId, Properties properties, String world) {
        // Blend
        JsonObject blend = new JsonObject();
        String blendType = properties.getProperty("blend", "add");
        blend.addProperty("type", "custom");
        //blend.addProperty("type", blendType);
        blend.add("blender", GSON.toJsonTree(BlenderUtil.getInstance().BLEND_MAP.getOrDefault(blendType, BlenderUtil.getInstance().BLEND_MAP.get("add"))).getAsJsonObject());

        ResourceLocation textureId;
        String source = properties.getProperty("source");
        String namespace;
        String path;
        if (source == null) {
            namespace = propertiesId.getNamespace();
            path = String.format("%s/%s/%s.png", skyParent, world, skyName);
        } else {
            if (source.startsWith("./")) {
                namespace = propertiesId.getNamespace();
                path = skyParent + String.format("/%s/%s", world, source.substring(2));
            } else {
                String[] parts = source.split("/", 3);
                if (parts.length == 3 && parts[0].equals("assets")) {
                    namespace = parts[1];
                    path = parts[2];
                } else {
                    ResourceLocation sourceResourceLocation = ResourceLocation.tryParse(source);
                    if (sourceResourceLocation != null) {
                        namespace = sourceResourceLocation.getNamespace();
                        path = sourceResourceLocation.getPath();
                    } else {
                        this.logger.error("Invalid source format: {}", source);
                        return;
                    }
                }
            }
        }
        try {
            textureId = ResourceLocation.fromNamespaceAndPath(namespace, path);
        } catch (ResourceLocationException e) {
            this.logger.error("Illegal character in namespaced identifier: {}", source);
            return;
        }
        InputStream textureInputStream = resourceManagerHelper.getInputStream(textureId);
        if (textureInputStream == null) {
            this.logger.error("Unable to find/read namespaced identifier: {}", textureId);
            return;
        }

        // Properties
        JsonObject propertiesObject = new JsonObject();
        this.processProperties(propertiesObject, properties, skyName);

        // Conditions
        JsonObject conditionsObject = new JsonObject();
        this.processConditions(conditionsObject, properties, world);

        // Metadata
        JsonObject json = new JsonObject();
        json.addProperty("schemaVersion", 2);
        json.addProperty("type", "single-sprite-square-textured");
        json.addProperty("texture", textureId.toString());
        json.add("blend", blend);
        json.add("properties", propertiesObject);
        json.add("conditions", conditionsObject);

        if (NuitInteropConfig.INSTANCE.debugMode) {
            this.logger.info("Output for {} conversion:\n{}", propertiesId, GSON.toJson(json));
        }

        SkyboxManager.getInstance().addSkybox(propertiesId, json);
        this.convertedSkyMap.put(propertiesId, GSON.toJson(json));
        this.logger.info("Converted & Added Skybox from {}!", propertiesId);
    }

    private void generateSky(String dimension, String type) {
        // Fade
        JsonObject fade = new JsonObject();
        fade.addProperty("alwaysOn", true);

        // Worlds
        JsonArray worlds = new JsonArray();
        worlds.add(dimension);

        // Conditions
        JsonObject conditions = new JsonObject();
        conditions.add("worlds", worlds);

        // Properties
        JsonObject properties = new JsonObject();
        properties.addProperty("priority", Integer.MIN_VALUE);
        properties.add("fade", fade);

        // Metadata
        JsonObject json = new JsonObject();
        json.addProperty("schemaVersion", 2);
        json.addProperty("type", type);
        json.add("properties", properties);
        json.add("conditions", conditions);

        if (NuitInteropConfig.INSTANCE.debugMode) {
            this.logger.info("Generated {} skybox:\n{}", dimension, GSON.toJson(json));
        }

        ResourceLocation resourceLocation = ResourceLocation.fromNamespaceAndPath("nuit-interop", type);
        SkyboxManager.getInstance().addSkybox(resourceLocation, json);
        this.convertedSkyMap.put(resourceLocation, GSON.toJson(json));
        this.logger.info("Added generated {} skybox!", dimension);
    }

    private void generateOverworldDecorations() {
        // Blend
        JsonObject blend = new JsonObject();
        blend.addProperty("type", "replace"); // apparently using custom and the same exact method for mono color just implodes? this makes no sense
        blend.add("blender", GSON.toJsonTree(BlenderUtil.getInstance().BLEND_MAP.get("replace")).getAsJsonObject());

        // Fade
        JsonObject fade = new JsonObject();
        fade.addProperty("alwaysOn", true);

        // Worlds
        JsonArray worlds = new JsonArray();
        worlds.add("minecraft:overworld");

        // Weather
        JsonArray weather = new JsonArray();
        weather.add("clear");
        weather.add("snow");

        // Conditions
        JsonObject conditions = new JsonObject();
        conditions.add("worlds", worlds);
        conditions.add("weather", weather);

        // Properties
        JsonObject properties = new JsonObject();
        properties.addProperty("priority", Integer.MAX_VALUE);
        properties.add("fade", fade);

        // Decorations
        JsonObject decorations = new JsonObject();
        decorations.addProperty("showSun", true);
        decorations.addProperty("showMoon", true);
        decorations.addProperty("showStars", true);

        // Metadata
        JsonObject json = new JsonObject();
        json.addProperty("schemaVersion", 2);
        json.addProperty("type", "monocolor");
        json.add("blend", blend);
        json.add("properties", properties);
        json.add("conditions", conditions);
        json.add("decorations", decorations);
        if (NuitInteropConfig.INSTANCE.debugMode) {
            this.logger.info("Generated Overworld decorations:\n{}", GSON.toJson(json));
        }

        ResourceLocation resourceLocation = ResourceLocation.fromNamespaceAndPath("nuit-interop", "overworld-decorations");
        SkyboxManager.getInstance().addSkybox(resourceLocation, json);
        this.convertedSkyMap.put(resourceLocation, GSON.toJson(json));
        this.logger.info("Added generated Overworld decorations!");
    }

    /**
     * Converts MCPatcher Sky Properties to Nuit properties
     *
     * @param json       The properties object for Nuit
     * @param properties The sky properties
     */
    private void processProperties(JsonObject json, Properties properties, String skyName) {
        // Adds priority
        String skyNumberString = skyName.replace("sky", "");
        int skyNumber = Utils.parseInt(skyNumberString, 0);

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

        JsonArray jsonAxis = new JsonArray();
        if (properties.containsKey("axis")) {
            String[] axis = properties.getProperty("axis").trim().replaceAll(" +", " ").split(" ");
            List<String> rev = Arrays.asList(axis);
            Collections.reverse(rev);
            axis = rev.toArray(axis);
            for (String a : axis) {
                jsonAxis.add(Float.parseFloat(a) * 90);
            }
        } else {
            //Default South
            jsonAxis.add(0f);
            jsonAxis.add(90f);
            jsonAxis.add(0f);
        }

        // Speed -> Rotation Speed Y
        float speed = Float.parseFloat(properties.getProperty("speed", "1")) * -1;

        // Rotation
        JsonObject rotation = new JsonObject();
        rotation.add("axis", jsonAxis);
        rotation.addProperty("rotationSpeedY", speed);

        // Transition -> Transition In/Out Duration
        int transitionDuration = Integer.parseInt(properties.getProperty("transition", "1")) * 20;

        // Properties Metadata
        if (skyNumberString.equals(String.valueOf(skyNumber)))
            json.addProperty("priority", skyNumber);
        json.add("fade", fade);
        json.add("rotation", rotation);
        json.addProperty("transitionInDuration", transitionDuration);
        json.addProperty("transitionOutDuration", transitionDuration);
    }


    /**
     * Converts properties into conditions object
     *
     * @param json       The conditions object
     * @param properties The sky properties
     * @param world      The world string
     */
    private void processConditions(JsonObject json, Properties properties, String world) {
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
                jsonWeather.add("snow");
            }
            json.add("weather", jsonWeather);
        } else {
            JsonArray jsonWeather = new JsonArray();
            jsonWeather.add("clear");
            jsonWeather.add("snow");
            json.add("weather", jsonWeather);
        }

        // Biomes
        if (properties.containsKey("biomes")) {
            String[] biomes = properties.getProperty("biomes").split(" ");
            if (biomes.length > 0) {
                JsonArray jsonBiomes = new JsonArray();
                for (String biome : biomes) {
                    jsonBiomes.add(biome);
                }
                json.add("biomes", jsonBiomes);
            }
        }

        // World location -> worlds
        JsonArray worlds = new JsonArray();
        worlds.add(world.equals("world0") ? "minecraft:overworld" : world.equals("world1") ? "minecraft:the_end" : world);
        json.add("worlds", worlds);

        // Heights -> yRanges
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
                json.add("yRanges", jsonYRanges);
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
                json.add("loop", loopObject);
            }
        }
    }
}
