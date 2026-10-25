package xyz.daikirai.mixin;

import net.minecraft.client.gui.GuiOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.daikirai.gui.onyx.menu.SettingsStyle;

/**
 * Draws the options-style button on the untouched vanilla options screen, so "Android" and
 * "Minecraft" settings can be reached from each other.
 */
@Mixin(GuiOptions.class)
public class MixinGuiOptions {

    @Inject(method = "drawScreen(IIF)V", at = @At("TAIL"))
    private void onyx$drawStyleButton(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        SettingsStyle.drawOverVanilla((GuiOptions) (Object) this, mouseX, mouseY);
    }
}
