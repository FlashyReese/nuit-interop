package me.flashyreese.mods.nuit_interop.sky;

import com.google.common.collect.ImmutableMap;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.platform.BlendFactor;
import com.mojang.serialization.Codec;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL46C;

import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

public enum OptiFineBlend {
    ALPHA("alpha", new BlendFunction(BlendFactor.SRC_ALPHA, BlendFactor.ONE_MINUS_SRC_ALPHA), alpha -> {
        GL46C.glBlendEquation(GL46C.GL_FUNC_ADD);
        return new Vector4f(1.0F, 1.0F, 1.0F, alpha);
    }),
    ADD("add", new BlendFunction(BlendFactor.SRC_ALPHA, BlendFactor.ONE), alpha -> {
        GL46C.glBlendEquation(GL46C.GL_FUNC_ADD);
        return new Vector4f(1.0F, 1.0F, 1.0F, alpha);
    }),
    SUBTRACT("subtract", new BlendFunction(BlendFactor.ONE_MINUS_DST_COLOR, BlendFactor.ZERO), alpha -> {
        GL46C.glBlendEquation(GL46C.GL_FUNC_ADD);
        return new Vector4f(alpha, alpha, alpha, 1.0F);
    }),
    MULTIPLY("multiply", new BlendFunction(BlendFactor.DST_COLOR, BlendFactor.ONE_MINUS_SRC_ALPHA), alpha -> {
        GL46C.glBlendEquation(GL46C.GL_FUNC_ADD);
        return new Vector4f(alpha, alpha, alpha, alpha);
    }),
    DODGE("dodge", new BlendFunction(BlendFactor.ONE, BlendFactor.ONE), alpha -> {
        GL46C.glBlendEquation(GL46C.GL_FUNC_ADD);
        return new Vector4f(alpha, alpha, alpha, 1.0F);
    }),
    BURN("burn", new BlendFunction(BlendFactor.ZERO, BlendFactor.ONE_MINUS_SRC_COLOR), alpha -> {
        GL46C.glBlendEquation(GL46C.GL_FUNC_ADD);
        return new Vector4f(alpha, alpha, alpha, 1.0F);
    }),
    SCREEN("screen", new BlendFunction(BlendFactor.ONE, BlendFactor.ONE_MINUS_SRC_COLOR), alpha -> {
        GL46C.glBlendEquation(GL46C.GL_FUNC_ADD);
        return new Vector4f(alpha, alpha, alpha, 1.0F);
    }),
    OVERLAY("overlay", new BlendFunction(BlendFactor.DST_COLOR, BlendFactor.SRC_COLOR), alpha -> {
        GL46C.glBlendEquation(GL46C.GL_FUNC_ADD);
        return new Vector4f(alpha, alpha, alpha, 1.0F);
    }),
    REPLACE("replace", null, alpha -> {
        GL46C.glBlendEquation(GL46C.GL_FUNC_ADD);
        return new Vector4f(1.0F, 1.0F, 1.0F, alpha);
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
    private final BlendFunction blendFunction;
    private final Function<Float, Vector4f> colorAndEquationFunc;

    OptiFineBlend(String name, BlendFunction blendFunction, Function<Float, Vector4f> colorAndEquationFunc) {
        this.name = name;
        this.blendFunction = blendFunction;
        this.colorAndEquationFunc = colorAndEquationFunc;
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

    public Vector4f applyEquationAndGetColor(float alpha) {
        return this.colorAndEquationFunc.apply(alpha);
    }

    @Override
    public String toString() {
        return this.name;
    }
}
