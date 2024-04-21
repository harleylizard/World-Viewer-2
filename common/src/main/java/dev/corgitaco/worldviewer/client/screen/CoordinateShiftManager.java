package dev.corgitaco.worldviewer.client.screen;

public record CoordinateShiftManager(int shift, int scaleShift) {


    public int getRegionBlockSize() {
        return 1 << shift << scaleShift;
    }

    public int getRegionImageSize() {
        return 1 << shift;
    }

    public int getTileImageSize() {
        return getRegionImageSize() >> (shift >> 1);
    }

    public int getTileBlockSize() {
        return ((1 << shift) >> (shift >> 1)) << scaleShift;
    }

    public int getTileCoordFromBlockCoord(int blockCoord) {
        return blockCoord >> (this.shift >> 1) >> scaleShift;
    }

    public int getBlockCoordFromTileCoord(int tileCoord) {
        return tileCoord << (this.shift >> 1) << scaleShift;
    }

    public int getRegionCoordFromTileCoord(int tileCoord) {
        return tileCoord >> (this.shift >> 1);
    }

    public int getTileCoordFromRegionCoord(int regionCoord) {
        return regionCoord << (this.shift >> 1);
    }

    public int getRegionWorldX(long regionPos) {
        return getRegionX(regionPos) << this.shift << this.scaleShift;
    }

    public int getRegionWorldZ(long regionPos) {
        return getRegionZ(regionPos) << this.shift << this.scaleShift;
    }

    public int getRegionCoordFromBlockCoord(int coord) {
        return coord >> this.shift >> this.scaleShift;
    }

    public int getBlockCoordFromRegionCoord(int coord) {
        return coord << this.shift << this.scaleShift;
    }

    public int getRegionX(long regionPos) {
        return getX(regionPos);
    }

    public int getRegionZ(long regionPos) {
        return getZ(regionPos);
    }

    public static int getX(long $$0) {
        return (int)($$0 & 4294967295L);
    }

    public static int getZ(long $$0) {
        return (int)($$0 >>> 32 & 4294967295L);
    }

}
