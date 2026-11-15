package xyz.daikirai.client;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.win32.StdCallLibrary;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;

import java.lang.reflect.Method;
import java.util.Locale;

/** Reconcile input with the new LWJGL window, only during a fullscreen transition. */
public final class FullscreenInput {
    private static final Logger LOG = LogManager.getLogger("NeoRaven/FullscreenInput");
    private static long until;
    private static boolean repeatEvents;
    private static boolean rebind;
    private static String lastState;

    private FullscreenInput() { }

    public static void beforeSwitch(Minecraft mc) {
        repeatEvents = Keyboard.isCreated() && Keyboard.areRepeatEventsEnabled();
        LOG.info("Fullscreen transition start: {}", state(mc));
        // Neither Minecraft's focus flag nor Mouse's cached grab flag identifies the native HWND.
        // Release the old window before LWJGL destroys it, even if the two flags disagree.
        KeyBinding.unPressAllKeys();
        mc.inGameHasFocus = false;
        if (Mouse.isCreated()) Mouse.setGrabbed(false);
    }

    public static void afterSwitch(Minecraft mc) {
        rebind = true;
        until = System.nanoTime() + 3_000_000_000L;
        lastState = null;
        tick(mc);
    }

    public static void tick(Minecraft mc) {
        if (until == 0 || !Display.isCreated()) return;
        if (System.nanoTime() > until) {
            LOG.info("Fullscreen transition settled: {}", state(mc));
            until = 0;
            return;
        }
        try {
            // Windows can give the new top-level window foreground ownership before keyboard
            // focus follows. Repair only our own foreground window; never activate another app
            // or pull the player back from Alt-Tab.
            boolean foreground = WindowFocus.repairIfForeground();
            if (foreground && Display.isActive()) {
                // Display.initControls catches creation failures internally. Retry missing devices
                // only; destroying a working keyboard here would drop real key events.
                if (!Keyboard.isCreated()) Keyboard.create();
                if (!Mouse.isCreated()) Mouse.create();
                boolean gameplay = mc.currentScreen == null && mc.theWorld != null && mc.thePlayer != null;
                if (rebind || mc.inGameHasFocus != gameplay || Mouse.isGrabbed() != gameplay) {
                    if (rebind) Keyboard.enableRepeatEvents(repeatEvents);
                    KeyBinding.unPressAllKeys();
                    mc.inGameHasFocus = gameplay;
                    // Force a native re-grab once. setIngameFocus() alone short-circuits when its
                    // Java flag is already true, and also calls displayGuiScreen(null).
                    Mouse.setGrabbed(false);
                    if (gameplay) mc.mouseHelper.grabMouseCursor();
                    rebind = false;
                }
            }
            String current = state(mc);
            if (!current.equals(lastState)) {
                LOG.info("Fullscreen input: {}", current);
                lastState = current;
            }
        } catch (Exception e) {
            // Bound failures rather than logging or resetting devices every game tick forever.
            until = 0;
            LOG.warn("Could not restore fullscreen input: " + state(mc), e);
        }
    }

    private static String state(Minecraft mc) {
        return "fullscreen=" + Display.isFullscreen() + ", active=" + Display.isActive()
                + ", gameFocus=" + mc.inGameHasFocus + ", mouse=" + Mouse.isCreated()
                + ", grabbed=" + (Mouse.isCreated() && Mouse.isGrabbed())
                + ", keyboard=" + Keyboard.isCreated() + ", screen="
                + (mc.currentScreen == null ? "world" : mc.currentScreen.getClass().getSimpleName());
    }

    /** No global hooks, input simulation or foreground-window activation. Called on the game thread. */
    private static final class WindowFocus {
        private interface User32 extends StdCallLibrary {
            Pointer GetForegroundWindow();
            Pointer GetFocus();
            Pointer SetFocus(Pointer window);
        }
        private static boolean initialized, unavailable;
        private static User32 user32;
        private static Object implementation;
        private static Method getHwnd;

        static boolean repairIfForeground() {
            if (!initialized) {
                initialized = true;
                try {
                    if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("windows")) {
                        unavailable = true;
                    } else {
                        Method getImplementation = Display.class.getDeclaredMethod("getImplementation");
                        getImplementation.setAccessible(true);
                        implementation = getImplementation.invoke(null);
                        getHwnd = implementation.getClass().getDeclaredMethod("getHwnd");
                        getHwnd.setAccessible(true);
                        user32 = (User32) Native.loadLibrary("user32", User32.class);
                    }
                } catch (Exception | LinkageError e) {
                    unavailable = true;
                    LOG.warn("Native fullscreen focus check unavailable; using LWJGL focus", e);
                }
            }
            if (unavailable) return Display.isActive();
            try {
                // Never cache HWND: both setDisplayMode and setFullscreen may replace it.
                long handle = ((Number) getHwnd.invoke(implementation)).longValue();
                if (handle == 0) return false;
                Pointer window = new Pointer(handle);
                if (!window.equals(user32.GetForegroundWindow())) return false;
                if (!window.equals(user32.GetFocus())) {
                    user32.SetFocus(window);
                    LOG.info("Restored keyboard focus on the new foreground game window");
                }
                return window.equals(user32.GetFocus());
            } catch (Exception | LinkageError e) {
                unavailable = true;
                LOG.warn("Native fullscreen focus check failed; using LWJGL focus", e);
                return Display.isActive();
            }
        }
    }
}
