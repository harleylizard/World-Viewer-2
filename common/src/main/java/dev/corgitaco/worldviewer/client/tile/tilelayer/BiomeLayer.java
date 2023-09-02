package dev.corgitaco.worldviewer.client.tile.tilelayer;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.corgitaco.worldviewer.client.ClientUtil;
import dev.corgitaco.worldviewer.client.WVRenderType;
import dev.corgitaco.worldviewer.common.storage.DataTileManager;
import dev.corgitaco.worldviewer.common.storage.OptimizedBiomeStorage;
import it.unimi.dsi.fastutil.longs.LongSet;
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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class BiomeLayer extends TileLayer {

    @Nullable
    private final NativeImage image;

    @Nullable
    private final OptimizedBiomeStorage biomesData;


    public BiomeLayer(DataTileManager tileManager, int y, int tileWorldX, int tileWorldZ, int size, int sampleResolution, LongSet sampledChunks, @Nullable BiomeLayer lowerResolution) {
        super(tileManager, y, tileWorldX, tileWorldZ, size, sampleResolution, sampledChunks, lowerResolution);
        int sampledSize = size / sampleResolution;

        OptimizedBiomeStorage data = new OptimizedBiomeStorage(sampledSize);

        if (lowerResolution != null) {

            int previousSampledSize = size / lowerResolution.sampleResolution;

            int scale = sampledSize / previousSampledSize;
            for (int sampleX = 0; sampleX < previousSampledSize; sampleX++) {
                for (int sampleZ = 0; sampleZ < previousSampledSize; sampleZ++) {
                    int scaledX = sampleX * scale;
                    int scaledZ = sampleZ * scale;
                    @Nullable
                    Holder<Biome> biomeRaw = lowerResolution.biomesData.getBiomeRaw(sampleX, sampleZ);
                    if (biomeRaw != null) {
                        data.getBiome(scaledX, scaledZ, 0, 0, ((worldX1, worldZ1) -> biomeRaw));
                    }
                }
            }
        }


        NativeImage image = ClientUtil.createImage(sampledSize, sampledSize, true);
        BlockPos.MutableBlockPos worldPos = new BlockPos.MutableBlockPos();
        for (int sampleX = 0; sampleX < sampledSize; sampleX++) {
            for (int sampleZ = 0; sampleZ < sampledSize; sampleZ++) {
                if (Thread.currentThread().isInterrupted()) {
                    this.biomesData = null;
                    this.image = null;
                    image.close();
                    return;
                }
                int worldX = tileWorldX + (sampleX * sampleResolution);
                int worldZ = tileWorldZ + (sampleZ * sampleResolution);
                worldPos.set(worldX, y, worldZ);

                sampledChunks.add(ChunkPos.asLong(worldPos));
                Holder<Biome> biomeHolder = tileManager.getBiome(worldX, worldZ);

                int dataX = sampleX;
                int dataZ = sampleZ;
                ResourceKey<Biome> biome = biomeHolder.unwrapKey().orElseThrow();

                data.getBiome(dataX, dataZ, worldX, worldZ, (worldX1, worldZ1) -> biomeHolder);

                image.setPixelRGBA(dataX, dataZ, _ARGBToABGR(FAST_COLORS.computeIfAbsent(biome, biomeResourceKey -> {
                    Biome value = biomeHolder.value();
                    float baseTemperature = value.getBaseTemperature();
                    float lerp = Mth.inverseLerp(baseTemperature, -2, 2);
                    int r = (int) Mth.clampedLerp(137, 139, lerp);
                    int g = (int) Mth.clampedLerp(207, 0, lerp);
                    int b = (int) Mth.clampedLerp(240, 0, lerp);

                    return FastColor.ARGB32.color(255, r, g, b);
                })));

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
                this.image = NativeImage.read(Files.readAllBytes(imagePath));
                if (this.image.getWidth() != (size / this.sampleResolution)) {
                    throw new IllegalArgumentException("Improper image width.");
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
    public Renderer renderer() {
        return (graphics, size1, id, opacity, renderTileContext) -> {
            Matrix4f matrix = graphics.pose().last().pose();
            Map<String, ?> data = renderTileContext.data();
            if (data.get("highlighted_biome") instanceof ResourceKey biomeResourceKey) {
                if (FAST_COLORS.containsKey(biomeResourceKey)) {
                    int color = FAST_COLORS.getInt(biomeResourceKey);
                    VertexConsumer vertexConsumer = graphics.bufferSource().getBuffer(WVRenderType.COLOR_FILTER_WORLD_VIEWER_GUI.apply(id, RenderType.NO_TRANSPARENCY));
                    float a = FastColor.ARGB32.alpha(color) / 255F;

                    float r = FastColor.ARGB32.red(color) / 255F;
                    float g = FastColor.ARGB32.green(color) / 255F;
                    float b = FastColor.ARGB32.blue(color) / 255F;

                    vertexConsumer.vertex(matrix, (float) 0, (float) size1, (float) 0).color(1F, 1F, 1F, opacity).uv(0, 1).color(r, g, b, a).endVertex();
                    vertexConsumer.vertex(matrix, (float) size1, (float) size1, (float) 0).color(1F, 1F, 1F, opacity).uv(1, 1).color(r, g, b, a).endVertex();
                    vertexConsumer.vertex(matrix, (float) size1, (float) 0, (float) 0).color(1F, 1F, 1F, opacity).uv(1, 0).color(r, g, b, a).endVertex();
                    vertexConsumer.vertex(matrix, (float) 0, (float) 0, (float) 0).color(1F, 1F, 1F, opacity).uv(0, 0).color(r, g, b, a).endVertex();
                    return;
                }

            }
            super.renderer().render(graphics, size1, id, opacity, renderTileContext);
        };
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

        return Collections.singletonList(Component.literal("Biome: " + biomeResourceKey.unwrapKey().get().location()).withStyle(Style.EMPTY.withColor(FAST_COLORS.getOrDefault(biomeResourceKey, FastColor.ARGB32.color(255, 255, 255, 255)))));
    }

    public static final Object2IntOpenHashMap<ResourceKey<Biome>> FAST_COLORS = Util.make(new Object2IntOpenHashMap<>(), map -> {
        map.put(Biomes.BADLANDS, tryParseColor("0xD94515"));
        map.put(Biomes.BAMBOO_JUNGLE, tryParseColor("0x2C4205"));
        map.put(Biomes.BEACH, tryParseColor("0xFADE55"));
        map.put(Biomes.DESERT, tryParseColor("0xFA9418"));
        map.put(Biomes.BIRCH_FOREST, tryParseColor("0x307444"));
        map.put(Biomes.PLAINS, tryParseColor("0x8DB360"));
        map.put(Biomes.WINDSWEPT_HILLS, tryParseColor("0x606060"));
        map.put(Biomes.FOREST, tryParseColor("0x056621"));
        map.put(Biomes.TAIGA, tryParseColor("0x0B6659"));
        map.put(Biomes.SWAMP, tryParseColor("0x07F9B2"));
        map.put(Biomes.RIVER, tryParseColor("0x0000FF"));
        map.put(Biomes.NETHER_WASTES, tryParseColor("0xFF0000"));
        map.put(Biomes.THE_VOID, tryParseColor("0x8080FF"));
        map.put(Biomes.FROZEN_RIVER, tryParseColor("0xA0A0A0"));
        map.put(Biomes.SNOWY_PLAINS, tryParseColor("0xFFFFFF"));
        map.put(Biomes.MUSHROOM_FIELDS, tryParseColor("0xFF00FF"));
        map.put(Biomes.JUNGLE, tryParseColor("0x537B09"));
        map.put(Biomes.SPARSE_JUNGLE, tryParseColor("0x628B17"));
        map.put(Biomes.STONY_SHORE, tryParseColor("0xA2A284"));
        map.put(Biomes.SNOWY_BEACH, tryParseColor("0xFAF0C0"));
        map.put(Biomes.DARK_FOREST, tryParseColor("0x40511A"));
        map.put(Biomes.SNOWY_TAIGA, tryParseColor("0x31554A"));
        map.put(Biomes.OLD_GROWTH_PINE_TAIGA, tryParseColor("0x596651"));
        map.put(Biomes.OLD_GROWTH_SPRUCE_TAIGA, tryParseColor("0x818E79"));
        map.put(Biomes.SAVANNA, tryParseColor("0xBDB25F"));
        map.put(Biomes.SAVANNA_PLATEAU, tryParseColor("0xA79D24"));
        map.put(Biomes.WOODED_BADLANDS, tryParseColor("0xCA8C65"));
        map.put(Biomes.ERODED_BADLANDS, tryParseColor("0xFF6D3D"));
        map.put(Biomes.SUNFLOWER_PLAINS, tryParseColor("0xB5DB88"));
        map.put(Biomes.FLOWER_FOREST, tryParseColor("0x2D8E49"));
        map.put(Biomes.ICE_SPIKES, tryParseColor("0xB4DCDC"));
        map.put(Biomes.OCEAN, tryParseColor("0x000070"));
        map.put(Biomes.DEEP_OCEAN, tryParseColor("0x000030"));
        map.put(Biomes.COLD_OCEAN, tryParseColor("0x0056d6"));
        map.put(Biomes.DEEP_COLD_OCEAN, tryParseColor("0x004ecc"));
        map.put(Biomes.LUKEWARM_OCEAN, tryParseColor("0x45ADF2"));
        map.put(Biomes.DEEP_LUKEWARM_OCEAN, tryParseColor("0x3BA3E8"));
        map.put(Biomes.FROZEN_OCEAN, tryParseColor("0x9090A0"));
        map.put(Biomes.DEEP_FROZEN_OCEAN, tryParseColor("0x676791"));
        map.put(Biomes.FROZEN_PEAKS, tryParseColor("0x8BC0FC"));
        map.put(Biomes.WINDSWEPT_SAVANNA, tryParseColor("0xE5DA87"));
        map.put(Biomes.WINDSWEPT_FOREST, tryParseColor("0x589C6C"));
        map.put(Biomes.STONY_PEAKS, tryParseColor("0xC0C0C0"));
        map.put(Biomes.JAGGED_PEAKS, tryParseColor("0x969696"));
        map.put(Biomes.GROVE, tryParseColor("0x42FFBa"));
        map.put(Biomes.SOUL_SAND_VALLEY, tryParseColor("0x964B00"));
        map.put(Biomes.WARPED_FOREST, tryParseColor("0x89cff0"));
        map.put(Biomes.BASALT_DELTAS, tryParseColor("0x5A5A5A"));
        map.put(Biomes.CRIMSON_FOREST, tryParseColor("0xDC143C"));
    });


    public static int tryParseColor(String input) {
        int result = Integer.MAX_VALUE;

        if (input.isEmpty()) {
            return result;
        }

        try {
            String colorSubString = input.substring(2);
            result = (255 << 24) | (Integer.parseInt(colorSubString, 16));

            return result;
        } catch (NumberFormatException e) {
            e.printStackTrace();
        }

        return result;
    }

    public static int _ARGBToABGR(int argb) {
        int r = FastColor.ARGB32.red(argb);
        int g = FastColor.ARGB32.green(argb);
        int b = FastColor.ARGB32.blue(argb);
        int a = FastColor.ARGB32.alpha(argb);

        return FastColor.ABGR32.color(a, b, g, r);
    }

    @Override
    @Nullable
    public CompoundTag tag() {
        CompoundTag compoundTag = new CompoundTag();
        compoundTag.put("biomes", this.biomesData.save());
        compoundTag.putInt("res", this.sampleResolution);
        return compoundTag;
    }

    @Override
    public NativeImage image() {
        return this.image;
    }
}
