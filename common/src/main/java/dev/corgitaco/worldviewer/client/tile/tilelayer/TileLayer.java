package dev.corgitaco.worldviewer.client.tile.tilelayer;

import com.mojang.blaze3d.platform.NativeImage;
import dev.corgitaco.worldviewer.client.screen.WorldScreenv2;
import dev.corgitaco.worldviewer.common.storage.DataTileManager;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.Util;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;

public abstract class TileLayer {

    public static final LinkedHashMap<String, Factory> FACTORY_REGISTRY = Util.make(() -> {
        LinkedHashMap<String, Factory> map = new LinkedHashMap<>();
        map.put("biomes", BiomeLayer::new);
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

    public abstract NativeImage image();
    public boolean usesLod() {
        return true;
    }

    @FunctionalInterface
    public interface Factory {

        TileLayer make(DataTileManager tileManager, int scrollWorldY, int tileWorldX, int tileWorldZ, int size, int sampleResolution, WorldScreenv2 screen, LongSet sampledDataChunks);
    }
}


