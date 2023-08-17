package dev.corgitaco.worldviewer.client.tile.tilelayer;

import com.mojang.blaze3d.platform.NativeImage;
import dev.corgitaco.worldviewer.client.screen.WorldScreenv2;
import dev.corgitaco.worldviewer.common.storage.DataTileManager;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FastColor;
import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

public class SlimeChunkLayer extends TileLayer {

    @Nullable
    private final NativeImage image;

    @Nullable
    private final boolean[] slimeChunkData;

    public SlimeChunkLayer(DataTileManager tileManager, int y, int tileWorldX, int tileWorldZ, int size, int sampleResolution, WorldScreenv2 screen, LongSet sampledChunks) {
        super(tileManager, y, tileWorldX, tileWorldZ, size, sampleResolution, screen, sampledChunks);
        int[][] colorData;
        int dataSize = SectionPos.blockToSectionCoord(size);

        boolean[] data = new boolean[dataSize * dataSize];
        if (sampleResolution <= 16 && size <= 128) {
            int[][] colorData1 = new int[size][size];
            for (int x = 0; x < dataSize; x++) {
                for (int z = 0; z < dataSize; z++) {
                    int chunkX = SectionPos.blockToSectionCoord(tileWorldX) + x;
                    int chunkZ = SectionPos.blockToSectionCoord(tileWorldZ) + z;

                    sampledChunks.add(ChunkPos.asLong(chunkX, chunkZ));
                    if (tileManager.isSlimeChunk(chunkX, chunkZ)) {
                        data[x + z * dataSize] = true;

                        for (int xMove = 0; xMove < 16; xMove++) {
                            for (int zMove = 0; zMove < 16; zMove++) {
                                if (xMove <= 1 || xMove >= 14 || zMove <= 1 || zMove >= 14) {
                                    int dataX = SectionPos.sectionToBlockCoord(x) + xMove;
                                    int dataZ = SectionPos.sectionToBlockCoord(z) + zMove;
                                    colorData1[dataX][dataZ] = BiomeLayer._ARGBToABGR(FastColor.ARGB32.color(255, 120, 190, 93));
                                } else {
                                    BiomeLayer._ARGBToABGR(FastColor.ARGB32.color(0, 0, 0, 0));
                                }
                            }
                        }
                    }
                }
            }
            colorData = colorData1;
            this.slimeChunkData = data;

        } else {
            colorData = null;
            this.slimeChunkData = null;
        }

        if (colorData != null) {
            this.image = makeNativeImageFromColorData(colorData);
        } else {
            this.image = null;
        }
    }

    public SlimeChunkLayer(int size, Path imagePath, Path dataPath) throws Exception {
        super(size, imagePath, dataPath);

        File dataPathFile = dataPath.toFile();
        File imagePathFile = imagePath.toFile();
        if (dataPathFile.exists() && imagePathFile.exists()) {
            try {
                CompoundTag compoundTag = NbtIo.read(dataPathFile);
                byte[] slimeChunks = compoundTag.getByteArray("slime_chunks");
                boolean[] slimeChunkData = new boolean[slimeChunks.length];
                for (int i = 0; i < slimeChunks.length; i++) {
                    byte slimeChunk = slimeChunks[i];
                    slimeChunkData[i] = slimeChunk == 1;
                }

                this.slimeChunkData = slimeChunkData;
                this.image = NativeImage.read(Files.readAllBytes(imagePath));
            } catch (IOException e) {
                throw e;
            }

        } else {
            this.slimeChunkData = null;
            this.image = null;
        }
    }

    @Override
    public @Nullable List<Component> toolTip(double mouseScreenX, double mouseScreenY, int mouseWorldX, int mouseWorldZ, int mouseTileLocalX, int mouseTileLocalY) {
        if (slimeChunkData == null) {
            return Collections.emptyList();
        }
        boolean slimeChunk = this.slimeChunkData[SectionPos.blockToSectionCoord(mouseTileLocalX) + SectionPos.blockToSectionCoord(mouseTileLocalY) * ((int) Math.sqrt(this.slimeChunkData.length))];

        return Collections.singletonList(Component.literal("Slime Chunk? %s".formatted(slimeChunk ? "Yes" : "No")).setStyle(Style.EMPTY.withColor(slimeChunk ? FastColor.ARGB32.color(255, 120, 190, 93) : FastColor.ARGB32.color(255, 255, 255, 255))));
    }

    @Override
    public NativeImage image() {
        return this.image;
    }

    @Override
    public boolean isComplete() {
        return this.image != null && this.slimeChunkData != null;
    }

    @Override
    public @Nullable CompoundTag tag() {
        CompoundTag compoundTag = new CompoundTag();
        byte[] slimeChunks = new byte[slimeChunkData.length];

        for (int i = 0; i < slimeChunkData.length; i++) {
            slimeChunks[i] = (byte) (slimeChunkData[i] ? 1 : 0);
        }

        compoundTag.putByteArray("slime_chunks", slimeChunks);
        return super.tag();
    }
}
