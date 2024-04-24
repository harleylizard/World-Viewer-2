package dev.corgitaco.worldviewer.mixin;

import dev.corgitaco.worldviewer.client.WorldViewerClientConfig;
import dev.corgitaco.worldviewer.client.gui.WorldViewerGui;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.FastColor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class GuiMixin {

    @Unique
    private final WorldViewerGui worldViewer$worldViewerGui = new WorldViewerGui(new WorldViewerClientConfig.Gui(10, 10, 100, 100, 3, FastColor.ARGB32.color(255, 0, 0, 0)));


    @Inject(method = "render", at = @At("HEAD"))
    private void worldViewer$render(GuiGraphics guiGraphics, float partialTicks, CallbackInfo ci) {
        worldViewer$worldViewerGui.renderGui(guiGraphics, partialTicks);
    }


    @Inject(method = "tick()V", at = @At("RETURN"))
    private void tickScreen(CallbackInfo ci) {
        this.worldViewer$worldViewerGui.tick();
    }

    @Inject(method = "onDisconnected", at = @At("RETURN"))
    private void disconnect(CallbackInfo ci) {
        this.worldViewer$worldViewerGui.close();
    }
}
