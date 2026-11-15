package xyz.daikirai.mixin;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiTextField;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.daikirai.gui.onyx.menu.AndroidUi;

@Mixin(GuiTextField.class)
public abstract class MixinAndroidTextField {
    @Inject(method = "drawTextBox", at = @At("HEAD"))
    private void onyx$field(CallbackInfo ci) {
        GuiTextField field = (GuiTextField) (Object) this;
        if (AndroidUi.active() && field.getVisible() && field.getEnableBackgroundDrawing()) AndroidUi.field(field);
    }
    @Redirect(method = "drawTextBox", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiTextField;drawRect(IIIII)V", ordinal = 0))
    private void onyx$border(int x, int y, int right, int bottom, int color) {
        if (!AndroidUi.active()) Gui.drawRect(x, y, right, bottom, color);
    }
    @Redirect(method = "drawTextBox", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiTextField;drawRect(IIIII)V", ordinal = 1))
    private void onyx$fill(int x, int y, int right, int bottom, int color) {
        if (!AndroidUi.active()) Gui.drawRect(x, y, right, bottom, color);
    }
}
