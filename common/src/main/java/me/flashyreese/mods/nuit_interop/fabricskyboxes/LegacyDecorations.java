package me.flashyreese.mods.nuit_interop.fabricskyboxes;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import me.flashyreese.mods.nuit.components.Blend;
import net.minecraft.resources.ResourceLocation;

public record LegacyDecorations(ResourceLocation sunTexture, ResourceLocation moonTexture, boolean sunEnabled,
                                boolean moonEnabled, boolean starsEnabled, LegacyRotation rotation, Blend blend) {
    public static final ResourceLocation MOON_PHASES = ResourceLocation.withDefaultNamespace("textures/environment/moon_phases.png");
    public static final ResourceLocation SUN = ResourceLocation.withDefaultNamespace("textures/environment/sun.png");
    public static final LegacyDecorations DEFAULT = new LegacyDecorations(SUN, MOON_PHASES, false, false, false, LegacyRotation.DECORATIONS, Blend.decorations());

    public static final Codec<LegacyDecorations> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.optionalFieldOf("sun", SUN).forGetter(LegacyDecorations::sunTexture),
            ResourceLocation.CODEC.optionalFieldOf("moon", MOON_PHASES).forGetter(LegacyDecorations::moonTexture),
            Codec.BOOL.optionalFieldOf("showSun", false).forGetter(LegacyDecorations::sunEnabled),
            Codec.BOOL.optionalFieldOf("showMoon", false).forGetter(LegacyDecorations::moonEnabled),
            Codec.BOOL.optionalFieldOf("showStars", false).forGetter(LegacyDecorations::starsEnabled),
            LegacyRotation.CODEC.optionalFieldOf("rotation", LegacyRotation.DECORATIONS).forGetter(LegacyDecorations::rotation),
            Blend.CODEC.optionalFieldOf("blend", Blend.decorations()).forGetter(LegacyDecorations::blend)
    ).apply(instance, LegacyDecorations::new));
}
