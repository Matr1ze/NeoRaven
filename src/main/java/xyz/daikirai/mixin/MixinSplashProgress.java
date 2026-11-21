package xyz.daikirai.mixin;

import net.minecraftforge.fml.client.SplashProgress;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.daikirai.gui.onyx.menu.StartupAnimation;

/** Keep Forge's display context hand-off and progress lifecycle intact. */
@Mixin(value = SplashProgress.class, remap = false)
public final class MixinSplashProgress {
    @Inject(method = "getBool", at = @At("HEAD"), cancellable = true, remap = false)
    private static void neoraven$animateSplash(String key, boolean fallback,
                                                CallbackInfoReturnable<Boolean> cir) {
        if ("enabled".equals(key)) cir.setReturnValue(Boolean.TRUE);
    }

    @Inject(method = "getString", at = @At("HEAD"), cancellable = true, remap = false)
    private static void neoraven$splashBrand(String key, String fallback,
                                               CallbackInfoReturnable<String> cir) {
        if ("logoTexture".equals(key)) {
            cir.setReturnValue("textures/gui/title/mojang.png");
        } else if ("forgeTexture".equals(key)) {
            cir.setReturnValue("fml:textures/gui/forge.gif");
        }
    }

    @Inject(method = "finish", at = @At("RETURN"))
    private static void neoraven$finished(CallbackInfo ci) { StartupAnimation.finish(); }
}
