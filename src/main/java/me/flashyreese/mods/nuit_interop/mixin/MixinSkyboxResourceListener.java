package me.flashyreese.mods.nuit_interop.mixin;

import io.github.amerebagatelle.mods.nuit.resource.SkyboxResourceListener;
import me.flashyreese.mods.nuit_interop.NuitInterop;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceReloader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Mixin(SkyboxResourceListener.class)
public class MixinSkyboxResourceListener {
    @Inject(method = "reload", at = @At(value = "TAIL"))
    public void reload(ResourceReloader.Synchronizer preparationBarrier, ResourceManager resourceManager, Executor executor, Executor executor2, CallbackInfoReturnable<CompletableFuture<Void>> cir) {
        NuitInterop.getInstance().inject(resourceManager);
    }
}
