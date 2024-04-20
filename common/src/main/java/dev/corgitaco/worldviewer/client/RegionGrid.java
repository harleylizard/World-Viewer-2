package dev.corgitaco.worldviewer.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.corgitaco.worldviewer.client.screen.CoordinateShiftManager;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.FastColor;

public class RegionGrid implements Region {

    private final long regionPos;
    private final CoordinateShiftManager shiftManager;

    private final WVDynamicTexture texture;

    public RegionGrid(long regionPos, CoordinateShiftManager shiftManager) {
        this.regionPos = regionPos;
        this.shiftManager = shiftManager;

        WVNativeImage image = ClientUtil.createImage(shiftManager.getRegionImageSize(), shiftManager.getRegionImageSize(), true);

        int scale = 2;

        int range = shiftManager.getTileBlockSize() << scale;
        int offset = shiftManager.getTileBlockSize() << scale >> this.coordinateShiftManager().scaleShift();

        for (int xTile = 0; xTile <= range; xTile++) {
            for (int y = 0; y < image.getHeight(); y++) {
                for (int lineThickness = -1; lineThickness <= 1; lineThickness++) {
                    int drawX = xTile * offset;
                    int drawY = y + lineThickness;

                    if (withinBounds(image, drawX, drawY)) {
                        image.setPixelRGBA(drawX, drawY, FastColor.ABGR32.color(255, 255, 255, 255));
                    }
                }
            }
        }

        for (int yTile = 0; yTile <= range; yTile++) {
            for (int x = 0; x < image.getWidth(); x++) {
                for (int lineThickness = -1; lineThickness <= 1; lineThickness++) {
                    int drawY = yTile * offset;
                    int drawX = x + lineThickness;
                    if (withinBounds(image, drawX, drawY)) {
                        image.setPixelRGBA(drawX, drawY, FastColor.ABGR32.color(255, 255, 255, 255));
                    }
                }
            }
        }


        this.texture = new WVDynamicTexture(image);
    }

    // Line thickness may cause this to leak over the edge of the image so we have to do this to mitigate such cases.
    public boolean withinBounds(NativeImage image, int x, int y) {
        return image.getHeight() > y && y >= 0 && image.getWidth() > x && 0 <= x;
    }

    @Override
    public void render(MultiBufferSource.BufferSource bufferSource, PoseStack stack) {
        ClientUtil.blit(bufferSource.getBuffer(WVRenderType.WORLD_VIEWER_GUI.apply(this.texture.getId(), RenderType.NO_TRANSPARENCY)), stack, 1, getRenderX(), getRenderY(), 0, 0, this.coordinateShiftManager().getRegionImageSize(), this.coordinateShiftManager().getRegionImageSize(), this.coordinateShiftManager().getRegionImageSize(), this.coordinateShiftManager().getRegionImageSize());
    }

    @Override
    public long regionPos() {
        return this.regionPos;
    }

    @Override
    public CoordinateShiftManager coordinateShiftManager() {
        return this.shiftManager;
    }

    @Override
    public void close() {
        this.texture.close();
    }
}
