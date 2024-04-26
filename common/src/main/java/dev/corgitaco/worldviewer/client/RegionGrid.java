package dev.corgitaco.worldviewer.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.corgitaco.worldviewer.client.screen.CoordinateShiftManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.FastColor;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

public class RegionGrid implements Region {

    private final long regionPos;
    private final CoordinateShiftManager shiftManager;
    private final int scale;

    private final WVDynamicTexture texture;

    public RegionGrid(long regionPos, CoordinateShiftManager shiftManager, int scale) {
        this.regionPos = regionPos;
        this.shiftManager = shiftManager;
        this.scale = scale;

        WVNativeImage image = ClientUtil.createImage(shiftManager.getRegionImageSize(), shiftManager.getRegionImageSize(), true);

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
    public void render(MultiBufferSource.BufferSource bufferSource, PoseStack stack, BoundingBox worldViewArea) {
        renderRegionImage(bufferSource.getBuffer(WVRenderType.WORLD_VIEWER_GUI.apply(texture.getId(), RenderType.NO_TRANSPARENCY)), stack, worldViewArea, 1);
    }

    public void renderCoords(MultiBufferSource.BufferSource bufferSource, PoseStack stack, BoundingBox worldViewArea) {
        int range = this.shiftManager.getTileBlockSize() << scale;
        int offset = this.shiftManager.getTileBlockSize() << scale >> this.coordinateShiftManager().scaleShift();

        for (int xTile = 0; xTile <= range; xTile++) {
            for (int zTile = 0; zTile <= range; zTile++) {
                int xOffset = xTile * offset;
                int yOffset = zTile * offset;

                int worldX = getRegionBlockX() + (xTile * range);
                int worldZ = getRegionBlockZ() + (zTile * range);


                if (worldViewArea.intersects(worldX, worldZ, worldX, worldZ)) {
                    Font font = Minecraft.getInstance().font;
                    String coord = "%d,%d".formatted(worldX, worldZ);

                    int drawX = getRenderX() + xOffset - font.width(coord) - 2;
                    int drawY = getRenderY() + yOffset + 4;
                    font.drawInBatch(coord, drawX, drawY, FastColor.ARGB32.color(255, 255, 255, 255), false, stack.last().pose(), bufferSource, Font.DisplayMode.NORMAL, 0, 15728880, font.isBidirectional());
                }
            }
        }
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
