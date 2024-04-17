package dev.corgitaco.worldviewer.client.screen;

import net.minecraft.world.level.ChunkPos;

public record CoordinateShiftManager(int shift) {


    public int getRegionBlockSize() {
        return 1 << shift;
    }

    public int getTileSize() {
        return getRegionBlockSize() >> (shift / 2);
    }

    public int getTileFromBlockCoord(int blockCoord) {
        return blockCoord >> this.shift / 2;
    }

    public int getRegionWorldX(long regionPos) {
        return getRegionX(regionPos) << this.shift;
    }

    public int getRegionWorldZ(long regionPos) {
        return getRegionZ(regionPos) << this.shift;
    }

    public int getRegionCoordFromBlockCoord(int coord) {
        return coord >> this.shift;
    }

    public int getBlockCoordFromRegionCoord(int coord) {
        return coord << this.shift;
    }

    public int getRegionX(long regionPos) {
        return ChunkPos.getX(regionPos);
    }

    public int getRegionZ(long regionPos) {
        return ChunkPos.getZ(regionPos);
    }

}
