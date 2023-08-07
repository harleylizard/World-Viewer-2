package dev.corgitaco.worldviewer.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.corgitaco.worldviewer.client.screen.WorldScreenv2;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class MixinLevelRenderer {

    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "renderLevel", at = @At("HEAD"), cancellable = true)
    private void cancelLevelRenderingIfWorldScreen(float $$0, long $$1, PoseStack $$2, CallbackInfo ci) {
        if (this.minecraft.screen instanceof WorldScreenv2) {
            ci.cancel();
        }
    }
}
