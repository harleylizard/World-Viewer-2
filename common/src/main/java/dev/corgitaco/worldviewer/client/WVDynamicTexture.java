package dev.corgitaco.worldviewer.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.corgitaco.worldviewer.common.WorldViewer;
import net.minecraft.client.renderer.texture.DynamicTexture;

public class WVDynamicTexture extends DynamicTexture {
    public WVDynamicTexture(NativeImage $$0) {
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
}
