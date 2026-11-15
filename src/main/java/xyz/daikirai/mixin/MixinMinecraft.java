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
import java.awt.Graphics2D;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.ByteBuffer;
import javax.imageio.ImageIO;

@Mixin(Minecraft.class)
public class MixinMinecraft {
    /** Frame rate used while the window is unfocused or minimized. */
    private static final int HIDDEN_FPS = 20;

    @Shadow public GuiScreen currentScreen;
    @Shadow private boolean fullscreen;
    @Shadow public int displayWidth;
    @Shadow public int displayHeight;
    private boolean neoraven$startBorderless;

    @Inject(method = "setInitialDisplayMode", at = @At("HEAD"))
    private void neoraven$windowedNativeStartup(CallbackInfo ci) {
        if (xyz.daikirai.client.BorderlessWindow.supported()) {
            neoraven$startBorderless = fullscreen;
            fullscreen = false;
        }
    }

    /**
     * Launchers start 1.8.9 at a fixed 854x480, which makes the splash and the first menus tiny.
     * When the client is not running under the smoke-test harness, open larger than that; the
     * F11/borderless switch then happens once the title screen exists (see {@code startGame}).
     */
    @Inject(method = "setInitialDisplayMode", at = @At("TAIL"))
    private void neoraven$startupWindowSize(CallbackInfo ci) {
        int[] size = xyz.daikirai.client.StartupWindow.preferredWindowSize();
        if (size == null) {
            return;
        }
        try {
            this.displayWidth = size[0];
            this.displayHeight = size[1];
            Display.setDisplayMode(new org.lwjgl.opengl.DisplayMode(size[0], size[1]));
        } catch (Throwable ignored) {
            // Keep the launcher geometry when the display mode is rejected.
        }
    }

    @Inject(method = "dispatchKeypresses", at = @At("HEAD"))
    private void neoraven$traceKeyboard(CallbackInfo ci) {
        xyz.daikirai.client.InputDiagnostics.keyboard("Minecraft.dispatchKeypresses");
    }

    @Inject(method = "clickMouse", at = @At("HEAD"), cancellable = true)
    private void neoraven$automatedTellyLeftClick(CallbackInfo ci) {
        xyz.daikirai.module.player.Scaffold2Module module = xyz.daikirai.module.player.Scaffold2Module.instance;
        if (module != null && !module.allowMouseAction(0)) ci.cancel();
    }

    @Inject(method = "rightClickMouse", at = @At("HEAD"), cancellable = true)
    private void neoraven$automatedTellyRightClick(CallbackInfo ci) {
        xyz.daikirai.module.player.Scaffold2Module module = xyz.daikirai.module.player.Scaffold2Module.instance;
        if (module != null && !module.allowMouseAction(1)) ci.cancel();
    }

    @Inject(method = "drawSplashScreen", at = @At("HEAD"), cancellable = true)
    private void neoraven$earlySplash(net.minecraft.client.renderer.texture.TextureManager textures, CallbackInfo ci) {
        xyz.daikirai.gui.onyx.menu.EarlySplash.draw(textures);
        ci.cancel();
    }

    @Inject(method = "createDisplay", at = @At("RETURN"))
    private void neoraven$initialWindowBranding(CallbackInfo ci) {
        neoraven$applyWindowBranding();
        xyz.daikirai.gui.onyx.menu.StartupAnimation.prepare();
    }

    @Inject(method = "startGame", at = @At("RETURN"))
    private void neoraven$windowBranding(CallbackInfo ci) {
        neoraven$applyWindowBranding();
        boolean auto = false;
        try {
            auto = xyz.daikirai.client.StartupWindow.autoFullscreen();
        } catch (Throwable ignored) {
            // Never keep the client windowed because config loading failed.
        }
        if (neoraven$startBorderless || auto) {
            neoraven$startBorderless = false;
            xyz.daikirai.client.BorderlessWindow.toggle((Minecraft) (Object) this);
        }
    }

