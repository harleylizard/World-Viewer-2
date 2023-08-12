package dev.corgitaco.worldviewer.client.tile.tilelayer;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.corgitaco.worldviewer.client.ClientUtil;
import dev.corgitaco.worldviewer.client.WVRenderType;
import dev.corgitaco.worldviewer.client.screen.WorldScreenv2;
import dev.corgitaco.worldviewer.common.storage.DataTileManager;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class HeightsLayer extends TileLayer {

    private final int sampleResolution;
    private NativeImage image;

    private final int[][] heightsData;

    public HeightsLayer(DataTileManager tileManager, int y, int worldX, int worldZ, int size, int sampleResolution, WorldScreenv2 screen, LongSet sampledChunks) {
        super(tileManager, y, worldX, worldZ, size, sampleResolution, screen);
        this.sampleResolution = sampleResolution;


        int sampledSize = size / sampleResolution;
        int[][] colorData = new int[sampledSize][sampledSize];
        int[][] data = new int[sampledSize][sampledSize];

        BlockPos.MutableBlockPos worldPos = new BlockPos.MutableBlockPos();
        for (int sampleX = 0; sampleX < sampledSize; sampleX++) {
            for (int sampleZ = 0; sampleZ < sampledSize; sampleZ++) {
                worldPos.set(worldX + (sampleX * sampleResolution), 0, worldZ + (sampleZ * sampleResolution));

                sampledChunks.add(ChunkPos.asLong(worldPos));

                y = tileManager.getHeight(Heightmap.Types.OCEAN_FLOOR, worldPos.getX(), worldPos.getZ());

                int grayScale = getGrayScale(y, tileManager.serverLevel());

                colorData[sampleX][sampleZ] = grayScale;
                data[sampleX][sampleZ] = y;
            }
        }

        this.image = makeNativeImageFromColorData(colorData);
        this.heightsData = data;

    }

    public static int getGrayScale(int y, LevelHeightAccessor heightAccessor) {
        float pct = Mth.clamp(Mth.inverseLerp(y, 0, 255), 0, 1F);
        int color = Math.round(Mth.clampedLerp(0, 255, pct));
        return FastColor.ARGB32.color(255, color, color, color);
    }

    @Override
    public @Nullable List<Component> toolTip(double mouseScreenX, double mouseScreenY, int mouseWorldX, int mouseWorldZ, int mouseTileLocalX, int mouseTileLocalY) {
        int y = heightsData[mouseTileLocalX / sampleResolution][mouseTileLocalY / sampleResolution];

        return Collections.singletonList(Component.literal("Ocean Floor Height: " + y));
    }


    @Override
    public Renderer renderer() {
        return (graphics, size1, id, opacity, worldScreenv2) -> {
            VertexConsumer vertexConsumer = graphics.bufferSource().getBuffer(WVRenderType.WORLD_VIEWER_GUI.apply(id, WVRenderType.DST_COLOR_SRC_ALPHA_TRANSPARENCY));
            ClientUtil.blit(vertexConsumer, graphics.pose(), opacity, 0, 0, 0F, 0F, size1, size1, size1, size1);
        };
    }

    @Override
    public NativeImage image() {
        return this.image;
    }
}
