package dev.corgitaco.worldviewer.client.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.corgitaco.worldviewer.client.WorldViewerClientConfig;
import dev.corgitaco.worldviewer.client.render.WorldViewerRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.jetbrains.annotations.Nullable;

public final class WorldViewerGui implements AutoCloseable {
    private final WorldViewerClientConfig.Gui guiConfig;

    @Nullable
    private WorldViewerRenderer viewer;


    public WorldViewerGui(WorldViewerClientConfig.Gui guiConfig) {
        this.guiConfig = guiConfig;
    }

    public void renderGui(GuiGraphics guiGraphics, float partialTicks) {
        if (Minecraft.getInstance().player != null) {
            if (this.viewer == null) {
                viewer = new WorldViewerRenderer(this.guiConfig.mapSizeX(), this.guiConfig.mapSizeY());
                viewer.init();
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
                viewer.render(guiGraphics.bufferSource(), poseStack, -1, -1, partialTicks);
                poseStack.popPose();
                guiGraphics.disableScissor();
            });
        }
    }

    public void tick() {
        if (this.viewer != null) {
            this.viewer.tick();
        }
    }

    @Override
    public void close() {
        if (this.viewer != null) {
            this.viewer.close();
        }
        this.viewer = null;
    }
}
