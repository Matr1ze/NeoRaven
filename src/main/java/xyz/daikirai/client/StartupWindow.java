package xyz.daikirai.client;

import org.apache.logging.log4j.LogManager;
import org.lwjgl.opengl.Display;

/**
 * Window policy for ordinary launches.
 *
 * <p>Minecraft is normally started by a launcher with a fixed {@code --width/--height}, so the
 * splash animation and the first menu screens were cramped into an 854x480 window. The client now
 * opens at a larger share of the desktop instead, and as soon as the title screen exists it enters
 * the borderless fullscreen mode - the same path F11 uses - unless the player disabled that in
 * {@code keystrokes/config.txt}.</p>
 */
public final class StartupWindow {

    /** Fraction of the desktop that the windowed startup covers. */
    private static final float WINDOW_FRACTION = 0.88F;
    private static final String LOGGER = "NeoRaven/Window";

    private StartupWindow() {
    }

    /**
     * Automation runs (the in-game smoke tests) need the exact launch geometry and often move the
     * window themselves, so they opt out of both the startup resize and the automatic fullscreen.
     */
    public static boolean fixedByAutomation() {
        return Boolean.getBoolean("neoraven.keepWindow");
    }

    /** Size the window should open at, or {@code null} to keep the launcher's own geometry. */
    public static int[] preferredWindowSize() {
        if (fixedByAutomation()) {
            return null;
        }
        try {
            java.awt.DisplayMode desktop = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .getDefaultScreenDevice().getDisplayMode();
            int width = Math.round(desktop.getWidth() * WINDOW_FRACTION);
            int height = Math.round(desktop.getHeight() * WINDOW_FRACTION);
            if (width <= 0 || height <= 0) {
                return null;
            }
            LogManager.getLogger(LOGGER).info("Startup window {}x{} on a {}x{} desktop",
                    width, height, desktop.getWidth(), desktop.getHeight());
            return new int[] {width, height};
        } catch (Throwable e) {
            LogManager.getLogger(LOGGER).warn("Could not query the desktop size, keeping the launcher window", e);
            return null;
        }
    }

    /** True when the title screen should switch into borderless fullscreen automatically. */
    public static boolean autoFullscreen() {
        return !fixedByAutomation() && BorderlessWindow.supported() && ClientConfig.autoFullscreen;
    }

    /** Desktop size in pixels, for the size assertions of the startup-window test. */
    public static int[] desktopSize() {
        java.awt.DisplayMode desktop = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getDefaultScreenDevice().getDisplayMode();
        return new int[] {desktop.getWidth(), desktop.getHeight()};
    }

    /** The size the windowed startup should occupy, for the startup-window test. */
    public static int[] windowedStartupSize() {
        int[] desktop = desktopSize();
        return new int[] {Math.round(desktop[0] * WINDOW_FRACTION), Math.round(desktop[1] * WINDOW_FRACTION)};
    }

    /** The current client area size; used by the startup-window test. */
    public static int[] displaySize() {
        return new int[] {Display.getWidth(), Display.getHeight()};
    }
}
