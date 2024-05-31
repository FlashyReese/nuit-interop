package me.flashyreese.mods.nuit_interop;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import io.github.amerebagatelle.mods.nuit.SkyboxManager;
import io.github.amerebagatelle.mods.nuit.api.skyboxes.Skybox;
import me.flashyreese.mods.nuit_interop.config.NuitInteropConfig;
import me.flashyreese.mods.nuit_interop.mixin.SkyboxManagerAccessor;
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
    private static final String MCPATCHER_SKY_PARENT = "mcpatcher/sky";
    private static final Pattern PATTERN_FUNCTION = Pattern.compile("/(?<world>\\w+)/(?<name>\\w+).properties$");
    private static NuitInterop INSTANCE;
    private final Logger logger = LoggerFactory.getLogger("Nuit-Interop");

    public static NuitInterop getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new NuitInterop();
        }
        return INSTANCE;
    }

    public void inject(ResourceManager manager) {
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
        final JsonArray overworldLayers = new JsonArray();
        final JsonArray endLayers = new JsonArray();
        final JsonArray netherLayers = new JsonArray();

        this.convertNamespace(managerAccessor, overworldLayers, endLayers, netherLayers);

        if (!overworldLayers.isEmpty()) {
            JsonObject overworldJson = new JsonObject();
            overworldJson.addProperty("schemaVersion", 2);
            overworldJson.addProperty("type", "optifine-custom-sky");
            overworldJson.add("layers", overworldLayers);
            overworldJson.addProperty("world", "minecraft:overworld");

            Skybox skybox = OptiFineCustomSky.CODEC.decode(JsonOps.INSTANCE, overworldJson).getOrThrow().getFirst();

            SkyboxManager.getInstance().addSkybox(ResourceLocation.tryBuild("nuit_interop", "native-optifine-overworld"), skybox);
        }

        if (!endLayers.isEmpty()) {
            JsonObject endJson = new JsonObject();
            endJson.addProperty("schemaVersion", 2);
            endJson.addProperty("type", "optifine-custom-sky");
            endJson.add("layers", endLayers);
            endJson.addProperty("world", "minecraft:the_end");

            Skybox skybox = OptiFineCustomSky.CODEC.decode(JsonOps.INSTANCE, endJson).getOrThrow().getFirst();

            SkyboxManager.getInstance().addSkybox(ResourceLocation.tryBuild("nuit_interop", "native-%s-end"), skybox);
        }

        if (!netherLayers.isEmpty()) {
            JsonObject endJson = new JsonObject();
            endJson.addProperty("schemaVersion", 2);
            endJson.addProperty("type", "optifine-custom-sky");
            endJson.add("layers", endLayers);
            endJson.addProperty("world", "minecraft:the_nether");

            Skybox skybox = OptiFineCustomSky.CODEC.decode(JsonOps.INSTANCE, endJson).getOrThrow().getFirst();

            SkyboxManager.getInstance().addSkybox(ResourceLocation.tryBuild("nuit_interop", "native-%s-nether"), skybox);
        }
    }

    /**
     * Converts a specific namespace
     *
     * @param resourceManagerHelper The resource manager helper
     */
    private void convertNamespace(ResourceManagerHelper resourceManagerHelper, JsonArray overworldLayers, JsonArray endLayers, JsonArray netherLayers) {
        List<ResourceLocation> streamOptiFine = resourceManagerHelper.searchIn(OPTIFINE_SKY_PARENT).filter(id -> NuitInteropConfig.INSTANCE.processOptiFine && id.getPath().endsWith(".properties")).toList();
        List<ResourceLocation> streamMcPatcher = resourceManagerHelper.searchIn(MCPATCHER_SKY_PARENT).filter(id -> NuitInteropConfig.INSTANCE.processMCPatcher && id.getPath().endsWith(".properties")).toList();

        List<ResourceLocation> mergedResources = new ArrayList<>();
        mergedResources.addAll(streamOptiFine);
        mergedResources.addAll(streamMcPatcher);


        mergedResources
                .stream()
                .sorted(Comparator.comparing(ResourceLocation::getPath, (id1, id2) -> {
                    // Sorting for older versions of Nuit without priority
                    Matcher matcherId1 = PATTERN_FUNCTION.matcher(id1);
                    Matcher matcherId2 = PATTERN_FUNCTION.matcher(id2);
                    if (matcherId1.find() && matcherId2.find()) {
                        int id1No = Utils.parseInt(matcherId1.group("name").replace("sky", ""), -2);
                        int id2No = Utils.parseInt(matcherId2.group("name").replace("sky", ""), -2);
                        if (id1No >= -1 && id2No >= -1) {
                            return id1No - id2No;
                        }
                    }
                    return 0;
                })).forEach(id -> {
                    Matcher matcher = PATTERN_FUNCTION.matcher(id.getPath());
                    if (matcher.find()) {
                        String world = matcher.group("world");
                        String name = matcher.group("name");

                        if (world == null || name == null)
                            return;

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

                        JsonObject json = Utils.convertOptiFineSkyProperties(resourceManagerHelper, properties, id);
                        if (json != null) {
                            switch (world) {
                                case "world0" -> overworldLayers.add(json);
                                case "world1" -> endLayers.add(json);
                                case "world-1" -> netherLayers.add(json);
                            }
                        }
                    }
                });
    }
}
