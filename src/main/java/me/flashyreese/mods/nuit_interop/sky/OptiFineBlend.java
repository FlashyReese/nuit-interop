package me.flashyreese.mods.nuit_interop.sky;

import com.google.common.collect.ImmutableMap;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.serialization.Codec;

import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

public enum OptiFineBlend {
    ALPHA("alpha", alpha -> {
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
    }),
    ADD("add", alpha -> {
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
    }),
    SUBTRACT("subtract", alpha -> {
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SrcFactor.ONE_MINUS_DST_COLOR, GlStateManager.DstFactor.ZERO);
        RenderSystem.setShaderColor(alpha, alpha, alpha, 1.0F);
    }),
    MULTIPLY("multiply", alpha -> {
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SrcFactor.DST_COLOR, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.setShaderColor(alpha, alpha, alpha, alpha);
    }),
    DODGE("dodge", alpha -> {
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SrcFactor.ONE, GlStateManager.DstFactor.ONE);
        RenderSystem.setShaderColor(alpha, alpha, alpha, 1.0F);
    }),
    BURN("burn", alpha -> {
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SrcFactor.ZERO, GlStateManager.DstFactor.ONE_MINUS_SRC_COLOR);
        RenderSystem.setShaderColor(alpha, alpha, alpha, 1.0F);
    }),
    SCREEN("screen", alpha -> {
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SrcFactor.ONE, GlStateManager.DstFactor.ONE_MINUS_SRC_COLOR);
        RenderSystem.setShaderColor(alpha, alpha, alpha, 1.0F);
    }),
    OVERLAY("overlay", alpha -> {
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SrcFactor.DST_COLOR, GlStateManager.DstFactor.SRC_COLOR);
        RenderSystem.setShaderColor(alpha, alpha, alpha, 1.0F);
    }),
    REPLACE("replace", alpha -> {
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
    });

    private static final Map<String, OptiFineBlend> VALUES;
    public static final Codec<OptiFineBlend> CODEC = Codec.STRING.xmap(OptiFineBlend::fromString, OptiFineBlend::toString);

    static {
        ImmutableMap.Builder<String, OptiFineBlend> builder = ImmutableMap.builder();
        for (OptiFineBlend value : values()) {
            builder.put(value.name, value);
        }
        VALUES = builder.build();
    }

    private final String name;
    private final Consumer<Float> blendFunc;

    OptiFineBlend(String name, Consumer<Float> blendFunc) {
        this.name = name;
        this.blendFunc = blendFunc;
    }

    public String getName() {
        return name;
    }

    public Consumer<Float> getBlendFunc() {
        return blendFunc;
    }

    public static OptiFineBlend fromString(String name) {
        return Objects.requireNonNull(VALUES.get(name));
    }

    @Override
    public String toString() {
        return this.name;
    }
}
