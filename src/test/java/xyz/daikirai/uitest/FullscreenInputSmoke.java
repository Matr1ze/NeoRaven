package xyz.daikirai.uitest;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.win32.StdCallLibrary;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;
import xyz.daikirai.client.FullscreenInput;
import xyz.daikirai.gui.onyx.OnyxClickGui;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Files;

/** Test-only mod. Real native window recreation and queued Win32 key/mouse delivery, isolated save. */
@Mod(modid = "neoraven_fullscreen_test", name = "Fullscreen input test", version = "1", dependencies = "after:clickgui")
public final class FullscreenInputSmoke {
    private final Minecraft mc = Minecraft.getMinecraft();
    private int stage;
    private long next;
    private GuiScreen expected;
    private long originalWindow;
    private int windowWidth, windowHeight;
    private interface User32 extends StdCallLibrary {
        Pointer GetForegroundWindow();
        Pointer GetFocus();
        boolean PostMessageW(Pointer hwnd, int message, Pointer wParam, Pointer lParam);
    }
    @Mod.EventHandler public void init(FMLInitializationEvent event) {
        if (Boolean.getBoolean("neoraven.fullscreenSmoke")) MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent public void frame(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || System.nanoTime() < next) return;
        next = System.nanoTime() + 900_000_000L;
        try {
            switch (stage++) {
                case 0:
                    if (mc.isFullScreen()) mc.toggleFullscreen();
                    originalWindow = xyz.daikirai.client.BorderlessWindow.windowHandle();
                    windowWidth = mc.displayWidth; windowHeight = mc.displayHeight;
                    expected = mc.currentScreen;
                    require(expected != null, "Initial menu missing");
                    Keyboard.enableRepeatEvents(true);
                    mc.toggleFullscreen(); break;
                case 1:
                    check(false); require(Keyboard.areRepeatEventsEnabled(), "Repeat mode lost");
                    mc.toggleFullscreen(); break;
                case 2:
                    check(false);
                    expected = new OnyxClickGui(); mc.displayGuiScreen(expected);
                    mc.toggleFullscreen(); break;
                case 3:
                    check(false); mc.toggleFullscreen(); break;
                case 4:
                    check(false);
                    mc.gameSettings.pauseOnLostFocus = false;
                    mc.launchIntegratedServer("fullscreen-input-test", "Fullscreen input test",
                            new WorldSettings(42, WorldSettings.GameType.CREATIVE, false, false, WorldType.FLAT));
                    expected = null; next = System.nanoTime() + 3_000_000_000L; break;
                case 5:
                    require(mc.theWorld != null && mc.thePlayer != null, "World did not start");
                    mc.displayGuiScreen(null);
                    mc.toggleFullscreen(); break;
                case 6:
                    check(true); mc.toggleFullscreen(); break;
                case 7:
                    check(true);
                    // Rapid switching must retain the native window and the live mouse grab.
                    for (int i = 0; i < 20; i++) {
                        mc.toggleFullscreen();
                        require(xyz.daikirai.client.BorderlessWindow.windowHandle() == originalWindow, "Rapid F11 recreated HWND");
                        require(Mouse.isGrabbed(), "Rapid F11 lost mouse grab");
                    }
                    expected = new OnyxClickGui(); mc.displayGuiScreen(expected);
                    mc.toggleFullscreen(); break;
                case 8:
                    check(false); mc.toggleFullscreen(); break;
                case 9:
                    check(false);
                    mc.displayGuiScreen(null); expected = null;
                    next = System.nanoTime() + 1_000_000_000L; break;
                case 10:
                    check(true);
                    Files.write(new File("PASS.txt").toPath(),
                            "PASS: 28 borderless transitions, unchanged HWND, window size restoration, menu/ClickGUI/world, native input queue, GUI close\n".getBytes("UTF-8"));
                    finish(); break;
                default: throw new AssertionError("Unexpected stage");
            }
        } catch (Throwable e) {
            e.printStackTrace();
            try { Files.write(new File("FAIL.txt").toPath(), e.toString().getBytes("UTF-8")); }
            catch (Exception ignored) { }
            finish();
        }
    }
    private void check(boolean gameplay) throws Exception {
        require(mc.currentScreen == expected, "Fullscreen replaced the current GUI");
        require(Display.isActive(), "Window inactive");
        require(mc.inGameHasFocus == gameplay && Mouse.isGrabbed() == gameplay, "Wrong input owner");
        require(Keyboard.isCreated() && Mouse.isCreated(), "Missing input device");
        require(!Display.isFullscreen(), "Exclusive fullscreen still used");
        require(xyz.daikirai.client.BorderlessWindow.active() == mc.isFullScreen(), "Game fullscreen state differs");
        require(mc.gameSettings.fullScreen == mc.isFullScreen(), "Setting differs from fullscreen state");
        if (!mc.isFullScreen()) require(mc.displayWidth == windowWidth && mc.displayHeight == windowHeight, "Window size not restored");
        Method implementationMethod = Display.class.getDeclaredMethod("getImplementation");
        implementationMethod.setAccessible(true);
        Object implementation = implementationMethod.invoke(null);
        Method hwndMethod = implementation.getClass().getDeclaredMethod("getHwnd"); hwndMethod.setAccessible(true);
        Pointer hwnd = new Pointer(((Number) hwndMethod.invoke(implementation)).longValue());
        require(Pointer.nativeValue(hwnd) == originalWindow, "Native window recreated");
        User32 win = (User32) Native.loadLibrary("user32", User32.class);
        require(hwnd.equals(win.GetForegroundWindow()) && hwnd.equals(win.GetFocus()), "Wrong native input focus");
        while (Keyboard.next()) { } while (Mouse.next()) { }
        // Send only to this test game's message queue, never to the desktop or another program.
        require(win.PostMessageW(hwnd, 0x100, new Pointer(0x77), new Pointer(1)), "Post key down failed"); // F8
        require(win.PostMessageW(hwnd, 0x101, new Pointer(0x77), new Pointer(0xC0000001L)), "Post key up failed");
        require(win.PostMessageW(hwnd, 0x20A, new Pointer(120L << 16), new Pointer(0)), "Post wheel failed");
        Display.processMessages(); Keyboard.poll(); Mouse.poll();
        boolean down = false, up = false, wheel = false;
        while (Keyboard.next()) if (Keyboard.getEventKey() == Keyboard.KEY_F8) {
            if (Keyboard.getEventKeyState()) down = true; else up = true;
        }
        while (Mouse.next()) if (Mouse.getEventDWheel() == 120) wheel = true;
        require(down && up && wheel, "Native events did not reach LWJGL: " + down + "/" + up + "/" + wheel);
        System.out.println("[FULLSCREEN TEST] PASS stage=" + stage + " fullscreen=" + Display.isFullscreen() + " gameplay=" + gameplay);
    }
    private void finish() {
        MinecraftForge.EVENT_BUS.unregister(this);
        mc.shutdown();
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
