package dev.corgitaco.worldviewer.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.corgitaco.worldviewer.client.screen.CoordinateShiftManager;
import dev.corgitaco.worldviewer.client.tile.RenderTileContext;
import dev.corgitaco.worldviewer.client.tile.SingleScreenTileLayer;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;

public class RenderTileLayerTileRegion extends TileRegion<SingleScreenTileLayer> {

    private final SingleScreenTileLayer[] layers;

    private final WVDynamicTexture texture; // TODO: Allow nullable
    private final RenderStateShard.TransparencyStateShard transparencyStateShard;


    public RenderTileLayerTileRegion(CoordinateShiftManager coordinateShiftManager, long regionPos, RenderStateShard.TransparencyStateShard transparencyStateShard) {
        super(coordinateShiftManager, regionPos);
        this.transparencyStateShard = transparencyStateShard;

        int tileSize = coordinateShiftManager.getTileImageSize();
        layers = new SingleScreenTileLayer[(int) (tileSize * tileSize)];

        texture = new WVDynamicTexture(ClientUtil.createImage(coordinateShiftManager.getRegionImageSize(), coordinateShiftManager.getRegionImageSize(), true));
    }


    public void insertTile(SingleScreenTileLayer layer) {
        int regionWorldX = getRegionBlockX();
        int regionWorldZ = getRegionBlockZ();
        int minTileWorldX = layer.getMinTileWorldX();
        int minTileWorldZ = layer.getMinTileWorldZ();

        int localBlockX = minTileWorldX - regionWorldX;
        int localBlockZ = minTileWorldZ - regionWorldZ;

        int localTileXIdx = this.coordinateShiftManager.getTileCoordFromBlockCoord(localBlockX);
        int localTileZIdx = this.coordinateShiftManager.getTileCoordFromBlockCoord(localBlockZ);

        int tileImageSize = this.coordinateShiftManager.getTileImageSize();
        int idx = localTileXIdx + localTileZIdx * tileImageSize;

        layers[idx] = layer;

        int xOffset = localTileXIdx * this.coordinateShiftManager.getTileImageSize();
        int zOffset = localTileZIdx * this.coordinateShiftManager.getTileImageSize();

        @Nullable int[] image = layer.image();
        if (image != null) {
            this.texture.uploadSubImageWithOffset(xOffset, zOffset, tileImageSize, tileImageSize, image);
        }
    }

    @Override
    public boolean hasTile(int idx) {
        return this.layers[idx] != null;
    }

    public void render(MultiBufferSource.BufferSource bufferSource, PoseStack stack) {
        int renderX = getRenderX();
        int renderY = getRenderY();
        ClientUtil.blit(bufferSource.getBuffer(WVRenderType.WORLD_VIEWER_GUI.apply(texture.getId(), this.transparencyStateShard)), stack, 1, renderX, renderY, 0F, 0F, this.coordinateShiftManager.getRegionImageSize(), this.coordinateShiftManager.getRegionImageSize(), this.coordinateShiftManager.getRegionImageSize(), this.coordinateShiftManager.getRegionImageSize());
    }


    @Override
    public void renderLast(MultiBufferSource.BufferSource bufferSource, PoseStack stack, RenderTileContext renderTileContext) {
        for (SingleScreenTileLayer layer : this.layers) {
            if (layer != null) {
                layer.afterTilesRender(bufferSource, stack, 1, renderTileContext);
            }
        }
    }

    public Long2ObjectMap<DynamicTexture> spriteRenderer(RenderTileContext renderTileContext) {
        Long2ObjectMap<DynamicTexture> spriteRenderers = new Long2ObjectLinkedOpenHashMap<>();

        for (SingleScreenTileLayer layer : this.layers) {
            if (layer != null) {
                spriteRenderers.putAll(layer.spriteRenderer(renderTileContext));
            }
        }

        return spriteRenderers;
    }

    public Collection<Component> toolTip(double mouseScreenX, double mouseScreenY, int mouseWorldX, int mouseWorldZ) {
        int idx = getTileIdx(mouseWorldX, mouseWorldZ);

        @Nullable SingleScreenTileLayer layer = layers[idx];
        if (layer != null) {
            int mouseTileLocalX = (mouseWorldX - layer.getMinTileWorldX());
            int mouseTileLocalY = (mouseWorldZ - layer.getMinTileWorldZ());
            return layer.toolTip(mouseScreenX, mouseScreenY, mouseWorldX, mouseWorldZ, mouseTileLocalX, mouseTileLocalY, mouseTileLocalX >> this.coordinateShiftManager.scaleShift(), mouseTileLocalY >> this.coordinateShiftManager.scaleShift());
        }

        return Collections.emptyList();
    }


    @Override
    public void close() {
        this.texture.close();
    }
}
