package me.flashyreese.mods.nuit_interop.mixin;

import me.flashyreese.mods.nuit.resource.SkyboxResourceListener;
import me.flashyreese.mods.nuit_interop.NuitInterop;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SkyboxResourceListener.class)
public class MixinSkyboxResourceListener {
    @Inject(method = "readFiles", at = @At(value = "TAIL"))
    public void reload(ResourceManager resourceManager, CallbackInfo ci) {
        NuitInterop.getInstance().inject(resourceManager);
    }
}
