package me.flashyreese.mods.nuit_interop.mixin;

import com.google.gson.JsonObject;
import me.flashyreese.mods.nuit.api.skyboxes.Skybox;
import me.flashyreese.mods.nuit.resource.SkyboxResourceListener;
import me.flashyreese.mods.nuit_interop.NuitInterop;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.LinkedHashMap;
import java.util.Map;

@Mixin(SkyboxResourceListener.class)
public class MixinSkyboxResourceListener {
    @Unique
    private final Map<Identifier, Skybox> nuit_interop$convertedSkyboxes = new LinkedHashMap<>();

    @Inject(method = "readFiles", at = @At(value = "TAIL"))
    private void nuit_interop$readFiles(ResourceManager resourceManager, CallbackInfoReturnable<Map<Identifier, JsonObject>> cir) {
        NuitInterop.getInstance().inject(resourceManager, cir.getReturnValue(), this.nuit_interop$convertedSkyboxes);
    }

    @Inject(method = "applySkyboxes", at = @At(value = "TAIL"))
    private void nuit_interop$applySkyboxes(Map<Identifier, JsonObject> skyboxJson, CallbackInfo ci) {
        NuitInterop.getInstance().addConvertedSkyboxes(this.nuit_interop$convertedSkyboxes);
    }
}
