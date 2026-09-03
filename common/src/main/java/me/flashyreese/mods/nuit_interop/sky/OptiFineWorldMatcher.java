package me.flashyreese.mods.nuit_interop.sky;

import net.minecraft.resources.Identifier;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.dimension.DimensionType;

final class OptiFineWorldMatcher {
    private static final Identifier OVERWORLD = Identifier.withDefaultNamespace("overworld");
    private static final Identifier NETHER = Identifier.withDefaultNamespace("the_nether");
    private static final Identifier END = Identifier.withDefaultNamespace("the_end");

    static boolean matches(
            Identifier configuredWorld,
            Identifier currentWorld,
            DimensionType.Skybox currentSkybox,
            CardinalLighting.Type cardinalLightType
    ) {
        if (OVERWORLD.equals(configuredWorld)) {
            // Legacy world0 packs applied to normal server worlds even when those worlds used
            // a different dimension id. NONE with default cardinal lighting covers modern
            // lobby/minigame dimensions that deliberately suppress the vanilla sky pass.
            return currentSkybox == DimensionType.Skybox.OVERWORLD
                    || (currentSkybox == DimensionType.Skybox.NONE
                        && cardinalLightType == CardinalLighting.Type.DEFAULT);
        }

        if (NETHER.equals(configuredWorld)) {
            return currentSkybox == DimensionType.Skybox.NONE
                    && cardinalLightType == CardinalLighting.Type.NETHER;
        }

        if (END.equals(configuredWorld)) {
            return currentSkybox == DimensionType.Skybox.END;
        }

        // Preserve exact matching for custom values used by native definitions of the
        // registered interop skybox type.
        return configuredWorld.equals(currentWorld);
    }
}
