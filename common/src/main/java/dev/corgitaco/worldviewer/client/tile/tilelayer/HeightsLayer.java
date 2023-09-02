package dev.corgitaco.worldviewer.client.tile.tilelayer;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.corgitaco.worldviewer.client.ClientUtil;
import dev.corgitaco.worldviewer.client.WVRenderType;
import dev.corgitaco.worldviewer.client.screen.WorldScreenv2;
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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

public class HeightsLayer extends TileLayer {

    @Nullable
    private final NativeImage image;

    @Nullable
    private final int[] heightsData;

    public HeightsLayer(DataTileManager tileManager, int y, int worldX, int worldZ, int size, int sampleResolution, LongSet sampledChunks) {
        super(tileManager, y, worldX, worldZ, size, sampleResolution, sampledChunks);

        int sampledSize = size / sampleResolution;
        NativeImage colorData = ClientUtil.createImage(sampledSize, sampledSize, true);
        int[] data = new int[sampledSize * sampledSize];

        BlockPos.MutableBlockPos worldPos = new BlockPos.MutableBlockPos();
        for (int sampleX = 0; sampleX < sampledSize; sampleX++) {
            for (int sampleZ = 0; sampleZ < sampledSize; sampleZ++) {
                if (Thread.currentThread().isInterrupted()) {
                    this.heightsData = null;
                    this.image = null;
                    colorData.close();
                    return;
                }

                worldPos.set(worldX + (sampleX * sampleResolution), 0, worldZ + (sampleZ * sampleResolution));

                sampledChunks.add(ChunkPos.asLong(worldPos));

                y = tileManager.getHeight(Heightmap.Types.OCEAN_FLOOR, worldPos.getX(), worldPos.getZ());

                int grayScale = getGrayScale(y, tileManager.serverLevel());

                colorData.setPixelRGBA(sampleX, sampleZ, grayScale);
                data[sampleX + sampleZ * sampledSize] = y;
            }
        }

        this.image = colorData;
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
                this.image = NativeImage.read(Files.readAllBytes(imagePath));
                if (this.image.getWidth() != (size / this.sampleResolution)) {
                    throw new IllegalArgumentException("Improper image width.");
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
    public @Nullable List<Component> toolTip(double mouseScreenX, double mouseScreenY, int mouseWorldX, int mouseWorldZ, int mouseTileLocalX, int mouseTileLocalY) {
        int y = heightsData[(mouseTileLocalX / sampleResolution) + (mouseTileLocalY / sampleResolution) * ((int) Math.sqrt(this.heightsData.length - 1))];

        return Collections.singletonList(Component.literal("Ocean Floor Height: " + y));
    }


    @Override
    public Renderer renderer() {
        return (graphics, size1, id, opacity, renderTileContext) -> {
            VertexConsumer vertexConsumer = graphics.bufferSource().getBuffer(WVRenderType.WORLD_VIEWER_GUI.apply(id, WVRenderType.DST_COLOR_SRC_ALPHA_TRANSPARENCY));
            ClientUtil.blit(vertexConsumer, graphics.pose(), opacity, 0, 0, 0F, 0F, size1, size1, size1, size1);
        };
    }

    @Override
    public boolean isComplete() {
        return this.image != null && this.heightsData != null && this.sampleResolution > 0;
    }

    @Override
    public NativeImage image() {
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
