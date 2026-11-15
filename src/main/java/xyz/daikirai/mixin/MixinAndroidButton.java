package xyz.daikirai.mixin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.daikirai.gui.onyx.menu.AndroidUi;

@Mixin(GuiButton.class)
public abstract class MixinAndroidButton {
    @Shadow protected boolean hovered;
    @Shadow protected abstract void mouseDragged(Minecraft mc, int x, int y);
    @Inject(method = "drawButton", at = @At("HEAD"), cancellable = true)
    private void onyx$material(Minecraft mc, int x, int y, CallbackInfo ci) {
        if (!AndroidUi.active()) return;
        GuiButton button = (GuiButton) (Object) this;
        if (button.visible) {
            hovered = x >= button.xPosition && y >= button.yPosition
                    && x < button.xPosition + button.width && y < button.yPosition + button.height;
            AndroidUi.button(button, hovered);
            mouseDragged(mc, x, y);
        }
        ci.cancel();
    }
}
