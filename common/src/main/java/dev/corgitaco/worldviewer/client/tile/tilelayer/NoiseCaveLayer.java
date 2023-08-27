package dev.corgitaco.worldviewer.client.tile.tilelayer;

import com.mojang.blaze3d.platform.NativeImage;
import dev.corgitaco.worldviewer.client.screen.WorldScreenv2;
import dev.corgitaco.worldviewer.common.storage.DataTileManager;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class NoiseCaveLayer extends TileLayer {

    @Nullable
    private final NativeImage image;


    public NoiseCaveLayer(DataTileManager dataTileManager, int y, int tileWorldX, int tileWorldZ, int size, int sampleResolution, WorldScreenv2 screen, LongSet sampledChunks) {
        super(dataTileManager, y, tileWorldX, tileWorldZ, size, sampleResolution, screen, sampledChunks);

        int sampledSize = size / sampleResolution;
        NativeImage colorData = new NativeImage(sampledSize, sampledSize, true);

        ServerLevel serverLevel = dataTileManager.serverLevel();
        ChunkGenerator generator = serverLevel.getChunkSource().getGenerator();

        BlockPos.MutableBlockPos worldPos = new BlockPos.MutableBlockPos();
        for (int sampleX = 0; sampleX < sampledSize; sampleX++) {
            for (int sampleZ = 0; sampleZ < sampledSize; sampleZ++) {
                worldPos.set(tileWorldX + (sampleX * sampleResolution), 0, tileWorldZ + (sampleZ * sampleResolution));

                sampledChunks.add(ChunkPos.asLong(worldPos));

                NoiseColumn baseColumn = generator.getBaseColumn(worldPos.getX(), worldPos.getZ(), serverLevel, serverLevel.getChunkSource().randomState());


                int foundCaveBlocks = 0;
                int minBuildHeight = serverLevel.getMinBuildHeight();
                int seaLevel = generator.getSeaLevel();
                int searchRange = seaLevel - minBuildHeight;

                for (int index = minBuildHeight; index < seaLevel; index++) {
                    BlockState block = baseColumn.getBlock(index);
                    if (block.isAir() || block.getFluidState().is(FluidTags.LAVA)) {
                        foundCaveBlocks++;
                    }

                }

                if (foundCaveBlocks > 2) {
                    int grayScale = getGrayScale(((float) foundCaveBlocks) / ((float) searchRange), dataTileManager.serverLevel());
                    colorData.setPixelRGBA(sampleX, sampleZ, grayScale);
                }
            }
        }

        this.image = colorData;
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
                this.image = NativeImage.read(Files.readAllBytes(imagePath));
            } catch (IOException e) {
                throw e;
            }
        } else {
            this.image = null;
        }
    }

    public static int getGrayScale(float pct, LevelHeightAccessor heightAccessor) {
        int color = Math.round(Mth.clampedLerp(50, 255, pct));
        return FastColor.ABGR32.color(255, 0, 0, color);
    }

    @Override
    public @Nullable NativeImage image() {
        return this.image;
    }

    @Override
    public boolean isComplete() {
        return this.image != null;
    }
}
