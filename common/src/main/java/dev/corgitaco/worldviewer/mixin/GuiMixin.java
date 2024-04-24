package dev.corgitaco.worldviewer.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.corgitaco.worldviewer.client.render.WorldViewerRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.FastColor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class GuiMixin {

    WorldViewerRenderer viewer;

    @Inject(method = "render", at = @At("HEAD"))
    private void worldViewer$render(GuiGraphics guiGraphics, float partialTicks, CallbackInfo ci) {
        if (Minecraft.getInstance().player != null) {
            int minimapSize = 150;
            if (this.viewer == null) {
                viewer = new WorldViewerRenderer(minimapSize, minimapSize);
                viewer.init();
            }

            int offsetFromEdge = 100;

            int borderSize = 3;


            PoseStack poseStack = guiGraphics.pose();
            guiGraphics.enableScissor(offsetFromEdge, offsetFromEdge, offsetFromEdge + minimapSize, offsetFromEdge + minimapSize);

            poseStack.pushPose();
            guiGraphics.fill(offsetFromEdge - borderSize, offsetFromEdge - borderSize, offsetFromEdge + minimapSize + borderSize, offsetFromEdge +  minimapSize + borderSize, FastColor.ARGB32.color(255, 0, 0, 255));
            poseStack.translate(offsetFromEdge, offsetFromEdge, 0);
            viewer.render(guiGraphics.bufferSource(), poseStack, -1, -1, partialTicks);

            poseStack.popPose();
            guiGraphics.disableScissor();

        }
    }


    @Inject(method = "tick()V", at = @At("RETURN"))
    private void tickScreen(CallbackInfo ci) {
        if (this.viewer != null) {
            this.viewer.tick();
        }
    }

    @Inject(method = "onDisconnected", at = @At("RETURN"))
    private void disconnect(CallbackInfo ci) {
        this.viewer.close();
        this.viewer = null;
    }
}
