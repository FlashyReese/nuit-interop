package me.flashyreese.mods.nuit_interop.fabric.config;

import me.flashyreese.mods.nuit_interop.NuitInterop;
import net.fabricmc.api.ClientModInitializer;

public class NuitInteropClientFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        NuitInterop.init();
    }
}
