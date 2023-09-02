package dev.corgitaco.worldviewer.common.storage;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

public class OptimizedBiomeStorage {

    public final int[] values;
    private final int size;

    private Holder<Biome>[] lookUp;


    @SuppressWarnings("unchecked")
    public OptimizedBiomeStorage(CompoundTag tag, Registry<Biome> biomeRegistry) {
        this.values = tag.getIntArray("values");

        this.lookUp = tag.getList("lookup", CompoundTag.TAG_STRING).stream().map(tag1 -> (StringTag) tag1).map(StringTag::getAsString).map(ResourceLocation::new)
                .map(location -> ResourceKey.create(Registries.BIOME, location)).map(biomeRegistry::getHolderOrThrow).toArray(Holder[]::new);

        this.size = (int) Math.sqrt(this.values.length);
    }

    public OptimizedBiomeStorage(int[] values, Holder<Biome>[] lookUp, int size) {
        this.values = values;
        this.lookUp = lookUp;
        this.size = size;
    }

    public OptimizedBiomeStorage(int size) {
        this(new int[size * size], new Holder[0], size);
        Arrays.fill(values, -1);
    }

    public CompoundTag save() {
        CompoundTag compoundTag = new CompoundTag();
        compoundTag.putIntArray("values", this.values);

        ListTag tag = new ListTag();
        for (Holder<Biome> biomeHolder : this.lookUp) {
            tag.add(StringTag.valueOf(biomeHolder.unwrapKey().orElseThrow().location().toString()));
        }

        compoundTag.put("lookup", tag);

        return compoundTag;
    }


    public Holder<Biome> getBiome(int storageQuartX, int storageQuartZ, int worldX, int worldZ, BiomeGetter getter) {
        Holder<Biome> biome = getBiomeRaw(storageQuartX, storageQuartZ);

        if (biome == null) {
            Holder<Biome> holder = getter.get(worldX, worldZ);

            int lookupIdx = -1;
            Holder<Biome>[] up = this.lookUp;
            for (int i = 0; i < up.length; i++) {
                Holder<Biome> biomeHolder = up[i];
                if (biomeHolder == holder) {
                    lookupIdx = i;
                    break;
                }
            }

            if (lookupIdx == -1) {
                this.lookUp = Arrays.copyOf(this.lookUp, this.lookUp.length + 1);
                this.lookUp[this.lookUp.length - 1] = holder;
                lookupIdx = this.lookUp.length - 1;
            }

            values[getIndex(storageQuartX, storageQuartZ)] = lookupIdx;
            biome = holder;
        }

        return biome;
    }

    @Nullable
    public Holder<Biome> getBiomeRaw(int x, int z) {
        int value = values[getIndex(x, z)];
        if (value == -1) {
            return null;
        }
        return this.lookUp[value];
    }

    private int getIndex(int x, int z) {
        return x + z * (this.size);
    }

    @FunctionalInterface
    public interface BiomeGetter {

        Holder<Biome> get(int worldX, int worldZ);
    }
}