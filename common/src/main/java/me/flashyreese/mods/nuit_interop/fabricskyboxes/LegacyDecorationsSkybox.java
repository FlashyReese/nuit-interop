package me.flashyreese.mods.nuit_interop.fabricskyboxes;

import me.flashyreese.mods.nuit.api.skyboxes.SkyboxRenderContext;

public class LegacyDecorationsSkybox extends LegacyAbstractSkybox {
    public LegacyDecorationsSkybox(LegacyProperties properties, LegacyConditions conditions, LegacyDecorations decorations) {
        super(properties, conditions, decorations);
    }

    @Override
    public void render(SkyboxRenderContext context) {
        context.applyFog();
        if (this.alpha <= 0.0F) {
            return;
        }

        this.renderDecorations(context, context.skyModelViewStack());
    }
}
