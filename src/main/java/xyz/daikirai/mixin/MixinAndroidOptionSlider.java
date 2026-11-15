package xyz.daikirai.mixin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiOptionSlider;
import net.minecraft.client.settings.GameSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.daikirai.gui.onyx.menu.AndroidUi;

@Mixin(GuiOptionSlider.class)
public abstract class MixinAndroidOptionSlider {
    @Shadow private float sliderValue;
    @Shadow public boolean dragging;
    @Shadow private GameSettings.Options options;
    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void onyx$slider(Minecraft mc, int x, int y, CallbackInfo ci) {
        if (!AndroidUi.active()) return;
        GuiButton button = (GuiButton) (Object) this;
        if (button.visible) {
            if (dragging) {
                sliderValue = Math.max(0, Math.min(1, (x - button.xPosition - 4F) / (button.width - 8F)));
                mc.gameSettings.setOptionFloatValue(options, options.denormalizeValue(sliderValue));
                sliderValue = options.normalizeValue(mc.gameSettings.getOptionFloatValue(options));
                button.displayString = mc.gameSettings.getKeyBinding(options);
            }
            AndroidUi.slider(button, sliderValue);
        }
        ci.cancel();
    }
}
