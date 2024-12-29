package me.flashyreese.mods.nuit_interop.client.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

public class ModMenuApiImpl implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> new NuitInteropConfigScreen(parent, NuitInteropConfig.INSTANCE);
    }
}