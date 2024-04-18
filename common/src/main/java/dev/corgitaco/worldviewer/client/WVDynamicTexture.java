package dev.corgitaco.worldviewer.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.corgitaco.worldviewer.client.render.ColorUtils;
import dev.corgitaco.worldviewer.common.WorldViewer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.FastColor;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

public class WVDynamicTexture extends DynamicTexture {
    public WVDynamicTexture(WVNativeImage $$0) {
        super($$0);
    }

    @Override
    public void upload() {
        NativeImage pixels = this.getPixels();
        if (pixels != null) {
            this.bind();
            pixels.upload(0, 0, 0, 0, 0, pixels.getWidth(), pixels.getHeight(), false, true, false, false);
        } else {
            WorldViewer.LOGGER.warn("Trying to upload disposed texture {}", this.getId());
        }
    }

    public void uploadSubImageWithOffset(int xOffset, int zOffset, int width, int height, @NotNull int[] subPixels) {
        NativeImage pixels = this.getPixels();
        if (pixels != null) {
            this.bind();

            ByteBuffer buffer = ByteBuffer.allocateDirect(width * height * 4);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int pixel = subPixels[x + y * width];
                    byte alpha = (byte) ColorUtils.ABGR.unpackAlpha(pixel);
                    byte red = (byte) ColorUtils.ABGR.unpackRed(pixel);
                    byte green = (byte) ColorUtils.ABGR.unpackGreen(pixel);
                    byte blue = (byte) ColorUtils.ABGR.unpackBlue(pixel);

                    // Calculate the index in the buffer for the current pixel
                    int index = (y * width + x) * 4;

                    // Set RGBA values in the buffer
                    buffer.put(index, red); // Red
                    buffer.put(index + 1, green); // Green
                    buffer.put(index + 2,  blue); // Blue
                    buffer.put(index + 3, alpha); // Alpha
                }
            }

            buffer.flip();
            long memAddress = MemoryUtil.memAddress(buffer);

            ((WVNativeImage) pixels).uploadSubregion(0, xOffset, zOffset, width, height, memAddress);
        } else {
            WorldViewer.LOGGER.warn("Trying to upload disposed texture {}", this.getId());
        }
    }
}
