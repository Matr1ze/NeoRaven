package xyz.daikirai.mixin;

import net.minecraft.client.gui.GuiMainMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.daikirai.gui.onyx.menu.MenuStyleSwitch;

/**
 * Draws the title-screen style switcher on the untouched vanilla menu.
 *
 * <p>The Android and Onyx menus draw the switcher themselves; the vanilla screen has no hook of its
 * own, and without this the user could switch to "Minecraft" and never get back. Only the exact
 * vanilla class is touched - the two custom menus extend {@code GuiMainMenu} and override both
 * methods, so this injection never runs for them.</p>
 */
@Mixin(GuiMainMenu.class)
public class MixinGuiMainMenu {

    @Inject(method = "drawScreen(IIF)V", at = @At("TAIL"))
    private void onyx$drawStyleSwitch(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        MenuStyleSwitch.drawOverVanilla((GuiMainMenu) (Object) this, mouseX, mouseY);
    }

    @Inject(method = "mouseClicked(III)V", at = @At("HEAD"), cancellable = true)
    private void onyx$clickStyleSwitch(int mouseX, int mouseY, int button, CallbackInfo ci) {
        if (MenuStyleSwitch.clickOverVanilla((GuiMainMenu) (Object) this, mouseX, mouseY, button)) {
            ci.cancel();
        }
    }

    @Inject(method = "keyTyped(CI)V", at = @At("HEAD"), cancellable = true)
    private void onyx$keyStyleSwitch(char typedChar, int keyCode, CallbackInfo ci) {
        if (MenuStyleSwitch.keyTyped(keyCode)) {
            ci.cancel();
        }
    }
}
