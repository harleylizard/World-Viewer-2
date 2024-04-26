package dev.corgitaco.worldviewer.client.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.corgitaco.worldviewer.client.WorldViewerClientConfig;
import dev.corgitaco.worldviewer.client.render.WorldViewerRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.jetbrains.annotations.Nullable;

public final class WorldViewerGui implements AutoCloseable, WorldViewerRenderer.Access {
    private final WorldViewerClientConfig.Gui guiConfig;

    @Nullable
    private WorldViewerRenderer worldViewerRenderer;


    public WorldViewerGui(WorldViewerClientConfig.Gui guiConfig) {
        this.guiConfig = guiConfig;
    }

    public WorldViewerGui(WorldViewerClientConfig.Gui guiConfig, WorldViewerRenderer worldViewerRenderer) {
        this.guiConfig = guiConfig;
        this.worldViewerRenderer = worldViewerRenderer;
    }

    public void renderGui(GuiGraphics guiGraphics, float partialTicks) {
        if (Minecraft.getInstance().player != null) {
            if (this.worldViewerRenderer == null) {
                worldViewerRenderer = new WorldViewerRenderer(this.guiConfig.mapSizeX(), this.guiConfig.mapSizeY());
                worldViewerRenderer.init();
            }

            PoseStack poseStack = guiGraphics.pose();

            guiGraphics.drawManaged(() -> {
                guiGraphics.fill(
                        this.guiConfig.xOffset() - this.guiConfig.borderSize(),
                        this.guiConfig.yOffset() - this.guiConfig.borderSize(),
                        this.guiConfig.xOffset() + this.guiConfig.mapSizeX() + this.guiConfig.borderSize(),
                        this.guiConfig.yOffset() + this.guiConfig.mapSizeY() + this.guiConfig.borderSize(),
                        this.guiConfig.borderColor()
                );

                guiGraphics.enableScissor(this.guiConfig.xOffset(), this.guiConfig.yOffset(), this.guiConfig.xOffset() + this.guiConfig.mapSizeX(), this.guiConfig.yOffset() + this.guiConfig.mapSizeY());

                poseStack.pushPose();
                poseStack.translate(this.guiConfig.xOffset(), this.guiConfig.yOffset(), 0);
                worldViewerRenderer.render(guiGraphics.bufferSource(), poseStack, -1, -1, partialTicks);
                poseStack.popPose();
                guiGraphics.disableScissor();
            });
        }
    }

    public void tick() {
        if (this.worldViewerRenderer != null) {
            this.worldViewerRenderer.tick();
        }
    }

    @Override
    public void close() {
        if (this.worldViewerRenderer != null) {
            this.worldViewerRenderer.close();
        }
        this.worldViewerRenderer = null;
    }

    @Override
    public WorldViewerRenderer worldViewerRenderer() {
        return this.worldViewerRenderer;
    }
}
