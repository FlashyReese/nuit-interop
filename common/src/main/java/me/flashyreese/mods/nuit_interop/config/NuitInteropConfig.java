package me.flashyreese.mods.nuit_interop.config;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Modifier;

public class NuitInteropConfig {
    private static final Logger logger = LoggerFactory.getLogger("Nuit-Interop Config");
    private static final Gson GSON = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .setPrettyPrinting()
            .excludeFieldsWithModifiers(Modifier.PRIVATE)
            .create();

    public static final NuitInteropConfig INSTANCE = NuitInteropConfig.load(Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("nuit-interop.json").toFile());
    public NuitInteropMode mode = NuitInteropMode.NATIVE;
    public boolean interoperability = true;
    public boolean debugMode = true;
    public boolean preferNuitNative = true;
    public boolean processOptiFine = true;
    public boolean processMCPatcher = false;
    private File file;

    public static NuitInteropConfig load(File file) {
        NuitInteropConfig config;

        if (file.exists()) {
            try (FileReader reader = new FileReader(file)) {
                config = GSON.fromJson(reader, NuitInteropConfig.class);
            } catch (Exception e) {
                logger.error("Could not parse config, falling back to defaults!", e);
                config = new NuitInteropConfig();
            }
        } else {
            config = new NuitInteropConfig();
        }

        config.file = file;
        config.writeChanges();
        return config;
    }

    public void writeChanges() {
        File dir = this.file.getParentFile();
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                throw new RuntimeException("Could not create parent directories");
            }
        } else if (!dir.isDirectory()) {
            throw new RuntimeException("The parent file is not a directory");
        }

        try (FileWriter writer = new FileWriter(this.file)) {
            GSON.toJson(this, writer);
        } catch (IOException e) {
            throw new RuntimeException("Could not save configuration file", e);
        }
    }
}