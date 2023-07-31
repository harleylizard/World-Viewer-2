package dev.corgitaco.worldviewer.client.tile;

import dev.corgitaco.worldviewer.util.LongPackingUtil;
import net.minecraft.core.BlockPos;

import static dev.corgitaco.worldviewer.util.LongPackingUtil.getTileX;
import static dev.corgitaco.worldviewer.util.LongPackingUtil.getTileZ;

public class TileCoordinateShiftingManager {

    private final int shift;


    public TileCoordinateShiftingManager(int initialScale) {
        this.shift = initialScale;
    }

    public int getShift() {
        return shift;
    }

    public TileCoordinateShiftingManager next() {
        return new TileCoordinateShiftingManager(this.shift + 1);
    }

    public TileCoordinateShiftingManager previous() {
        return new TileCoordinateShiftingManager(this.shift - 1);
    }

    public long tileKey(BlockPos pos) {
        return LongPackingUtil.tileKey(blockToTile(pos.getX()), blockToTile(pos.getZ()));
    }

    public int blockToTile(int blockCoord) {
        return LongPackingUtil.blockToTile(blockCoord, shift);
    }

    public int tileToBlock(int tileCoord) {
        return LongPackingUtil.tileToBlock(tileCoord, shift);
    }

    public int getScreenCenterX(int screenWidth) {
        return (screenWidth / 2) / shift;
    }

    public int getScreenCenterZ(int screenHeight) {
        return (int) ((screenHeight / 2) / shift);
    }

    public int getWorldXFromTileKey(long tileKey) {
        return tileToBlock(getTileX(tileKey));
    }

    public int getWorldZFromTileKey(long tileKey) {
        return tileToBlock(getTileZ(tileKey));
    }


    public int getTileX(long tileKey) {
        return LongPackingUtil.getTileX(tileKey);
    }

    public int getTileZ(long tileKey) {
        return LongPackingUtil.getTileZ(tileKey);
    }
}