    private static void neoraven$applyWindowBranding() {
        Display.setTitle("NeoRaven 1.2");
        try (InputStream input = Minecraft.class.getResourceAsStream("/assets/clickgui/textures/raven.png")) {
            if (input == null) return;
            BufferedImage source = ImageIO.read(input);
            if (source == null) return;
            Display.setIcon(new ByteBuffer[]{iconPixels(source, 16), iconPixels(source, 32)});

        } catch (Exception ignored) {
            // A missing/invalid custom icon must not prevent the client from starting.
        }
    }

    private static ByteBuffer iconPixels(BufferedImage source, int size) {
        BufferedImage scaled = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = scaled.createGraphics();
        graphics.setColor(Color.BLACK);
        graphics.fillRect(0, 0, size, size);
        graphics.drawImage(source, 0, 0, size, size, null);
        graphics.dispose();
        ByteBuffer pixels = ByteBuffer.allocateDirect(size * size * 4);
        for (int y = size - 1; y >= 0; --y) {
            for (int x = 0; x < size; ++x) {
                // Rotate the source artwork 180 degrees, then apply LWJGL's bottom-up row order.
                int argb = scaled.getRGB(size - 1 - x, size - 1 - y);
                pixels.put((byte) (argb >> 16));
                pixels.put((byte) (argb >> 8));
                pixels.put((byte) argb);
                pixels.put((byte) (argb >> 24));
            }
        }
        pixels.flip();
        return pixels;
    }

    @Inject(method = "displayGuiScreen", at = @At("RETURN"))
    private void onyx$screenOpened(GuiScreen requested, CallbackInfo ci) {
        GuiOpenAnimation.opened(currentScreen);
    }

    @Inject(method = "toggleFullscreen", at = @At("HEAD"), cancellable = true)
    private void neoraven$prepareFullscreenInput(CallbackInfo ci) {
        if (xyz.daikirai.client.BorderlessWindow.supported()) {
            xyz.daikirai.client.BorderlessWindow.toggle((Minecraft) (Object) this);
            ci.cancel();
            return;
        }
        xyz.daikirai.client.FullscreenInput.beforeSwitch((Minecraft) (Object) this);
    }

    @Inject(method = "toggleFullscreen", at = @At("RETURN"))
    private void onyx$fullscreenInput(CallbackInfo ci) {
        if (xyz.daikirai.client.BorderlessWindow.supported()) return;
        xyz.daikirai.client.FullscreenInput.afterSwitch((Minecraft) (Object) this);
    }

    @Inject(method = "runTick", at = @At("HEAD"))
    private void neoraven$recoverFullscreenInput(CallbackInfo ci) {
        xyz.daikirai.client.FullscreenInput.tick((Minecraft) (Object) this);
    }

    private static boolean onyx$customScreen(GuiScreen screen) {
        return screen != null && (screen.getClass().getName().startsWith("xyz.daikirai.")
                || xyz.daikirai.gui.onyx.menu.AndroidUi.active(screen));
    }

    @Redirect(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiScreen;handleInput()V"))
    private void onyx$screenInput(GuiScreen screen) throws IOException {
        if (!onyx$customScreen(screen)) {
            screen.handleInput();
            return;
        }
        GuiFont.begin();
        try {
            screen.handleInput();
        } finally {
            GuiFont.end();
        }
    }

    @Redirect(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiScreen;handleMouseInput()V"))
    private void onyx$mouseInput(GuiScreen screen) throws IOException {
        if (!onyx$customScreen(screen)) {
            screen.handleMouseInput();
            return;
        }
        GuiFont.begin();
        try {
            screen.handleMouseInput();
        } finally {
            GuiFont.end();
        }
    }

    @Redirect(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiScreen;updateScreen()V"))
    private void onyx$screenTick(GuiScreen screen) {
        if (!onyx$customScreen(screen)) {
            screen.updateScreen();
            return;
        }
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
        if (!onyx$customScreen(screen)) {
            screen.setWorldAndResolution(mc, width, height);
            return;
        }
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
        int limit = minecraft.gameSettings == null ? 120 : minecraft.gameSettings.limitFramerate;
        // Keep menus responsive without an unlimited GPU loop while no world is running.
        if (minecraft.theWorld == null && minecraft.currentScreen != null) limit = Math.min(limit, 120);
        cir.setReturnValue(Integer.valueOf(limit));
    }
}
