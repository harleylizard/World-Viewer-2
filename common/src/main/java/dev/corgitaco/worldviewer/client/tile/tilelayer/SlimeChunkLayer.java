package dev.corgitaco.worldviewer.client.tile.tilelayer;

import com.mojang.blaze3d.platform.NativeImage;
import dev.corgitaco.worldviewer.client.screen.WorldScreenv2;
import dev.corgitaco.worldviewer.common.storage.DataTileManager;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FastColor;
import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class SlimeChunkLayer extends TileLayer {

    @Nullable
    private NativeImage image;

    @Nullable
    private final boolean[][] slimeChunkData;

    public SlimeChunkLayer(DataTileManager tileManager, int y, int tileWorldX, int tileWorldZ, int size, int sampleResolution, WorldScreenv2 screen, LongSet sampledChunks) {
        super(tileManager, y, tileWorldX, tileWorldZ, size, sampleResolution, screen, sampledChunks);
        int[][] colorData;
        int dataSize = SectionPos.blockToSectionCoord(size);

        boolean[][] data = new boolean[dataSize][dataSize];
        if (sampleResolution <= 16 && size <= 128) {
            int[][] colorData1 = new int[size][size];
            for (int x = 0; x < dataSize; x++) {
                for (int z = 0; z < dataSize; z++) {
                    int chunkX = SectionPos.blockToSectionCoord(tileWorldX) + x;
                    int chunkZ = SectionPos.blockToSectionCoord(tileWorldZ) + z;

                    sampledChunks.add(ChunkPos.asLong(chunkX, chunkZ));
                    if (tileManager.isSlimeChunk(chunkX, chunkZ)) {
                        data[x][z] = true;

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

    @Override
    public @Nullable List<Component> toolTip(double mouseScreenX, double mouseScreenY, int mouseWorldX, int mouseWorldZ, int mouseTileLocalX, int mouseTileLocalY) {
        if (slimeChunkData == null) {
            return Collections.emptyList();
        }
        boolean slimeChunk = this.slimeChunkData[SectionPos.blockToSectionCoord(mouseTileLocalX)][SectionPos.blockToSectionCoord(mouseTileLocalY)];

        return Collections.singletonList(Component.literal("Slime Chunk? %s".formatted(slimeChunk ? "Yes" : "No")).setStyle(Style.EMPTY.withColor(slimeChunk ? FastColor.ARGB32.color(255, 120, 190, 93) : FastColor.ARGB32.color(255, 255, 255, 255))));
    }

    @Override
    public NativeImage image() {
        return this.image;
    }
}
