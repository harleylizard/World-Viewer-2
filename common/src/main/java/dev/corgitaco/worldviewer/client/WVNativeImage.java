package dev.corgitaco.worldviewer.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;

public class WVNativeImage extends NativeImage {
    public WVNativeImage(int $$0, int $$1, boolean $$2) {
        super($$0, $$1, $$2);
    }

    public void uploadSubregion(int level, int xOffset, int yOffset, int width, int height, long pixelMemoryAddress) {
        GlStateManager._texSubImage2D(3553, level, xOffset, yOffset, width, height, this.format().glFormat(), 5121, pixelMemoryAddress);
    }
}
