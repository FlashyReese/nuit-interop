package me.flashyreese.mods.nuit_interop;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.Strictness;
import me.flashyreese.mods.nuit.api.NuitApi;
import me.flashyreese.mods.nuit.api.skyboxes.Skybox;
import me.flashyreese.mods.nuit.api.skyboxes.SkyboxType;
import me.flashyreese.mods.nuit_interop.config.NuitInteropConfig;
import me.flashyreese.mods.nuit_interop.fabricskyboxes.LegacyFabricSkyBoxesParser;
import me.flashyreese.mods.nuit_interop.optifine.OptiFineSkyPropertiesConverter;
import me.flashyreese.mods.nuit_interop.sky.OptiFineCustomSky;
import me.flashyreese.mods.nuit_interop.utils.ResourceManagerHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NuitInterop {
    public static final String MOD_ID = "nuit_interop";
    private static final Logger LOGGER = LoggerFactory.getLogger("Nuit-Interop");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeNulls().setStrictness(Strictness.LENIENT).create();

    // Skybox conversion paths and patterns
    private static final String FABRIC_SKYBOXES_SKY_PARENT = "sky";
    private static final String OPTIFINE_SKY_PARENT = "optifine/sky";
    private static final Pattern OPTIFINE_SKY_PATTERN = Pattern.compile("optifine/sky/(?<world>world-?\\d+)/(?<name>[^/]+).properties$");
    private static final String MCPATCHER_SKY_PARENT = "mcpatcher/sky";
    private static final Pattern MCPATCHER_SKY_PATTERN = Pattern.compile("mcpatcher/sky/(?<world>world-?\\d+)/(?<name>[^/]+).properties$");

    private static final SkyboxType<OptiFineCustomSky> OPTIFINE_CUSTOM_SKY_SKYBOX_TYPE;

    static {
        OPTIFINE_CUSTOM_SKY_SKYBOX_TYPE = new SkyboxType<>(Identifier.tryBuild(MOD_ID, "optifine-custom-sky"), 1, OptiFineCustomSky.CODEC);
    }

    public static void init() {
        NuitApi.registerSkyboxType(OPTIFINE_CUSTOM_SKY_SKYBOX_TYPE);
    }

    private static NuitInterop instance;

    public static NuitInterop getInstance() {
        if (instance == null) {
            instance = new NuitInterop();
        }
        return instance;
    }

    public void inject(ResourceManager manager, Map<Identifier, JsonObject> skyboxJson, Map<Identifier, Skybox> convertedSkyboxes) {
        convertedSkyboxes.clear();
        if (!NuitInteropConfig.INSTANCE.interoperability) return;

        if (NuitInteropConfig.INSTANCE.preferNuitNative && !skyboxJson.isEmpty()) {
            LOGGER.info("Nuit Native is preferred and existing skyboxes already detected! Skipping conversion.");
            return;
        } else if (!skyboxJson.isEmpty()) {
            LOGGER.warn("Nuit-Interop is preventing native Nuit resource packs from loading!");
        }

        LOGGER.warn("Removing existing Nuit skies and converting legacy skyboxes...");
        LOGGER.warn("Visual bugs may occur; do not report these issues to Nuit or resource pack creators!");

        skyboxJson.clear();
        convert(skyboxJson, convertedSkyboxes, new ResourceManagerHelper(manager));
    }

    public void addConvertedSkyboxes(Map<Identifier, Skybox> convertedSkyboxes) {
        if (convertedSkyboxes.isEmpty()) {
            return;
        }

        try {
            NuitApi nuitApi = NuitApi.getInstance();
            convertedSkyboxes.forEach(nuitApi::addSkybox);
        } finally {
            convertedSkyboxes.clear();
        }
    }

    private void convert(Map<Identifier, JsonObject> skyboxJson, Map<Identifier, Skybox> convertedSkyboxes, ResourceManagerHelper managerHelper) {
        if (NuitInteropConfig.INSTANCE.processFabricSkyBoxes) {
            convertFabricSkyBoxes(convertedSkyboxes, managerHelper);
        }
        if (NuitInteropConfig.INSTANCE.processOptiFine) {
            convertNamespace(skyboxJson, managerHelper, OPTIFINE_SKY_PARENT, OPTIFINE_SKY_PATTERN);
        }
        if (NuitInteropConfig.INSTANCE.processMCPatcher) {
            convertNamespace(skyboxJson, managerHelper, MCPATCHER_SKY_PARENT, MCPATCHER_SKY_PATTERN);
        }
    }

    private void convertFabricSkyBoxes(Map<Identifier, Skybox> convertedSkyboxes, ResourceManagerHelper managerHelper) {
        managerHelper.searchIn(FABRIC_SKYBOXES_SKY_PARENT)
                .filter(id -> id.getPath().endsWith(".json"))
                .sorted(Comparator.comparing(Identifier::toString))
                .forEach(id -> this.processFabricSkyBox(convertedSkyboxes, managerHelper, id));
    }

    private void processFabricSkyBox(Map<Identifier, Skybox> convertedSkyboxes, ResourceManagerHelper managerHelper, Identifier id) {
        try (InputStream inputStream = managerHelper.getInputStream(id)) {
            if (inputStream == null) {
                return;
            }

            JsonObject json = GSON.fromJson(new InputStreamReader(inputStream), JsonObject.class);
            if (json == null) {
                return;
            }

            Optional<Skybox> skybox = LegacyFabricSkyBoxesParser.parse(id, json);
            if (skybox.isEmpty()) {
                return;
            }

            LOGGER.info("Loading legacy FabricSkyBoxes skybox {}...", id);
            convertedSkyboxes.put(id, skybox.get());
        } catch (Exception e) {
            if (NuitInteropConfig.INSTANCE.debugMode) {
                LOGGER.error("Error converting legacy FabricSkyBoxes skybox: {}", id, e);
            }
        }
    }

    private void convertNamespace(Map<Identifier, JsonObject> skyboxJson, ResourceManagerHelper managerHelper, String skyParent, Pattern pattern) {
        JsonArray netherLayers = new JsonArray();
        JsonArray overworldLayers = new JsonArray();
        JsonArray endLayers = new JsonArray();

        managerHelper.searchIn(skyParent)
                .filter(id -> id.getPath().endsWith(".properties"))
                .sorted(Comparator.comparing(Identifier::getPath, (id1, id2) -> this.compareSkyboxIds(id1, id2, pattern)))
                .forEach(id -> processSkybox(managerHelper, id, pattern, netherLayers, overworldLayers, endLayers));

        if (!netherLayers.isEmpty()) {
            createAndAddSkybox(skyboxJson, "minecraft:the_nether", "native-optifine-custom-sky-nether", netherLayers);
        }
        if (!overworldLayers.isEmpty()) {
            createAndAddSkybox(skyboxJson, "minecraft:overworld", "native-optifine-custom-sky-overworld", overworldLayers);
        }
        if (!endLayers.isEmpty()) {
            createAndAddSkybox(skyboxJson, "minecraft:the_end", "native-optifine-custom-sky-end", endLayers);
        }
    }

    private int compareSkyboxIds(String id1, String id2, Pattern pattern) {
        Matcher matcherId1 = pattern.matcher(id1);
        Matcher matcherId2 = pattern.matcher(id2);

        if (matcherId1.find() && matcherId2.find()) {
            int id1No = parseSkyLayerId(matcherId1.group("name"));
            int id2No = parseSkyLayerId(matcherId2.group("name"));

            if (id1No >= 0 && id2No >= 0) {
                return Integer.compare(id1No, id2No);
            }
        }
        return 0;
    }

    private static int parseSkyLayerId(String name) {
        if (name == null || !name.matches("sky\\d+")) {
            return -1;
        }

        try {
            return Integer.parseInt(name.substring(3));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private void processSkybox(ResourceManagerHelper managerHelper, Identifier id, Pattern pattern, JsonArray netherLayers, JsonArray overworldLayers, JsonArray endLayers) {
        Matcher matcher = pattern.matcher(id.getPath());
        if (!matcher.find()) return;

        String world = matcher.group("world");
        String name = matcher.group("name");
        if (world == null || name == null) return;

        if (name.equals("moon_phases") || name.equals("sun")) {
            LOGGER.info("Skipping {}: moon_phases/sun aren't supported!", id);
            return;
        }

        if (parseSkyLayerId(name) < 0) {
            return;
        }

        LOGGER.info("Converting {} to Nuit Format...", id);
        Properties properties = this.loadProperties(managerHelper, id);
        if (properties == null) return;

        JsonObject json = OptiFineSkyPropertiesConverter.convert(managerHelper, properties, id);
        if (json != null) {
            if ("world-1".equals(world)) {
                netherLayers.add(json);
            } else if ("world0".equals(world)) {
                overworldLayers.add(json);
            } else if ("world1".equals(world)) {
                endLayers.add(json);
            }
        }
    }

    private Properties loadProperties(ResourceManagerHelper managerHelper, Identifier id) {
        try (InputStream inputStream = managerHelper.getInputStream(id)) {
            if (inputStream == null) {
                if (NuitInteropConfig.INSTANCE.debugMode) {
                    LOGGER.error("Error reading resource: {}", id);
                }
                return null;
            }
            Properties properties = new Properties();
            properties.load(inputStream);
            return properties;
        } catch (IOException e) {
            if (NuitInteropConfig.INSTANCE.debugMode) {
                LOGGER.error("Error loading properties from: {}", id);
            }
        }
        return null;
    }

    private void createAndAddSkybox(Map<Identifier, JsonObject> skyboxes, String world, String skyboxName, JsonArray layers) {
        JsonObject skyboxJson = new JsonObject();
        skyboxJson.addProperty("schemaVersion", 1);
        skyboxJson.addProperty("type", "nuit_interop:optifine-custom-sky");
        skyboxJson.add("layers", layers);
        skyboxJson.addProperty("world", world);

        skyboxes.put(Identifier.tryBuild(MOD_ID, skyboxName), skyboxJson);
    }
}
