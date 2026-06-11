package me.flashyreese.mods.nuit_interop.fabricskyboxes;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import me.flashyreese.mods.nuit.mixin.SkyRendererAccessor;
import me.flashyreese.mods.nuit.skybox.TextureRegistrar;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4fStack;

import java.util.ArrayList;
import java.util.List;

public class LegacyDecorationsSkybox extends LegacyAbstractSkybox implements TextureRegistrar {
    public LegacyDecorationsSkybox(LegacyProperties properties, LegacyConditions conditions, LegacyDecorations decorations) {
        super(properties, conditions, decorations);
    }

    @Override
    public void render(SkyRendererAccessor skyRendererAccessor, Matrix4fStack matrix4fStack, float tickDelta, Camera camera, GpuBufferSlice fogParameters, MultiBufferSource.BufferSource bufferSource) {
        if (this.alpha <= 0.0F) {
            return;
        }

        this.renderDecorations(skyRendererAccessor, matrix4fStack, tickDelta, camera);
    }

    @Override
    public List<Identifier> getTexturesToRegister() {
        List<Identifier> textures = new ArrayList<>();
        if (this.decorations.sunEnabled()) {
            textures.add(this.decorations.sunTexture());
        }
        if (this.decorations.moonEnabled()) {
            textures.add(this.decorations.moonTexture());
        }
        return textures;
    }
}
