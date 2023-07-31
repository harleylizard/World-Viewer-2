package dev.corgitaco.worldviewer.client.tile;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.corgitaco.worldviewer.client.ClientUtil;
import dev.corgitaco.worldviewer.client.tile.tilelayer.TileLayer;
import dev.corgitaco.worldviewer.common.WorldViewer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.FastColor;

import java.util.LinkedHashMap;
import java.util.Map;

public class RenderTileOfTiles implements ScreenTile {

    public Map<String, NativeImage> layers = new LinkedHashMap<>();
    public Map<String, DynamicTexture> textureMap = new LinkedHashMap<>();

    private final int worldX;
    private final int worldZ;

    private final int size;

    public RenderTileOfTiles(ScreenTile[][] delegates) {
        this.worldX = delegates[0][0].getTileWorldX();
        this.worldZ = delegates[0][0].getTileWorldZ();
        this.size = delegates[0][0].size() * delegates[0].length;

        for (int x = 0; x < delegates.length; x++) {
            for (int z = 0; z < delegates[x].length; z++) {


                ScreenTile delegate = delegates[x][z];


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
    public int getTileWorldX() {
        return this.worldX;
    }

    @Override
    public int getTileWorldZ() {
        return this.worldZ;
    }

    @Override
    public void renderTile(GuiGraphics guiGraphics) {
        this.layers.forEach((s, nativeImage) -> {
            DynamicTexture dynamicTexture = textureMap.computeIfAbsent(s, key1 -> new DynamicTexture(nativeImage));

            RenderSystem.setShaderColor(1, 1, 1, 1);
            RenderSystem.setShaderTexture(0, dynamicTexture.getId());
            ClientUtil.blit(guiGraphics.pose(), 0,0,0F,0F, this.size, this.size, this.size, this.size);
            RenderSystem.setShaderColor(1, 1, 1, 1);
        });
    }

    @Override
    public Map<String, NativeImage> layers() {
        return this.layers;
    }

    @Override
    public int size() {
        return this.size;
    }
}
