package dev.corgitaco.worldviewer.client.tile.tilelayer;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.corgitaco.worldviewer.client.screen.WorldScreenv2;
import dev.corgitaco.worldviewer.common.storage.DataTileManager;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;

public abstract class TileLayer {

    public static final LinkedHashMap<String, Factory> FACTORY_REGISTRY = Util.make(() -> {
        LinkedHashMap<String, Factory> map = new LinkedHashMap<>();
        map.put("biomes", BiomeLayer::new);
//        map.put("heights", HeightsLayer::new);
        map.put("slime_chunks", SlimeChunkLayer::new);
//        map.put("structures", StructuresLayer::new);
        return map;
    });

    protected final DataTileManager dataTileManager;
    protected int size;
    protected WorldScreenv2 screen;

    public TileLayer(DataTileManager dataTileManager, int y, int tileWorldX, int tileWorldZ, int size, int sampleResolution, WorldScreenv2 screen) {
        this.dataTileManager = dataTileManager;
        this.size = size;
        this.screen = screen;
    }

    @Nullable
    public MutableComponent toolTip(double mouseScreenX, double mouseScreenY, int mouseWorldX, int mouseWorldZ, int mouseTileLocalX, int mouseTileLocalY) {
        return null;
    }

    public void afterTilesRender(GuiGraphics guiGraphics, double opacity, int tileMinWorldX, int tileMinWorldZ) {
    }

    @Nullable
    public abstract NativeImage image();
    public boolean usesLod() {
        return true;
    }

    public static NativeImage makeNativeImageFromColorData(int[][] data) {
        NativeImage nativeImage = new NativeImage(data.length, data.length, true);
        for (int x = 0; x < data.length; x++) {
            int[] colorRow = data[x];
            for (int y = 0; y < colorRow.length; y++) {
                int color = colorRow[y];
                nativeImage.setPixelRGBA(x, y, color);
            }
        }
        return nativeImage;
    }

    @FunctionalInterface
    public interface Factory {

        TileLayer make(DataTileManager tileManager, int scrollWorldY, int tileWorldX, int tileWorldZ, int size, int sampleResolution, WorldScreenv2 screen, LongSet sampledDataChunks);
    }
}


