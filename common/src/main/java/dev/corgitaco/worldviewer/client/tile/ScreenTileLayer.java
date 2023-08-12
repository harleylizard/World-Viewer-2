package dev.corgitaco.worldviewer.client.tile;

import com.mojang.blaze3d.platform.NativeImage;
import dev.corgitaco.worldviewer.client.screen.WorldScreenv2;
import dev.corgitaco.worldviewer.client.tile.tilelayer.TileLayer;
import net.minecraft.client.gui.GuiGraphics;
import org.jetbrains.annotations.Nullable;

public interface ScreenTileLayer {


    int getMinTileWorldX();

    int getMinTileWorldZ();


    int getMaxTileWorldX();

    int getMaxTileWorldZ();

    void renderTile(GuiGraphics guiGraphics, float scale, float opacity, WorldScreenv2 worldScreenv2);

    TileLayer.Renderer renderer();

    @Nullable
    NativeImage image();

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
