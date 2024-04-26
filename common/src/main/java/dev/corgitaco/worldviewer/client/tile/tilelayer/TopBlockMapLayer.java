package dev.corgitaco.worldviewer.client.tile.tilelayer;

import dev.corgitaco.worldviewer.client.render.ColorUtils;
import dev.corgitaco.worldviewer.common.storage.DataTileManager;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
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

public class TopBlockMapLayer extends TileLayer {

    @Nullable
    private final int[] data;


    @Nullable
    private final int[] image;

    public TopBlockMapLayer(DataTileManager tileManager, int y, int tileWorldX, int tileWorldZ, int size, int sampleResolution, LongSet sampledChunks, @Nullable TopBlockMapLayer higherResolution) {
        super(tileManager, y, tileWorldX, tileWorldZ, size, sampleResolution, sampledChunks, higherResolution);

        int[] data = null;

        int[] image = null;

        BlockPos.MutableBlockPos worldPos = new BlockPos.MutableBlockPos();
        for (int sampleX = 0; sampleX < size; sampleX++) {
            for (int sampleZ = 0; sampleZ < size; sampleZ++) {
                if (Thread.currentThread().isInterrupted()) {
                    this.data = null;
                    this.image = null;
                    return;
                }
                worldPos.set(tileWorldX + (sampleX * sampleResolution), 0, tileWorldZ + (sampleZ * sampleResolution));

                int idx = sampleX + sampleZ * size;

                LevelChunk chunkNow = Minecraft.getInstance().level.getChunkSource().getChunkNow(SectionPos.blockToSectionCoord(worldPos.getX()), SectionPos.blockToSectionCoord(worldPos.getZ()));


                if (chunkNow != null) {
                    if (image == null) {
                        image = new int[size * size];

                    }
                    if (data == null) {
                        data = new int[size * size];
                        Arrays.fill(data, Integer.MIN_VALUE);
                    }

                    BlockState blockState = chunkNow.getBlockState(worldPos.setY(chunkNow.getHeight(Heightmap.Types.WORLD_SURFACE, worldPos.getX(), worldPos.getZ())));
                    int mapColor = blockState.getMapColor(Minecraft.getInstance().level, BlockPos.ZERO).col;
                    int r = (mapColor >> 16 & 255);
                    int g = (mapColor >> 8 & 255);
                    int b = (mapColor & 255);

                    image[sampleX + sampleZ * size] = ColorUtils.ABGR.packABGR(255, b, g, r);
                    data[idx] = Block.BLOCK_STATE_REGISTRY.getId(blockState);
                }
            }
        }

        this.image = image;
        this.data = data;
    }

    public TopBlockMapLayer(int size, Path imagePath, Path dataPath, int sampleRes) throws Exception {
        super(size, imagePath, dataPath, sampleRes);
        File imagePathFile = imagePath.toFile();
        File dataPathFile = dataPath.toFile();
        if (imagePathFile.exists() && dataPathFile.exists()) {
            while (!imagePathFile.canRead() || !dataPathFile.canRead()) {
                Thread.sleep(1);
            }

            try {

                BufferedImage bufferedImage = Files.exists(imagePath) ? ImageIO.read(imagePath.toFile()) : null;
                if (bufferedImage != null) {
                    this.image = ((DataBufferInt) bufferedImage.getRaster().getDataBuffer()).getData();
                } else {
                    this.image = null;
                }
                CompoundTag read = NbtIo.read(dataPathFile);

                this.sampleResolution = read.getInt("res");
            } catch (IOException e) {
                throw e;
            }
        } else {
            this.image = null;
        }
        this.data = null;
    }

    @Override
    @Nullable
    public int[] image() {
        return this.image;
    }

    @Override
    public boolean isComplete() {
        return this.image != null;
    }

    @Override
    public boolean usesLod() {
        return false;
    }

    @Override
    public @Nullable CompoundTag tag() {
        CompoundTag compoundTag = new CompoundTag();
        compoundTag.putInt("res", this.sampleResolution);
        return compoundTag;
    }
}
