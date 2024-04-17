package dev.corgitaco.worldviewer.mixin;

import net.minecraft.client.main.Main;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.IOException;

@Mixin(value = Main.class, remap = false)
public class MixinMain {

    @Inject(method = "main", at = @At("HEAD"), remap = false)
    private static void waitForConsole(String[] strings, CallbackInfo ci) throws IOException {
        System.out.println(ProcessHandle.current().pid());
//        int read = System.in.read();
    }

}
