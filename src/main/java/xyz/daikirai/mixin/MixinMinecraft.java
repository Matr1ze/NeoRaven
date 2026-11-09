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

    @Inject(method = "createDisplay", at = @At("RETURN"))
    private void neoraven$initialWindowBranding(CallbackInfo ci) {
        neoraven$applyWindowBranding();
    }

    @Inject(method = "startGame", at = @At("RETURN"))
    private void neoraven$windowBranding(CallbackInfo ci) {
        neoraven$applyWindowBranding();
    }

    private static void neoraven$applyWindowBranding() {
        Display.setTitle("NeoRaven 1.2");
        try (InputStream input = Minecraft.class.getResourceAsStream("/assets/clickgui/textures/raven.png")) {
            if (input == null) return;
            BufferedImage source = ImageIO.read(input);
            if (source == null) return;
            Display.setIcon(new ByteBuffer[]{iconPixels(source, 16), iconPixels(source, 32)});
            neoraven$applyDarkTitleBar();
        } catch (Exception ignored) {
            // A missing/invalid custom icon must not prevent the client from starting.
        }
    }

    /** Requests Windows' dark title-bar treatment without changing the user's global taskbar theme. */
    private static void neoraven$applyDarkTitleBar() {
        try {
            java.lang.reflect.Field implementationField = Display.class.getDeclaredField("display_impl");
            implementationField.setAccessible(true);
            Object implementation = implementationField.get(null);
            java.lang.reflect.Field hwndField = implementation.getClass().getDeclaredField("hwnd");
            hwndField.setAccessible(true);
            long hwndValue = hwndField.getLong(implementation);
            if (hwndValue == 0L) return;

            Class<?> pointerClass = Class.forName("com.sun.jna.Pointer");
            Object hwnd = pointerClass.getConstructor(long.class).newInstance(hwndValue);
            Class<?> memoryClass = Class.forName("com.sun.jna.Memory");
            Object value = memoryClass.getConstructor(long.class).newInstance(4L);
            memoryClass.getMethod("setInt", long.class, int.class).invoke(value, 0L, 1);
            Class<?> libraryClass = Class.forName("com.sun.jna.NativeLibrary");
            Object library = libraryClass.getMethod("getInstance", String.class).invoke(null, "dwmapi");
            Object function = libraryClass.getMethod("getFunction", String.class)
                    .invoke(library, "DwmSetWindowAttribute");
            Class<?> functionClass = Class.forName("com.sun.jna.Function");
            java.lang.reflect.Method invokeInt = functionClass.getMethod("invokeInt", Object[].class);
            int[][] attributes = {{20, 1}, {19, 1}, {35, 0x000000}, {36, 0xFFFFFF}, {34, 0x000000}};
            for (int[] attribute : attributes) {
                memoryClass.getMethod("setInt", long.class, int.class)
                        .invoke(value, 0L, Integer.valueOf(attribute[1]));
                Object[] args = {hwnd, Integer.valueOf(attribute[0]), value, Integer.valueOf(4)};
                invokeInt.invoke(function, new Object[]{args});
            }
        } catch (Throwable ignored) {
            // Dark title bars are a Windows cosmetic enhancement; keep startup portable.
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

    /**
     * LWJGL can leave the old mouse-grab state behind when Windows recreates the display during
     * F11 fullscreen changes. Reconcile input ownership with the active screen after Minecraft has
     * completed its resize: gameplay owns the grabbed mouse, while an open GUI keeps it released.
     */
    @Inject(method = "toggleFullscreen", at = @At("RETURN"))
    private void onyx$fullscreenInput(CallbackInfo ci) {
        neoraven$applyDarkTitleBar();
        Minecraft minecraft = (Minecraft) (Object) this;
        if (currentScreen == null && minecraft.theWorld != null && minecraft.thePlayer != null
                && Display.isActive()) {
            minecraft.setIngameFocus();
        } else {
            minecraft.setIngameNotInFocus();
        }
    }

    @Inject(method = "runTick", at = @At("HEAD"))
    private void neoraven$recoverFullscreenInput(CallbackInfo ci) {
        Minecraft minecraft = (Minecraft) (Object) this;
        if (currentScreen == null && minecraft.theWorld != null && minecraft.thePlayer != null
                && Display.isActive() && !minecraft.inGameHasFocus) {
            minecraft.setIngameFocus();
        }
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
