package me.flashyreese.mods.nuit_interop.fabricskyboxes;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import me.flashyreese.mods.nuit.components.RangeEntry;

import java.util.List;

public record LegacyLoop(double days, List<RangeEntry> ranges) {
    public static final LegacyLoop DEFAULT = new LegacyLoop(7.0D, ImmutableList.of());
    public static final Codec<LegacyLoop> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            LegacyUtils.clampedDouble(1.0D, Double.MAX_VALUE).optionalFieldOf("days", 7.0D).forGetter(LegacyLoop::days),
            RangeEntry.CODEC.listOf().optionalFieldOf("ranges", ImmutableList.of()).forGetter(LegacyLoop::ranges)
    ).apply(instance, LegacyLoop::new));
}
