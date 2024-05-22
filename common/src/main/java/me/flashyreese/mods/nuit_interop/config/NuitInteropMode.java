package me.flashyreese.mods.nuit_interop.config;

public enum NuitInteropMode {
    CONVERSION("mode.conversion"),
    NATIVE("mode.native");


    private final String translationKey;

    NuitInteropMode(String translationKey) {
        this.translationKey = translationKey;
    }

    public String getTranslationKey() {
        return this.translationKey;
    }
}
