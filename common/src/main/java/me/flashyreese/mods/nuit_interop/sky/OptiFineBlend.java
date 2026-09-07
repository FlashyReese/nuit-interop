package me.flashyreese.mods.nuit_interop.sky;

import com.google.common.collect.ImmutableMap;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.serialization.Codec;

import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

public enum OptiFineBlend {
    ALPHA("alpha", blend(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, alpha -> RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha))),
    ADD("add", blend(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE, alpha -> RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha))),
    SUBTRACT("subtract", blend(GlStateManager.SourceFactor.ONE_MINUS_DST_COLOR, GlStateManager.DestFactor.ZERO, alpha -> RenderSystem.setShaderColor(alpha, alpha, alpha, 1.0F))),
    MULTIPLY("multiply", blend(GlStateManager.SourceFactor.DST_COLOR, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, alpha -> RenderSystem.setShaderColor(alpha, alpha, alpha, alpha))),
    DODGE("dodge", blend(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE, alpha -> RenderSystem.setShaderColor(alpha, alpha, alpha, 1.0F))),
    BURN("burn", blend(GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR, alpha -> RenderSystem.setShaderColor(alpha, alpha, alpha, 1.0F))),
    SCREEN("screen", blend(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR, alpha -> RenderSystem.setShaderColor(alpha, alpha, alpha, 1.0F))),
    OVERLAY("overlay", blend(GlStateManager.SourceFactor.DST_COLOR, GlStateManager.DestFactor.SRC_COLOR, alpha -> RenderSystem.setShaderColor(alpha, alpha, alpha, 1.0F))),
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
    private final Consumer<Float> blendFunction;

    OptiFineBlend(String name, Consumer<Float> blendFunction) {
        this.name = name;
        this.blendFunction = blendFunction;
    }

    private static Consumer<Float> blend(GlStateManager.SourceFactor source, GlStateManager.DestFactor destination, Consumer<Float> colorModifier) {
        return alpha -> {
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(source, destination);
            colorModifier.accept(alpha);
        };
    }

    public static OptiFineBlend byName(String name) {
        if (name == null) {
            return ADD;
        }
        return VALUES.getOrDefault(name.toLowerCase(Locale.ROOT).trim(), ADD);
    }

    public void apply(float alpha) {
        this.blendFunction.accept(alpha);
    }

    public String getName() {
        return this.name;
    }

    @Override
    public String toString() {
        return this.name;
    }
}
