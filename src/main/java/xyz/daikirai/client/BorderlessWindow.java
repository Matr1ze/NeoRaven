package xyz.daikirai.client;

import com.sun.jna.*;
import com.sun.jna.win32.StdCallLibrary;
import net.minecraft.client.Minecraft;
import org.apache.logging.log4j.LogManager;
import org.lwjgl.opengl.Display;
import xyz.daikirai.mixin.IMinecraft;
import java.lang.reflect.Method;

/** Windows fullscreen without destroying the HWND, GL context or input devices. */
public final class BorderlessWindow {
    private interface Win extends StdCallLibrary {
        int GetWindowLongW(Pointer window, int index);
        int SetWindowLongW(Pointer window, int index, int value);
        boolean GetWindowPlacement(Pointer window, Pointer placement);
        boolean SetWindowPlacement(Pointer window, Pointer placement);
        Pointer MonitorFromWindow(Pointer window, int flags);
        boolean GetMonitorInfoW(Pointer monitor, Pointer info);
        boolean SetWindowPos(Pointer window, Pointer after, int x, int y, int width, int height, int flags);
        boolean GetClientRect(Pointer window, Pointer rect);
    }
    private static Win win;
    private static Memory placement;
    private static int savedStyle;
    private static Pointer savedWindow;
    private static boolean enabled;
    private BorderlessWindow() { }
    public static boolean supported() {
        return System.getProperty("os.name", "").startsWith("Windows");
    }
    public static boolean active() { return enabled; }
    public static long windowHandle() throws Exception {
        Method impl = Display.class.getDeclaredMethod("getImplementation"); impl.setAccessible(true);
        Object display = impl.invoke(null);
        Method hwnd = display.getClass().getDeclaredMethod("getHwnd"); hwnd.setAccessible(true);
        return ((Number) hwnd.invoke(display)).longValue();
    }
    public static void toggle(Minecraft mc) {
        Pointer window = null;
        boolean entering = !enabled;
        try {
            if (win == null) win = (Win) Native.loadLibrary("user32", Win.class);
            window = new Pointer(windowHandle());
            if (entering) {
                Memory nextPlacement = new Memory(44); nextPlacement.clear(); nextPlacement.setInt(0, 44);
                require(win.GetWindowPlacement(window, nextPlacement), "GetWindowPlacement");
                Memory monitor = new Memory(40); monitor.clear(); monitor.setInt(0, 40);
                require(win.GetMonitorInfoW(win.MonitorFromWindow(window, 2), monitor), "GetMonitorInfo");
                savedStyle = win.GetWindowLongW(window, -16);
                placement = nextPlacement; savedWindow = window;
                // Remove caption / resizable frame, preserve visibility and all other style bits.
                win.SetWindowLongW(window, -16, savedStyle & ~0x00CF0000);
                require(win.SetWindowPos(window, null, monitor.getInt(4), monitor.getInt(8),
                        monitor.getInt(12) - monitor.getInt(4), monitor.getInt(16) - monitor.getInt(8),
                        0x20 | 0x4 | 0x10), "Enter borderless");
            } else {
                require(window.equals(savedWindow), "Window changed during borderless session");
                restore(window);
            }
            enabled = entering;
            ((IMinecraft) mc).setFullscreenFlag(enabled);
            mc.gameSettings.fullScreen = enabled;
            resize(mc, window);
            mc.gameSettings.saveOptions();
            LogManager.getLogger("NeoRaven/FullscreenInput").info("Borderless={} hwnd={} size={}x{} (window and input devices retained)",
                    enabled, window, mc.displayWidth, mc.displayHeight);
            InputDiagnostics.event("BORDERLESS " + enabled + " hwnd=" + window);
        } catch (Exception | LinkageError e) {
            // Do not silently fall back to the exclusive fullscreen path being avoided.
            if (window != null && window.equals(savedWindow) && placement != null) {
                try { restore(window); enabled = false; ((IMinecraft) mc).setFullscreenFlag(false);
                    mc.gameSettings.fullScreen = false; resize(mc, window); mc.gameSettings.saveOptions();
                } catch (Exception rollback) { e.addSuppressed(rollback); }
            }
            LogManager.getLogger("NeoRaven/FullscreenInput").error("Borderless switch failed", e);
        }
    }
    private static void restore(Pointer window) {
        win.SetWindowLongW(window, -16, savedStyle);
        require(win.SetWindowPlacement(window, placement), "Restore window placement");
        require(win.SetWindowPos(window, null, 0, 0, 0, 0, 0x20 | 0x1 | 0x2 | 0x4 | 0x10), "Restore frame");
    }
    private static void resize(Minecraft mc, Pointer window) {
        Memory rect = new Memory(16);
        require(win.GetClientRect(window, rect), "GetClientRect");
        ((IMinecraft) mc).resizeWindow(Math.max(1, rect.getInt(8)), Math.max(1, rect.getInt(12)));
    }
    private static void require(boolean ok, String operation) {
        if (!ok) throw new IllegalStateException(operation + " failed, Win32 error=" + Native.getLastError());
    }
}
