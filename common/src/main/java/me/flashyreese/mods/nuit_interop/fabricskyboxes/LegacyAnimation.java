package me.flashyreese.mods.nuit_interop.fabricskyboxes;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.ints.Int2LongArrayMap;
import me.flashyreese.mods.nuit.components.Texture;
import me.flashyreese.mods.nuit.components.UVRange;
import me.flashyreese.mods.nuit.util.CodecUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;

import java.util.Map;

public class LegacyAnimation {
    public static final Codec<LegacyAnimation> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Texture.CODEC.fieldOf("texture").forGetter(LegacyAnimation::texture),
            UVRange.CODEC.fieldOf("uvRanges").forGetter(LegacyAnimation::uvRange),
            LegacyUtils.clampedInt(1, Integer.MAX_VALUE).fieldOf("gridColumns").forGetter(LegacyAnimation::gridColumns),
            LegacyUtils.clampedInt(1, Integer.MAX_VALUE).fieldOf("gridRows").forGetter(LegacyAnimation::gridRows),
            LegacyUtils.clampedInt(1, Integer.MAX_VALUE).fieldOf("duration").forGetter(LegacyAnimation::duration),
            Codec.BOOL.optionalFieldOf("interpolate", true).forGetter(LegacyAnimation::interpolate),
            CodecUtils.unboundedMapFixed(Integer.class, Codec.LONG, Int2LongArrayMap::new).optionalFieldOf("frameDuration", CodecUtils.fastUtilInt2LongArrayMap()).forGetter(LegacyAnimation::frameDuration)
    ).apply(instance, LegacyAnimation::new));

    private final Texture texture;
    private final UVRange uvRange;
    private final int gridColumns;
    private final int gridRows;
    private final int duration;
    private final boolean interpolate;
    private final Map<Integer, Long> frameDuration;
    private UVRange currentFrame;
    private int index;
    private long nextTime;

    public LegacyAnimation(Texture texture, UVRange uvRange, int gridColumns, int gridRows, int duration, boolean interpolate, Map<Integer, Long> frameDuration) {
        this.texture = texture;
        this.uvRange = uvRange;
        this.gridColumns = gridColumns;
        this.gridRows = gridRows;
        this.duration = duration;
        this.interpolate = interpolate;
        this.frameDuration = frameDuration;
    }

    public void tick() {
        long currentTime = Util.getEpochMillis();
        if (this.nextTime <= currentTime && !Minecraft.getInstance().isPaused()) {
            this.index = (this.index + 1) % (this.gridRows * this.gridColumns);
            this.currentFrame = this.calculateNextFrameUVRange(this.index);
            this.nextTime = currentTime + this.frameDuration.getOrDefault(this.index + 1, (long) this.duration);
        }
    }

    private UVRange calculateNextFrameUVRange(int nextFrameIndex) {
        float frameWidth = 1.0F / this.gridColumns;
        float frameHeight = 1.0F / this.gridRows;
        float minU = (float) (nextFrameIndex / this.gridRows) * frameWidth;
        float maxU = minU + frameWidth;
        float minV = (float) (nextFrameIndex % this.gridRows) * frameHeight;
        float maxV = minV + frameHeight;
        return new UVRange(minU, minV, maxU, maxV);
    }

    public Texture texture() {
        return this.texture;
    }

    public UVRange uvRange() {
        return this.uvRange;
    }

    public int gridColumns() {
        return this.gridColumns;
    }

    public int gridRows() {
        return this.gridRows;
    }

    public int duration() {
        return this.duration;
    }

    public boolean interpolate() {
        return this.interpolate;
    }

    public Map<Integer, Long> frameDuration() {
        return this.frameDuration;
    }

    public UVRange currentFrame() {
        return this.currentFrame;
    }
}
