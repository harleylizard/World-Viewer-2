package dev.corgitaco.worldviewer.client.tile.tilelayer;

import com.mojang.blaze3d.platform.NativeImage;
import dev.corgitaco.worldviewer.client.ClientUtil;
import dev.corgitaco.worldviewer.common.storage.DataTileManager;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferInt;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public class NoiseCaveLayer extends TileLayer {

    @Nullable
    private final int[] image;


    private final int[] foundCaveBlocks;


    public NoiseCaveLayer(DataTileManager dataTileManager, int y, int tileWorldX, int tileWorldZ, int size, int sampleResolution, LongSet sampledChunks, @Nullable NoiseCaveLayer lowerResolution) {
        super(dataTileManager, y, tileWorldX, tileWorldZ, size, sampleResolution, sampledChunks, lowerResolution);
        ServerLevel serverLevel = dataTileManager.serverLevel();
        ChunkGenerator generator = serverLevel.getChunkSource().getGenerator();
        int minBuildHeight = serverLevel.getMinBuildHeight();
        int seaLevel = generator.getSeaLevel();
        int searchRange = seaLevel - minBuildHeight;


        int sampledSize = size / sampleResolution;

        int[] image = new int[size * size];

        int[] data = new int[size * size];
        Arrays.fill(data, -1);

        BlockPos.MutableBlockPos worldPos = new BlockPos.MutableBlockPos();
        for (int sampleX = 0; sampleX < size; sampleX++) {
            for (int sampleZ = 0; sampleZ < size; sampleZ++) {
                if (Thread.currentThread().isInterrupted()) {
                    this.image = null;
                    this.foundCaveBlocks = null;
                    return;
                }
                worldPos.set(tileWorldX + (sampleX * sampleResolution), 0, tileWorldZ + (sampleZ * sampleResolution));

                int foundCaveBlocks = 0;

                int idx = sampleX + sampleZ * size;
                int previous = data[idx];

                if (previous == -1) {
                    sampledChunks.add(ChunkPos.asLong(worldPos));
                    NoiseColumn baseColumn = generator.getBaseColumn(worldPos.getX(), worldPos.getZ(), serverLevel, serverLevel.getChunkSource().randomState());
                    for (int index = minBuildHeight; index < seaLevel; index++) {
                        BlockState block = baseColumn.getBlock(index);
                        if (block.isAir() || block.getFluidState().is(FluidTags.LAVA)) {
                            foundCaveBlocks++;
                        }
                    }
                } else {
                    foundCaveBlocks = previous;
                }
                data[idx] = foundCaveBlocks;

                if (foundCaveBlocks > 2) {
                    int grayScale = getGrayScale(((float) foundCaveBlocks) / ((float) searchRange), dataTileManager.serverLevel());
                    image[sampleX + sampleZ * size] = grayScale;
                }
            }
        }
        this.foundCaveBlocks = data;
        this.image = image;
    }

    public NoiseCaveLayer(int size, Path imagePath, Path dataPath, int sampleResolution) throws Exception {
        super(size, imagePath, dataPath, sampleResolution);
        File dataPathFile = dataPath.toFile();
        File imagePathFile = imagePath.toFile();
        if (dataPathFile.exists() && imagePathFile.exists()) {
            while (!imagePathFile.canRead() || !dataPathFile.canRead()) {
                Thread.sleep(1);
            }
            try {
                CompoundTag compoundTag = NbtIo.read(dataPath.toFile());
                this.sampleResolution = compoundTag.getInt("res");
                this.foundCaveBlocks = compoundTag.getIntArray("cave_blocks");
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
            this.image = null;
            this.foundCaveBlocks = null;
            this.sampleResolution = Integer.MIN_VALUE;
        }
    }

    public static int getGrayScale(float pct, LevelHeightAccessor heightAccessor) {
        int color = Math.round(Mth.clampedLerp(50, 255, pct));
        return FastColor.ABGR32.color(255, 0, 0, color);
    }

    @Override
    public @Nullable int[] image() {
        return this.image;
    }

    @Override
    @Nullable
    public CompoundTag tag() {
        CompoundTag compoundTag = new CompoundTag();
        compoundTag.putInt("res", this.sampleResolution);
        compoundTag.putIntArray("cave_blocks", this.foundCaveBlocks);
        return compoundTag;
    }

    @Override
    public boolean isComplete() {
        return this.image != null && this.sampleResolution > 0;
    }
}
