package me.flashyreese.mods.nuit_interop.fabricskyboxes;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import me.flashyreese.mods.nuit.components.Condition;
import me.flashyreese.mods.nuit.components.Conditions;
import me.flashyreese.mods.nuit.components.RangeEntry;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

public record LegacyConditions(List<Identifier> biomes, List<Identifier> worlds, List<Identifier> dimensions,
                               List<Identifier> effects, List<LegacyWeather> weathers,
                               List<RangeEntry> xRanges, List<RangeEntry> yRanges, List<RangeEntry> zRanges,
                               LegacyLoop loop, boolean biomesExcluded, boolean worldsExcluded,
                               boolean dimensionsExcluded, boolean effectsExcluded, boolean weatherExcluded,
                               boolean xRangesExcluded, boolean yRangesExcluded, boolean zRangesExcluded) {
    public static final LegacyConditions DEFAULT = new LegacyConditions(ImmutableList.of(), ImmutableList.of(), ImmutableList.of(), ImmutableList.of(), ImmutableList.of(), ImmutableList.of(), ImmutableList.of(), ImmutableList.of(), LegacyLoop.DEFAULT);

    public static final Codec<LegacyConditions> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Identifier.CODEC.listOf().optionalFieldOf("biomes", ImmutableList.of()).forGetter(LegacyConditions::biomes),
            Identifier.CODEC.listOf().optionalFieldOf("worlds", ImmutableList.of()).forGetter(LegacyConditions::worlds),
            Identifier.CODEC.listOf().optionalFieldOf("dimensions", ImmutableList.of()).forGetter(LegacyConditions::dimensions),
            Identifier.CODEC.listOf().optionalFieldOf("effects", ImmutableList.of()).forGetter(LegacyConditions::effects),
            LegacyWeather.CODEC.listOf().optionalFieldOf("weather", ImmutableList.of()).forGetter(LegacyConditions::weathers),
            RangeEntry.CODEC.listOf().optionalFieldOf("xRanges", ImmutableList.of()).forGetter(LegacyConditions::xRanges),
            RangeEntry.CODEC.listOf().optionalFieldOf("yRanges", ImmutableList.of()).forGetter(LegacyConditions::yRanges),
            RangeEntry.CODEC.listOf().optionalFieldOf("zRanges", ImmutableList.of()).forGetter(LegacyConditions::zRanges),
            LegacyLoop.CODEC.optionalFieldOf("loop", LegacyLoop.DEFAULT).forGetter(LegacyConditions::loop)
    ).apply(instance, LegacyConditions::new));

    public LegacyConditions(List<Identifier> biomes, List<Identifier> worlds, List<Identifier> dimensions,
                            List<Identifier> effects, List<LegacyWeather> weathers,
                            List<RangeEntry> xRanges, List<RangeEntry> yRanges, List<RangeEntry> zRanges,
                            LegacyLoop loop) {
        this(biomes, worlds, dimensions, effects, weathers, xRanges, yRanges, zRanges, loop, false, false, false, false, false, false, false, false);
    }

    public Conditions toNuitConditions() {
        List<me.flashyreese.mods.nuit.components.Weather> mappedWeather = this.weathers.stream().map(LegacyWeather::toNuitWeather).toList();
        return new Conditions(
                new Condition<>(this.biomesExcluded, new ArrayList<>(this.biomes)),
                new Condition<>(this.worldsExcluded, new ArrayList<>(this.worlds)),
                new Condition<>(this.dimensionsExcluded, new ArrayList<>(this.dimensions)),
                new Condition<>(this.effectsExcluded, new ArrayList<>(this.effects)),
                new Condition<>(this.weatherExcluded, new ArrayList<>(mappedWeather)),
                new Condition<>(this.xRangesExcluded, new ArrayList<>(this.xRanges)),
                new Condition<>(this.yRangesExcluded, new ArrayList<>(this.yRanges)),
                new Condition<>(this.zRangesExcluded, new ArrayList<>(this.zRanges))
        );
    }
}
