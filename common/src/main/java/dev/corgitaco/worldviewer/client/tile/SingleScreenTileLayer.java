package dev.corgitaco.worldviewer.client.tile;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.corgitaco.worldviewer.client.ClientUtil;
import dev.corgitaco.worldviewer.client.WVRenderType;
import dev.corgitaco.worldviewer.client.screen.WorldScreenv2;
import dev.corgitaco.worldviewer.client.tile.tilelayer.TileLayer;
import dev.corgitaco.worldviewer.common.storage.DataTileManager;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderStateShard;
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

    private final int maxTileWorldX;
    private final int maxTileWorldZ;
    private final int size;
    private final int sampleRes;

    private boolean shouldRender = true;

    private final LongSet sampledChunks = new LongOpenHashSet();

    public SingleScreenTileLayer(DataTileManager tileManager, TileLayer.Factory factory, int scrollY, int minTileWorldX, int minTileWorldZ, int size, int sampleRes, WorldScreenv2 worldScreenv2, @Nullable SingleScreenTileLayer lastResolution) {
        TileLayer tileLayer1 = null;
        this.minTileWorldX = minTileWorldX;
        this.minTileWorldZ = minTileWorldZ;

        this.maxTileWorldX = minTileWorldX + (size);
        this.maxTileWorldZ = minTileWorldZ + (size);
        this.size = size;
        this.sampleRes = sampleRes;

        if (lastResolution != null) {
            sampledChunks.addAll(lastResolution.sampledChunks);
            if (!lastResolution.tileLayer.usesLod()) {
                tileLayer1 = lastResolution.tileLayer;
            }
        }

        if (tileLayer1 == null) {
            tileLayer1 = factory.make(tileManager, scrollY, minTileWorldX, minTileWorldZ, size, sampleRes, worldScreenv2, sampledChunks);
        }
        this.tileLayer = tileLayer1;
        if (sampleRes == worldScreenv2.sampleResolution) {
            sampledChunks.forEach(tileManager::unloadTile);
        }


    }


    @Nullable
    public List<Component> toolTip(double mouseScreenX, double mouseScreenY, int mouseWorldX, int mouseWorldZ, int mouseTileLocalX, int mouseTileLocalY) {
        return this.tileLayer.toolTip(mouseScreenX, mouseScreenY, mouseWorldX, mouseWorldZ, mouseTileLocalX, mouseTileLocalY);
    }


    public void afterTilesRender(GuiGraphics guiGraphics, float opacity) {
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
    public void renderTile(GuiGraphics guiGraphics, float scale) {
        if (shouldRender) {
            if (this.dynamicTexture == null) {
                this.dynamicTexture = new DynamicTexture(this.tileLayer.image());
            }

            VertexConsumer vertexConsumer = guiGraphics.bufferSource().getBuffer(WVRenderType.WORLD_VIEWER_GUI.apply(dynamicTexture.getId(), transparencyStateShard()));

            ClientUtil.blit(vertexConsumer, guiGraphics.pose(), opacity(), 0, 0, 0F, 0F, this.size, this.size, this.size, this.size);
//          ClientUtil.drawOutlineWithWidth(guiGraphics, 0, 0, this.size, this.size, (int) Math.ceil(1.5 / scale), FastColor.ARGB32.color(255, 0, 255, 0));
        }
    }

    @Override
    public NativeImage image() {
        return this.tileLayer.image();
    }

    @Override
    public float opacity() {
        return this.tileLayer.opacity();
    }

    @Override
    public RenderStateShard.TransparencyStateShard transparencyStateShard() {
        return this.tileLayer.transparencyStateShard();
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

    @Override
    public void closeNativeImage() {
        this.tileLayer.image().close();
    }
}
