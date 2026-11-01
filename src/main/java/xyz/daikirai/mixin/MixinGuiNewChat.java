package xyz.daikirai.mixin;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiNewChat;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.IChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.daikirai.module.render.BetterChatModule;

import java.util.List;

/**
 * Better Chat 1.8.9's {@code MixinGuiNewChat}, ported onto this client's module settings.
 *
 * <p>Each new message restarts a 250 ms timer; the newest lines fade in (<code>0.004</code> per
 * millisecond, clamped) and slide up the last 9 px, scaled by the chat scale. The {@code Clear}
 * option drops the backdrop, and the two offsets move both the rendering and the click hitbox
 * ({@code getChatComponent}). The math is the original's; only the settings source changed.</p>
 */
@Mixin(GuiNewChat.class)
public abstract class MixinGuiNewChat extends Gui {

    @Shadow
    private boolean isScrolled;

    @Shadow
    public abstract float getChatScale();

    private float clickgui$percentComplete;
    private int clickgui$newLines;
    private long clickgui$prevMillis = System.currentTimeMillis();
    private float clickgui$animationPercent;
    private int clickgui$lineBeingDrawn;

    private void clickgui$updatePercentage(long diff) {
        if (clickgui$percentComplete < 1.0F) {
            clickgui$percentComplete += 0.004F * diff;
        }
        clickgui$percentComplete = Math.max(0.0F, Math.min(1.0F, clickgui$percentComplete));
    }

    @Inject(method = "drawChat", at = @At("HEAD"))
    private void clickgui$tickAnimation(int updateCounter, CallbackInfo ci) {
        long current = System.currentTimeMillis();
        long diff = current - clickgui$prevMillis;
        clickgui$prevMillis = current;
        clickgui$updatePercentage(diff);
        float t = clickgui$percentComplete;
        clickgui$animationPercent = Math.max(0.0F, Math.min(1.0F, 1.0F - (--t) * t * t * t));
    }

    @Inject(method = "drawChat", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GlStateManager;pushMatrix()V", ordinal = 0,
            shift = At.Shift.AFTER))
    private void clickgui$translate(int updateCounter, CallbackInfo ci) {
        float y = BetterChatModule.appliedYOffset();
        if (BetterChatModule.smooth() && !this.isScrolled) {
            y += (9.0F - 9.0F * clickgui$animationPercent) * this.getChatScale();
        }
        GlStateManager.translate(BetterChatModule.appliedXOffset(), y, 0.0F);
    }

    @Redirect(method = "drawChat", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiNewChat;drawRect(IIIII)V", ordinal = 0))
    private void clickgui$transparentBackground(int left, int top, int right, int bottom, int color) {
        if (!BetterChatModule.clear()) {
            drawRect(left, top, right, bottom, color);
        }
    }

    @ModifyArg(method = "drawChat", at = @At(value = "INVOKE",
            target = "Ljava/util/List;get(I)Ljava/lang/Object;", ordinal = 0, remap = false), index = 0)
    private int clickgui$lineBeingDrawn(int line) {
        clickgui$lineBeingDrawn = line;
        return line;
    }

    @ModifyArg(method = "drawChat", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/FontRenderer;drawStringWithShadow(Ljava/lang/String;FFI)I"), index = 3)
    private int clickgui$textOpacity(int original) {
        if (BetterChatModule.smooth() && clickgui$lineBeingDrawn <= clickgui$newLines) {
            int opacity = (original >> 24) & 0xFF;
            opacity *= clickgui$animationPercent;
            return (original & ~(0xFF << 24)) | (opacity << 24);
        }
        return original;
    }

    @Inject(method = "printChatMessageWithOptionalDeletion", at = @At("HEAD"))
    private void clickgui$resetPercentage(IChatComponent component, int id, CallbackInfo ci) {
        clickgui$percentComplete = 0.0F;
    }

    @ModifyVariable(method = "setChatLine", at = @At("STORE"), ordinal = 0)
    private List<IChatComponent> clickgui$setNewLines(List<IChatComponent> original) {
        clickgui$newLines = original.size() - 1;
        return original;
    }

    @ModifyVariable(method = "getChatComponent", at = @At(value = "STORE", ordinal = 0), ordinal = 3)
    private int clickgui$modifyX(int original) {
        return original - (int) BetterChatModule.appliedXOffset();
    }

    @ModifyVariable(method = "getChatComponent", at = @At(value = "STORE", ordinal = 0), ordinal = 4)
    private int clickgui$modifyY(int original) {
        return original + (int) BetterChatModule.appliedYOffset();
    }
}
