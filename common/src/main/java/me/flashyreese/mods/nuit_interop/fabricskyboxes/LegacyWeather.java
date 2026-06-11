package me.flashyreese.mods.nuit_interop.fabricskyboxes;

import com.google.common.collect.ImmutableMap;
import com.mojang.serialization.Codec;

import java.util.Map;
import java.util.Objects;

public enum LegacyWeather {
    CLEAR("clear"),
    RAIN("rain"),
    BIOME_RAIN("rain_biome"),
    SNOW("snow"),
    THUNDER("thunder"),
    RAIN_THUNDER("rain_thunder"),
    SNOW_THUNDER("snow_thunder");

    private static final Map<String, LegacyWeather> VALUES;
    public static final Codec<LegacyWeather> CODEC = Codec.STRING.xmap(LegacyWeather::fromString, LegacyWeather::toString);

    static {
        ImmutableMap.Builder<String, LegacyWeather> builder = ImmutableMap.builder();
        for (LegacyWeather value : values()) {
            builder.put(value.name, value);
        }
        VALUES = builder.build();
    }

    private final String name;

    LegacyWeather(String name) {
        this.name = name;
    }

    public static LegacyWeather fromString(String name) {
        return Objects.requireNonNull(VALUES.get(name));
    }

    public me.flashyreese.mods.nuit.components.Weather toNuitWeather() {
        return switch (this) {
            case CLEAR -> me.flashyreese.mods.nuit.components.Weather.NO_PRECIPITATION;
            case RAIN -> me.flashyreese.mods.nuit.components.Weather.WORLD_PRECIPITATION;
            case BIOME_RAIN -> me.flashyreese.mods.nuit.components.Weather.RAIN_IN_BIOME;
            case SNOW -> me.flashyreese.mods.nuit.components.Weather.SNOW_IN_BIOME;
            case THUNDER -> me.flashyreese.mods.nuit.components.Weather.WORLD_THUNDERSTORM;
            case RAIN_THUNDER -> me.flashyreese.mods.nuit.components.Weather.THUNDER_IN_RAIN_BIOME;
            case SNOW_THUNDER -> me.flashyreese.mods.nuit.components.Weather.THUNDER_IN_SNOW_BIOME;
        };
    }

    @Override
    public String toString() {
        return this.name;
    }
}
