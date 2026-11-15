package xyz.daikirai.mixin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiSlider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.daikirai.gui.onyx.menu.AndroidUi;

@Mixin(GuiSlider.class)
public abstract class MixinAndroidSlider {
    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void onyx$slider(Minecraft mc, int x, int y, CallbackInfo ci) {
        if (!AndroidUi.active()) return;
        GuiSlider slider = (GuiSlider) (Object) this;
        if (slider.visible) {
            if (slider.isMouseDown) slider.func_175219_a(Math.max(0, Math.min(1,
                    (x - slider.xPosition - 4F) / (slider.width - 8F))));
            AndroidUi.slider(slider, slider.func_175217_d());
        }
        ci.cancel();
    }
}
