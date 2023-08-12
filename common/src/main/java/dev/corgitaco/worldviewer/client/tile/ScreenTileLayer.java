package dev.corgitaco.worldviewer.client.tile;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderStateShard;

public interface ScreenTileLayer {


    int getMinTileWorldX();

    int getMinTileWorldZ();


    int getMaxTileWorldX();

    int getMaxTileWorldZ();

    void renderTile(GuiGraphics guiGraphics, float scale);

    RenderStateShard.TransparencyStateShard transparencyStateShard();

    NativeImage image();

    float opacity();

    int size();

    boolean sampleResCheck(int worldScreenSampleRes);

    boolean shouldRender();

    void setShouldRender(boolean shouldRender);

    void closeDynamicTexture();

    void releaseDynamicTextureID();

    void closeNativeImage();

    default void closeAll() {
        closeNativeImage();
        closeDynamicTexture();
    }
}
