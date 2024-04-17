package dev.corgitaco.worldviewer.client;

import dev.corgitaco.worldviewer.client.screen.CoordinateShiftManager;
import dev.corgitaco.worldviewer.client.tile.SingleScreenTileLayer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;

public class TileRenderRegion implements AutoCloseable {


    private final CoordinateShiftManager coordinateShiftManager;
    private final long regionPos;

    private final SingleScreenTileLayer[] layers;

    public WVDynamicTexture texture;


    public TileRenderRegion(CoordinateShiftManager coordinateShiftManager, long regionPos) {
        this.coordinateShiftManager = coordinateShiftManager;
        this.regionPos = regionPos;
        if (coordinateShiftManager.shift() % 2 != 0) {
            throw new IllegalArgumentException("Shift must be an even value");
        }

        int tileSize = coordinateShiftManager.getTileSize();
        layers = new SingleScreenTileLayer[(int) (tileSize * tileSize)];

        int blockSize = coordinateShiftManager.getRegionBlockSize();
        texture = new WVDynamicTexture(ClientUtil.createImage(blockSize, blockSize, true));
    }


    public void insertLayer(SingleScreenTileLayer layer) {
        int regionWorldX = getRegionBlockX();
        int regionWorldZ = getRegionBlockZ();
        int minTileWorldX = layer.getMinTileWorldX();
        int minTileWorldZ = layer.getMinTileWorldZ();

        int localBlockX = minTileWorldX - regionWorldX;
        int localBlockZ = minTileWorldZ - regionWorldZ;

        int localTileXIdx = this.coordinateShiftManager.getTileFromBlockCoord(localBlockX);
        int localTileZIdx = this.coordinateShiftManager.getTileFromBlockCoord(localBlockZ);

        layers[localTileXIdx + localTileZIdx * this.coordinateShiftManager.getTileSize()] = layer;

        this.texture.uploadSubImageWithOffset(localBlockX, localBlockZ, this.coordinateShiftManager.getTileSize(), this.coordinateShiftManager.getTileSize(), layer.image());
    }

    public void render(GuiGraphics guiGraphics) {
        ClientUtil.blit(guiGraphics.bufferSource().getBuffer(WVRenderType.WORLD_VIEWER_GUI.apply(texture.getId(), RenderType.NO_TRANSPARENCY)), guiGraphics.pose(), 1, getRegionBlockX(), getRegionBlockZ(), 0F, 0F, this.coordinateShiftManager.getRegionBlockSize(), this.coordinateShiftManager.getRegionBlockSize(), this.coordinateShiftManager.getRegionBlockSize(), this.coordinateShiftManager.getRegionBlockSize());
    }


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

    @Override
    public void close() {
        this.texture.close();
    }
}
