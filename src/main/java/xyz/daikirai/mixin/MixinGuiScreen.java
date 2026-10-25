package xyz.daikirai.mixin;

import net.minecraft.client.gui.GuiOptions;
import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import xyz.daikirai.gui.onyx.menu.SettingsStyle;
import xyz.daikirai.gui.onyx.render.GuiOpenAnimation;

@Mixin(GuiScreen.class)
public class MixinGuiScreen {
    @Inject(method = "drawWorldBackground", at = @At("HEAD"), cancellable = true)
    private void onyx$separateBackground(int tint, CallbackInfo ci) {
        if (GuiOpenAnimation.skipsBackground((GuiScreen) (Object) this)) ci.cancel();
    }

    @ModifyArgs(method = "drawWorldBackground", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiScreen;drawGradientRect(IIIIII)V"))
    private void onyx$backgroundOpacity(Args args) {
        args.set(4, GuiOpenAnimation.backgroundColor((Integer) args.get(4)));
        args.set(5, GuiOpenAnimation.backgroundColor((Integer) args.get(5)));
    }

    @ModifyVariable(method = "handleMouseInput", at = @At("STORE"), ordinal = 0)
    private int onyx$mouseX(int x) {
        return GuiOpenAnimation.mouseX((GuiScreen) (Object) this, x);
    }

    @ModifyVariable(method = "handleMouseInput", at = @At("STORE"), ordinal = 1)
    private int onyx$mouseY(int y) {
        return GuiOpenAnimation.mouseY((GuiScreen) (Object) this, y);
    }

    /**
     * The options-style button also has to be clickable on the vanilla options screen. That screen
     * does not override {@code mouseClicked} (it lets {@code GuiScreen} walk its button list), so
     * the hook lives here and filters by the exact target class.
     */
    @Inject(method = "mouseClicked(III)V", at = @At("HEAD"), cancellable = true)
    private void onyx$optionsStyleButton(int mouseX, int mouseY, int button, CallbackInfo ci) {
        GuiScreen screen = (GuiScreen) (Object) this;
        if (screen.getClass() == GuiOptions.class
                && SettingsStyle.clickOverVanilla(screen, mouseX, mouseY, button)) {
            ci.cancel();
        }
    }
}
