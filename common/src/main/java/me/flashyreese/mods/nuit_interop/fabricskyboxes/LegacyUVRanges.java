package me.flashyreese.mods.nuit_interop.fabricskyboxes;

import com.google.common.collect.Lists;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import me.flashyreese.mods.nuit.components.UVRange;

import java.util.List;

public class LegacyUVRanges {
    public static final LegacyUVRanges SINGLE_SPRITE = new LegacyUVRanges(
            new UVRange(1.0F / 3.0F, 1.0F / 2.0F, 2.0F / 3.0F, 1.0F),
            new UVRange(2.0F / 3.0F, 0.0F, 1.0F, 1.0F / 2.0F),
            new UVRange(2.0F / 3.0F, 1.0F / 2.0F, 1.0F, 1.0F),
            new UVRange(0.0F, 1.0F / 2.0F, 1.0F / 3.0F, 1.0F),
            new UVRange(1.0F / 3.0F, 0.0F, 2.0F / 3.0F, 1.0F / 2.0F),
            new UVRange(0.0F, 0.0F, 1.0F / 3.0F, 1.0F / 2.0F)
    );

    public static final Codec<LegacyUVRanges> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UVRange.CODEC.fieldOf("north").forGetter(LegacyUVRanges::north),
            UVRange.CODEC.fieldOf("south").forGetter(LegacyUVRanges::south),
            UVRange.CODEC.fieldOf("east").forGetter(LegacyUVRanges::east),
            UVRange.CODEC.fieldOf("west").forGetter(LegacyUVRanges::west),
            UVRange.CODEC.fieldOf("top").forGetter(LegacyUVRanges::top),
            UVRange.CODEC.fieldOf("bottom").forGetter(LegacyUVRanges::bottom)
    ).apply(instance, LegacyUVRanges::new));

    private final List<UVRange> uvRanges = Lists.newArrayList();
    private final UVRange north;
    private final UVRange south;
    private final UVRange east;
    private final UVRange west;
    private final UVRange top;
    private final UVRange bottom;

    public LegacyUVRanges(UVRange north, UVRange south, UVRange east, UVRange west, UVRange top, UVRange bottom) {
        this.north = north;
        this.south = south;
        this.east = east;
        this.west = west;
        this.top = top;
        this.bottom = bottom;
        this.uvRanges.add(bottom);
        this.uvRanges.add(north);
        this.uvRanges.add(south);
        this.uvRanges.add(top);
        this.uvRanges.add(east);
        this.uvRanges.add(west);
    }

    public UVRange north() {
        return this.north;
    }

    public UVRange south() {
        return this.south;
    }

    public UVRange east() {
        return this.east;
    }

    public UVRange west() {
        return this.west;
    }

    public UVRange top() {
        return this.top;
    }

    public UVRange bottom() {
        return this.bottom;
    }

    public UVRange byId(int index) {
        return this.uvRanges.get(index);
    }
}
