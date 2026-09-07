package me.flashyreese.mods.nuit_interop.fabricskyboxes;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import me.flashyreese.mods.nuit.components.Texture;
import me.flashyreese.mods.nuit.components.UVRange;
import me.flashyreese.mods.nuit.render.NuitRenderBackend;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

public final class LegacyFsbRenderer {
    private LegacyFsbRenderer() {
    }

    public static Matrix4f getMatrixForRotatedFace(int face) {
        Matrix4f matrix = new Matrix4f();
        return switch (face) {
            case 1 -> matrix.rotate(Axis.XP.rotationDegrees(90.0F));
            case 2 -> matrix.rotate(Axis.XP.rotationDegrees(-90.0F)).rotate(Axis.YP.rotationDegrees(180.0F));
            case 3 -> matrix.rotate(Axis.XP.rotationDegrees(180.0F));
            case 4 -> matrix.rotate(Axis.ZP.rotationDegrees(90.0F)).rotate(Axis.YP.rotationDegrees(-90.0F));
            case 5 -> matrix.rotate(Axis.ZP.rotationDegrees(-90.0F)).rotate(Axis.YP.rotationDegrees(90.0F));
            default -> matrix;
        };
    }

    public static void drawTexturedMesh(MeshData meshData, ResourceLocation textureId) {
        NuitRenderBackend.drawTextured(meshData, GameRenderer::getPositionTexShader, textureId);
    }

    static void drawTexturedQuad(Matrix4f modelViewMatrix, Matrix4f faceMatrix, Texture texture) {
        drawTexturedQuad(modelViewMatrix, faceMatrix, texture.getTextureId(), texture.getUvRange());
    }

    static void drawTexturedQuad(Matrix4f modelViewMatrix, Matrix4f faceMatrix, ResourceLocation textureId, UVRange uvRange) {
        Matrix4f matrix = new Matrix4f(modelViewMatrix).mul(faceMatrix);
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        builder.addVertex(matrix, -100.0F, -100.0F, -100.0F).setUv(uvRange.minU(), uvRange.minV());
        builder.addVertex(matrix, -100.0F, -100.0F, 100.0F).setUv(uvRange.minU(), uvRange.maxV());
        builder.addVertex(matrix, 100.0F, -100.0F, 100.0F).setUv(uvRange.maxU(), uvRange.maxV());
        builder.addVertex(matrix, 100.0F, -100.0F, -100.0F).setUv(uvRange.maxU(), uvRange.minV());
        drawTexturedMesh(builder.buildOrThrow(), textureId);
    }

    public static void drawCelestialQuad(Matrix4f modelViewMatrix, ResourceLocation textureId, float size, float y, UVRange uvRange) {
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        if (y < 0.0F) {
            builder.addVertex(modelViewMatrix, -size, y, size).setUv(uvRange.minU(), uvRange.minV());
            builder.addVertex(modelViewMatrix, size, y, size).setUv(uvRange.maxU(), uvRange.minV());
            builder.addVertex(modelViewMatrix, size, y, -size).setUv(uvRange.maxU(), uvRange.maxV());
            builder.addVertex(modelViewMatrix, -size, y, -size).setUv(uvRange.minU(), uvRange.maxV());
        } else {
            builder.addVertex(modelViewMatrix, -size, y, -size).setUv(uvRange.minU(), uvRange.minV());
            builder.addVertex(modelViewMatrix, size, y, -size).setUv(uvRange.maxU(), uvRange.minV());
            builder.addVertex(modelViewMatrix, size, y, size).setUv(uvRange.maxU(), uvRange.maxV());
            builder.addVertex(modelViewMatrix, -size, y, size).setUv(uvRange.minU(), uvRange.maxV());
        }
        drawTexturedMesh(builder.buildOrThrow(), textureId);
    }
}
