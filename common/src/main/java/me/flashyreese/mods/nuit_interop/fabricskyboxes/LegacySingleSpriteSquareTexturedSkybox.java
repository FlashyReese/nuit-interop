package me.flashyreese.mods.nuit_interop.fabricskyboxes;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import me.flashyreese.mods.nuit.api.skyboxes.SkyboxRenderContext;
import me.flashyreese.mods.nuit.components.Blend;
import me.flashyreese.mods.nuit.components.Texture;
import me.flashyreese.mods.nuit.util.Utils;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

import java.util.List;
import java.util.stream.Stream;

public class LegacySingleSpriteSquareTexturedSkybox extends LegacyTexturedSkybox {
    public static final Codec<LegacySingleSpriteSquareTexturedSkybox> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            LegacyProperties.CODEC.fieldOf("properties").forGetter(skybox -> skybox.legacyProperties),
            LegacyConditions.CODEC.optionalFieldOf("conditions", LegacyConditions.DEFAULT).forGetter(skybox -> skybox.legacyConditions),
            LegacyDecorations.CODEC.optionalFieldOf("decorations", LegacyDecorations.DEFAULT).forGetter(skybox -> skybox.decorations),
            Blend.CODEC.optionalFieldOf("blend", Blend.normal()).forGetter(LegacyTexturedSkybox::getBlend),
            Texture.CODEC.fieldOf("texture").forGetter(LegacySingleSpriteSquareTexturedSkybox::getTexture)
    ).apply(instance, LegacySingleSpriteSquareTexturedSkybox::new));

    protected final Texture texture;

    public LegacySingleSpriteSquareTexturedSkybox(LegacyProperties properties, LegacyConditions conditions, LegacyDecorations decorations, Blend blend, Texture texture) {
        super(properties, conditions, decorations, blend);
        this.texture = texture;
    }

    @Override
    protected void renderTexturedSkybox(SkyboxRenderContext context, Matrix4fStack matrix4fStack, RenderPipeline pipeline, GpuBufferSlice dynamicTransforms) {
        for (int face = 0; face < 6; ++face) {
            Matrix4f matrix4f = Utils.getMatrixForRotatedFace(face);
            LegacyFsbRenderer.drawTexturedQuad(pipeline, dynamicTransforms, matrix4f, this.texture.getTextureId(), LegacyUVRanges.SINGLE_SPRITE.byId(face));
        }
    }

    public Texture getTexture() {
        return this.texture;
    }

    @Override
    public List<Identifier> getTexturesToRegister() {
        return Stream.concat(super.getTexturesToRegister().stream(), Stream.of(this.texture.getTextureId()))
                .distinct()
                .toList();
    }
}
