package dev.corgitaco.worldviewer.client.tile.tilelayer;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.corgitaco.worldviewer.client.ClientUtil;
import dev.corgitaco.worldviewer.client.WVRenderType;
import dev.corgitaco.worldviewer.client.tile.RenderTileContext;
import dev.corgitaco.worldviewer.common.storage.DataTileManager;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.Util;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class TileLayer {


    public static final List<TileLayerRegistryEntry<?>> FACTORY_REGISTRY = Util.make(() -> {
        List<TileLayerRegistryEntry<?>> tileLayers = new ArrayList<>();
        tileLayers.add(new TileLayerRegistryEntry<HeightsLayer>("heights", 0.5F, HeightsLayer::new, HeightsLayer::new, RenderType.NO_TRANSPARENCY, 0));
        tileLayers.add(new TileLayerRegistryEntry<BiomeLayer>("biomes", 1, BiomeLayer::new, BiomeLayer::new, WVRenderType.DST_COLOR_SRC_ALPHA_TRANSPARENCY,5));
        tileLayers.add(new TileLayerRegistryEntry<TopBlockMapLayer>("map", 1, TopBlockMapLayer::new, TopBlockMapLayer::new, RenderType.NO_TRANSPARENCY, 5));
//        tileLayers.add(new TileLayerRegistryEntry<NoiseCaveLayer>("caves", 1, NoiseCaveLayer::new, NoiseCaveLayer::new));
        tileLayers.add(new TileLayerRegistryEntry<SlimeChunkLayer>("slime_chunks", 1, SlimeChunkLayer::new, SlimeChunkLayer::new, RenderType.NO_TRANSPARENCY, 5));
        tileLayers.add(new TileLayerRegistryEntry<>("structures", 1, StructuresLayer::new, null, RenderType.NO_TRANSPARENCY, 5));
        return tileLayers;
    });
    protected int sampleResolution;


    public TileLayer(DataTileManager dataTileManager, int y, int tileWorldX, int tileWorldZ, int size, int sampleResolution, LongSet sampledChunks, @Nullable TileLayer lowerResolution) {
        this.sampleResolution = sampleResolution;
    }

    public TileLayer(int size, Path imagePath, Path dataPath, int sampleResolution) throws Exception {
        this.sampleResolution = sampleResolution;
    }

    @Nullable
    public List<Component> toolTip(double mouseScreenX, double mouseScreenY, int mouseWorldX, int mouseWorldZ, int mouseTileLocalX, int mouseTileLocalY) {
        return Collections.emptyList();
    }

    public void afterTilesRender(MultiBufferSource.BufferSource bufferSource, PoseStack stack, double opacity, int tileMinWorldX, int tileMinWorldZ, RenderTileContext tileContext) {
    }

    @Nullable
    public abstract int[] image();

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

    public abstract boolean isComplete();

    @FunctionalInterface
    public interface GenerationFactory<T> {
        T make(DataTileManager tileManager, int scrollWorldY, int tileWorldX, int tileWorldZ, int size, int sampleResolution, LongSet sampledDataChunks, T tileLayer);
    }

    public interface DiskFactory {
        TileLayer fromDisk(int size,  Path imagePath, Path dataPath, int sampleResolution) throws Exception;
    }

    public record TileLayerRegistryEntry<T extends TileLayer>(String name, float defaultOpacity, GenerationFactory<T> generationFactory, @Nullable DiskFactory diskFactory, RenderStateShard.TransparencyStateShard transparencyStateShard, int weight) {
    }
}


