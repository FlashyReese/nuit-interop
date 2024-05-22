package me.flashyreese.mods.nuit_interop.fabric.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.flashyreese.mods.nuit_interop.config.NuitInteropConfig;
import me.flashyreese.mods.nuit_interop.config.NuitInteropConfigScreen;

public class ModMenuApiImpl implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> new NuitInteropConfigScreen(parent, NuitInteropConfig.INSTANCE);
    }
}