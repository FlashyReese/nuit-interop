package me.flashyreese.mods.nuit_interop.fabricskyboxes;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import me.flashyreese.mods.nuit.components.Blend;
import me.flashyreese.mods.nuit.components.Texture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

import java.util.List;
import java.util.stream.Stream;

public class LegacySquareTexturedSkybox extends LegacyTexturedSkybox {
    public static final Codec<LegacySquareTexturedSkybox> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            LegacyProperties.CODEC.fieldOf("properties").forGetter(skybox -> skybox.legacyProperties),
            LegacyConditions.CODEC.optionalFieldOf("conditions", LegacyConditions.DEFAULT).forGetter(skybox -> skybox.legacyConditions),
            LegacyDecorations.CODEC.optionalFieldOf("decorations", LegacyDecorations.DEFAULT).forGetter(skybox -> skybox.decorations),
            Blend.CODEC.optionalFieldOf("blend", Blend.normal()).forGetter(LegacyTexturedSkybox::getBlend),
            LegacyTextures.CODEC.fieldOf("textures").forGetter(LegacySquareTexturedSkybox::getTextures)
    ).apply(instance, LegacySquareTexturedSkybox::new));

    protected LegacyTextures textures;

    public LegacySquareTexturedSkybox(LegacyProperties properties, LegacyConditions conditions, LegacyDecorations decorations, Blend blend, LegacyTextures textures) {
        super(properties, conditions, decorations, blend);
        this.textures = textures;
    }

    @Override
    protected void renderTexturedSkybox(Matrix4f modelViewMatrix) {
        for (int face = 0; face < 6; ++face) {
            Texture texture = this.textures.byId(face);
            Matrix4f faceMatrix = LegacyFsbRenderer.getMatrixForRotatedFace(face);
            LegacyFsbRenderer.drawTexturedQuad(modelViewMatrix, faceMatrix, texture);
        }
    }

    public LegacyTextures getTextures() {
        return this.textures;
    }

    @Override
    public List<ResourceLocation> getTexturesToRegister() {
        return Stream.concat(super.getTexturesToRegister().stream(), this.textures.all().stream().map(Texture::getTextureId))
                .distinct()
                .toList();
    }
}
