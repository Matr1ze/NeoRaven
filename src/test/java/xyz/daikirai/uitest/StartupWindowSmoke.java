package xyz.daikirai.uitest;

import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.opengl.Display;
import xyz.daikirai.client.BorderlessWindow;
import xyz.daikirai.client.StartupWindow;

import java.io.File;
import java.nio.file.Files;

/**
 * Test-only mod for the ordinary launch window policy.
 *
 * <p>The launcher starts the game at 854x480; the client is expected to open at a large share of
 * the desktop and to switch into borderless fullscreen once the title screen exists, which is the
 * same mode F11 toggles. Pressing F11 again must return to that large window instead of the small
 * launcher size.</p>
 */
@Mod(modid = "neoraven_startup_window_test", name = "NeoRaven startup window test", version = "1",
        dependencies = "after:clickgui")
public final class StartupWindowSmoke {

    private final Minecraft mc = Minecraft.getMinecraft();
    private int stage;
    private long next;
    private int desktopWidth;
    private int desktopHeight;

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        if (Boolean.getBoolean("neoraven.startupWindowSmoke")) {
            MinecraftForge.EVENT_BUS.register(this);
        }
    }

    @SubscribeEvent
    public void frame(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || System.nanoTime() < next) {
            return;
        }
        try {
            switch (stage++) {
                case 0:
                    desktopWidth = StartupWindow.desktopSize()[0];
                    desktopHeight = StartupWindow.desktopSize()[1];
                    log("desktop=" + desktopWidth + "x" + desktopHeight
                            + " display=" + Display.getWidth() + "x" + Display.getHeight()
                            + " mc=" + mc.displayWidth + "x" + mc.displayHeight
                            + " fullscreen=" + mc.isFullScreen()
                            + " borderless=" + BorderlessWindow.active());
                    require(Display.isCreated(), "Display missing after startGame");
                    require(BorderlessWindow.active(),
                            "the title screen did not enter borderless fullscreen automatically");
                    require(mc.isFullScreen(), "Minecraft does not report fullscreen");
                    // LWJGL keeps its launch display mode cached; Minecraft's display size is the
                    // real client rect queried from the borderless window, so it is authoritative.
                    require(mc.displayWidth >= desktopWidth * 0.95
                                    && mc.displayHeight >= desktopHeight * 0.95,
                            "fullscreen window is not monitor sized: "
                                    + mc.displayWidth + "x" + mc.displayHeight
                                    + " vs " + desktopWidth + "x" + desktopHeight);
                    log("PASS fullscreen at the title screen: " + mc.displayWidth + "x" + mc.displayHeight);
                    // F11 back to windowed: the restore target is the large startup window.
                    mc.toggleFullscreen();
                    next = System.nanoTime() + 1_500_000_000L;
                    break;
                case 1:
                    int[] expected = StartupWindow.windowedStartupSize();
                    log("restored=" + Display.getWidth() + "x" + Display.getHeight()
                            + " mc=" + mc.displayWidth + "x" + mc.displayHeight
                            + " expected~" + expected[0] + "x" + expected[1]
                            + " fullscreen=" + mc.isFullScreen()
                            + " borderless=" + BorderlessWindow.active());
                    require(!BorderlessWindow.active(), "F11 did not leave borderless fullscreen");
                    require(!mc.isFullScreen(), "Minecraft still reports fullscreen after F11");
                    require(Math.abs(mc.displayWidth - expected[0]) <= 8
                                    && Math.abs(mc.displayHeight - expected[1]) <= 8,
                            "F11 restored " + mc.displayWidth + "x" + mc.displayHeight
                                    + " instead of the large startup window " + expected[0] + "x" + expected[1]);
                    log("PASS F11 restored the large startup window: " + mc.displayWidth + "x" + mc.displayHeight);
                    Files.write(new File("PASS.txt").toPath(),
                            ("PASS: windowed startup " + expected[0] + "x" + expected[1]
                                    + ", automatic fullscreen " + desktopWidth + "x" + desktopHeight
                                    + ", F11 restored the large window\n").getBytes("UTF-8"));
                    MinecraftForge.EVENT_BUS.unregister(this);
                    mc.shutdown();
                    break;
                default:
                    throw new AssertionError("unexpected stage " + stage);
            }
        } catch (Throwable error) {
            error.printStackTrace();
            try {
                Files.write(new File("FAIL.txt").toPath(), (error.toString() + "\n").getBytes("UTF-8"));
            } catch (Exception ignored) {
                // The logging line above is the important record.
            }
            MinecraftForge.EVENT_BUS.unregister(this);
            mc.shutdown();
        }
    }

    private static void log(String message) {
        System.out.println("[WINDOW TEST] " + message);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
