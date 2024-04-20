package dev.corgitaco.worldviewer.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.corgitaco.worldviewer.client.screen.CoordinateShiftManager;
import dev.corgitaco.worldviewer.client.tile.SingleScreenTileLayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;

import java.util.Collection;
import java.util.Collections;

public class TileRenderRegion implements AutoCloseable {


    private final CoordinateShiftManager coordinateShiftManager;
    private final long regionPos;

    private final SingleScreenTileLayer[] layers;

    private final WVDynamicTexture texture; // TODO: Allow nullable


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

    public void render(MultiBufferSource.BufferSource bufferSource, PoseStack stack) {
        int renderX = getRenderX();
        int renderY = getRenderY();
        ClientUtil.blit(bufferSource.getBuffer(WVRenderType.WORLD_VIEWER_GUI.apply(texture.getId(), RenderType.NO_TRANSPARENCY)), stack, 1, renderX, renderY, 0F, 0F, this.coordinateShiftManager.getRegionImageSize(), this.coordinateShiftManager.getRegionImageSize(), this.coordinateShiftManager.getRegionImageSize(), this.coordinateShiftManager.getRegionImageSize());
    }

    public void renderLast(MultiBufferSource.BufferSource bufferSource, PoseStack stack) {

    }

    public Collection<Component> toolTip(MultiBufferSource.BufferSource bufferSource, PoseStack stack, double mouseWorldX, double mouseWorldZ) {
        return Collections.emptyList(); // TODO
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
