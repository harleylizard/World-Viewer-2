package dev.corgitaco.worldviewer.client.tile;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.corgitaco.worldviewer.client.ClientUtil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.FastColor;

import java.util.LinkedHashMap;
import java.util.Map;

public class RenderTileOfTiles implements ScreenTile {

    public Map<String, NativeImage> layers = new LinkedHashMap<>();
    public Map<String, DynamicTexture> textureMap = new LinkedHashMap<>();

    private boolean shouldRender = true;

    private final int minWorldX;
    private final int minWorldZ;

    private final int maxWorldX;
    private final int maxWorldZ;

    private final int size;
    private final int scale;

    public RenderTileOfTiles(ScreenTile[][] delegates, int scale) {
        this.minWorldX = delegates[0][0].getMinTileWorldX();
        this.minWorldZ = delegates[0][0].getMinTileWorldZ();
        this.maxWorldX = delegates[delegates.length - 1][delegates.length - 1].getMaxTileWorldX();
        this.maxWorldZ = delegates[delegates.length - 1][delegates.length - 1].getMaxTileWorldZ();

        this.size = delegates[0][0].size() * delegates[0].length;
        this.scale = scale;

        for (int x = 0; x < delegates.length; x++) {
            for (int z = 0; z < delegates[x].length; z++) {


                ScreenTile delegate = delegates[x][z];
                delegate.setShouldRender(false);


                for (Map.Entry<String, NativeImage> entry : delegate.layers().entrySet()) {
                    String s = entry.getKey();
                    NativeImage nativeImage = entry.getValue();

                    int offsetX = nativeImage.getWidth() * x;
                    int offsetZ = nativeImage.getHeight() * z;


                    NativeImage newImage = layers.computeIfAbsent(s, key -> {
                        int width = nativeImage.getWidth() * delegates[0].length;
                        int height = nativeImage.getHeight() * delegates[0].length;

                        return new NativeImage(width, height, true);
                    });

                    for (int pixelX = 0; pixelX < nativeImage.getWidth(); pixelX++) {
                        for (int pixelZ = 0; pixelZ < nativeImage.getWidth(); pixelZ++) {
                            int pixelRGBA = nativeImage.getPixelRGBA(pixelX, pixelZ);

                            newImage.setPixelRGBA(pixelX + offsetX, pixelZ + offsetZ, pixelRGBA);
                        }
                    }
                }
            }
        }
    }


    @Override
    public int getMinTileWorldX() {
        return this.minWorldX;
    }

    @Override
    public int getMinTileWorldZ() {
        return this.minWorldZ;
    }

    @Override
    public int getMaxTileWorldX() {
        return this.maxWorldX;
    }

    @Override
    public int getMaxTileWorldZ() {
        return this.maxWorldZ;
    }

    @Override
    public void renderTile(GuiGraphics guiGraphics, float scale) {
        if (shouldRender()) {
            RenderSystem.blendFunc(GlStateManager.SourceFactor.DST_COLOR,  GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            this.layers.forEach((s, nativeImage) -> {
                DynamicTexture dynamicTexture = textureMap.computeIfAbsent(s, key1 -> new DynamicTexture(nativeImage));

                RenderSystem.setShaderColor(1, 1, 1, 1);
                RenderSystem.setShaderTexture(0, dynamicTexture.getId());
                ClientUtil.blit(guiGraphics.pose(), 0, 0, 0F, 0F, this.size, this.size, this.size, this.size);
                RenderSystem.setShaderColor(1, 1, 1, 1);
//                ClientUtil.drawOutlineWithWidth(guiGraphics, 0, 0, this.size, this.size, (int) Math.ceil(1.5 / scale), FastColor.ARGB32.color(255, 255, 0, 0));
            });
            RenderSystem.disableBlend();
        }
    }


    @Override
    public Map<String, NativeImage> layers() {
        return this.layers;
    }

    @Override
    public int size() {
        return this.size;
    }

    @Override
    public boolean sampleResCheck(int worldScreenSampleRes) {
        return true;
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
