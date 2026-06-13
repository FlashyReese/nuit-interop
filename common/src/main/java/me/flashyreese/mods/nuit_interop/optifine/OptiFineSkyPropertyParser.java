package me.flashyreese.mods.nuit_interop.optifine;

import me.flashyreese.mods.nuit.components.RangeEntry;
import net.minecraft.IdentifierException;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.StringTokenizer;
import java.util.regex.Pattern;

final class OptiFineSkyPropertyParser {
    private static final Pattern SIGNED_RANGE_SEPARATOR = Pattern.compile("(\\d|\\))-(\\d|\\()");
    private static final int INVALID = Integer.MIN_VALUE;

    static List<String> tokens(String source, String delimiters) {
        if (source == null) {
            return List.of();
        }

        StringTokenizer tokenizer = new StringTokenizer(source.trim(), delimiters);
        List<String> values = new ArrayList<>();
        while (tokenizer.hasMoreTokens()) {
            values.add(tokenizer.nextToken());
        }
        return values;
    }

    static boolean parseBoolean(String value, boolean fallback) {
        if (value == null) {
            return fallback;
        }

        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if ("true".equals(normalized)) {
            return true;
        }
        if ("false".equals(normalized)) {
            return false;
        }
        return fallback;
    }

    static float parseFloat(String value, float fallback) {
        if (value == null) {
            return fallback;
        }

        try {
            return Float.parseFloat(value.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    static float[] parseAxis(String value) {
        List<String> parts = tokens(value, " ");
        if (parts.size() != 3) {
            return null;
        }

        float[] axis = new float[3];
        for (int i = 0; i < parts.size(); i++) {
            axis[i] = parseFloat(parts.get(i), Float.MIN_VALUE);
            if (axis[i] == Float.MIN_VALUE) {
                return null;
            }
        }

        if (axis[0] * axis[0] + axis[1] * axis[1] + axis[2] * axis[2] < 0.00001) {
            return null;
        }
        return axis;
    }

    static Identifier parseBiomeId(String biomeName) {
        String normalized = biomeName.toLowerCase(Locale.ROOT);
        Identifier explicitId = Identifier.tryParse(normalized);
        if (explicitId != null && normalized.contains(":")) {
            return explicitId;
        }

        try {
            return Identifier.tryBuild("minecraft", normalized);
        } catch (IdentifierException e) {
            return null;
        }
    }

    static List<RangeEntry> parseRanges(String source, boolean allowNegative) {
        List<RangeEntry> ranges = new ArrayList<>();
        for (String part : tokens(source, " ,")) {
            RangeEntry range = allowNegative ? parseSignedRange(part) : parseUnsignedRange(part);
            if (range == null) {
                return null;
            }
            ranges.add(range);
        }
        return ranges;
    }

    private static RangeEntry parseUnsignedRange(String value) {
        if (value == null) {
            return null;
        }

        if (value.contains("-")) {
            String[] parts = value.split("-");
            if (parts.length == 2) {
                int min = OptiFineSkyMath.parseInt(parts[0], -1);
                int max = OptiFineSkyMath.parseInt(parts[1], -1);
                if (min >= 0 && max >= 0) {
                    return new RangeEntry(Math.min(min, max), Math.max(min, max));
                }
            }
            return null;
        }

        int parsed = OptiFineSkyMath.parseInt(value, -1);
        return parsed >= 0 ? new RangeEntry(parsed, parsed) : null;
    }

    private static RangeEntry parseSignedRange(String value) {
        if (value == null || value.contains("=")) {
            return null;
        }

        String normalized = SIGNED_RANGE_SEPARATOR.matcher(value).replaceAll("$1=$2");
        if (normalized.contains("=")) {
            String[] parts = normalized.split("=");
            if (parts.length == 2) {
                int min = OptiFineSkyMath.parseInt(stripBrackets(parts[0]), INVALID);
                int max = OptiFineSkyMath.parseInt(stripBrackets(parts[1]), INVALID);
                if (min != INVALID && max != INVALID) {
                    return new RangeEntry(Math.min(min, max), Math.max(min, max));
                }
            }
            return null;
        }

        int parsed = OptiFineSkyMath.parseInt(stripBrackets(value), INVALID);
        return parsed != INVALID ? new RangeEntry(parsed, parsed) : null;
    }

    private static String stripBrackets(String value) {
        return value.startsWith("(") && value.endsWith(")") ? value.substring(1, value.length() - 1) : value;
    }
}
