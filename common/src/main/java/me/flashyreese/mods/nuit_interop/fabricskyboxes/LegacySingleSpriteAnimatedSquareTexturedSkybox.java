package me.flashyreese.mods.nuit_interop.fabricskyboxes;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import me.flashyreese.mods.nuit.components.Blend;
import me.flashyreese.mods.nuit.components.Texture;

import java.util.List;

public class LegacySingleSpriteAnimatedSquareTexturedSkybox extends LegacyAnimatedSquareTexturedSkybox {
    public static final Codec<LegacySingleSpriteAnimatedSquareTexturedSkybox> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            LegacyProperties.CODEC.fieldOf("properties").forGetter(skybox -> skybox.legacyProperties),
            LegacyConditions.CODEC.optionalFieldOf("conditions", LegacyConditions.DEFAULT).forGetter(skybox -> skybox.legacyConditions),
            LegacyDecorations.CODEC.optionalFieldOf("decorations", LegacyDecorations.DEFAULT).forGetter(skybox -> skybox.decorations),
            Blend.CODEC.optionalFieldOf("blend", Blend.normal()).forGetter(LegacyTexturedSkybox::getBlend),
            Texture.CODEC.listOf().fieldOf("animationTextures").forGetter(LegacySingleSpriteAnimatedSquareTexturedSkybox::getAnimationTextureList),
            Codec.FLOAT.fieldOf("fps").forGetter(LegacySingleSpriteAnimatedSquareTexturedSkybox::getFps)
    ).apply(instance, LegacySingleSpriteAnimatedSquareTexturedSkybox::new));

    public LegacySingleSpriteAnimatedSquareTexturedSkybox(LegacyProperties properties, LegacyConditions conditions, LegacyDecorations decorations, Blend blend, List<Texture> animationTextures, float fps) {
        super(properties, conditions, decorations, blend, animationTextures.stream().map(LegacySingleSpriteAnimatedSquareTexturedSkybox::toTextures).toList(), fps);
    }

    private static LegacyTextures toTextures(Texture texture) {
        return new LegacyTextures(
                LegacyTextures.withUv(texture, LegacyUVRanges.SINGLE_SPRITE.north()),
                LegacyTextures.withUv(texture, LegacyUVRanges.SINGLE_SPRITE.south()),
                LegacyTextures.withUv(texture, LegacyUVRanges.SINGLE_SPRITE.east()),
                LegacyTextures.withUv(texture, LegacyUVRanges.SINGLE_SPRITE.west()),
                LegacyTextures.withUv(texture, LegacyUVRanges.SINGLE_SPRITE.top()),
                LegacyTextures.withUv(texture, LegacyUVRanges.SINGLE_SPRITE.bottom())
        );
    }

    public List<Texture> getAnimationTextureList() {
        return this.getAnimationTextures().stream().map(LegacyTextures::north).toList();
    }
}
