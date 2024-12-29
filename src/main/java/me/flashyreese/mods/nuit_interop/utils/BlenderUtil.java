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

    public class NuitBlend {
        private final int sourceFactor;
        private final int destinationFactor;
        private final int equation;
        private final boolean redAlphaEnabled;
        private final boolean greenAlphaEnabled;
        private final boolean blueAlphaEnabled;
        private final boolean alphaEnabled;

        public NuitBlend(int sourceFactor, int destinationFactor, int equation, boolean redAlphaEnabled, boolean greenAlphaEnabled, boolean blueAlphaEnabled, boolean alphaEnabled) {
            this.sourceFactor = sourceFactor;
            this.destinationFactor = destinationFactor;
            this.equation = equation;
            this.redAlphaEnabled = redAlphaEnabled;
            this.greenAlphaEnabled = greenAlphaEnabled;
            this.blueAlphaEnabled = blueAlphaEnabled;
            this.alphaEnabled = alphaEnabled;
        }

        public int getSourceFactor() {
            return sourceFactor;
        }

        public int getDestinationFactor() {
            return destinationFactor;
        }

        public int getEquation() {
            return equation;
        }

        public boolean isRedAlphaEnabled() {
            return redAlphaEnabled;
        }

        public boolean isGreenAlphaEnabled() {
            return greenAlphaEnabled;
        }

        public boolean isBlueAlphaEnabled() {
            return blueAlphaEnabled;
        }

        public boolean isAlphaEnabled() {
            return alphaEnabled;
        }
    }
}
