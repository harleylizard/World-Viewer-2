package dev.corgitaco.worldviewer.client.tile.tilelayer;

import com.mojang.blaze3d.platform.NativeImage;
import dev.corgitaco.worldviewer.client.screen.WorldScreenv2;
import dev.corgitaco.worldviewer.common.storage.DataTileManager;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.util.FastColor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class TopBlockMapLayer extends TileLayer {

    @Nullable
    private final NativeImage image;

    public TopBlockMapLayer(DataTileManager tileManager, int y, int tileWorldX, int tileWorldZ, int size, int sampleResolution, WorldScreenv2 screen, LongSet sampledChunks) {
        super(tileManager, y, tileWorldX, tileWorldZ, size, sampleResolution, screen, sampledChunks);
        if (size <= 128) {
            this.sampleResolution = screen.sampleResolution;
            NativeImage nativeImage = new NativeImage(size, size, true);
            BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
            for (int chunkX = 0; chunkX < SectionPos.blockToSectionCoord(size); chunkX++) {
                for (int chunkZ = 0; chunkZ < SectionPos.blockToSectionCoord(size); chunkZ++) {
                    if (Thread.currentThread().isInterrupted()) {
                        this.image = null;
                        nativeImage.close();
                        return;
                    }
                    LevelChunk chunk = Minecraft.getInstance().level.getChunkSource().getChunkNow(SectionPos.blockToSectionCoord(tileWorldX) + chunkX, SectionPos.blockToSectionCoord(tileWorldZ) + chunkZ);
                    if (chunk != null) {
                        for (int x = 0; x < 16; x++) {
                            for (int z = 0; z < 16; z++) {
                                int pixelX = SectionPos.sectionToBlockCoord(chunkX) + x;
                                int pixelZ = SectionPos.sectionToBlockCoord(chunkZ) + z;

                                int height = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);

                                BlockState blockState = chunk.getBlockState(mutable.set(pixelX, height, pixelZ));

                                int mapColor = blockState.getMapColor(Minecraft.getInstance().level, BlockPos.ZERO).col;
                                int r = (mapColor >> 16 & 255);
                                int g = (mapColor >> 8 & 255);
                                int b = (mapColor & 255);

                                nativeImage.setPixelRGBA(pixelX, pixelZ, FastColor.ABGR32.color(255, b, g, r));
                            }

                        }
                    }
                }
            }
            this.image = nativeImage;
        } else {
            image = null;
        }
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
                this.image = NativeImage.read(Files.readAllBytes(imagePath));
                CompoundTag read = NbtIo.read(dataPathFile);

                this.sampleResolution = read.getInt("res");
            } catch (IOException e) {
                throw e;
            }
        } else {
            this.image = null;
        }
    }

    @Override
    @Nullable
    public NativeImage image() {
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
