package me.flashyreese.mods.nuit_interop.fabricskyboxes;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import me.flashyreese.mods.nuit.components.Blend;
import net.minecraft.resources.Identifier;

public record LegacyDecorations(Identifier sunTexture, Identifier moonTexture, boolean sunEnabled,
                                boolean moonEnabled, boolean starsEnabled, LegacyRotation rotation, Blend blend) {
    public static final Identifier MOON_PHASES = Identifier.withDefaultNamespace("textures/environment/celestial/moon/full_moon.png");
    public static final Identifier SUN = Identifier.withDefaultNamespace("textures/environment/celestial/sun.png");
    public static final LegacyDecorations DEFAULT = new LegacyDecorations(SUN, MOON_PHASES, false, false, false, LegacyRotation.DECORATIONS, Blend.decorations());

    public static final Codec<LegacyDecorations> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Identifier.CODEC.optionalFieldOf("sun", SUN).forGetter(LegacyDecorations::sunTexture),
            Identifier.CODEC.optionalFieldOf("moon", MOON_PHASES).forGetter(LegacyDecorations::moonTexture),
            Codec.BOOL.optionalFieldOf("showSun", false).forGetter(LegacyDecorations::sunEnabled),
            Codec.BOOL.optionalFieldOf("showMoon", false).forGetter(LegacyDecorations::moonEnabled),
            Codec.BOOL.optionalFieldOf("showStars", false).forGetter(LegacyDecorations::starsEnabled),
            LegacyRotation.CODEC.optionalFieldOf("rotation", LegacyRotation.DECORATIONS).forGetter(LegacyDecorations::rotation),
            Blend.CODEC.optionalFieldOf("blend", Blend.decorations()).forGetter(LegacyDecorations::blend)
    ).apply(instance, LegacyDecorations::new));
}
