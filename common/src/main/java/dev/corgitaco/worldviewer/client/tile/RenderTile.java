package dev.corgitaco.worldviewer.client.tile;

import com.google.common.base.Suppliers;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.corgitaco.worldviewer.client.ClientUtil;
import dev.corgitaco.worldviewer.client.screen.WorldScreenv2;
import dev.corgitaco.worldviewer.client.tile.tilelayer.TileLayer;
import dev.corgitaco.worldviewer.common.storage.DataTileManager;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FastColor;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class RenderTile implements ScreenTile {


    private final HashMap<String, TileLayer> tileLayers = new LinkedHashMap<>();
    private final Supplier<HashMap<String, NativeImage>> tileLayerImages = Suppliers.memoize(() -> {
        HashMap<String, NativeImage> map = new HashMap<>();

        tileLayers.forEach(((s, tileLayer) -> {
            NativeImage image = tileLayer.image();
            if (image != null) {
                map.put(s, image);
            }
        }));

        return map;
    });
    public Map<String, DynamicTexture> textureMap = new LinkedHashMap<>();


    private DataTileManager tileManager;
    private final int minTileWorldX;
    private final int minTileWorldZ;

    private final int maxTileWorldX;
    private final int maxTileWorldZ;
    private final int size;
    private final int sampleRes;
    private WorldScreenv2 worldScreenv2;

    private boolean shouldRender = true;

    private final LongSet sampledChunks = new LongOpenHashSet();

    public RenderTile(DataTileManager tileManager, Map<String, TileLayer.Factory> factories, int scrollY, int minTileWorldX, int minTileWorldZ, int size, int sampleRes, WorldScreenv2 worldScreenv2, @Nullable RenderTile lastResolution) {
        this.tileManager = tileManager;
        this.minTileWorldX = minTileWorldX;
        this.minTileWorldZ = minTileWorldZ;

        this.maxTileWorldX = minTileWorldX + (size);
        this.maxTileWorldZ = minTileWorldZ + (size);
        this.size = size;
        this.sampleRes = sampleRes;
        this.worldScreenv2 = worldScreenv2;

        if (lastResolution != null) {
            sampledChunks.addAll(lastResolution.sampledChunks);

            lastResolution.tileLayers.forEach((s, layer) -> {
                if (!layer.usesLod()) {
                    tileLayers.put(s, layer);
                }
            });
        }
        factories.forEach((s, factory) -> tileLayers.computeIfAbsent(s, (s1) -> factory.make(tileManager, scrollY, minTileWorldX, minTileWorldZ, size, sampleRes, worldScreenv2, sampledChunks)));
        if (sampleRes == worldScreenv2.sampleResolution) {
            sampledChunks.forEach(tileManager::unloadTile);
        }


    }


    public List<Component> toolTip(double mouseScreenX, double mouseScreenY, int mouseWorldX, int mouseWorldZ, int mouseTileLocalX, int mouseTileLocalY) {
        return this.tileLayers.values().stream().map(tileLayer -> tileLayer.toolTip(mouseScreenX, mouseScreenY, mouseWorldX, mouseWorldZ, mouseTileLocalX, mouseTileLocalY)).filter(Objects::nonNull).map(mutableComponent -> (Component) mutableComponent).collect(Collectors.toList());
    }


    public void afterTilesRender(GuiGraphics guiGraphics, float opacity) {
        this.tileLayers.forEach((s, tileLayer) -> {
            tileLayer.afterTilesRender(guiGraphics, opacity, getMinTileWorldX(), getMinTileWorldZ());
        });
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
            RenderSystem.blendFunc(GlStateManager.SourceFactor.DST_COLOR,  GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            this.tileLayerImages.get().forEach((s, nativeImage) -> {
                DynamicTexture dynamicTexture = textureMap.computeIfAbsent(s, key1 -> new DynamicTexture(nativeImage));

                RenderSystem.setShaderColor(1, 1, 1, 1);
                RenderSystem.setShaderTexture(0, dynamicTexture.getId());
                ClientUtil.blit(guiGraphics.pose(), 0, 0, 0F, 0F, this.size, this.size, this.size, this.size);
                RenderSystem.setShaderColor(1, 1, 1, 1);
//                ClientUtil.drawOutlineWithWidth(guiGraphics, 0, 0, this.size, this.size, (int) Math.ceil(1.5 / scale), FastColor.ARGB32.color(255, 0, 255, 0));

            });
            RenderSystem.disableBlend();
        }
    }

    @Override
    public int size() {
        return this.size;
    }

    @Override
    public Map<String, NativeImage> layers() {
        return this.tileLayerImages.get();
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
    public void close() {
        this.textureMap.values().removeIf(dynamicTexture -> {
            dynamicTexture.releaseId();
            return true;
        });
    }
}
