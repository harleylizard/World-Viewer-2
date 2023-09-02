package dev.corgitaco.worldviewer.client.tile;

import com.mojang.blaze3d.platform.NativeImage;
import dev.corgitaco.worldviewer.client.CloseCheck;
import dev.corgitaco.worldviewer.client.screen.WorldScreenv2;
import dev.corgitaco.worldviewer.client.tile.tilelayer.TileLayer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class SingleScreenTileLayer implements ScreenTileLayer {


    private final TileLayer tileLayer;

    @Nullable
    public DynamicTexture dynamicTexture;

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

    public void afterTilesRender(GuiGraphics guiGraphics, float scale, float opacity, RenderTileContext renderTileContext) {
        this.tileLayer.afterTilesRender(guiGraphics, opacity, getMinTileWorldX(), getMinTileWorldZ(), renderTileContext);
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
    public void renderTile(GuiGraphics guiGraphics, float scale, float opacity, RenderTileContext renderTileContext) {
        if (shouldRender && this.tileLayer.image() != null) {
            if (this.dynamicTexture == null) {
                this.dynamicTexture = new DynamicTexture(this.tileLayer.image());
            }
            renderer().render(guiGraphics, size, this.dynamicTexture.getId(), opacity, renderTileContext);
        }
    }

    @Override
    public TileLayer.Renderer renderer() {
        return this.tileLayer.renderer();
    }

    @Override
    public NativeImage image() {
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

    @Override
    public boolean sampleResCheck(int worldScreenSampleRes) {
        return this.sampleRes == worldScreenSampleRes;
    }

    @Override
    public boolean shouldRender() {
        return this.shouldRender;
    }

    @Override
    public void setShouldRender(boolean shouldRender) {
        this.shouldRender = shouldRender;
    }

    @Override
    public void closeDynamicTexture() {
        if (this.dynamicTexture != null) {
            this.dynamicTexture.close();
        }
    }

    @Override
    public void releaseDynamicTextureID() {
        if (this.dynamicTexture != null) {
            this.dynamicTexture.releaseId();
        }
    }

    public TileLayer tileLayer() {
        return tileLayer;
    }

    @Override
    public void closeNativeImage() {
        this.tileLayer.close();
    }

    @Override
    public boolean canClose() {
        if (image() == null) {
            return true;
        }
        return ((CloseCheck)(Object) this.image()).canClose();
    }

    @Override
    public void setCanClose(boolean canClose) {
        if (image() != null) {

            ((CloseCheck)(Object) this.image()).setCanClose(canClose);
        }
    }

    @Override
    public boolean shouldClose() {
        if (image() == null) {
            return true;
        }
        return ((CloseCheck)(Object) this.image()).shouldClose();
    }

    @Override
    public void setShouldClose(boolean shouldClose) {
        if (image() != null) {
            ((CloseCheck)(Object) this.image()).setShouldClose(shouldClose);
        }
    }
}
