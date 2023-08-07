package dev.corgitaco.worldviewer.client.tile.tilelayer;

import com.mojang.blaze3d.platform.NativeImage;
import dev.corgitaco.worldviewer.client.screen.WorldScreenv2;
import dev.corgitaco.worldviewer.common.storage.DataTileManager;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.levelgen.Heightmap;

public class HeightsLayer extends TileLayer {

    private int[][] colorData;
    private NativeImage image;

    public HeightsLayer(DataTileManager tileManager, int y, int worldX, int worldZ, int size, int sampleResolution, WorldScreenv2 screen, LongSet sampledChunks) {
        super(tileManager, y, worldX, worldZ, size, sampleResolution, screen);


        int sampledSize = size / sampleResolution;
        colorData = new int[sampledSize][sampledSize];

        BlockPos.MutableBlockPos worldPos = new BlockPos.MutableBlockPos();
        for (int sampleX = 0; sampleX < sampledSize; sampleX++) {
            for (int sampleZ = 0; sampleZ < sampledSize; sampleZ++) {
                worldPos.set(worldX + (sampleX * sampleResolution), 0, worldZ + (sampleZ * sampleResolution));

                sampledChunks.add(ChunkPos.asLong(worldPos));

                y = tileManager.getHeight(Heightmap.Types.OCEAN_FLOOR, worldPos.getX(), worldPos.getZ());

                int grayScale = getGrayScale(y, tileManager.serverLevel());

                colorData[sampleX][sampleZ] = grayScale;
            }
        }

        this.image = makeNativeImageFromColorData(colorData);

    }

    public static int getGrayScale(int y, LevelHeightAccessor heightAccessor) {
        float pct = Mth.clamp(Mth.inverseLerp(y, 0, 255), 0, 1F);
        int color = Math.round(Mth.clampedLerp(127, 255, pct));
        return FastColor.ARGB32.color(255, color, color, color);
    }

    @Override
    public float opacity() {
        return 1;
    }

    @Override
    public NativeImage image() {
        return this.image;
    }
}
