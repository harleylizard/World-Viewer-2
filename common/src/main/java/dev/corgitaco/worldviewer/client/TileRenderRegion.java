package dev.corgitaco.worldviewer.client;

import dev.corgitaco.worldviewer.client.render.ColorUtils;
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

        int localTileXIdx = this.coordinateShiftManager.getTileCoordFromBlockCoord(localBlockX);
        int localTileZIdx = this.coordinateShiftManager.getTileCoordFromBlockCoord(localBlockZ);

        int tileImageSize = this.coordinateShiftManager.getTileImageSize();
        int idx = localTileXIdx + localTileZIdx * tileImageSize;
        layers[idx] = layer;

        int xOffset = localTileXIdx * this.coordinateShiftManager.getTileImageSize();
        int zOffset = localTileZIdx * this.coordinateShiftManager.getTileImageSize();

        this.texture.uploadSubImageWithOffset(xOffset, zOffset, tileImageSize, tileImageSize, layer.image());
    }

    public SingleScreenTileLayer[] getLayers() {
        return layers;
    }

    public void render(GuiGraphics guiGraphics) {
        int renderX = getRenderX();
        int renderY = getRenderY();
        ClientUtil.blit(guiGraphics.bufferSource().getBuffer(WVRenderType.WORLD_VIEWER_GUI.apply(texture.getId(), RenderType.NO_TRANSPARENCY)), guiGraphics.pose(), 1, renderX, renderY, 0F, 0F, this.coordinateShiftManager.getRegionImageSize(), this.coordinateShiftManager.getRegionImageSize(), this.coordinateShiftManager.getRegionImageSize(), this.coordinateShiftManager.getRegionImageSize());
    }

    private int getRenderX() {
        return getRegionBlockX() >> this.coordinateShiftManager.scaleShift();
    }

    private int getRenderY() {
        return getRegionBlockZ() >> this.coordinateShiftManager.scaleShift();
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
