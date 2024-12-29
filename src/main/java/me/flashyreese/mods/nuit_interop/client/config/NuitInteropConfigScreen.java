package me.flashyreese.mods.nuit_interop.client.config;

import me.flashyreese.mods.nuit_interop.NuitInterop;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class NuitInteropConfigScreen extends Screen {
    private final Screen parent;
    private final NuitInteropConfig config;
    private final Logger logger = LoggerFactory.getLogger("Nuit-Interop");

    public NuitInteropConfigScreen(Screen parent, NuitInteropConfig config) {
        super(Component.translatable(getTranslationKey("title")));
        this.parent = parent;
        this.config = config;
    }

    private static String getTranslationKey(String optionKey) {
        return "options.nuit-interop." + optionKey;
    }

    private static String getTooltipKey(String translationKey) {
        return translationKey + ".tooltip";
    }

    @Override
    protected void init() {
        addRenderableWidget(createBooleanOptionButton(this.width / 2 - 100 - 110, this.height / 2 - 10 - 60, 420, 20, "interoperability", value -> config.interoperability = value, () -> config.interoperability, () -> {
            Minecraft.getInstance().reloadResourcePacks();
        }));
        addRenderableWidget(createNuitInteropModeOptionButton(this.width / 2 - 100 - 110, this.height / 2 - 10 - 36, 420, 20, "mode", value -> config.mode = value, () -> config.mode, () -> {
            if (config.interoperability) {
                Minecraft.getInstance().reloadResourcePacks();
            }
        }));
        addRenderableWidget(createBooleanOptionButton(this.width / 2 - 100 - 110, this.height / 2 - 10 - 12, 200, 20, "prefer_nuit_native", value -> config.preferNuitNative = value, () -> config.preferNuitNative, this::reloadResourcesIfInterop));
        addRenderableWidget(createBooleanOptionButton(this.width / 2 - 100 + 110, this.height / 2 - 10 - 12, 200, 20, "debug_mode", value -> config.debugMode = value, () -> config.debugMode, () -> {
        }));
        addRenderableWidget(createBooleanOptionButton(this.width / 2 - 100 - 110, this.height / 2 - 10 + 12, 200, 20, "process_optifine", value -> config.processOptiFine = value, () -> config.processOptiFine, this::reloadResourcesIfInterop));
        addRenderableWidget(createBooleanOptionButton(this.width / 2 - 100 + 110, this.height / 2 - 10 + 12, 200, 20, "process_mcpatcher", value -> config.processMCPatcher = value, () -> config.processMCPatcher, this::reloadResourcesIfInterop));
        addRenderableWidget(Button.builder(
                                Component.translatable(getTranslationKey("dump_data")),
                                button -> {
                                    Path path = FabricLoader.getInstance().getGameDir().resolve("nuit-interop-dump");

                                    try {
                                        // Delete the directory and its contents recursively
                                        if (Files.exists(path)) {
                                            if (NuitInteropConfig.INSTANCE.debugMode) {
                                                this.logger.info("Existing dump directory detected. Recursively deleting the contents...");
                                            }

                                            Files.walk(path)
                                                    .sorted(Comparator.reverseOrder())
                                                    .map(Path::toFile)
                                                    .filter(File::delete)
                                                    .forEach(file -> {
                                                        if (NuitInteropConfig.INSTANCE.debugMode) {
                                                            this.logger.info("Deleted: {}", file.getAbsolutePath());
                                                        }
                                                    });
                                        }
                                    } catch (IOException e) {
                                        this.logger.error("Error while deleting existing dump directory: {}", e.getMessage());
                                    }

                                    try {
                                        // Create the directory if it doesn't exist
                                        if (!Files.exists(path)) {
                                            Files.createDirectories(path);
                                            if (NuitInteropConfig.INSTANCE.debugMode) {
                                                this.logger.info("Dump directory created: {}", path.toAbsolutePath());
                                            }
                                        }

                                        NuitInterop.getInstance()
                                                .getConvertedSkyMap()
                                                .forEach((resourceLocation, json) -> {
                                                    String filename = resourceLocation.toString();
                                                    // Fixme: Replace all characters that are not allowed in file names with underscores
                                                    filename = filename.replaceAll("[^a-zA-Z0-9-_\\.]", "_");

                                                    // Write the JSON data to a file
                                                    Path output = path.resolve(filename + ".json");
                                                    try {
                                                        Files.write(output, json.getBytes());
                                                        if (NuitInteropConfig.INSTANCE.debugMode) {
                                                            this.logger.info("Successfully dumped {} to {}", resourceLocation, output.toAbsolutePath());
                                                        }
                                                    } catch (IOException e) {
                                                        this.logger.error("Error while dumping {} to {}: {}", resourceLocation, output.toAbsolutePath(), e.getMessage());
                                                        e.printStackTrace();
                                                    }
                                                });

                                        if (NuitInteropConfig.INSTANCE.debugMode) {
                                            this.logger.info("Opening dump directory: {}", path.toAbsolutePath());
                                        }

                                        Util.getPlatform().openUri(path.toUri());
                                    } catch (IOException e) {
                                        this.logger.error("Error while creating dump directory: {}", e.getMessage());
                                        e.printStackTrace();
                                    }
                                }
                        )
                        .bounds(this.width / 2 - 100 - 110, this.height / 2 - 10 + 36, 420, 20)
                        .tooltip(Tooltip.create(Component.translatable(getTooltipKey(getTranslationKey("dump_data")))))
                        .build()
        );

        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose()).bounds(this.width / 2 - 100, this.height - 40, 200, 20).build());
    }

    private void reloadResourcesIfInterop() {
        if (this.config.interoperability) {
            this.minecraft.reloadResourcePacks();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.renderBackground(graphics, mouseX, mouseY, delta);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 30, 0xFFFFFF);
        super.render(graphics, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    @Override
    public void removed() {
        this.config.writeChanges();
    }

    private Button createBooleanOptionButton(int x, int y, int width, int height, String key, Consumer<Boolean> consumer, Supplier<Boolean> supplier, Runnable onChange) {
        String translationKey = getTranslationKey(key);
        Component text = Component.translatable(translationKey);
        Component tooltipText = Component.translatable(getTooltipKey(translationKey));
        return Button.builder(CommonComponents.optionStatus(text, supplier.get()), button -> {
            boolean newValue = !supplier.get();
            button.setMessage(CommonComponents.optionStatus(text, newValue));
            consumer.accept(newValue);
            onChange.run();
        }).bounds(x, y, width, height).tooltip(Tooltip.create(tooltipText)).build();
    }

    private Button createNuitInteropModeOptionButton(int x, int y, int width, int height, String key, Consumer<NuitInteropMode> consumer, Supplier<NuitInteropMode> supplier, Runnable onChange) {
        String translationKey = getTranslationKey(key);
        Component text = Component.translatable(translationKey);
        Component tooltipText = Component.translatable(getTooltipKey(translationKey));
        return Button.builder(CommonComponents.optionNameValue(text, Component.translatable(getTranslationKey(supplier.get().getTranslationKey()))), button -> {
            NuitInteropMode currentMode = supplier.get();
            NuitInteropMode[] modes = NuitInteropMode.values();
            int currentIndex = currentMode.ordinal();
            int nextIndex = (currentIndex + 1) % modes.length; // Wrap around to the beginning if reached the end
            NuitInteropMode newValue = modes[nextIndex];
            button.setMessage(CommonComponents.optionNameValue(text, Component.translatable(getTranslationKey(newValue.getTranslationKey()))));
            consumer.accept(newValue);
            onChange.run();
        }).bounds(x, y, width, height).tooltip(Tooltip.create(tooltipText)).build();
    }
}
