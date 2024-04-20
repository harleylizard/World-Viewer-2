package dev.corgitaco.worldviewer.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.corgitaco.worldviewer.client.screen.CoordinateShiftManager;
import dev.corgitaco.worldviewer.client.tile.Tile;
import net.minecraft.client.renderer.MultiBufferSource;

public abstract class TileRegion<T extends Tile> implements AutoCloseable {

    protected final CoordinateShiftManager coordinateShiftManager;
    private final long regionPos;

    public TileRegion(CoordinateShiftManager coordinateShiftManager, long regionPos) {
        if (coordinateShiftManager.shift() % 2 != 0) {
            throw new IllegalArgumentException("Shift must be an even value");
        }
        this.coordinateShiftManager = coordinateShiftManager;
        this.regionPos = regionPos;
    }

    protected int getRenderX() {
        return getRegionBlockX() >> this.coordinateShiftManager.scaleShift();
    }

    protected int getRenderY() {
        return getRegionBlockZ() >> this.coordinateShiftManager.scaleShift();
    }

    public abstract void render(MultiBufferSource.BufferSource bufferSource, PoseStack stack);

    public void renderLast(MultiBufferSource.BufferSource bufferSource, PoseStack stack) {}


    public abstract void insertTile(T layer);

    public int getRegionBlockX() {
        return this.coordinateShiftManager.getRegionWorldX(regionPos);
    }

    public int getRegionBlockZ() {
        return this.coordinateShiftManager.getRegionWorldZ(regionPos);
    }

    public int getRegionX() {
        return this.coordinateShiftManager.getRegionX(regionPos);
    }

    public int getRegionZ() {
        return this.coordinateShiftManager.getRegionZ(regionPos);
    }

}
