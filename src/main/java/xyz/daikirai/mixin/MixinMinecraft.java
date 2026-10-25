package xyz.daikirai.mixin;

import xyz.daikirai.hook.Hooks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.lwjgl.opengl.Display;
import xyz.daikirai.gui.onyx.render.GuiFont;
import xyz.daikirai.gui.onyx.render.GuiOpenAnimation;

import java.io.IOException;

@Mixin(Minecraft.class)
public class MixinMinecraft {
    /** Frame rate used while the window is unfocused or minimized. */
    private static final int HIDDEN_FPS = 20;

    @Shadow public GuiScreen currentScreen;

    @Inject(method = "displayGuiScreen", at = @At("RETURN"))
    private void onyx$screenOpened(GuiScreen requested, CallbackInfo ci) {
        GuiOpenAnimation.opened(currentScreen);
    }

    @Redirect(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiScreen;handleInput()V"))
    private void onyx$screenInput(GuiScreen screen) throws IOException {
        GuiFont.begin();
        try {
            screen.handleInput();
        } finally {
            GuiFont.end();
        }
    }

    @Redirect(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiScreen;handleMouseInput()V"))
    private void onyx$mouseInput(GuiScreen screen) throws IOException {
        GuiFont.begin();
        try {
            screen.handleMouseInput();
        } finally {
            GuiFont.end();
        }
    }

    @Redirect(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiScreen;updateScreen()V"))
    private void onyx$screenTick(GuiScreen screen) {
        GuiFont.begin();
        try {
            screen.updateScreen();
        } finally {
            GuiFont.end();
        }
    }

    @Redirect(method = {"displayGuiScreen", "resize"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiScreen;setWorldAndResolution(Lnet/minecraft/client/Minecraft;II)V"))
    private void onyx$screenInit(GuiScreen screen, Minecraft mc, int width, int height) {
        GuiFont.begin();
        try {
            screen.setWorldAndResolution(mc, width, height);
        } finally {
            GuiFont.end();
        }
    }

    @Redirect(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/EntityRenderer;getMouseOver(F)V"))
    private void clickgui$getMouseOver(EntityRenderer renderer, float partialTicks) {
        Hooks.getMouseOver();
    }

    /**
     * Lifts 1.8.9's hard 30 fps clamp for "no world, but a screen is open".
     *
     * <p>Vanilla's {@code Minecraft#getLimitFramerate()} is
     * {@code theWorld == null && currentScreen != null ? 30 : gameSettings.limitFramerate}, and the
     * render loop feeds it to {@code Display.sync}. That is exactly the title screen: the rewritten
     * menu - and everything opened from it, the world list, the settings sheet and the ClickGUI -
     * was drawn once every 33.3 ms while the same client runs at several hundred fps in-world. The
     * menu therefore looked laggy, and the dock springs, the closing sheet and the wallpaper
     * parallax all stepped at 30 Hz.</p>
     *
     * <p>Screens now follow the game's own Max Framerate setting, so in-world and out-of-world
     * frames are limited by the same number (260 means "unlimited" in 1.8.9's options, and the
     * render loop skips {@code Display.sync} at that value).</p>
     *
     * <p>A minimized window is throttled to {@value #HIDDEN_FPS} fps instead. With Max Framerate on
     * "unlimited" the client otherwise spins at several thousand frames per second into a window the
     * driver is not presenting - on Windows that turns minimizing a fullscreen window into
     * multi-second stalls and then a hard hang - and every one of those frames still drove the
     * frosted-glass capture. An unfocused window that is still on screen keeps the normal limit.</p>
     */
    @Inject(method = "getLimitFramerate", at = @At("HEAD"), cancellable = true)
    private void onyx$menuFramerate(CallbackInfoReturnable<Integer> cir) {
        Minecraft minecraft = (Minecraft) (Object) this;
        if (!Display.isVisible()) {
            cir.setReturnValue(Integer.valueOf(HIDDEN_FPS));
            return;
        }
        cir.setReturnValue(Integer.valueOf(minecraft.gameSettings == null
                ? 260 : minecraft.gameSettings.limitFramerate));
    }
}
