package dev.corgitaco.worldviewer.client.tile.tilelayer;

import dev.corgitaco.worldviewer.client.render.ColorUtils;
import dev.corgitaco.worldviewer.common.storage.DataTileManager;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FastColor;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

public class SlimeChunkLayer extends TileLayer {

    @Nullable
    private final int[] image;

    @Nullable
    private final boolean[] slimeChunkData;

    public SlimeChunkLayer(DataTileManager tileManager, int y, int tileWorldX, int tileWorldZ, int size, int sampleResolution, LongSet sampledChunks, @Nullable SlimeChunkLayer higherResolution) {
        super(tileManager, y, tileWorldX, tileWorldZ, size, sampleResolution, sampledChunks, higherResolution);

        boolean[] data = null;

        int[] image = null;

        BlockPos.MutableBlockPos worldPos = new BlockPos.MutableBlockPos();
        for (int sampleX = 0; sampleX < size; sampleX++) {
            for (int sampleZ = 0; sampleZ < size; sampleZ++) {
                if (Thread.currentThread().isInterrupted()) {
                    this.slimeChunkData = null;
                    this.image = null;
                    return;
                }
                worldPos.set(tileWorldX + (sampleX * sampleResolution), 0, tileWorldZ + (sampleZ * sampleResolution));

                int idx = sampleX + sampleZ * size;

                if (tileManager.isSlimeChunk(SectionPos.blockToSectionCoord(worldPos.getX()), SectionPos.blockToSectionCoord(worldPos.getZ()))) {
                    if (data == null) {
                        data = new boolean[SectionPos.blockToSectionCoord(size) * SectionPos.blockToSectionCoord(size)];
                    }

                    if (image == null) {
                        image = new int[size * size];
                    }

                    image[idx] = ColorUtils.ABGR.packABGR(255, 93, 190, 120);


                    data[SectionPos.blockToSectionCoord(sampleX) * SectionPos.blockToSectionCoord(sampleZ) * SectionPos.blockToSectionCoord(size)] = true;
                }
            }
        }
        this.slimeChunkData = data;
        this.image = image;
    }

    public SlimeChunkLayer(int size, Path imagePath, Path dataPath, int sampleRes) throws Exception {
        super(size, imagePath, dataPath, sampleRes);

        File dataPathFile = dataPath.toFile();
        File imagePathFile = imagePath.toFile();
        if (dataPathFile.exists() && imagePathFile.exists()) {
            while (!imagePathFile.canRead() || !dataPathFile.canRead()) {
                Thread.sleep(1);
            }
            try {
                CompoundTag compoundTag = NbtIo.read(dataPathFile);
                byte[] slimeChunks = compoundTag.getByteArray("slime_chunks");
                boolean[] slimeChunkData = new boolean[slimeChunks.length];
                for (int i = 0; i < slimeChunks.length; i++) {
                    byte slimeChunk = slimeChunks[i];
                    slimeChunkData[i] = slimeChunk == 1;
                }

                this.slimeChunkData = slimeChunkData;
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
    public int[] image() {
        return this.image;
    }

    @Override
    public boolean isComplete() {
        return this.image != null && this.slimeChunkData != null;
    }

    @Override
    public @Nullable CompoundTag tag() {
        if (slimeChunkData == null) {
            return null;
        }

        CompoundTag compoundTag = new CompoundTag();
        byte[] slimeChunks = new byte[slimeChunkData.length];

        for (int i = 0; i < slimeChunkData.length; i++) {
            slimeChunks[i] = (byte) (slimeChunkData[i] ? 1 : 0);
        }

        compoundTag.putInt("res", this.sampleResolution);
        compoundTag.putByteArray("slime_chunks", slimeChunks);
        return super.tag();
    }

    @Override
    public boolean usesLod() {
        return false;
    }
}
