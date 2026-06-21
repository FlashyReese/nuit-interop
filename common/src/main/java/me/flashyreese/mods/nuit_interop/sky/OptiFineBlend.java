package me.flashyreese.mods.nuit_interop.sky;

import com.google.common.collect.ImmutableMap;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.platform.DestFactor;
import com.mojang.blaze3d.platform.SourceFactor;
import com.mojang.serialization.Codec;
import org.joml.Vector4f;

import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

public enum OptiFineBlend {
    ALPHA("alpha", new BlendFunction(SourceFactor.SRC_ALPHA, DestFactor.ONE_MINUS_SRC_ALPHA), alpha -> new Vector4f(1.0F, 1.0F, 1.0F, alpha)),
    ADD("add", new BlendFunction(SourceFactor.SRC_ALPHA, DestFactor.ONE), alpha -> new Vector4f(1.0F, 1.0F, 1.0F, alpha)),
    SUBTRACT("subtract", new BlendFunction(SourceFactor.ONE_MINUS_DST_COLOR, DestFactor.ZERO), alpha -> new Vector4f(alpha, alpha, alpha, 1.0F)),
    MULTIPLY("multiply", new BlendFunction(SourceFactor.DST_COLOR, DestFactor.ONE_MINUS_SRC_ALPHA), alpha -> new Vector4f(alpha, alpha, alpha, alpha)),
    DODGE("dodge", new BlendFunction(SourceFactor.ONE, DestFactor.ONE), alpha -> new Vector4f(alpha, alpha, alpha, 1.0F)),
    BURN("burn", new BlendFunction(SourceFactor.ZERO, DestFactor.ONE_MINUS_SRC_COLOR), alpha -> new Vector4f(alpha, alpha, alpha, 1.0F)),
    SCREEN("screen", new BlendFunction(SourceFactor.ONE, DestFactor.ONE_MINUS_SRC_COLOR), alpha -> new Vector4f(alpha, alpha, alpha, 1.0F)),
    OVERLAY("overlay", new BlendFunction(SourceFactor.DST_COLOR, DestFactor.SRC_COLOR), alpha -> new Vector4f(alpha, alpha, alpha, 1.0F)),
    REPLACE("replace", null, alpha -> new Vector4f(1.0F, 1.0F, 1.0F, alpha));

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
    private final BlendFunction blendFunction;
    private final Function<Float, Vector4f> colorModifierFunc;

    OptiFineBlend(String name, BlendFunction blendFunction, Function<Float, Vector4f> colorModifierFunc) {
        this.name = name;
        this.blendFunction = blendFunction;
        this.colorModifierFunc = colorModifierFunc;
    }

    public static OptiFineBlend byName(String name) {
        if (name == null) {
            return ADD;
        }
        return VALUES.getOrDefault(name.toLowerCase(Locale.ROOT).trim(), ADD);
    }

    public String getName() {
        return name;
    }

    public BlendFunction getBlendFunction() {
        return this.blendFunction;
    }

    public Vector4f getColorModifier(float alpha) {
        return this.colorModifierFunc.apply(alpha);
    }

    @Override
    public String toString() {
        return this.name;
    }
}
