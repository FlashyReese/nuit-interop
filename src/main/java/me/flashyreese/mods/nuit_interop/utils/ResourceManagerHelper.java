package me.flashyreese.mods.nuit_interop.utils;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.stream.Stream;

public class ResourceManagerHelper {
    private final ResourceManager resourceManager;

    public ResourceManagerHelper(ResourceManager resourceManager) {
        this.resourceManager = resourceManager;
    }

    public Stream<ResourceLocation> searchIn(String parent) {
        return this.resourceManager.listResources(parent, path -> true).keySet().stream();
    }

    public InputStream getInputStream(ResourceLocation resourceLocation) {
        try {
            Optional<Resource> resource = this.resourceManager.getResource(resourceLocation);
            if (resource.isEmpty())
                return null;
            return resource.get().open();
        } catch (IOException e) {
            return null;
        }
    }
}