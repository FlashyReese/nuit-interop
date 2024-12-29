package me.flashyreese.mods.nuit_interop.mixin;

import io.github.amerebagatelle.mods.nuit.resource.SkyboxResourceListener;
import me.flashyreese.mods.nuit_interop.NuitInterop;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Mixin(SkyboxResourceListener.class)
public class MixinSkyboxResourceListener {
    @Inject(method = "reload", at = @At(value = "TAIL"))
    public void reload(PreparableReloadListener.PreparationBarrier preparationBarrier, ResourceManager resourceManager, Executor executor, Executor executor2, CallbackInfoReturnable<CompletableFuture<Void>> cir) {
        NuitInterop.getInstance().inject(resourceManager);
    }
}
