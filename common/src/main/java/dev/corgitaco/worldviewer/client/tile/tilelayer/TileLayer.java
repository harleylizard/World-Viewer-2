package dev.corgitaco.worldviewer.client.tile.tilelayer;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.corgitaco.worldviewer.client.ClientUtil;
import dev.corgitaco.worldviewer.client.screen.WorldScreenv2;
import dev.corgitaco.worldviewer.common.storage.DataTileManager;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

public abstract class TileLayer {

    public static final LinkedHashMap<String, Factory> FACTORY_REGISTRY = Util.make(() -> {
        LinkedHashMap<String, Factory> map = new LinkedHashMap<>();
        map.put("biomes", BiomeLayer::new);
        map.put("heights", HeightsLayer::new);
        map.put("mixed_heights_biomes", MixedLayer::new);
        map.put("slime_chunks", SlimeChunkLayer::new);
        map.put("structures", StructuresLayer::new);
        return map;
    });



    protected final DataTileManager dataTileManager;
    private final int tileWorldX;
    private final int tileWorldZ;
    protected int size;
    protected WorldScreenv2 screen;

    public TileLayer(DataTileManager dataTileManager, int y, int tileWorldX, int tileWorldZ, int size, int sampleResolution, WorldScreenv2 screen) {
        this.dataTileManager = dataTileManager;
        this.tileWorldX = tileWorldX;
        this.tileWorldZ = tileWorldZ;
        this.size = size;
        this.screen = screen;
    }

    @Nullable
    public MutableComponent toolTip(double mouseScreenX, double mouseScreenY, int mouseWorldX, int mouseWorldZ, int mouseTileLocalX, int mouseTileLocalY) {
        return null;
    }

    public void afterTilesRender(GuiGraphics stack, double mouseX, double mouseY, double mouseWorldX, double mouseWorldZ, double opacity) {
    }

    public void render(GuiGraphics guiGraphics, float opacity, Map<String, TileLayer> layers) {

    }

    public void renderImage(GuiGraphics guiGraphics, DynamicTexture texture, float opacity, float brightness) {
        if (texture.getPixels() == null) {
            return;
        }
        RenderSystem.setShaderColor(opacity, opacity, opacity, opacity);
        RenderSystem.setShaderTexture(0, texture.getId());
        ClientUtil.blit(guiGraphics.pose(), 0, 0, 0.0F, 0.0F, this.size, this.size, this.size, this.size);
        RenderSystem.setShaderColor(1, 1, 1, 1);
    }


    @Nullable
    public DynamicTexture getImage() {
        return null;
    }

    public void close() {
    }

    public float brightness() {
        return 1;
    }

    public int getTileWorldX() {
        return tileWorldX;
    }

    public int getTileWorldZ() {
        return tileWorldZ;
    }

    public boolean usesLod() {
        return true;
    }

    @FunctionalInterface
    public interface Factory {

        TileLayer make(DataTileManager tileManager, int scrollWorldY, int tileWorldX, int tileWorldZ, int size, int sampleResolution, WorldScreenv2 screen, LongSet sampledDataChunks);
    }

    @NotNull
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
}


