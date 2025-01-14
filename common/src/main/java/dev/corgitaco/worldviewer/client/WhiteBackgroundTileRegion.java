package dev.corgitaco.worldviewer.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.corgitaco.worldviewer.client.render.ColorUtils;
import dev.corgitaco.worldviewer.client.screen.CoordinateShiftManager;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.Arrays;

public class WhiteBackgroundTileRegion extends TileRegion<WorldCoordSquare> {

    private final WVDynamicTexture texture;

    public WhiteBackgroundTileRegion(CoordinateShiftManager coordinateShiftManager, long regionPos) {
        super(coordinateShiftManager, regionPos);
        texture = new WVDynamicTexture(ClientUtil.createImage(coordinateShiftManager.getRegionImageSize(), coordinateShiftManager.getRegionImageSize(), true));
    }

    @Override
    public void render(MultiBufferSource.BufferSource bufferSource, PoseStack stack, BoundingBox worldViewArea) {
        renderRegionImage(bufferSource.getBuffer(WVRenderType.WORLD_VIEWER_GUI.apply(texture.getId(), RenderType.NO_TRANSPARENCY)), stack, worldViewArea, 1);
    }

    @Override
    public void insertTile(WorldCoordSquare layer) {
        int regionWorldX = getRegionBlockX();
        int regionWorldZ = getRegionBlockZ();
        int minTileWorldX = layer.getMinTileWorldX();
        int minTileWorldZ = layer.getMinTileWorldZ();

        int localBlockX = minTileWorldX - regionWorldX;
        int localBlockZ = minTileWorldZ - regionWorldZ;

        int localTileXIdx = this.coordinateShiftManager.getTileCoordFromBlockCoord(localBlockX);
        int localTileZIdx = this.coordinateShiftManager.getTileCoordFromBlockCoord(localBlockZ);

        int tileImageSize = this.coordinateShiftManager.getTileImageSize();

        int xOffset = localTileXIdx * this.coordinateShiftManager.getTileImageSize();
        int zOffset = localTileZIdx * this.coordinateShiftManager.getTileImageSize();

        int[] pixels = new int[this.coordinateShiftManager.getTileImageSize() * this.coordinateShiftManager.getTileImageSize()];

        Arrays.fill(pixels, ColorUtils.ABGR.packABGR(255, 255, 255, 255));

        this.texture.uploadSubImageWithOffset(xOffset, zOffset, tileImageSize, tileImageSize, pixels);
    }

    @Override
    public boolean hasTile(int idx) {
        return true;
    }

    @Override
    public boolean dropTile(int idx) {
        return false;
    }

    @Override
    public void close() {
        this.texture.close();
    }
}
