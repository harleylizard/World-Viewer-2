package dev.corgitaco.worldviewer.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

public class WVNativeImage extends NativeImage {
    public WVNativeImage(int $$0, int $$1, boolean $$2) {
        super($$0, $$1, $$2);
    }

    public void uploadSubregion(int level, int xOffset, int yOffset, int width, int height, long pixelMemoryAddress) {
        GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
        GL11.glPixelStorei(GL12.GL_UNPACK_SKIP_IMAGES, 0);
        GL11.glPixelStorei(GL12.GL_UNPACK_IMAGE_HEIGHT, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4);
        GlStateManager._texSubImage2D(3553, level, xOffset, yOffset, width, height, this.format().glFormat(), 5121, pixelMemoryAddress);
    }
}
