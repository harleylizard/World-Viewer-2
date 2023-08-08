package dev.corgitaco.worldviewer.client.tile;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.corgitaco.worldviewer.client.ClientUtil;
import dev.corgitaco.worldviewer.client.WVRenderType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.FastColor;
import org.jetbrains.annotations.Nullable;

public class MultiScreenTileLayer implements ScreenTileLayer {

    @Nullable
    public DynamicTexture dynamicTexture;

    public final NativeImage nativeImage;

    private boolean shouldRender = true;

    private final int minWorldX;
    private final int minWorldZ;

    private final int maxWorldX;
    private final int maxWorldZ;

    private final int size;
    private final float opacity;

    public MultiScreenTileLayer(ScreenTileLayer[][] delegates) {
        ScreenTileLayer firstDelegate = delegates[0][0];
        this.opacity = firstDelegate.opacity();
        ;
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

                        newImage.setPixelRGBA(pixelX + offsetX, pixelZ + offsetZ, pixelRGBA);
                    }
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
    public void renderTile(GuiGraphics guiGraphics, float scale) {
        if (shouldRender) {
            if (this.dynamicTexture == null) {
                this.dynamicTexture = new DynamicTexture(this.nativeImage);
            }

            VertexConsumer vertexConsumer = guiGraphics.bufferSource().getBuffer(WVRenderType.GUI_TEXTURE.apply(dynamicTexture.getId()));

            ClientUtil.blit(vertexConsumer, guiGraphics.pose(), 0, 0, 0F, 0F, this.size, this.size, this.size, this.size);
            ClientUtil.drawOutlineWithWidth(guiGraphics, 0, 0, this.size, this.size, (int) Math.ceil(1.5 / scale), FastColor.ARGB32.color(255, 0, 255, 0));
        }
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
    public float opacity() {
        return this.opacity;
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
        if (this.dynamicTexture != null) {
            this.dynamicTexture.releaseId();
        }
    }
}
