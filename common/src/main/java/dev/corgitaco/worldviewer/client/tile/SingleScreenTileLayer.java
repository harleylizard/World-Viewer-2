package dev.corgitaco.worldviewer.client.tile;

import com.mojang.blaze3d.platform.NativeImage;
import dev.corgitaco.worldviewer.client.screen.WorldScreenv2;
import dev.corgitaco.worldviewer.client.tile.tilelayer.TileLayer;
import dev.corgitaco.worldviewer.common.storage.DataTileManager;
import dev.corgitaco.worldviewer.mixin.NativeImageAccessor;
import dev.corgitaco.worldviewer.platform.ModPlatform;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;

public class SingleScreenTileLayer implements ScreenTileLayer {

    private static final ExecutorService SAVING_SERVICE = RenderTileManager.createExecutor(1);

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

    public SingleScreenTileLayer(DataTileManager tileManager, String name, TileLayer.GenerationFactory generationFactory, @Nullable TileLayer.DiskFactory diskFactory, int scrollY, int minTileWorldX, int minTileWorldZ, int size, int sampleRes, WorldScreenv2 worldScreenv2, @Nullable SingleScreenTileLayer lastResolution) {
        TileLayer tileLayer1 = null;
        this.minTileWorldX = minTileWorldX;
        this.minTileWorldZ = minTileWorldZ;

        this.maxTileWorldX = minTileWorldX + (size);
        this.maxTileWorldZ = minTileWorldZ + (size);
        this.size = size;
        this.sampleRes = sampleRes;

        Path imagePath = ModPlatform.INSTANCE.configPath().resolve("client").resolve("map").resolve(name).resolve("image").resolve("p." + worldScreenv2.shiftingManager.blockToTile(minTileWorldX) + "-" + worldScreenv2.shiftingManager.blockToTile(minTileWorldZ) + "_s." + sampleRes + ".png");
        Path dataPath = ModPlatform.INSTANCE.configPath().resolve("client").resolve("map").resolve(name).resolve("data").resolve("p." + worldScreenv2.shiftingManager.blockToTile(minTileWorldX) + "-" + worldScreenv2.shiftingManager.blockToTile(minTileWorldZ) + "_s." + sampleRes + ".dat");

        if (diskFactory != null) {
            try {
                tileLayer1 = diskFactory.fromDisk(size, imagePath, dataPath);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (tileLayer1 == null) {
            if (lastResolution != null) {
                sampledChunks.addAll(lastResolution.sampledChunks);
                if (!lastResolution.tileLayer.usesLod()) {
                    tileLayer1 = lastResolution.tileLayer;
                }
            }
        }
        if (tileLayer1 == null || !tileLayer1.isComplete()) {
            tileLayer1 = generationFactory.make(tileManager, scrollY, minTileWorldX, minTileWorldZ, size, sampleRes, worldScreenv2, sampledChunks);
            TileLayer finalTileLayer = tileLayer1;
            SAVING_SERVICE.submit(() -> {
                try {
                    NativeImage image = finalTileLayer.image();
                    if (image != null && ((NativeImageAccessor) (Object) image).wvGetPixels() != 0L) {
                        if (image != null) {
                            Files.createDirectories(imagePath.getParent());
                            image.writeToFile(imagePath);
                        }
                    }

                    CompoundTag tag = finalTileLayer.tag();
                    if (tag != null) {
                        Files.createDirectories(dataPath.getParent());
                        NbtIo.write(tag, dataPath.toFile());
                    }

                    Thread.sleep(50);
                } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                }
            });

        }
        if (sampleRes == worldScreenv2.sampleResolution) {
            sampledChunks.forEach(tileManager::unloadTile);
        }



        this.tileLayer = tileLayer1;
    }

    @Nullable
    public List<Component> toolTip(double mouseScreenX, double mouseScreenY, int mouseWorldX, int mouseWorldZ, int mouseTileLocalX, int mouseTileLocalY) {
        return this.tileLayer.toolTip(mouseScreenX, mouseScreenY, mouseWorldX, mouseWorldZ, mouseTileLocalX, mouseTileLocalY);
    }

    public void afterTilesRender(GuiGraphics guiGraphics, float scale, float opacity, WorldScreenv2 worldScreenv2) {
        this.tileLayer.afterTilesRender(guiGraphics, opacity, getMinTileWorldX(), getMinTileWorldZ(), worldScreenv2);
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
    public void renderTile(GuiGraphics guiGraphics, float scale, float opacity, WorldScreenv2 worldScreenv2) {
        if (shouldRender && this.tileLayer.image() != null) {
            if (this.dynamicTexture == null) {
                this.dynamicTexture = new DynamicTexture(this.tileLayer.image());
            }
            renderer().render(guiGraphics, size, this.dynamicTexture.getId(), opacity, worldScreenv2);
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

    @Override
    public void closeNativeImage() {
        if (this.tileLayer.image() != null) {
            this.tileLayer.image().close();
        }
    }
}
