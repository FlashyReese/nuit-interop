package me.flashyreese.mods.nuit_interop;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.Strictness;
import me.flashyreese.mods.nuit.SkyboxManager;
import me.flashyreese.mods.nuit.api.NuitPlatformHelper;
import me.flashyreese.mods.nuit.api.skyboxes.Skybox;
import me.flashyreese.mods.nuit.skybox.SkyboxType;
import me.flashyreese.mods.nuit_interop.config.NuitInteropConfig;
import me.flashyreese.mods.nuit_interop.fabricskyboxes.LegacyFabricSkyBoxesParser;
import me.flashyreese.mods.nuit_interop.sky.OptiFineCustomSky;
import me.flashyreese.mods.nuit_interop.utils.ResourceManagerHelper;
import me.flashyreese.mods.nuit_interop.utils.Utils;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Comparator;
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
    private static final Pattern OPTIFINE_SKY_PATTERN = Pattern.compile("optifine/sky/(?<world>\\w+)/(?<name>\\w+).properties$");
    private static final String MCPATCHER_SKY_PARENT = "mcpatcher/sky";
    private static final Pattern MCPATCHER_SKY_PATTERN = Pattern.compile("mcpatcher/sky/(?<world>\\w+)/(?<name>\\w+).properties$");

    private static final SkyboxType<OptiFineCustomSky> OPTIFINE_CUSTOM_SKY_SKYBOX_TYPE;

    static {
        OPTIFINE_CUSTOM_SKY_SKYBOX_TYPE = new SkyboxType<>(Identifier.tryBuild(MOD_ID, "optifine-custom-sky"), 1, OptiFineCustomSky.CODEC);
    }

    public static void init() {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        Registry.register(NuitPlatformHelper.INSTANCE.getSkyboxTypeRegistry(), OPTIFINE_CUSTOM_SKY_SKYBOX_TYPE.getName(), OPTIFINE_CUSTOM_SKY_SKYBOX_TYPE);
    }

    private static NuitInterop instance;

    public static NuitInterop getInstance() {
        if (instance == null) {
            instance = new NuitInterop();
        }
        return instance;
    }

    public void inject(ResourceManager manager) {
        if (!NuitInteropConfig.INSTANCE.interoperability) return;

        if (NuitInteropConfig.INSTANCE.preferNuitNative && !SkyboxManager.getInstance().getSkyboxMap().isEmpty()) {
            LOGGER.info("Nuit Native is preferred and existing skyboxes already detected! Skipping conversion.");
            return;
        } else {
            LOGGER.warn("Nuit-Interop is preventing native Nuit resource packs from loading!");
        }

        LOGGER.warn("Removing existing Nuit skies and converting legacy skyboxes...");
        LOGGER.warn("Visual bugs may occur; do not report these issues to Nuit or resource pack creators!");

        SkyboxManager.getInstance().clearSkyboxes();
        convert(new ResourceManagerHelper(manager));
    }

    private void convert(ResourceManagerHelper managerHelper) {
        if (NuitInteropConfig.INSTANCE.processFabricSkyBoxes) {
            convertFabricSkyBoxes(managerHelper);
        }
        if (NuitInteropConfig.INSTANCE.processOptiFine) {
            convertNamespace(managerHelper, OPTIFINE_SKY_PARENT, OPTIFINE_SKY_PATTERN);
        }
        if (NuitInteropConfig.INSTANCE.processMCPatcher) {
            convertNamespace(managerHelper, MCPATCHER_SKY_PARENT, MCPATCHER_SKY_PATTERN);
        }
    }

    private void convertFabricSkyBoxes(ResourceManagerHelper managerHelper) {
        managerHelper.searchIn(FABRIC_SKYBOXES_SKY_PARENT)
                .filter(id -> id.getPath().endsWith(".json"))
                .sorted(Comparator.comparing(Identifier::toString))
                .forEach(id -> this.processFabricSkyBox(managerHelper, id));
    }

    private void processFabricSkyBox(ResourceManagerHelper managerHelper, Identifier id) {
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
            SkyboxManager.getInstance().addSkybox(id, skybox.get());
        } catch (Exception e) {
            if (NuitInteropConfig.INSTANCE.debugMode) {
                LOGGER.error("Error converting legacy FabricSkyBoxes skybox: {}", id, e);
            }
        }
    }

    private void convertNamespace(ResourceManagerHelper managerHelper, String skyParent, Pattern pattern) {
        JsonArray overworldLayers = new JsonArray();
        JsonArray endLayers = new JsonArray();

        managerHelper.searchIn(skyParent)
                .filter(id -> id.getPath().endsWith(".properties"))
                .sorted(Comparator.comparing(Identifier::getPath, (id1, id2) -> this.compareSkyboxIds(id1, id2, pattern)))
                .forEach(id -> processSkybox(managerHelper, id, pattern, overworldLayers, endLayers));

        if (!overworldLayers.isEmpty()) {
            createAndAddSkybox("minecraft:overworld", "native-optifine-custom-sky-overworld", overworldLayers);
        }
        if (!endLayers.isEmpty()) {
            createAndAddSkybox("minecraft:the_end", "native-optifine-custom-sky-end", endLayers);
        }
    }

    private int compareSkyboxIds(String id1, String id2, Pattern pattern) {
        Matcher matcherId1 = pattern.matcher(id1);
        Matcher matcherId2 = pattern.matcher(id2);

        if (matcherId1.find() && matcherId2.find()) {
            int id1No = Utils.parseInt(matcherId1.group("name").replace("sky", ""), -1);
            int id2No = Utils.parseInt(matcherId2.group("name").replace("sky", ""), -1);

            if (id1No >= 0 && id2No >= 0) {
                return Integer.compare(id1No, id2No);
            }
        }
        return 0;
    }

    private void processSkybox(ResourceManagerHelper managerHelper, Identifier id, Pattern pattern, JsonArray overworldLayers, JsonArray endLayers) {
        Matcher matcher = pattern.matcher(id.getPath());
        if (!matcher.find()) return;

        String world = matcher.group("world");
        String name = matcher.group("name");
        if (world == null || name == null) return;

        if (name.equals("moon_phases") || name.equals("sun")) {
            LOGGER.info("Skipping {}: moon_phases/sun aren't supported!", id);
            return;
        }

        LOGGER.info("Converting {} to Nuit Format...", id);
        Properties properties = this.loadProperties(managerHelper, id);
        if (properties == null) return;

        JsonObject json = Utils.convertOptiFineSkyProperties(managerHelper, properties, id);
        if (json != null) {
            if ("world0".equals(world)) {
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

    private void createAndAddSkybox(String world, String skyboxName, JsonArray layers) {
        JsonObject skyboxJson = new JsonObject();
        skyboxJson.addProperty("schemaVersion", 1);
        skyboxJson.addProperty("type", "nuit_interop:optifine-custom-sky");
        skyboxJson.add("layers", layers);
        skyboxJson.addProperty("world", world);

        //Skybox skybox = OptiFineCustomSky.CODEC.decode(JsonOps.INSTANCE, skyboxJson).getOrThrow().getFirst();
        //SkyboxManager.getInstance().addSkybox(Identifier.fromNamespaceAndPath("nuit-interop", skyboxName), skybox);
        SkyboxManager.getInstance().addSkybox(Identifier.tryBuild(MOD_ID, skyboxName), skyboxJson);
    }
}
