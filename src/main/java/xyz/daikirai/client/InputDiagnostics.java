package xyz.daikirai.client;

import com.sun.jna.*;
import com.sun.jna.win32.StdCallLibrary;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;
import java.io.File;
import java.lang.reflect.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.logging.*;

/** Opt-in, game-thread-only input observation. Never consumes input or changes window focus. */
public final class InputDiagnostics {
    private static final java.time.format.DateTimeFormatter TIME = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static InputDiagnostics instance;
    private final ArrayBlockingQueue<String> queue = new ArrayBlockingQueue<>(8192);
    private volatile long heartbeat = System.nanoTime();
    private long next, ticks, frames, nativeKeys, nativeMoves, nativeClicks;
    private final java.util.concurrent.atomic.AtomicLong dropped = new java.util.concurrent.atomic.AtomicLong();
    private Object display;
    private Field hwndField;
    private User32 win;
    private Hook posted, sent;
    private Pointer postedHandle, sentHandle;
    private String previousScreen = "";
    private interface Kernel32 extends StdCallLibrary { int GetCurrentThreadId(); }
    private interface Hook extends StdCallLibrary.StdCallCallback { long callback(int code, long w, Pointer data); }
    private interface User32 extends StdCallLibrary {
        Pointer GetForegroundWindow(); Pointer GetFocus(); Pointer GetCapture();
        Pointer SetWindowsHookExW(int kind, Hook hook, Pointer module, int thread);
        long CallNextHookEx(Pointer hook, int code, long w, Pointer data);
    }
    private InputDiagnostics() throws Exception {
        File dir = new File(Minecraft.getMinecraft().mcDataDir, "logs/input-diagnostics");
        dir.mkdirs();
        FileHandler handler = new FileHandler(new File(dir, "input-%g.log").getPath(), 8 * 1024 * 1024, 4, true);
        handler.setEncoding("UTF-8");
        handler.setFormatter(new Formatter() { public String format(LogRecord r) { return r.getMessage() + "\n"; } });
        Thread writer = new Thread(() -> {
            try {
                for (;;) {
                    String line = queue.poll(2, java.util.concurrent.TimeUnit.SECONDS);
                    if (line == null && System.nanoTime() - heartbeat > 3_000_000_000L)
                        line = System.currentTimeMillis() + " WATCHDOG no game tick for ms=" + (System.nanoTime() - heartbeat) / 1_000_000;
                    if (line != null) { handler.publish(new LogRecord(Level.INFO, line)); handler.flush(); }
                }
            } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            finally { handler.close(); }
        }, "NeoRaven input diagnostic writer");
        writer.setDaemon(true); writer.start();
        record("SESSION START; game input only; text contents excluded; pid=" + java.lang.management.ManagementFactory.getRuntimeMXBean().getName());
        try {
            Method m = Display.class.getDeclaredMethod("getImplementation"); m.setAccessible(true);
            display = m.invoke(null);
            hwndField = display.getClass().getDeclaredField("hwnd"); hwndField.setAccessible(true);
            if (Native.POINTER_SIZE == 8 && System.getProperty("os.name", "").startsWith("Windows")) {
                win = (User32) Native.loadLibrary("user32", User32.class);
                int tid = ((Kernel32) Native.loadLibrary("kernel32", Kernel32.class)).GetCurrentThreadId();
                posted = (code, w, data) -> {
                    try { if (code >= 0 && w == 1 && data != null)
                        nativeEvent("queue", data.getPointer(0), data.getInt(8), data.getLong(16), data.getLong(24));
                    } catch (Throwable e) { record("hook error " + e); }
                    return win.CallNextHookEx(null, code, w, data);
                };
                sent = (code, w, data) -> {
                    try { if (code >= 0 && data != null)
                        nativeEvent("dispatch", data.getPointer(24), data.getInt(16), data.getLong(8), data.getLong(0));
                    } catch (Throwable e) { record("hook error " + e); }
                    return win.CallNextHookEx(null, code, w, data);
                };
                postedHandle = win.SetWindowsHookExW(3, posted, null, tid);
                sentHandle = win.SetWindowsHookExW(4, sent, null, tid);
                record("native thread=" + tid + " postedHook=" + postedHandle + " sentHook=" + sentHandle);
            }
        } catch (Exception | LinkageError e) { record("native diagnostics unavailable " + e); }
    }
    /** Also callable on the game thread by the incident snapshot agent without restarting. */
    public static synchronized void install() {
        if (instance != null) return;
        try { instance = new InputDiagnostics(); MinecraftForge.EVENT_BUS.register(instance); }
        catch (Exception e) { org.apache.logging.log4j.LogManager.getLogger().warn("Input diagnostics unavailable", e); }
    }
    public static void event(String line) { if (instance != null) instance.record(line); }
    public static void keyboard(String consumer) {
        if (instance == null) return;
        int key = Keyboard.getEventKey();
        String name = key == Keyboard.KEY_F11 ? "F11" : key == Keyboard.KEY_ESCAPE ? "ESC"
                : key == Keyboard.KEY_RSHIFT ? "RSHIFT" : "key";
        event("CONSUMED " + consumer + " " + name + " down=" + Keyboard.getEventKeyState()
                + " eventTime=" + Keyboard.getEventNanoseconds());
    }
    public static void mouse(String consumer) {
        if (instance == null || Mouse.getEventButton() < 0 && Mouse.getEventDWheel() == 0) return;
        event("CONSUMED " + consumer + " button=" + Mouse.getEventButton() + " down=" + Mouse.getEventButtonState()
                + " wheel=" + Mouse.getEventDWheel() + " xy=" + Mouse.getEventX() + "," + Mouse.getEventY());
    }
    private void record(String line) {
        if (!queue.offer(java.time.LocalDateTime.now().format(TIME) + " " + line)) dropped.incrementAndGet();
    }
    private void nativeEvent(String source, Pointer window, int message, long w, long l) throws Exception {
        if (window == null || Pointer.nativeValue(window) != hwndField.getLong(display)) return;
        if (message == 0x200) { nativeMoves++; return; }
        if (message >= 0x201 && message <= 0x20e) { nativeClicks++; record(source + " mouse msg=" + message + " w=" + w + " xy=" + l); }
        else if (message == 0x100 || message == 0x101 || message == 0x104 || message == 0x105) {
            nativeKeys++;
            // Don't make chat, account passwords or music searches reconstructable from logs.
            String key = w == 0x1b ? "ESC" : w == 0x7a ? "F11" : w == 0x10 || w == 0xa1 ? "SHIFT" : "key";
            record(source + " keyboard " + key + " down=" + (message == 0x100 || message == 0x104));
        } else if (message == 5 || message == 6 || message == 7 || message == 8 || message == 0x215 || message == 0x21f || message == 0x212 || message == 0x211)
            record(source + " window msg=" + message + " w=" + w + " l=" + l);
    }
    @SubscribeEvent public void render(TickEvent.RenderTickEvent e) { if (e.phase == TickEvent.Phase.END) frames++; }
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        ticks++; heartbeat = System.nanoTime();
        Minecraft mc = Minecraft.getMinecraft();
        String screen = mc.currentScreen == null ? "world" : mc.currentScreen.getClass().getName() + "@" + System.identityHashCode(mc.currentScreen);
        if (!screen.equals(previousScreen)) { record("SCREEN " + previousScreen + " -> " + screen); previousScreen = screen; }
        if (heartbeat < next) return;
        next = heartbeat + 500_000_000L;
        try {
            record("STATE ticks=" + ticks + " frames=" + frames + " screen=" + screen
                    + " fullscreen=" + mc.isFullScreen() + " exclusive=" + Display.isFullscreen() + " borderless=" + BorderlessWindow.active() + " active=" + Display.isActive() + " visible=" + Display.isVisible()
                    + " gameFocus=" + mc.inGameHasFocus + " size=" + mc.displayWidth + "x" + mc.displayHeight
                    + " mouse=" + Mouse.isCreated() + " grab=" + Mouse.isGrabbed() + " xy=" + Mouse.getX() + "," + Mouse.getY()
                    + " lastMouseEvent=" + Mouse.getEventNanoseconds() + " keyboard=" + Keyboard.isCreated()
                    + " lastKeyEvent=" + Keyboard.getEventNanoseconds() + " native(keys/move/click)=" + nativeKeys + "/" + nativeMoves + "/" + nativeClicks
                    + " hwnd=" + (display == null ? "?" : hwndField.getLong(display))
                    + " foreground=" + (win == null ? "?" : win.GetForegroundWindow())
                    + " focus=" + (win == null ? "?" : win.GetFocus()) + " capture=" + (win == null ? "?" : win.GetCapture())
                    + " dropped=" + dropped + " panel=" + panelState(mc.currentScreen));
        } catch (Exception e1) { record("snapshot error " + e1); }
    }
    private static String panelState(Object screen) {
        if (screen == null || !screen.getClass().getName().endsWith("OnyxClickGui")) return "-";
        try {
            Field closing = screen.getClass().getDeclaredField("closing"); closing.setAccessible(true);
            Field open = screen.getClass().getDeclaredField("open"); open.setAccessible(true);
            return "closing=" + closing.get(screen) + ",openDone=" + open.get(screen).getClass().getMethod("isDone").invoke(open.get(screen));
        } catch (Exception e) { return e.toString(); }
    }
}
