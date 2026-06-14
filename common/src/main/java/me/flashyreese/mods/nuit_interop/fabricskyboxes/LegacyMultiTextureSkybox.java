package me.flashyreese.mods.nuit_interop.fabricskyboxes;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import me.flashyreese.mods.nuit.components.Blend;
import me.flashyreese.mods.nuit.components.Texture;
import me.flashyreese.mods.nuit.components.UVRange;
import me.flashyreese.mods.nuit.mixin.SkyRendererAccessor;
import me.flashyreese.mods.nuit.skybox.TextureRegistrar;
import me.flashyreese.mods.nuit.util.Utils;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

import java.util.ArrayList;
import java.util.List;

public class LegacyMultiTextureSkybox extends LegacyTexturedSkybox implements TextureRegistrar {
    public static final Codec<LegacyMultiTextureSkybox> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            LegacyProperties.CODEC.fieldOf("properties").forGetter(skybox -> skybox.legacyProperties),
            LegacyConditions.CODEC.optionalFieldOf("conditions", LegacyConditions.DEFAULT).forGetter(skybox -> skybox.legacyConditions),
            LegacyDecorations.CODEC.optionalFieldOf("decorations", LegacyDecorations.DEFAULT).forGetter(skybox -> skybox.decorations),
            Blend.CODEC.optionalFieldOf("blend", Blend.normal()).forGetter(LegacyTexturedSkybox::getBlend),
            LegacyAnimation.CODEC.listOf().optionalFieldOf("animations", new ArrayList<>()).forGetter(LegacyMultiTextureSkybox::getAnimations)
    ).apply(instance, LegacyMultiTextureSkybox::new));

    private final List<LegacyAnimation> animations;
    private final float quadSize = 100.0F;
    private final UVRange quad = new UVRange(-this.quadSize, -this.quadSize, this.quadSize, this.quadSize);

    public LegacyMultiTextureSkybox(LegacyProperties properties, LegacyConditions conditions, LegacyDecorations decorations, Blend blend, List<LegacyAnimation> animations) {
        super(properties, conditions, decorations, blend);
        this.animations = animations;
    }

    @Override
    protected void renderTexturedSkybox(SkyRendererAccessor skyRendererAccessor, Matrix4fStack matrix4fStack, float tickDelta, Camera camera, GpuBufferSlice fogParameters, MultiBufferSource.BufferSource bufferSource, RenderPipeline pipeline, GpuBufferSlice dynamicTransforms) {
        for (LegacyAnimation animation : this.animations) {
            animation.tick();
        }

        for (int face = 0; face < 6; ++face) {
            Matrix4f matrix4f = Utils.getMatrixForRotatedFace(face);
            UVRange faceUVRange = LegacyUVRanges.SINGLE_SPRITE.byId(face);
            for (LegacyAnimation animation : this.animations) {
                UVRange intersect = Utils.findUVIntersection(faceUVRange, animation.uvRange());
                if (intersect != null && animation.currentFrame() != null) {
                    UVRange intersectionOnCurrentTexture = Utils.mapUVRanges(faceUVRange, this.quad, intersect);
                    UVRange intersectionOnCurrentFrame = Utils.mapUVRanges(animation.uvRange(), animation.currentFrame(), intersect);
                    this.drawPartial(pipeline, dynamicTransforms, matrix4f, animation.texture(), intersectionOnCurrentTexture, intersectionOnCurrentFrame);
                }
            }
        }
    }

    private void drawPartial(RenderPipeline pipeline, GpuBufferSlice dynamicTransforms, Matrix4f matrix4f, Texture texture, UVRange position, UVRange uv) {
        try (ByteBufferBuilder byteBufferBuilder = new ByteBufferBuilder(pipeline.getVertexFormat().getVertexSize() * 4)) {
            BufferBuilder builder = new BufferBuilder(byteBufferBuilder, pipeline.getVertexFormatMode(), pipeline.getVertexFormat());
            builder.addVertex(matrix4f, position.minU(), -this.quadSize, position.minV()).setUv(uv.minU(), uv.minV());
            builder.addVertex(matrix4f, position.minU(), -this.quadSize, position.maxV()).setUv(uv.minU(), uv.maxV());
            builder.addVertex(matrix4f, position.maxU(), -this.quadSize, position.maxV()).setUv(uv.maxU(), uv.maxV());
            builder.addVertex(matrix4f, position.maxU(), -this.quadSize, position.minV()).setUv(uv.maxU(), uv.minV());
            LegacyFsbRenderer.drawTexturedMesh(pipeline, builder.buildOrThrow(), dynamicTransforms, texture.getTextureId());
        }
    }

    public List<LegacyAnimation> getAnimations() {
        return this.animations;
    }

    @Override
    public List<Identifier> getTexturesToRegister() {
        return this.animations.stream().map(LegacyAnimation::texture).map(Texture::getTextureId).distinct().toList();
    }
}
