package xyz.daikirai.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Brands Forge's early splash without replacing its loading thread or progress renderer. */
@Mixin(net.minecraftforge.fml.client.SplashProgress.class)
public final class MixinSplashProgress {
    @Inject(method = "getBool", at = @At("HEAD"), cancellable = true, remap = false)
    private static void neoraven$animateSplash(String key, boolean fallback,
                                                CallbackInfoReturnable<Boolean> cir) {
        if ("rotate".equals(key)) {
            cir.setReturnValue(Boolean.TRUE);
        }
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
}
