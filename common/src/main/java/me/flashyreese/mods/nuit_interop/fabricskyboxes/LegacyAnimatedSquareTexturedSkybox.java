package me.flashyreese.mods.nuit_interop.fabricskyboxes;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import me.flashyreese.mods.nuit.components.Blend;
import me.flashyreese.mods.nuit.components.Texture;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.Util;
import org.joml.Matrix4f;

import java.util.List;
import java.util.stream.Stream;

public class LegacyAnimatedSquareTexturedSkybox extends LegacyTexturedSkybox {
    public static final Codec<LegacyAnimatedSquareTexturedSkybox> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            LegacyProperties.CODEC.fieldOf("properties").forGetter(skybox -> skybox.legacyProperties),
            LegacyConditions.CODEC.optionalFieldOf("conditions", LegacyConditions.DEFAULT).forGetter(skybox -> skybox.legacyConditions),
            LegacyDecorations.CODEC.optionalFieldOf("decorations", LegacyDecorations.DEFAULT).forGetter(skybox -> skybox.decorations),
            Blend.CODEC.optionalFieldOf("blend", Blend.normal()).forGetter(LegacyTexturedSkybox::getBlend),
            LegacyTextures.CODEC.listOf().fieldOf("animationTextures").forGetter(LegacyAnimatedSquareTexturedSkybox::getAnimationTextures),
            Codec.FLOAT.fieldOf("fps").forGetter(LegacyAnimatedSquareTexturedSkybox::getFps)
    ).apply(instance, LegacyAnimatedSquareTexturedSkybox::new));

    private final List<LegacyTextures> animationTextures;
    private final float fps;
    private final long frameTimeMillis;
    private int frameIndex;
    private long lastTime;

    public LegacyAnimatedSquareTexturedSkybox(LegacyProperties properties, LegacyConditions conditions, LegacyDecorations decorations, Blend blend, List<LegacyTextures> animationTextures, float fps) {
        super(properties, conditions, decorations, blend);
        this.animationTextures = animationTextures;
        this.fps = fps;
        this.frameTimeMillis = fps > 0.0F && fps <= 360.0F ? (long) (1000.0F / fps) : 16L;
    }

    @Override
    protected void renderTexturedSkybox(Matrix4f modelViewMatrix) {
        if (this.animationTextures.isEmpty()) {
            return;
        }

        this.updateFrame();
        LegacyTextures textures = this.animationTextures.get(this.frameIndex);
        for (int face = 0; face < 6; ++face) {
            Texture texture = textures.byId(face);
            Matrix4f faceMatrix = LegacyFsbRenderer.getMatrixForRotatedFace(face);
            LegacyFsbRenderer.drawTexturedQuad(modelViewMatrix, faceMatrix, texture);
        }
    }

    protected void updateFrame() {
        long currentTime = Util.getEpochMillis();
        if (this.lastTime == 0L) {
            this.lastTime = currentTime;
            return;
        }

        if (!Minecraft.getInstance().isPaused() && currentTime >= this.lastTime + this.frameTimeMillis) {
            this.frameIndex = (this.frameIndex + 1) % this.animationTextures.size();
            this.lastTime = currentTime;
        }
    }

    public List<LegacyTextures> getAnimationTextures() {
        return this.animationTextures;
    }

    public float getFps() {
        return this.fps;
    }

    @Override
    public List<ResourceLocation> getTexturesToRegister() {
        return Stream.concat(super.getTexturesToRegister().stream(), this.animationTextures.stream().flatMap(textures -> textures.all().stream()).map(Texture::getTextureId))
                .distinct()
                .toList();
    }
}
