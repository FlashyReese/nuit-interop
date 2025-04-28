package me.flashyreese.mods.nuit_interop.sky;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import me.flashyreese.mods.nuit.components.RangeEntry;
import me.flashyreese.mods.nuit_interop.utils.Utils;

import java.util.List;

public class Loop {
    public static final Loop DEFAULT = new Loop(7.0, ImmutableList.of());
    public static final Codec<Loop> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                            Utils.getClampedDouble(1.0, Double.MAX_VALUE).optionalFieldOf("days", 7.0).forGetter(Loop::getDays),
                            RangeEntry.CODEC.listOf().optionalFieldOf("ranges", ImmutableList.of()).forGetter(Loop::getRanges)
                    )
                    .apply(instance, Loop::new)
    );
    private final double days;
    private final List<RangeEntry> ranges;

    public Loop(double days, List<RangeEntry> ranges) {
        this.days = days;
        this.ranges = ranges;
    }

    public double getDays() {
        return this.days;
    }

    public List<RangeEntry> getRanges() {
        return this.ranges;
    }
}
