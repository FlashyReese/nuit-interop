package me.flashyreese.mods.nuit_interop;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import io.github.amerebagatelle.mods.nuit.SkyboxManager;
import io.github.amerebagatelle.mods.nuit.api.skyboxes.Skybox;
import me.flashyreese.mods.nuit_interop.config.NuitInteropConfig;
import me.flashyreese.mods.nuit_interop.sky.OptiFineCustomSky;
import me.flashyreese.mods.nuit_interop.utils.ResourceManagerHelper;
import me.flashyreese.mods.nuit_interop.utils.Utils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NuitInterop {
    public static final String MOD_ID = "nuit_interop";

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
                if (!SkyboxManager.getInstance().getSkyboxMap().isEmpty()) {
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
                            // TODO/NOTE: Support moon/sun
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

                        JsonObject json = Utils.convertOptiFineSkyProperties(resourceManagerHelper, properties, id);
                        if (json != null) {
                            if (world.equals("world0")) {
                                overworldLayers.add(json);
                            } else if (world.equals("world1")) {
                                endLayers.add(json);
                            }
                        }
                    }
                });

        if (!overworldLayers.isEmpty()) {
            JsonObject overworldJson = new JsonObject();
            overworldJson.addProperty("schemaVersion", 1);
            overworldJson.addProperty("type", "optifine-custom-sky");
            overworldJson.add("layers", overworldLayers);
            overworldJson.addProperty("world", "minecraft:overworld");
            Skybox skybox = OptiFineCustomSky.CODEC.decode(JsonOps.INSTANCE, overworldJson).getOrThrow().getFirst();
            SkyboxManager.getInstance().addSkybox(ResourceLocation.fromNamespaceAndPath("nuit-interop", "native-optifine-custom-sky-overworld"), skybox);
        }

        if (!endLayers.isEmpty()) {
            JsonObject endJson = new JsonObject();
            endJson.addProperty("schemaVersion", 1);
            endJson.addProperty("type", "optifine-custom-sky");
            endJson.add("layers", endLayers);
            endJson.addProperty("world", "minecraft:the_end");
            Skybox skybox = OptiFineCustomSky.CODEC.decode(JsonOps.INSTANCE, endJson).getOrThrow().getFirst();
            SkyboxManager.getInstance().addSkybox(ResourceLocation.fromNamespaceAndPath("nuit-interop", "native-optifine-custom-sky-end"), skybox);
        }
    }
}