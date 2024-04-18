package dev.corgitaco.worldviewer.client.screen;

import net.minecraft.world.level.ChunkPos;

public record CoordinateShiftManager(int shift, int scaleShift) {


    public int getRegionBlockSize() {
        return 1 << shift << scaleShift;
    }

    public int getRegionImageSize() {
        return 1 << shift;
    }

    public int getTileImageSize() {
        return getRegionImageSize() >> (shift / 2);
    }

    public int getTileBlockSize() {
        return getRegionBlockSize() >> (shift / 2);
    }

    public int getTileCoordFromBlockCoord(int blockCoord) {
        return blockCoord >> (this.shift / 2) >> scaleShift;
    }

    public int getBlockCoordFromTileCoord(int tileCoord) {
        return tileCoord << (this.shift / 2) << scaleShift;
    }

    public int getRegionCoordFromTileCoord(int tileCoord) {
        return tileCoord >> (this.shift / 2) << scaleShift;
    }

    public int getTileCoordFromRegionCoord(int regionCoord) {
        return regionCoord << (this.shift / 2) << scaleShift;
    }


    public int getRegionWorldX(long regionPos) {
        return getRegionX(regionPos) << this.shift << scaleShift;
    }

    public int getRegionWorldZ(long regionPos) {
        return getRegionZ(regionPos) << this.shift << scaleShift;
    }

    public int getRegionCoordFromBlockCoord(int coord) {
        return coord >> this.shift >> scaleShift;
    }

    public int getBlockCoordFromRegionCoord(int coord) {
        return coord << this.shift >> scaleShift;
    }

    public int getRegionX(long regionPos) {
        return ChunkPos.getX(regionPos);
    }

    public int getRegionZ(long regionPos) {
        return ChunkPos.getZ(regionPos);
    }

}
