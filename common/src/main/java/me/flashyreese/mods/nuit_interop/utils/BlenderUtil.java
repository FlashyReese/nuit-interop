package me.flashyreese.mods.nuit_interop.utils;

import java.util.HashMap;
import java.util.Map;

public class BlenderUtil {

    private static BlenderUtil INSTANCE;
    public final Map<String, FSBBlend> BLEND_MAP = new HashMap<>();

    public BlenderUtil() {
        BLEND_MAP.put("alpha", new FSBBlend(770, 771, 32774, false, false, false, true));
        BLEND_MAP.put("add", new FSBBlend(770, 1, 32774, false, false, false, true));
        BLEND_MAP.put("subtract", new FSBBlend(775, 0, 32774, true, true, true, false));
        BLEND_MAP.put("multiply", new FSBBlend(774, 771, 32774, true, true, true, true));
        BLEND_MAP.put("dodge", new FSBBlend(1, 1, 32774, true, true, true, false));
        BLEND_MAP.put("burn", new FSBBlend(0, 769, 32774, true, true, true, false));
        BLEND_MAP.put("screen", new FSBBlend(1, 769, 32774, true, true, true, false));
        BLEND_MAP.put("overlay", new FSBBlend(774, 768, 32774, true, true, true, false));

        // Workaround for `replace`
        BLEND_MAP.put("replace", new FSBBlend(0, 1, 32774, false, false, false, true));
        //BLEND_MAP.put("replace", new FSBBlend(771, 1, 32774, false, false, false, true));
        //BLEND_MAP.put("replace", new FSBBlend(770, 771, 32774, false, false, false, true));
    }

    public static BlenderUtil getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new BlenderUtil();
        }
        return INSTANCE;
    }

    public class FSBBlend {
        private final int sourceFactor;
        private final int destinationFactor;
        private final int equation;
        private final boolean redAlphaEnabled;
        private final boolean greenAlphaEnabled;
        private final boolean blueAlphaEnabled;
        private final boolean alphaEnabled;

        public FSBBlend(int sourceFactor, int destinationFactor, int equation, boolean redAlphaEnabled, boolean greenAlphaEnabled, boolean blueAlphaEnabled, boolean alphaEnabled) {
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
