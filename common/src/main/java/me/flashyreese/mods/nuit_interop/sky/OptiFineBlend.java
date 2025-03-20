package me.flashyreese.mods.nuit_interop.sky;

import com.google.common.collect.ImmutableMap;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.serialization.Codec;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Consumer;

public enum OptiFineBlend {
    ALPHA("alpha", alpha -> {
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
    }),
    ADD("add", alpha -> {
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
    }),
    SUBTRACT("subtract", alpha -> {
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE_MINUS_DST_COLOR, GlStateManager.DestFactor.ZERO);
        RenderSystem.setShaderColor(alpha, alpha, alpha, 1.0F);
    }),
    MULTIPLY("multiply", alpha -> {
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.DST_COLOR, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.setShaderColor(alpha, alpha, alpha, alpha);
    }),
    DODGE("dodge", alpha -> {
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
        RenderSystem.setShaderColor(alpha, alpha, alpha, 1.0F);
    }),
    BURN("burn", alpha -> {
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR);
        RenderSystem.setShaderColor(alpha, alpha, alpha, 1.0F);
    }),
    SCREEN("screen", alpha -> {
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR);
        RenderSystem.setShaderColor(alpha, alpha, alpha, 1.0F);
    }),
    OVERLAY("overlay", alpha -> {
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.DST_COLOR, GlStateManager.DestFactor.SRC_COLOR);
        RenderSystem.setShaderColor(alpha, alpha, alpha, 1.0F);
    }),
    REPLACE("replace", alpha -> {
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
    });

    public static final Codec<OptiFineBlend> CODEC = Codec.STRING.xmap(OptiFineBlend::byName, OptiFineBlend::toString);
    private static final Map<String, OptiFineBlend> VALUES;

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

    public static OptiFineBlend byName(String name) {
        return Arrays.stream(OptiFineBlend.values()).filter(blend -> blend.toString().toLowerCase().equals(name)).findFirst().orElse(ADD);
    }

    public String getName() {
        return name;
    }

    public Consumer<Float> getBlendFunc() {
        return blendFunc;
    }

    @Override
    public String toString() {
        return this.name;
    }
}
