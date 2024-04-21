package dev.corgitaco.worldviewer.client.screen;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.corgitaco.worldviewer.client.render.ColorUtils;
import dev.corgitaco.worldviewer.client.render.WorldViewerRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FastColor;

import java.util.List;
import java.util.Optional;

public class WorldViewerScreen extends Screen {


    private WorldViewerRenderer worldViewerRenderer;

    public WorldViewerScreen(Component component) {
        super(component);
    }

    @Override
    protected void init() {
        super.init();
        this.worldViewerRenderer = new WorldViewerRenderer(height, width);
        this.worldViewerRenderer.init();

    }

    @Override
    public void tick() {
        super.tick();
        this.worldViewerRenderer.tick();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        PoseStack poseStack = guiGraphics.pose();
        poseStack.pushPose();
        guiGraphics.fill(0, 0, width, height, ColorUtils.ARGB.packARGB(255, 0, 0, 0));
        this.worldViewerRenderer.render(guiGraphics.bufferSource(), poseStack, mouseX, mouseY, partialTicks);
        guiGraphics.drawString(Minecraft.getInstance().font, Minecraft.getInstance().fpsString, 0, 0, FastColor.ARGB32.color(255, 255, 255, 255));
        poseStack.popPose();
        List<Component> toolTip = this.worldViewerRenderer.getToolTip(mouseX, mouseY);
        guiGraphics.renderTooltip(Minecraft.getInstance().font, toolTip, Optional.empty(), mouseX, mouseY);
        super.render(guiGraphics, mouseX, mouseY, partialTicks);
    }

    @Override
    public void onClose() {
        super.onClose();
        this.worldViewerRenderer.close();
    }
}
