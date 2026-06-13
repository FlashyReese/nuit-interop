package me.flashyreese.mods.nuit_interop.config;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class NuitInteropConfigScreen extends Screen {
    private final Screen parent;
    private final NuitInteropConfig config;

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
        addRenderableWidget(createBooleanOptionButton(this.width / 2 - 100 - 110, this.height / 2 - 10 - 60, 420, 20, "interoperability", value -> config.interoperability = value, () -> config.interoperability, () -> Minecraft.getInstance().reloadResourcePacks()));
        addRenderableWidget(createBooleanOptionButton(this.width / 2 - 100 - 110, this.height / 2 - 10 - 12, 200, 20, "prefer_nuit_native", value -> config.preferNuitNative = value, () -> config.preferNuitNative, this::reloadResourcesIfInterop));
        addRenderableWidget(createBooleanOptionButton(this.width / 2 - 100 + 110, this.height / 2 - 10 - 12, 200, 20, "debug_mode", value -> config.debugMode = value, () -> config.debugMode, () -> {
        }));
        addRenderableWidget(createBooleanOptionButton(this.width / 2 - 100 - 110, this.height / 2 - 10 + 12, 200, 20, "process_fabricskyboxes", value -> config.processFabricSkyBoxes = value, () -> config.processFabricSkyBoxes, this::reloadResourcesIfInterop));
        addRenderableWidget(createBooleanOptionButton(this.width / 2 - 100 + 110, this.height / 2 - 10 + 12, 200, 20, "process_optifine", value -> config.processOptiFine = value, () -> config.processOptiFine, this::reloadResourcesIfInterop));
        addRenderableWidget(createBooleanOptionButton(this.width / 2 - 100 - 110, this.height / 2 - 10 + 36, 200, 20, "process_mcpatcher", value -> config.processMCPatcher = value, () -> config.processMCPatcher, this::reloadResourcesIfInterop));

        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose()).bounds(this.width / 2 - 100, this.height - 40, 200, 20).build());
    }

    private void reloadResourcesIfInterop() {
        if (this.config.interoperability) {
            this.minecraft.reloadResourcePacks();
        }
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        context.drawCenteredString(this.font, this.title, this.width / 2, 30, 0xFFFFFF);
        super.render(context, mouseX, mouseY, delta);
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
}
