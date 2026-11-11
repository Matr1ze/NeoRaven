package xyz.daikirai.music;

import com.sun.jna.Callback;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.win32.StdCallLibrary;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Lets the game window receive text from a Chinese IME (Windows, 64-bit only).
 *
 * <p>LWJGL 2 drops the committed IME string, so the window procedure is wrapped once. While
 * {@link #setActive(boolean)} is true, the committed string of every {@code WM_IME_COMPOSITION}
 * is queued here and hidden from LWJGL (otherwise it could arrive twice). The composition and
 * candidate pop-ups still use the system IME UI. While inactive the wrapper only forwards
 * messages. Any failure leaves {@link #available()} false and the caller falls back to the
 * separate input window.</p>
 */
final class MusicIme {

    private static final int GWLP_WNDPROC = -4;
    private static final int WM_IME_COMPOSITION = 0x010F;
    private static final int GCS_RESULTSTR = 0x0800;
    private static final int GCS_RESULTCLAUSE = 0x1000;
    private static final int GCS_RESULTREADSTR = 0x0200;
    private static final int GCS_RESULTREADCLAUSE = 0x0400;
    private static final int RESULT_FLAGS = GCS_RESULTSTR | GCS_RESULTCLAUSE
            | GCS_RESULTREADSTR | GCS_RESULTREADCLAUSE;
    private static final int IACE_DEFAULT = 0x0010;
    private static final int CFS_POINT = 0x0002;
    private static final int CFS_CANDIDATEPOS = 0x0040;

    private static final int WH_GETMESSAGE = 3;
    private static final int WH_CALLWNDPROC = 4;
    private static final int WM_KEYDOWN = 0x0100;
    private static final int WM_CHAR = 0x0102;
    private static final int WM_IME_CHAR = 0x0286;

    private interface User32 extends StdCallLibrary {
        Pointer GetActiveWindow();

        Pointer GetFocus();

        Pointer GetKeyboardLayout(int threadId);

        Pointer SetWindowLongPtrW(Pointer hwnd, int index, Callback proc);

        long CallWindowProcW(Pointer previous, Pointer hwnd, int msg, long wParam, long lParam);

        Pointer SetWindowsHookExW(int idHook, Callback proc, Pointer module, int threadId);

        long CallNextHookEx(Pointer hook, int code, long wParam, Pointer lParam);
    }

    private interface Kernel32 extends StdCallLibrary {
        int GetCurrentThreadId();
    }

    private interface HookProc extends StdCallLibrary.StdCallCallback {
        long callback(int code, long wParam, Pointer lParam);
    }

    private interface Imm32 extends StdCallLibrary {
        Pointer ImmGetContext(Pointer hwnd);

        boolean ImmReleaseContext(Pointer hwnd, Pointer himc);

        int ImmGetCompositionStringW(Pointer himc, int index, Pointer buffer, int length);

        boolean ImmAssociateContextEx(Pointer hwnd, Pointer himc, int flags);

        boolean ImmSetCompositionWindow(Pointer himc, Pointer form);

        boolean ImmSetCandidateWindow(Pointer himc, Pointer form);

        boolean ImmGetOpenStatus(Pointer himc);
    }

    private interface WndProc extends StdCallLibrary.StdCallCallback {
        long callback(Pointer hwnd, int msg, long wParam, long lParam);
    }

    private static final ConcurrentLinkedQueue<String> COMMITTED = new ConcurrentLinkedQueue<String>();

    private static User32 user32;
    private static Imm32 imm32;
    private static WndProc proc;   // strong reference: JNA frees the native stub otherwise
    private static HookProc sentHook;     // WH_CALLWNDPROC: messages sent to any window of this thread
    private static HookProc postedHook;   // WH_GETMESSAGE: messages posted to any window of this thread
    private static Pointer sentHandle;
    private static Pointer postedHandle;
    private static int threadId;
    private static Pointer window;
    private static Pointer previous;
    private static volatile boolean active;
    private static boolean failed;
    private static int lastX = Integer.MIN_VALUE;
    private static int lastY = Integer.MIN_VALUE;

    private MusicIme() {
    }

    /** Whether the hook is installed; call {@link #setActive(boolean)} from the render thread first. */
    static boolean available() {
        return window != null && !failed;
    }

    /** Turns text capture on or off. The first {@code true} installs the hook (render thread only). */
    static void setActive(boolean value) {
        if (value == active && (!value || window != null)) {
            return;
        }
        if (value && window == null && !install()) {
            active = false;
            return;
        }
        active = value && window != null;
        System.out.println("[music-ime] setActive(" + value + ") -> active=" + active);
        if (active) {
            try {
                imm32.ImmAssociateContextEx(window, null, IACE_DEFAULT);
                logState("activate");
            } catch (Throwable t) {
                disable(t);
            }
        } else {
            lastX = Integer.MIN_VALUE;
        }
    }

    /** Takes the next committed string, or null when nothing is waiting. */
    static String poll() {
        return COMMITTED.poll();
    }

    /** Moves the IME pop-ups to a point in client-area pixels (top-left of the window is 0,0). */
    static void placeAt(int x, int y) {
        if (!available() || !active || (x == lastX && y == lastY)) {
            return;
        }
        lastX = x;
        lastY = y;
        Pointer context = null;
        try {
            context = imm32.ImmGetContext(window);
            if (context == null) {
                return;
            }
            Memory composition = new Memory(28);   // COMPOSITIONFORM
            composition.clear();
            composition.setInt(0, CFS_POINT);
            composition.setInt(4, x);
            composition.setInt(8, y);
            imm32.ImmSetCompositionWindow(context, composition);
            Memory candidate = new Memory(32);     // CANDIDATEFORM
            candidate.clear();
            candidate.setInt(0, 0);
            candidate.setInt(4, CFS_CANDIDATEPOS);
            candidate.setInt(8, x);
            candidate.setInt(12, y);
            imm32.ImmSetCandidateWindow(context, candidate);
        } catch (Throwable t) {
            disable(t);
        } finally {
            if (context != null) {
                try {
                    imm32.ImmReleaseContext(window, context);
                } catch (Throwable ignored) {
                    // nothing left to release
                }
            }
        }
    }

    private static boolean install() {
        if (failed) {
            return false;
        }
        try {
            if (!System.getProperty("os.name", "").toLowerCase().contains("windows")
                    || Native.POINTER_SIZE != 8) {
                failed = true;
                return false;
            }
            user32 = (User32) Native.loadLibrary("user32", User32.class);
            imm32 = (Imm32) Native.loadLibrary("imm32", Imm32.class);
            Pointer hwnd = user32.GetActiveWindow();
            if (hwnd == null) {
                return false;   // not the game thread or window not active yet; retry next frame
            }
            proc = new WndProc() {
                @Override
                public long callback(Pointer h, int msg, long wParam, long lParam) {
                    int flags = (int) lParam;
                    if (active && msg == WM_IME_COMPOSITION && (flags & GCS_RESULTSTR) != 0) {
                        readResult(h);
                        lParam = (int) (flags & ~RESULT_FLAGS) & 0xFFFFFFFFL;
                    }
                    return user32.CallWindowProcW(previous, h, msg, wParam, lParam);
                }
            };
            Pointer old = user32.SetWindowLongPtrW(hwnd, GWLP_WNDPROC, proc);
            if (old == null) {
                proc = null;
                failed = true;
                return false;
            }
            previous = old;
            window = hwnd;
            System.out.println("[music] IME hook installed");
            installThreadHooks();
            return true;
        } catch (Throwable t) {
            disable(t);
            return false;
        }
    }

    /**
     * Thread-wide hooks: they see IME messages even when Windows sends them to a window other
     * than the one wrapped above, and log what arrives while capture is active.
     */
    private static void installThreadHooks() {
        try {
            Kernel32 kernel32 = (Kernel32) Native.loadLibrary("kernel32", Kernel32.class);
            threadId = kernel32.GetCurrentThreadId();
            sentHook = new HookProc() {
                @Override
                public long callback(int code, long wParam, Pointer lParam) {
                    if (code >= 0 && active && lParam != null) {
                        try {
                            // CWPSTRUCT (64-bit): lParam @0, wParam @8, message @16, hwnd @24
                            int msg = lParam.getInt(16);
                            Pointer hwnd = lParam.getPointer(24);
                            long msgL = lParam.getLong(0);
                            long msgW = lParam.getLong(8);
                            onMessage("sent", hwnd, msg, msgW, msgL);
                        } catch (Throwable t) {
                            System.out.println("[music-ime] sent hook error: " + t);
                        }
                    }
                    return user32.CallNextHookEx(null, code, wParam, lParam);
                }
            };
            postedHook = new HookProc() {
                @Override
                public long callback(int code, long wParam, Pointer lParam) {
                    if (code >= 0 && wParam == 1 && active && lParam != null) {   // PM_REMOVE only
                        try {
                            // MSG (64-bit): hwnd @0, message @8, wParam @16, lParam @24
                            Pointer hwnd = lParam.getPointer(0);
                            int msg = lParam.getInt(8);
                            long msgW = lParam.getLong(16);
                            long msgL = lParam.getLong(24);
                            onMessage("posted", hwnd, msg, msgW, msgL);
                        } catch (Throwable t) {
                            System.out.println("[music-ime] posted hook error: " + t);
                        }
                    }
                    return user32.CallNextHookEx(null, code, wParam, lParam);
                }
            };
            sentHandle = user32.SetWindowsHookExW(WH_CALLWNDPROC, sentHook, null, threadId);
            postedHandle = user32.SetWindowsHookExW(WH_GETMESSAGE, postedHook, null, threadId);
            System.out.println("[music-ime] thread hooks sent=" + (sentHandle != null)
                    + " posted=" + (postedHandle != null) + " thread=" + threadId);
        } catch (Throwable t) {
            System.out.println("[music-ime] thread hooks failed: " + t);
        }
    }

    private static void onMessage(String how, Pointer hwnd, int msg, long wParam, long lParam) {
        boolean ime = (msg >= 0x010D && msg <= 0x010F) || (msg >= 0x0281 && msg <= 0x0290);
        boolean key = msg == WM_KEYDOWN || msg == WM_CHAR;
        if (!ime && !key) {
            return;
        }
        boolean main = hwnd != null && hwnd.equals(window);
        System.out.println("[music-ime] " + how + " msg=0x" + Integer.toHexString(msg)
                + " w=0x" + Long.toHexString(wParam) + " l=0x" + Integer.toHexString((int) lParam)
                + (main ? " main" : " other=" + hwnd));
        if (main) {
            if (msg == WM_KEYDOWN && "posted".equals(how)) {
                logState("key");
            }
            return;   // the wrapped window procedure (or LWJGL) handles these
        }
        if ("sent".equals(how) && msg == WM_IME_COMPOSITION && ((int) lParam & GCS_RESULTSTR) != 0) {
            readResult(hwnd);
        } else if ("posted".equals(how) && (msg == WM_CHAR || msg == WM_IME_CHAR) && wParam >= 0x80) {
            COMMITTED.add(String.valueOf((char) wParam));
        }
    }

    private static void logState(String when) {
        Pointer context = null;
        try {
            Pointer focus = user32.GetFocus();
            Pointer layout = user32.GetKeyboardLayout(0);
            context = imm32.ImmGetContext(window);
            boolean open = context != null && imm32.ImmGetOpenStatus(context);
            System.out.println("[music-ime] " + when + " window=" + window + " focus=" + focus
                    + " focusIsMain=" + (focus != null && focus.equals(window))
                    + " layout=" + layout + " context=" + (context != null) + " open=" + open);
        } catch (Throwable t) {
            System.out.println("[music-ime] state failed: " + t);
        } finally {
            if (context != null) {
                try {
                    imm32.ImmReleaseContext(window, context);
                } catch (Throwable ignored) {
                    // nothing left to release
                }
            }
        }
    }

    private static void readResult(Pointer hwnd) {
        Pointer context = null;
        try {
            context = imm32.ImmGetContext(hwnd);
            if (context == null) {
                return;
            }
            int bytes = imm32.ImmGetCompositionStringW(context, GCS_RESULTSTR, null, 0);
            System.out.println("[music-ime] result bytes=" + bytes);
            if (bytes <= 0) {
                return;
            }
            Memory buffer = new Memory(bytes + 2);
            buffer.clear();
            int read = imm32.ImmGetCompositionStringW(context, GCS_RESULTSTR, buffer, bytes);
            if (read > 0) {
                String text = buffer.getString(0, true);
                if (!text.isEmpty()) {
                    COMMITTED.add(text);
                }
            }
        } catch (Throwable t) {
            System.out.println("[music] IME read failed: " + t);
        } finally {
            if (context != null) {
                try {
                    imm32.ImmReleaseContext(hwnd, context);
                } catch (Throwable ignored) {
                    // nothing left to release
                }
            }
        }
    }

    private static void disable(Throwable t) {
        System.out.println("[music] IME hook unavailable: " + t);
        active = false;
        failed = true;
    }
}
