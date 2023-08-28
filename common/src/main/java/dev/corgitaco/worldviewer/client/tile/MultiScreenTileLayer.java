package dev.corgitaco.worldviewer.client.tile;

import com.mojang.blaze3d.platform.NativeImage;
import dev.corgitaco.worldviewer.client.CloseCheck;
import dev.corgitaco.worldviewer.client.screen.WorldScreenv2;
import dev.corgitaco.worldviewer.client.tile.tilelayer.TileLayer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MultiScreenTileLayer implements ScreenTileLayer {

    @Nullable
    public DynamicTexture dynamicTexture;

    @NotNull
    public final NativeImage nativeImage;

    private boolean shouldRender = true;

    private final int minWorldX;
    private final int minWorldZ;

    private final int maxWorldX;
    private final int maxWorldZ;

    private final int size;
    private final TileLayer.Renderer renderer;

    public MultiScreenTileLayer(ScreenTileLayer[][] delegates) {
        ScreenTileLayer firstDelegate = delegates[0][0];
        this.renderer = firstDelegate.renderer();
        this.minWorldX = firstDelegate.getMinTileWorldX();
        this.minWorldZ = firstDelegate.getMinTileWorldZ();
        this.maxWorldX = delegates[delegates.length - 1][delegates.length - 1].getMaxTileWorldX();
        this.maxWorldZ = delegates[delegates.length - 1][delegates.length - 1].getMaxTileWorldZ();

        this.size = firstDelegate.size() * delegates[0].length;

        int width = firstDelegate.image().getWidth() * delegates[0].length;
        int height = firstDelegate.image().getHeight() * delegates[0].length;

        NativeImage newImage = new NativeImage(width, height, false);

        for (int x = 0; x < delegates.length; x++) {
            for (int z = 0; z < delegates[x].length; z++) {
                ScreenTileLayer delegate = delegates[x][z];

                delegate.setShouldRender(false);

                NativeImage nativeImage = delegate.image();

                int offsetX = nativeImage.getWidth() * x;
                int offsetZ = nativeImage.getHeight() * z;

                for (int pixelX = 0; pixelX < nativeImage.getWidth(); pixelX++) {
                    for (int pixelZ = 0; pixelZ < nativeImage.getWidth(); pixelZ++) {
                        int pixelRGBA = nativeImage.getPixelRGBA(pixelX, pixelZ);

                        try {
                            newImage.setPixelRGBA(pixelX + offsetX, pixelZ + offsetZ, pixelRGBA);

                        } catch (Exception e) {
                            String s = "";
                        }
                    }
                }

                CloseCheck closeCheck = (CloseCheck)(Object) delegate.image();
                if(closeCheck.canClose()) {
                    delegate.closeAll();
                } else {
                    delegate.releaseDynamicTextureID();
                    closeCheck.setShouldClose(true);
                }
            }
        }
        this.nativeImage = newImage;
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
    public void renderTile(GuiGraphics guiGraphics, float scale, float opacity, WorldScreenv2 worldScreenv2) {
        if (shouldRender && this.nativeImage != null) {
            if (this.dynamicTexture == null) {
                this.dynamicTexture = new DynamicTexture(this.nativeImage);
            }
            renderer.render(guiGraphics, this.size, this.dynamicTexture.getId(), opacity, worldScreenv2);
        }
    }

    @Override
    public TileLayer.Renderer renderer() {
        return renderer;
    }

    @Override
    public NativeImage image() {
        return this.nativeImage;
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
    public void releaseDynamicTextureID() {
        if (this.dynamicTexture != null) {
            this.dynamicTexture.releaseId();
        }
    }

    @Override
    public void closeDynamicTexture() {
        if (this.dynamicTexture != null) {
            this.dynamicTexture.close();
        }
    }

    @Override
    public void closeNativeImage() {
        this.nativeImage.close();
    }

    @Override
    public boolean canClose() {
        return ((CloseCheck)(Object)this.nativeImage).canClose();
    }

    @Override
    public void setCanClose(boolean canClose) {
        ((CloseCheck)(Object)this.nativeImage).setCanClose(canClose);
    }

    @Override
    public boolean shouldClose() {
        return ((CloseCheck)(Object)this.nativeImage).shouldClose();
    }

    @Override
    public void setShouldClose(boolean shouldClose) {
        ((CloseCheck)(Object)this.nativeImage).setShouldClose(shouldClose);
    }
}
