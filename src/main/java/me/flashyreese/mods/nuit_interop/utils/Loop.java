package me.flashyreese.mods.nuit_interop.utils;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.amerebagatelle.mods.nuit.components.MinMaxEntry;
import io.github.amerebagatelle.mods.nuit.util.CodecUtils;

import java.util.List;

public record Loop(double days, List<MinMaxEntry> ranges) {
    public static final Loop DEFAULT = new Loop(7, ImmutableList.of());
    public static final Codec<Loop> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            CodecUtils.getClampedDouble(1, Double.MAX_VALUE).optionalFieldOf("days", 7.0d).forGetter(Loop::days),
            MinMaxEntry.CODEC.listOf().optionalFieldOf("ranges", ImmutableList.of()).forGetter(Loop::ranges)
    ).apply(instance, Loop::new));
}