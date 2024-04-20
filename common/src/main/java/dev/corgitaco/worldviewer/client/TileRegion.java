package dev.corgitaco.worldviewer.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.corgitaco.worldviewer.client.screen.CoordinateShiftManager;
import dev.corgitaco.worldviewer.client.tile.RenderTileContext;
import dev.corgitaco.worldviewer.client.tile.Tile;
import net.minecraft.client.renderer.MultiBufferSource;

public abstract class TileRegion<T extends Tile> implements Region {

    protected final CoordinateShiftManager coordinateShiftManager;
    private final long regionPos;

    public TileRegion(CoordinateShiftManager coordinateShiftManager, long regionPos) {
        if (coordinateShiftManager.shift() % 2 != 0) {
            throw new IllegalArgumentException("Shift must be an even value");
        }
        this.coordinateShiftManager = coordinateShiftManager;
        this.regionPos = regionPos;
    }

    public void renderLast(MultiBufferSource.BufferSource bufferSource, PoseStack stack, RenderTileContext renderTileContext) {}


    public abstract void insertTile(T layer);

    @Override
    public long regionPos() {
        return this.regionPos;
    }

    @Override
    public CoordinateShiftManager coordinateShiftManager() {
        return this.coordinateShiftManager;
    }
}
