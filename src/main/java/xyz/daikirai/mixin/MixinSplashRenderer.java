package xyz.daikirai.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.daikirai.gui.onyx.menu.StartupAnimation;

/** Forge 1.8.9 render worker; drawing runs under its existing display mutex. */
@Mixin(targets = "net.minecraftforge.fml.client.SplashProgress$3", remap = false)
public abstract class MixinSplashRenderer {
    @Inject(method = "run", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/Display;update()V"))
    private void neoraven$frame(CallbackInfo ci) { StartupAnimation.draw(); }

    @ModifyArg(method = "run", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/Display;sync(I)V"), index = 0)
    private int neoraven$frameLimit(int original) { return 60; }
}
