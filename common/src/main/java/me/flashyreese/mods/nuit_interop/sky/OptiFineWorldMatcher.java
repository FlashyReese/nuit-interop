package me.flashyreese.mods.nuit_interop.sky;

import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.resources.ResourceLocation;

final class OptiFineWorldMatcher {
    private static final ResourceLocation OVERWORLD = ResourceLocation.withDefaultNamespace("overworld");
    private static final ResourceLocation NETHER = ResourceLocation.withDefaultNamespace("the_nether");
    private static final ResourceLocation END = ResourceLocation.withDefaultNamespace("the_end");

    static boolean matches(
            ResourceLocation configuredWorld,
            ResourceLocation currentWorld,
            DimensionSpecialEffects.SkyType currentSkyType,
            boolean ultraWarm
    ) {
        if (OVERWORLD.equals(configuredWorld)) {
            return currentSkyType == DimensionSpecialEffects.SkyType.NORMAL
                    || (currentSkyType == DimensionSpecialEffects.SkyType.NONE && !ultraWarm);
        }

        if (NETHER.equals(configuredWorld)) {
            return currentSkyType == DimensionSpecialEffects.SkyType.NONE && ultraWarm;
        }

        if (END.equals(configuredWorld)) {
            return currentSkyType == DimensionSpecialEffects.SkyType.END;
        }

        return configuredWorld.equals(currentWorld);
    }
}
