package me.flashyreese.mods.nuit_interop.fabricskyboxes;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import me.flashyreese.mods.nuit.components.Blend;
import me.flashyreese.mods.nuit.components.Texture;
import me.flashyreese.mods.nuit.mixin.SkyRendererAccessor;
import me.flashyreese.mods.nuit.skybox.TextureRegistrar;
import me.flashyreese.mods.nuit.util.Utils;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

import java.util.List;

public class LegacySquareTexturedSkybox extends LegacyTexturedSkybox implements TextureRegistrar {
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
    protected void renderTexturedSkybox(SkyRendererAccessor skyRendererAccessor, Matrix4fStack matrix4fStack, float tickDelta, Camera camera, GpuBufferSlice fogParameters, MultiBufferSource.BufferSource bufferSource, RenderPipeline pipeline, GpuBufferSlice dynamicTransforms) {
        for (int face = 0; face < 6; ++face) {
            Texture texture = this.textures.byId(face);
            Matrix4f matrix4f = Utils.getMatrixForRotatedFace(face);
            LegacyFsbRenderer.drawTexturedQuad(pipeline, dynamicTransforms, matrix4f, texture);
        }
    }

    public LegacyTextures getTextures() {
        return this.textures;
    }

    @Override
    public List<Identifier> getTexturesToRegister() {
        return this.textures.all().stream().map(Texture::getTextureId).distinct().toList();
    }
}
