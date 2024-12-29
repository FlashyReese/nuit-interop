package me.flashyreese.mods.nuit_interop.mixin;

import io.github.amerebagatelle.mods.nuit.SkyboxManager;
import io.github.amerebagatelle.mods.nuit.skybox.AbstractSkybox;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(value = SkyboxManager.class, remap = false)
public interface SkyboxManagerAccessor {
    @Accessor("skyboxMap")
    Map<ResourceLocation, AbstractSkybox> getSkyboxes();
}
