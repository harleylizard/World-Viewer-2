package dev.corgitaco.worldviewer.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.corgitaco.worldviewer.client.render.ColorUtils;
import dev.corgitaco.worldviewer.common.WorldViewer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.FastColor;
import org.jetbrains.annotations.NotNull;
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
                    byte alpha = ColorUtils.ABGR.unpackAlpha(pixel);
                    byte red = ColorUtils.ABGR.unpackRed(pixel);
                    byte green = ColorUtils.ABGR.unpackGreen(pixel);
                    byte blue = ColorUtils.ABGR.unpackBlue(pixel);

                    buffer.put(red);
                    buffer.put(green);
                    buffer.put(blue);
                    buffer.put(alpha);
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
