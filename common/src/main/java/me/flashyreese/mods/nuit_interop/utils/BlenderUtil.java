package me.flashyreese.mods.nuit_interop.utils;

import java.util.HashMap;
import java.util.Map;

public class BlenderUtil {
    private static BlenderUtil INSTANCE;
    public final Map<String, NuitBlend> BLEND_MAP = new HashMap<>();

    public BlenderUtil() {
        BLEND_MAP.put("alpha", new NuitBlend(770, 771, 32774, false, false, false, true));
        BLEND_MAP.put("add", new NuitBlend(770, 1, 32774, false, false, false, true));
        BLEND_MAP.put("subtract", new NuitBlend(775, 0, 32774, true, true, true, false));
        BLEND_MAP.put("multiply", new NuitBlend(774, 771, 32774, true, true, true, true));
        BLEND_MAP.put("dodge", new NuitBlend(1, 1, 32774, true, true, true, false));
        BLEND_MAP.put("burn", new NuitBlend(0, 769, 32774, true, true, true, false));
        BLEND_MAP.put("screen", new NuitBlend(1, 769, 32774, true, true, true, false));
        BLEND_MAP.put("overlay", new NuitBlend(774, 768, 32774, true, true, true, false));

        // Workaround for `replace`
        BLEND_MAP.put("replace", new NuitBlend(0, 1, 32774, false, false, false, true));
        //BLEND_MAP.put("replace", new NuitBlend(771, 1, 32774, false, false, false, true));
        //BLEND_MAP.put("replace", new NuitBlend(770, 771, 32774, false, false, false, true));
    }

    public static BlenderUtil getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new BlenderUtil();
        }

        return INSTANCE;
    }

    public record NuitBlend(int sourceFactor, int destinationFactor, int equation,
                            boolean redAlphaEnabled, boolean greenAlphaEnabled, boolean blueAlphaEnabled,
                            boolean alphaEnabled) {
    }
}