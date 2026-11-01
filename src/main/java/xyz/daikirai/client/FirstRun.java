package xyz.daikirai.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import xyz.daikirai.gui.onyx.menu.WelcomeScreen;

import java.io.File;

/**
 * First launch detection for the welcome screen.
 *
 * <p>The rule is the client's own config folder: if {@code <game dir>/keystrokes} does not exist yet,
 * this is a fresh install. That has to be sampled <b>before</b> {@link ClientConfig#load()} runs,
 * because loading the config creates the folder on a fresh install — once it exists the answer is
 * "no welcome". {@link #capture()} runs at the top of the client's {@code init} for exactly that
 * reason.</p>
 *
 * <p>Showing is done from the client tick rather than a mixin: it waits until the title screen is on
 * screen and then swaps the welcome screen in, so it works for all three title-screen styles (the
 * Android and Onyx menus both extend {@code GuiMainMenu}).</p>
 */
public final class FirstRun {

    /** Directory that only exists after the client has run once (see {@link ClientConfig#DIR}). */
    private static final File CONFIG_DIR = new File(ClientConfig.DIR);

    private static boolean firstLaunch;
    private static boolean pending;

    /** Must be called before anything touches {@link ClientConfig#load()}. */
    public static void capture() {
        firstLaunch = !CONFIG_DIR.isDirectory();
        pending = firstLaunch;
        System.out.println("[onyx] first launch: " + firstLaunch + " (" + CONFIG_DIR + ")");
    }

    public static boolean isFirstLaunch() {
        return firstLaunch;
    }

    /** True while the welcome screen still has to be shown this session. */
    public static boolean pending() {
        return pending;
    }

    /** "Get started" was pressed: never show it again this session. */
    public static void dismissed() {
        pending = false;
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !pending) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen == null || !(mc.currentScreen instanceof GuiMainMenu)
                || mc.currentScreen instanceof WelcomeScreen) {
            return;
        }
        mc.displayGuiScreen(new WelcomeScreen());
    }
}
