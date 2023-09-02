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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class TileLayer {


    public static final List<TileLayerRegistryEntry> FACTORY_REGISTRY = Util.make(() -> {
        List<TileLayerRegistryEntry> tileLayers = new ArrayList<>();
        tileLayers.add(new TileLayerRegistryEntry("biomes", 1, BiomeLayer::new, BiomeLayer::new));
        tileLayers.add(new TileLayerRegistryEntry("map", 1, TopBlockMapLayer::new, TopBlockMapLayer::new));
        tileLayers.add(new TileLayerRegistryEntry("heights", 0.5F, HeightsLayer::new, HeightsLayer::new));
        tileLayers.add(new TileLayerRegistryEntry("caves", 1, NoiseCaveLayer::new, NoiseCaveLayer::new));
        tileLayers.add(new TileLayerRegistryEntry("slime_chunks", 1, SlimeChunkLayer::new, SlimeChunkLayer::new));
        tileLayers.add(new TileLayerRegistryEntry("structures", 1, StructuresLayer::new, null));
        return tileLayers;
    });
    protected int sampleResolution;


    public TileLayer(DataTileManager dataTileManager, int y, int tileWorldX, int tileWorldZ, int size, int sampleResolution, WorldScreenv2 screen, LongSet sampledChunks) {
        this.sampleResolution = sampleResolution;
    }

    public TileLayer(int size, Path imagePath, Path dataPath, int sampleResolution) throws Exception {
        this.sampleResolution = sampleResolution;
    }

    @Nullable
    public List<Component> toolTip(double mouseScreenX, double mouseScreenY, int mouseWorldX, int mouseWorldZ, int mouseTileLocalX, int mouseTileLocalY) {
        return Collections.emptyList();
    }

    public void afterTilesRender(GuiGraphics guiGraphics, double opacity, int tileMinWorldX, int tileMinWorldZ, WorldScreenv2 screenv2) {
    }

    @Nullable
    public abstract NativeImage image();

    @Nullable
    public CompoundTag tag() {
        return null;
    }

    public boolean usesLod() {
        return true;
    }

    public int sampleRes() {
        return this.sampleResolution;
    }

    public void setSampleResolution(int sampleResolution) {
        this.sampleResolution = sampleResolution;
    }

    public void close() {
        if (this.image() != null) {
            this.image().close();
        }
    }

    public static NativeImage makeNativeImageFromColorData(int[][] data) {
        NativeImage nativeImage = ClientUtil.createImage(data.length, data.length, false);
        for (int x = 0; x < data.length; x++) {
            int[] colorRow = data[x];
            for (int y = 0; y < colorRow.length; y++) {
                int color = colorRow[y];
                nativeImage.setPixelRGBA(x, y, color);
            }
        }
        return nativeImage;
    }

    public Renderer renderer() {
        return (graphics, size1, id, opacity, worldScreenv2) -> {
            VertexConsumer vertexConsumer = graphics.bufferSource().getBuffer(WVRenderType.WORLD_VIEWER_GUI.apply(id, RenderType.NO_TRANSPARENCY));
            ClientUtil.blit(vertexConsumer, graphics.pose(), opacity, 0, 0, 0F, 0F, size1, size1, size1, size1);
        };
    }

    public abstract boolean isComplete();

    @FunctionalInterface
    public interface GenerationFactory {

        TileLayer make(DataTileManager tileManager, int scrollWorldY, int tileWorldX, int tileWorldZ, int size, int sampleResolution, WorldScreenv2 screen, LongSet sampledDataChunks);
    }

    public interface DiskFactory {
        TileLayer fromDisk(int size,  Path imagePath, Path dataPath, int sampleResolution) throws Exception;
    }

    @FunctionalInterface
    public interface Renderer {

        void render(GuiGraphics graphics, int size, int id, float opacity, WorldScreenv2 screenv2);
    }

    public record TileLayerRegistryEntry(String name, float defaultOpacity, GenerationFactory generationFactory, @Nullable DiskFactory diskFactory) {
    }
}


