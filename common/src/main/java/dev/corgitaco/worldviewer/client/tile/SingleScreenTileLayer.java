package dev.corgitaco.worldviewer.client.tile;

import com.mojang.blaze3d.platform.NativeImage;
import dev.corgitaco.worldviewer.client.tile.tilelayer.TileLayer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class SingleScreenTileLayer implements ScreenTileLayer {


    private final TileLayer tileLayer;

    private final int minTileWorldX;
    private final int minTileWorldZ;
    private final int size;

    private final int maxTileWorldX;
    private final int maxTileWorldZ;
    private final int sampleRes;

    private boolean shouldRender = true;

    public SingleScreenTileLayer(TileLayer tileLayer, int minTileWorldX, int minTileWorldZ, int size) {
        this.tileLayer = tileLayer;
        this.minTileWorldX = minTileWorldX;
        this.minTileWorldZ = minTileWorldZ;
        this.size = size;
        this.maxTileWorldX = minTileWorldX + size;
        this.maxTileWorldZ = minTileWorldZ + size;
        this.sampleRes = this.tileLayer.sampleRes();
    }



    @Nullable
    public List<Component> toolTip(double mouseScreenX, double mouseScreenY, int mouseWorldX, int mouseWorldZ, int mouseTileLocalX, int mouseTileLocalY) {
        return this.tileLayer.toolTip(mouseScreenX, mouseScreenY, mouseWorldX, mouseWorldZ, mouseTileLocalX, mouseTileLocalY);
    }

    public void afterTilesRender(GuiGraphics guiGraphics, float scale, float opacity) {
        this.tileLayer.afterTilesRender(guiGraphics, opacity, getMinTileWorldX(), getMinTileWorldZ());
    }

    public int getMinTileWorldX() {
        return minTileWorldX;
    }

    public int getMinTileWorldZ() {
        return minTileWorldZ;
    }

    @Override
    public int getMaxTileWorldX() {
        return this.maxTileWorldX;
    }

    @Override
    public int getMaxTileWorldZ() {
        return maxTileWorldZ;
    }

    @Override
    public @Nullable int[] image() {
        return this.tileLayer.image();
    }

    @Override
    public int size() {
        return this.size;
    }

    public int getSampleRes() {
        return sampleRes;
    }

    public int getSize() {
        return size;
    }

    public TileLayer tileLayer() {
        return tileLayer;
    }
}
