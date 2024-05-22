package me.flashyreese.mods.nuit_interop.neoforge;

import me.flashyreese.mods.nuit_interop.NuitInterop;
import me.flashyreese.mods.nuit_interop.config.NuitInteropConfig;
import me.flashyreese.mods.nuit_interop.config.NuitInteropConfigScreen;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(NuitInterop.MOD_ID)
public final class NuitInteropNeoForgeMod {
    public NuitInteropNeoForgeMod(IEventBus bus) {
        ModLoadingContext.get().getActiveContainer().registerExtensionPoint(IConfigScreenFactory.class, (mc, parent) -> new NuitInteropConfigScreen(parent, NuitInteropConfig.INSTANCE));
    }
}
