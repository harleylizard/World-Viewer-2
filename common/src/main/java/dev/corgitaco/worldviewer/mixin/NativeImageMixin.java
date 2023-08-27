package dev.corgitaco.worldviewer.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import dev.corgitaco.worldviewer.client.CloseCheck;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.File;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Path;

@Mixin(NativeImage.class)
public class NativeImageMixin implements CloseCheck {

    private boolean canClose = true;
    private boolean shouldClose = false;

    @Override
    public boolean canClose() {
        return this.canClose;
    }

    @Override
    public void setCanClose(boolean canClose) {
        this.canClose = canClose;
    }

    @Override
    public boolean shouldClose() {
        return this.shouldClose;
    }

    @Override
    public void setShouldClose(boolean shouldClose) {
        this.shouldClose = shouldClose;
    }


    @Inject(method = "asByteArray", at = @At("HEAD"))
    private void markCantCloseByteArray(CallbackInfoReturnable<byte[]> cir) {
        this.canClose = false;
    }

    @Inject(method = "asByteArray", at = @At("RETURN"))
    private void markCanCloseByteArray(CallbackInfoReturnable<byte[]> cir) {
        this.canClose = true;
    }

    @Inject(method = "writeToFile(Ljava/nio/file/Path;)V", at = @At("HEAD"))
    private void markCantClosePath(Path $$0, CallbackInfo ci) {
        this.canClose = false;
    }

    @Inject(method = "writeToFile(Ljava/nio/file/Path;)V", at = @At("RETURN"))
    private void markCanClosePath(Path $$0, CallbackInfo ci) {
        this.canClose = true;
    }

    @Inject(method = "writeToFile(Ljava/io/File;)V", at = @At("HEAD"))
    private void markCantCloseFile(File $$0, CallbackInfo ci) {
        this.canClose = false;
    }

    @Inject(method = "writeToFile(Ljava/io/File;)V", at = @At("RETURN"))
    private void markCanCloseFile(File $$0, CallbackInfo ci) {
        this.canClose = true;
    }

    @Inject(method = "writeToChannel", at = @At("HEAD"))
    private void markCantCloseChannel(WritableByteChannel byteChannel, CallbackInfoReturnable<Boolean> cir) {
        this.canClose = false;
    }

    @Inject(method = "writeToChannel", at = @At("RETURN"))
    private void markCanCloseChannel(WritableByteChannel byteChannel, CallbackInfoReturnable<Boolean> cir) {
        this.canClose = true;
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void isClosing(CallbackInfo ci) {
        if (!canClose) {
            new Throwable().printStackTrace();
        }
    }
}
