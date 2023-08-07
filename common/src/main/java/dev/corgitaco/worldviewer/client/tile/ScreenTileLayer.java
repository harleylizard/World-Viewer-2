package dev.corgitaco.worldviewer.client.tile;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.gui.GuiGraphics;

public interface ScreenTileLayer {


    int getMinTileWorldX();

    int getMinTileWorldZ();


    int getMaxTileWorldX();
    int getMaxTileWorldZ();

    void renderTile(GuiGraphics guiGraphics, float scale);

    NativeImage image();

    float opacity();

    int size();

    boolean sampleResCheck(int worldScreenSampleRes);

    boolean shouldRender();

    void setShouldRender(boolean shouldRender);

    void close();
}
