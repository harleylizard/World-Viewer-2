package dev.corgitaco.worldviewer.client;

import dev.corgitaco.worldviewer.client.tile.Tile;

public record WorldCoordSquare(int minX, int minZ, int maxX, int maxZ) implements Tile {
    @Override
    public int getMinTileWorldX() {
        return this.minX;
    }

    @Override
    public int getMinTileWorldZ() {
        return this.minZ;
    }

    @Override
    public int getMaxTileWorldX() {
        return this.maxX;
    }

    @Override
    public int getMaxTileWorldZ() {
        return this.maxZ;
    }
}
