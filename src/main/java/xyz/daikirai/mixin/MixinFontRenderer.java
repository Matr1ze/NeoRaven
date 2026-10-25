package xyz.daikirai.mixin;

import xyz.daikirai.hook.Hooks;
import xyz.daikirai.gui.onyx.render.GuiFont;
import net.minecraft.client.gui.FontRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FontRenderer.class)
public class MixinFontRenderer {
    @ModifyVariable(method = "renderStringAtPos", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private String clickgui$render(String s) {
        return Hooks.renderString(s);
    }

    @ModifyVariable(method = "getStringWidth", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private String clickgui$width(String s) {
        return GuiFont.active() ? s : Hooks.renderString(s);
    }

    @Inject(method = "drawString(Ljava/lang/String;FFIZ)I", at = @At("HEAD"), cancellable = true)
    private void onyx$draw(String text, float x, float y, int color, boolean shadow, CallbackInfoReturnable<Integer> cir) {
        if (GuiFont.active()) {
            Integer result = GuiFont.draw((FontRenderer) (Object) this, Hooks.renderString(text), x, y, color, shadow, false);
            if (result != null) cir.setReturnValue(result);
        }
    }

    @Inject(method = "renderString", at = @At("HEAD"), cancellable = true)
    private void onyx$splitDraw(String text, float x, float y, int color, boolean shadow, CallbackInfoReturnable<Integer> cir) {
        if (GuiFont.active()) {
            Integer result = GuiFont.draw((FontRenderer) (Object) this, Hooks.renderString(text), x, y, color, shadow, true);
            if (result != null) cir.setReturnValue(result);
        }
    }

    @Inject(method = "getStringWidth", at = @At("HEAD"), cancellable = true)
    private void onyx$stringWidth(String text, CallbackInfoReturnable<Integer> cir) {
        if (GuiFont.active()) {
            Integer result = GuiFont.width((FontRenderer) (Object) this, Hooks.renderString(text));
            if (result != null) cir.setReturnValue(result);
        }
    }

    @Inject(method = "getCharWidth", at = @At("HEAD"), cancellable = true)
    private void onyx$charWidth(char c, CallbackInfoReturnable<Integer> cir) {
        if (GuiFont.active()) {
            Integer result = GuiFont.charWidth((FontRenderer) (Object) this, c);
            if (result != null) cir.setReturnValue(result);
        }
    }

    @Inject(method = "trimStringToWidth(Ljava/lang/String;IZ)Ljava/lang/String;", at = @At("HEAD"), cancellable = true)
    private void onyx$trim(String text, int width, boolean reverse, CallbackInfoReturnable<String> cir) {
        if (GuiFont.active()) {
            String result = GuiFont.trim((FontRenderer) (Object) this, text, width, reverse);
            if (result != null) cir.setReturnValue(result);
        }
    }

    @Inject(method = "sizeStringToWidth", at = @At("HEAD"), cancellable = true)
    private void onyx$wrap(String text, int width, CallbackInfoReturnable<Integer> cir) {
        if (GuiFont.active()) {
            Integer result = GuiFont.wrapIndex((FontRenderer) (Object) this, text, width);
            if (result != null) cir.setReturnValue(result);
        }
    }
}
