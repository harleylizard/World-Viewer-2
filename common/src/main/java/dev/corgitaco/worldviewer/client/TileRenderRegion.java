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

        int tileSize = coordinateShiftManager.getTileImageSize();
        layers = new SingleScreenTileLayer[(int) (tileSize * tileSize)];

        texture = new WVDynamicTexture(ClientUtil.createImage(coordinateShiftManager.getRegionImageSize(), coordinateShiftManager.getRegionImageSize(), true));
    }


    public void insertLayer(SingleScreenTileLayer layer) {
        int regionWorldX = getRegionBlockX();
        int regionWorldZ = getRegionBlockZ();
        int minTileWorldX = layer.getMinTileWorldX();
        int minTileWorldZ = layer.getMinTileWorldZ();

        int localBlockX = minTileWorldX - regionWorldX;
        int localBlockZ = minTileWorldZ - regionWorldZ;

        int localTileXIdx = this.coordinateShiftManager.getTileCoordFromBlockCoord(localBlockX) >> this.coordinateShiftManager.scaleShift();
        int localTileZIdx = this.coordinateShiftManager.getTileCoordFromBlockCoord(localBlockZ) >> this.coordinateShiftManager.scaleShift();

        int tileImageSize = this.coordinateShiftManager.getTileImageSize();
        layers[localTileXIdx + localTileZIdx * tileImageSize] = layer;

        this.texture.uploadSubImageWithOffset(localBlockX >> this.coordinateShiftManager.scaleShift() , localBlockZ >> this.coordinateShiftManager.scaleShift(), this.coordinateShiftManager.getTileImageSize(), this.coordinateShiftManager.getTileImageSize(), layer.image());
    }

    public void render(GuiGraphics guiGraphics) {
        ClientUtil.blit(guiGraphics.bufferSource().getBuffer(WVRenderType.WORLD_VIEWER_GUI.apply(texture.getId(), RenderType.NO_TRANSPARENCY)), guiGraphics.pose(), 1, getRegionBlockX() >> this.coordinateShiftManager.scaleShift(), getRegionBlockZ() >> this.coordinateShiftManager.scaleShift(), 0F, 0F, this.coordinateShiftManager.getRegionImageSize(), this.coordinateShiftManager.getRegionImageSize(), this.coordinateShiftManager.getRegionImageSize(), this.coordinateShiftManager.getRegionImageSize());
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
