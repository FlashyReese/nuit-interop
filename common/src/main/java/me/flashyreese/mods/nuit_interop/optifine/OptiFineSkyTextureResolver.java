package me.flashyreese.mods.nuit_interop.optifine;

import me.flashyreese.mods.nuit_interop.utils.ResourceManagerHelper;
import net.minecraft.IdentifierException;
import net.minecraft.resources.Identifier;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class OptiFineSkyTextureResolver {
    private static final Pattern SKY_PROPERTIES = Pattern.compile("sky(\\d+)\\.properties$");

    static Identifier resolve(String source, ResourceManagerHelper resourceManager, Identifier propertiesId) {
        for (String candidate : sourceCandidates(source, propertiesId)) {
            Identifier textureId = toTextureId(candidate, propertiesId);
            if (textureId != null && exists(resourceManager, textureId)) {
                return textureId;
            }
        }
        return null;
    }

    private static List<String> sourceCandidates(String source, Identifier propertiesId) {
        List<String> candidates = new ArrayList<>();
        if (source != null) {
            candidates.add(source);
            return candidates;
        }

        candidates.add(defaultNumericSource(propertiesId));
        String adjacentPng = "./" + fileName(propertiesId.getPath()).replace(".properties", ".png");
        if (!candidates.contains(adjacentPng)) {
            candidates.add(adjacentPng);
        }
        return candidates;
    }

    private static Identifier toTextureId(String source, Identifier propertiesId) {
        String sourcePath = source.trim();
        if (sourcePath.isEmpty()) {
            return null;
        }

        if (sourcePath.startsWith("minecraft:")) {
            return buildId("minecraft", withPngSuffix(sourcePath.substring("minecraft:".length())));
        }

        String path = normalizePackPath(sourcePath, basePath(propertiesId.getPath()));
        path = withPngSuffix(path);

        String[] assetParts = path.split("/", 3);
        if (assetParts.length == 3 && assetParts[0].equals("assets")) {
            return buildId(assetParts[1], assetParts[2]);
        }

        Identifier explicitId = Identifier.tryParse(path);
        if (explicitId != null && path.contains(":")) {
            return explicitId;
        }

        return buildId(propertiesId.getNamespace(), path);
    }

    private static String normalizePackPath(String path, String basePath) {
        if (path.startsWith("assets/minecraft/")) {
            return path.substring("assets/minecraft/".length());
        }
        if (path.startsWith("./")) {
            String relativePath = path.substring(2);
            return basePath.endsWith("/") ? basePath + relativePath : basePath + "/" + relativePath;
        }
        if (path.startsWith("/~")) {
            path = path.substring(1);
        }
        if (path.startsWith("~/")) {
            return "optifine/" + path.substring(2);
        }
        if (path.startsWith("/")) {
            return "optifine/" + path.substring(1);
        }
        return path;
    }

    private static boolean exists(ResourceManagerHelper resourceManager, Identifier textureId) {
        try (InputStream stream = resourceManager.getInputStream(textureId)) {
            return stream != null;
        } catch (Exception e) {
            return false;
        }
    }

    private static Identifier buildId(String namespace, String path) {
        try {
            return Identifier.tryBuild(namespace, path);
        } catch (IdentifierException e) {
            return null;
        }
    }

    private static String defaultNumericSource(Identifier propertiesId) {
        Matcher matcher = SKY_PROPERTIES.matcher(fileName(propertiesId.getPath()));
        if (matcher.matches()) {
            return matcher.group(1) + ".png";
        }
        return fileName(propertiesId.getPath()).replace(".properties", ".png");
    }

    private static String basePath(String path) {
        int lastSlash = path.lastIndexOf('/');
        return lastSlash < 0 ? "" : path.substring(0, lastSlash);
    }

    private static String fileName(String path) {
        int lastSlash = path.lastIndexOf('/');
        return lastSlash < 0 ? path : path.substring(lastSlash + 1);
    }

    private static String withPngSuffix(String path) {
        return path.endsWith(".png") ? path : path + ".png";
    }
}
