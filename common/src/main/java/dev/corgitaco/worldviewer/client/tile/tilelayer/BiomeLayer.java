package dev.corgitaco.worldviewer.client.tile.tilelayer;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.corgitaco.worldviewer.client.WVRenderType;
import dev.corgitaco.worldviewer.client.render.ColorUtils;
import dev.corgitaco.worldviewer.common.storage.DataTileManager;
import dev.corgitaco.worldviewer.common.storage.OptimizedBiomeStorage;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.Object2ByteOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferInt;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class BiomeLayer extends TileLayer {

    @Nullable
    private final int[] image;

    @Nullable
    private final OptimizedBiomeStorage biomesData;


    public BiomeLayer(DataTileManager tileManager, int y, int tileWorldX, int tileWorldZ, int size, int sampleResolution, LongSet sampledChunks, @Nullable BiomeLayer lowerResolution) {
        super(tileManager, y, tileWorldX, tileWorldZ, size, sampleResolution, sampledChunks, lowerResolution);

        OptimizedBiomeStorage data = new OptimizedBiomeStorage(size);

        int[] image = new int[size * size];
        BlockPos.MutableBlockPos worldPos = new BlockPos.MutableBlockPos();
        for (int sampleX = 0; sampleX < size; sampleX++) {
            for (int sampleZ = 0; sampleZ < size; sampleZ++) {
                if (Thread.currentThread().isInterrupted()) {
                    this.biomesData = null;
                    this.image = null;
                    return;
                }
                int worldX = tileWorldX + (sampleX * sampleResolution);
                int worldZ = tileWorldZ + (sampleZ * sampleResolution);
                worldPos.set(worldX, y, worldZ);

                sampledChunks.add(ChunkPos.asLong(worldPos));
                Holder<Biome> biomeHolder = tileManager.getBiome(worldX, worldZ);

                ResourceKey<Biome> biome = biomeHolder.unwrapKey().orElseThrow();

                data.getBiome(sampleX, sampleZ, worldX, worldZ, (worldX1, worldZ1) -> biomeHolder);
                int color = FAST_COLORS.computeIfAbsent(biome, biomeResourceKey -> {
                    Biome value = biomeHolder.value();
                    float baseTemperature = value.getBaseTemperature();
                    float lerp = Mth.inverseLerp(baseTemperature, -2, 2);
                    byte r = (byte) Mth.clampedLerp(137, 139, lerp);
                    byte g = (byte) Mth.clampedLerp(207, 0, lerp);
                    byte b = (byte) Mth.clampedLerp(240, 0, lerp);

                    return ColorUtils.ABGR.packABGR((byte) 255, r, g, b);
                });

                if (worldX <= 16 & worldZ <= 16 && worldX >= 0 && worldZ >= 0) {
                    color = ColorUtils.ABGR.packABGR(255, 255, 0, 255);
                }



                image[sampleX + sampleZ * size] = color;


            }
        }
        this.image = image;
        this.biomesData = data;
    }

    public BiomeLayer(int size, Path imagePath, Path dataPath, int sampleResolution) throws Exception {
        super(size, imagePath, dataPath, sampleResolution);
        File dataPathFile = dataPath.toFile();
        File imagePathFile = imagePath.toFile();
        if (dataPathFile.exists() && imagePathFile.exists()) {
            while (!imagePathFile.canRead() || !dataPathFile.canRead()) {
                Thread.sleep(1);
            }

            try {
                CompoundTag compoundTag = NbtIo.read(dataPathFile);
                this.biomesData = new OptimizedBiomeStorage(compoundTag.getCompound("biomes"), Minecraft.getInstance().level.registryAccess().registryOrThrow(Registries.BIOME));
                this.sampleResolution = compoundTag.getInt("res");

                BufferedImage bufferedImage = Files.exists(imagePath) ? ImageIO.read(imagePath.toFile()) : null;
                if (bufferedImage != null) {
                    if (bufferedImage.getWidth() != (size / this.sampleResolution)) {
                        throw new IllegalArgumentException("Improper image width.");
                    }
                    this.image = ((DataBufferInt) bufferedImage.getRaster().getDataBuffer()).getData();
                } else {
                    this.image = null;
                }

            } catch (IOException e) {
                throw e;
            }

        } else {
            this.biomesData = null;
            this.sampleResolution = Integer.MIN_VALUE;
            this.image = null;
        }
    }

    @Override
    public boolean isComplete() {
        return this.image != null && this.biomesData != null && this.sampleResolution > 0;
    }

    @Override
    @Nullable
    public List<Component> toolTip(double mouseScreenX, double mouseScreenY, int mouseWorldX, int mouseWorldZ, int mouseTileLocalX, int mouseTileLocalY) {
        int storageX = mouseTileLocalX / sampleResolution;
        int storageZ = mouseTileLocalY / sampleResolution;
        Holder<Biome> biomeResourceKey = this.biomesData.getBiome(storageX, storageZ, mouseWorldX, mouseWorldZ, (worldX1, worldZ1) -> null);

        return Collections.singletonList(Component.literal("Biome: " + biomeResourceKey.unwrapKey().get().location()).withStyle(Style.EMPTY.withColor(FAST_COLORS.getOrDefault(biomeResourceKey, ColorUtils.ABGR.packABGR((byte) 255, (byte) 255, (byte) 255, (byte) 255)))));
    }

    public static final Object2IntOpenHashMap<ResourceKey<Biome>> FAST_COLORS = Util.make(new Object2IntOpenHashMap<>(), map -> {
        map.put(Biomes.BADLANDS, ColorUtils.ABGR.tryParseColor("0xD94515"));
        map.put(Biomes.BAMBOO_JUNGLE, ColorUtils.ABGR.tryParseColor("0x2C4205"));
        map.put(Biomes.BEACH, ColorUtils.ABGR.tryParseColor("0xFADE55"));
        map.put(Biomes.DESERT, ColorUtils.ABGR.tryParseColor("0xFA9418"));
        map.put(Biomes.BIRCH_FOREST, ColorUtils.ABGR.tryParseColor("0x307444"));
        map.put(Biomes.PLAINS, ColorUtils.ABGR.tryParseColor("0x8DB360"));
        map.put(Biomes.WINDSWEPT_HILLS, ColorUtils.ABGR.tryParseColor("0x606060"));
        map.put(Biomes.FOREST, ColorUtils.ABGR.tryParseColor("0x056621"));
        map.put(Biomes.TAIGA, ColorUtils.ABGR.tryParseColor("0x0B6659"));
        map.put(Biomes.SWAMP, ColorUtils.ABGR.tryParseColor("0x07F9B2"));
        map.put(Biomes.RIVER, ColorUtils.ABGR.tryParseColor("0x0000FF"));
        map.put(Biomes.NETHER_WASTES, ColorUtils.ABGR.tryParseColor("0xFF0000"));
        map.put(Biomes.THE_VOID, ColorUtils.ABGR.tryParseColor("0x8080FF"));
        map.put(Biomes.FROZEN_RIVER, ColorUtils.ABGR.tryParseColor("0xA0A0A0"));
        map.put(Biomes.SNOWY_PLAINS, ColorUtils.ABGR.tryParseColor("0xFFFFFF"));
        map.put(Biomes.MUSHROOM_FIELDS, ColorUtils.ABGR.tryParseColor("0xFF00FF"));
        map.put(Biomes.JUNGLE, ColorUtils.ABGR.tryParseColor("0x537B09"));
        map.put(Biomes.SPARSE_JUNGLE, ColorUtils.ABGR.tryParseColor("0x628B17"));
        map.put(Biomes.STONY_SHORE, ColorUtils.ABGR.tryParseColor("0xA2A284"));
        map.put(Biomes.SNOWY_BEACH, ColorUtils.ABGR.tryParseColor("0xFAF0C0"));
        map.put(Biomes.DARK_FOREST, ColorUtils.ABGR.tryParseColor("0x40511A"));
        map.put(Biomes.SNOWY_TAIGA, ColorUtils.ABGR.tryParseColor("0x31554A"));
        map.put(Biomes.OLD_GROWTH_PINE_TAIGA, ColorUtils.ABGR.tryParseColor("0x596651"));
        map.put(Biomes.OLD_GROWTH_SPRUCE_TAIGA, ColorUtils.ABGR.tryParseColor("0x818E79"));
        map.put(Biomes.SAVANNA, ColorUtils.ABGR.tryParseColor("0xBDB25F"));
        map.put(Biomes.SAVANNA_PLATEAU, ColorUtils.ABGR.tryParseColor("0xA79D24"));
        map.put(Biomes.WOODED_BADLANDS, ColorUtils.ABGR.tryParseColor("0xCA8C65"));
        map.put(Biomes.ERODED_BADLANDS, ColorUtils.ABGR.tryParseColor("0xFF6D3D"));
        map.put(Biomes.SUNFLOWER_PLAINS, ColorUtils.ABGR.tryParseColor("0xB5DB88"));
        map.put(Biomes.FLOWER_FOREST, ColorUtils.ABGR.tryParseColor("0x2D8E49"));
        map.put(Biomes.ICE_SPIKES, ColorUtils.ABGR.tryParseColor("0xB4DCDC"));
        map.put(Biomes.OCEAN, ColorUtils.ABGR.tryParseColor("0x000070"));
        map.put(Biomes.DEEP_OCEAN, ColorUtils.ABGR.tryParseColor("0x000030"));
        map.put(Biomes.COLD_OCEAN, ColorUtils.ABGR.tryParseColor("0x0056d6"));
        map.put(Biomes.DEEP_COLD_OCEAN, ColorUtils.ABGR.tryParseColor("0x004ecc"));
        map.put(Biomes.LUKEWARM_OCEAN, ColorUtils.ABGR.tryParseColor("0x45ADF2"));
        map.put(Biomes.DEEP_LUKEWARM_OCEAN, ColorUtils.ABGR.tryParseColor("0x3BA3E8"));
        map.put(Biomes.FROZEN_OCEAN, ColorUtils.ABGR.tryParseColor("0x9090A0"));
        map.put(Biomes.DEEP_FROZEN_OCEAN, ColorUtils.ABGR.tryParseColor("0x676791"));
        map.put(Biomes.FROZEN_PEAKS, ColorUtils.ABGR.tryParseColor("0x8BC0FC"));
        map.put(Biomes.WINDSWEPT_SAVANNA, ColorUtils.ABGR.tryParseColor("0xE5DA87"));
        map.put(Biomes.WINDSWEPT_FOREST, ColorUtils.ABGR.tryParseColor("0x589C6C"));
        map.put(Biomes.STONY_PEAKS, ColorUtils.ABGR.tryParseColor("0xC0C0C0"));
        map.put(Biomes.JAGGED_PEAKS, ColorUtils.ABGR.tryParseColor("0x969696"));
        map.put(Biomes.GROVE, ColorUtils.ABGR.tryParseColor("0x42FFBa"));
        map.put(Biomes.SOUL_SAND_VALLEY, ColorUtils.ABGR.tryParseColor("0x964B00"));
        map.put(Biomes.WARPED_FOREST, ColorUtils.ABGR.tryParseColor("0x89cff0"));
        map.put(Biomes.BASALT_DELTAS, ColorUtils.ABGR.tryParseColor("0x5A5A5A"));
        map.put(Biomes.CRIMSON_FOREST, ColorUtils.ABGR.tryParseColor("0xDC143C"));
    });


    @Override
    @Nullable
    public CompoundTag tag() {
        CompoundTag compoundTag = new CompoundTag();
        compoundTag.put("biomes", this.biomesData.save());
        compoundTag.putInt("res", this.sampleResolution);
        return compoundTag;
    }

    @Override
    public int[] image() {
        return this.image;
    }
}
