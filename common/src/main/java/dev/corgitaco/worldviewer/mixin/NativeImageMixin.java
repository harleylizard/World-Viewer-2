package dev.corgitaco.worldviewer.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NativeImage.class)
public class NativeImageMixin {

    @Shadow
    private long pixels;

    private String closeString;

    @Inject(method = "<init>(Lcom/mojang/blaze3d/platform/NativeImage$Format;IIZJ)V", at = @At("RETURN"))
    private void checkAllocated(NativeImage.Format $$0, int $$1, int $$2, boolean $$3, long $$4, CallbackInfo ci) {
        if (this.pixels == 0) {
            String s = "";
        }
    }

    @Inject(method = "close", at = @At("RETURN"))
    private void onCLose(CallbackInfo ci) {
        if (closeString == null) {
            closeString = "";
            Throwable throwable = new Throwable();
            for (StackTraceElement stackTraceElement : throwable.getStackTrace()) {
                closeString += stackTraceElement.toString();
            }
        }
    }

    @Inject(method = "checkAllocated", at = @At("HEAD"))
    private void yes(CallbackInfo ci) {
        if (pixels == 0L) {
            String closeString1 = this.closeString;
        }
    }

}
