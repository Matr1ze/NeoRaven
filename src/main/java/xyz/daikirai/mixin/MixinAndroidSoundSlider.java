package xyz.daikirai.mixin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.daikirai.gui.onyx.menu.AndroidUi;

/** Leave vanilla sound updates intact and replace just the textured thumb draw. */
@Mixin(targets = "net.minecraft.client.gui.GuiScreenOptionsSounds$Button")
public abstract class MixinAndroidSoundSlider {
    @Shadow public float field_146156_o;
    @Inject(method = "mouseDragged", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GlStateManager;color(FFFF)V"), cancellable = true)
    private void onyx$thumb(Minecraft mc, int x, int y, CallbackInfo ci) {
        if (AndroidUi.active()) {
            AndroidUi.slider((GuiButton) (Object) this, field_146156_o);
            ci.cancel();
        }
    }
}
