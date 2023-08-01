package dev.corgitaco.worldviewer.client.tile;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.gui.GuiGraphics;

import java.util.Map;

public interface ScreenTile {


    int getMinTileWorldX();

    int getMinTileWorldZ();


    int getMaxTileWorldX();
    int getMaxTileWorldZ();

    void renderTile(GuiGraphics guiGraphics, float scale);

    Map<String, NativeImage> layers();

    int size();

    boolean sampleResCheck(int worldScreenSampleRes);

    boolean shouldRender();

    void setShouldRender(boolean shouldRender);

    void close();
}
