package me.flashyreese.mods.nuit_interop.neoforge;

import me.flashyreese.mods.nuit_interop.NuitInterop;
import me.flashyreese.mods.nuit_interop.config.NuitInteropConfig;
import me.flashyreese.mods.nuit_interop.config.NuitInteropConfigScreen;
import net.neoforged.api.distmarker.Dist;import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = NuitInterop.MOD_ID, dist = Dist.CLIENT)
public final class NuitInteropNeoForgeMod {
    public NuitInteropNeoForgeMod(IEventBus bus) {
        NuitInterop.init();
        ModLoadingContext.get().getActiveContainer().registerExtensionPoint(IConfigScreenFactory.class, (mc, parent) -> new NuitInteropConfigScreen(parent, NuitInteropConfig.INSTANCE));
    }
}
