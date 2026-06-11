package me.flashyreese.mods.nuit_interop.fabricskyboxes;

import com.google.common.collect.Lists;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import me.flashyreese.mods.nuit.components.Texture;
import me.flashyreese.mods.nuit.components.UVRange;
import net.minecraft.resources.Identifier;

import java.util.List;

public class LegacyTextures {
    public static final Codec<LegacyTextures> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Texture.CODEC.fieldOf("north").forGetter(LegacyTextures::north),
            Texture.CODEC.fieldOf("south").forGetter(LegacyTextures::south),
            Texture.CODEC.fieldOf("east").forGetter(LegacyTextures::east),
            Texture.CODEC.fieldOf("west").forGetter(LegacyTextures::west),
            Texture.CODEC.fieldOf("top").forGetter(LegacyTextures::top),
            Texture.CODEC.fieldOf("bottom").forGetter(LegacyTextures::bottom)
    ).apply(instance, LegacyTextures::new));

    private final List<Texture> textureList = Lists.newArrayList();
    private final Texture north;
    private final Texture south;
    private final Texture east;
    private final Texture west;
    private final Texture top;
    private final Texture bottom;

    public LegacyTextures(Texture north, Texture south, Texture east, Texture west, Texture top, Texture bottom) {
        this.north = north;
        this.south = south;
        this.east = east;
        this.west = west;
        this.top = top;
        this.bottom = bottom;
        this.textureList.add(bottom);
        this.textureList.add(north);
        this.textureList.add(south);
        this.textureList.add(top);
        this.textureList.add(east);
        this.textureList.add(west);
    }

    public LegacyTextures(Identifier north, Identifier south, Identifier east, Identifier west, Identifier top, Identifier bottom) {
        this(new Texture(north), new Texture(south), new Texture(east), new Texture(west), new Texture(top), new Texture(bottom));
    }

    public Texture north() {
        return this.north;
    }

    public Texture south() {
        return this.south;
    }

    public Texture east() {
        return this.east;
    }

    public Texture west() {
        return this.west;
    }

    public Texture top() {
        return this.top;
    }

    public Texture bottom() {
        return this.bottom;
    }

    public Texture byId(int index) {
        return this.textureList.get(index);
    }

    public List<Texture> all() {
        return this.textureList;
    }

    static Texture withUv(Texture texture, UVRange uvRange) {
        return new Texture(texture.getTextureId(), uvRange);
    }
}
