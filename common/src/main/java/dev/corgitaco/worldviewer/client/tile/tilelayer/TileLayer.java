package dev.corgitaco.worldviewer.client.tile.tilelayer;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.corgitaco.worldviewer.client.ClientUtil;
import dev.corgitaco.worldviewer.client.WVRenderType;
import dev.corgitaco.worldviewer.client.screen.WorldScreenv2;
import dev.corgitaco.worldviewer.common.storage.DataTileManager;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class TileLayer {


    public static final List<TileLayerRegistryEntry> FACTORY_REGISTRY = Util.make(() -> {
        List<TileLayerRegistryEntry> tileLayers = new ArrayList<>();
        tileLayers.add(new TileLayerRegistryEntry("biomes", 1, BiomeLayer::new));
        tileLayers.add(new TileLayerRegistryEntry("heights", 0.5F, HeightsLayer::new));
        tileLayers.add(new TileLayerRegistryEntry("slime_chunks", 1, SlimeChunkLayer::new));
        return tileLayers;
    });

    protected int size;
    protected WorldScreenv2 screen;

    public TileLayer(DataTileManager dataTileManager, int y, int tileWorldX, int tileWorldZ, int size, int sampleResolution, WorldScreenv2 screen) {
        this.size = size;
        this.screen = screen;
    }

    @Nullable
    public List<Component> toolTip(double mouseScreenX, double mouseScreenY, int mouseWorldX, int mouseWorldZ, int mouseTileLocalX, int mouseTileLocalY) {
        return Collections.emptyList();
    }

    public void afterTilesRender(GuiGraphics guiGraphics, double opacity, int tileMinWorldX, int tileMinWorldZ) {
    }

    @Nullable
    public abstract NativeImage image();

    public boolean usesLod() {
        return true;
    }

    public static NativeImage makeNativeImageFromColorData(int[][] data) {
        NativeImage nativeImage = new NativeImage(data.length, data.length, false);
        for (int x = 0; x < data.length; x++) {
            int[] colorRow = data[x];
            for (int y = 0; y < colorRow.length; y++) {
                int color = colorRow[y];
                nativeImage.setPixelRGBA(x, y, color);
            }
        }
        return nativeImage;
    }

    public float defaultOpacity() {
        return 0.7F;
    }

    public Renderer renderer() {
        return (graphics, size1, id, opacity, worldScreenv2) -> {
            VertexConsumer vertexConsumer = graphics.bufferSource().getBuffer(WVRenderType.WORLD_VIEWER_GUI.apply(id, RenderType.NO_TRANSPARENCY));
            ClientUtil.blit(vertexConsumer, graphics.pose(), opacity, 0, 0, 0F, 0F, size1, size1, size1, size1);
        };
    }

    @FunctionalInterface
    public interface Factory {

        TileLayer make(DataTileManager tileManager, int scrollWorldY, int tileWorldX, int tileWorldZ, int size, int sampleResolution, WorldScreenv2 screen, LongSet sampledDataChunks);
    }

    @FunctionalInterface
    public interface Renderer {

        void render(GuiGraphics graphics, int size, int id, float opacity, WorldScreenv2 screenv2);
    }

    public record TileLayerRegistryEntry(String name, float defaultOpacity, Factory factory) {
    }
}


