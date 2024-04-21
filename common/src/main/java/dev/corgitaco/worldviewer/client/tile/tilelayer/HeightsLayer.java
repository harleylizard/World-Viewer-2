package dev.corgitaco.worldviewer.client.tile.tilelayer;

import dev.corgitaco.worldviewer.common.storage.DataTileManager;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class HeightsLayer extends TileLayer {

    @Nullable
    private final int[] image;

    @Nullable
    private final int[] heightsData;

    public HeightsLayer(DataTileManager tileManager, int y, int worldX, int worldZ, int size, int sampleResolution, LongSet sampledChunks, @Nullable HeightsLayer lowerResolution) {
        super(tileManager, y, worldX, worldZ, size, sampleResolution, sampledChunks, lowerResolution);

        int[] data = new int[size * size];
        Arrays.fill(data, Integer.MIN_VALUE);

        int[] heightsData = new int[size * size];

        BlockPos.MutableBlockPos worldPos = new BlockPos.MutableBlockPos();
        for (int sampleX = 0; sampleX < size; sampleX++) {
            for (int sampleZ = 0; sampleZ < size; sampleZ++) {
                if (Thread.currentThread().isInterrupted()) {
                    this.heightsData = null;
                    this.image = null;
                    return;
                }
                worldPos.set(worldX + (sampleX * sampleResolution), 0, worldZ + (sampleZ * sampleResolution));

                int idx = sampleX + sampleZ * size;
                int previous = data[idx];
                int worldY;

                if (previous == Integer.MIN_VALUE) {
                    sampledChunks.add(ChunkPos.asLong(worldPos));
                    worldY = tileManager.getHeight(Heightmap.Types.OCEAN_FLOOR, worldPos.getX(), worldPos.getZ());
                } else {
                    worldY = previous;
                }

                int grayScale = getGrayScale(worldY, tileManager.serverLevel());
                heightsData[sampleX + sampleZ * size] = grayScale;
                data[idx] = worldY;
            }
        }

        this.image = heightsData;
        this.heightsData = data;
    }

    public HeightsLayer(int size, Path imagePath, Path dataPath, int sampleResolution) throws Exception {
        super(size, imagePath, dataPath, sampleResolution);
        File dataPathFile = dataPath.toFile();
        File imagePathFile = imagePath.toFile();
        if (dataPathFile.exists() && imagePathFile.exists()) {
            while (!imagePathFile.canRead() || !dataPathFile.canRead()) {
                Thread.sleep(1);
            }
            try {
                CompoundTag compoundTag = NbtIo.read(dataPath.toFile());
                this.heightsData = compoundTag.getIntArray("heights");
                this.sampleResolution = compoundTag.getInt("res");
                BufferedImage bufferedImage = Files.exists(imagePath) ? ImageIO.read(imagePath.toFile()) : null;
                if (bufferedImage != null) {
                    if (bufferedImage.getWidth() != (size / this.sampleResolution)) {
                        throw new IllegalArgumentException("Improper image width.");
                    }
                    this.image = ((DataBufferInt) bufferedImage.getRaster().getDataBuffer()).getData();
                } else  {
                    this.image = null;
                }
            } catch (IOException e) {
                throw e;
            }
        } else {
            this.heightsData = null;
            this.image = null;
            this.sampleResolution = Integer.MIN_VALUE;
        }
    }

    public static int getGrayScale(int y, LevelHeightAccessor heightAccessor) {
        float pct = Mth.clamp(Mth.inverseLerp(y, 0, 255), 0, 1F);
        int color = Math.round(Mth.clampedLerp(0, 255, pct));
        return FastColor.ABGR32.color(255, color, color, color);
    }

    @Override
    public @Nullable List<Component> toolTip(double mouseScreenX, double mouseScreenY, int mouseWorldX, int mouseWorldZ, int mouseTileLocalX, int mouseTileLocalY, int mouseTileImageLocalX, int mouseTileImageLocalY) {
        int y = heightsData[(mouseTileImageLocalX) + (mouseTileImageLocalX) * ((int) Math.sqrt(this.heightsData.length))];

        return Collections.singletonList(Component.literal("Ocean Floor Height: " + y));
    }

    @Override
    public boolean isComplete() {
        return this.image != null && this.heightsData != null && this.sampleResolution > 0;
    }

    @Override
    public int[] image() {
        return this.image;
    }

    @Override
    @Nullable
    public  CompoundTag tag() {
        CompoundTag compoundTag = new CompoundTag();
        compoundTag.putIntArray("heights", this.heightsData);
        compoundTag.putInt("res", this.sampleResolution);
        return compoundTag;
    }
}
