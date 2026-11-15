package xyz.daikirai.gui.onyx.render;

import xyz.daikirai.gui.onyx.material.Theme;

/**
 * The one place that decides which glass material every surface uses.
 *
 * <p>The Theme page has two switches, "Frosted glass" and "Liquid glass". Every screen and HUD
 * asks this class instead of reading those switches itself, so flipping a switch changes the
 * ClickGUI, the music player, the menus and every HUD card at once.</p>
 *
 * <ul>
 *   <li>Liquid glass on (with or without frosted): {@link Mode#LIQUID}. Liquid wins.</li>
 *   <li>Only frosted glass on, or the Translucent appearance: {@link Mode#FROST}.</li>
 *   <li>Both off: {@link Mode#OFF}, plain opaque surfaces.</li>
 * </ul>
 *
 * <p>Surfaces are drawn through {@link UI#glassSurface} / {@link UI#hudGlass}, which read
 * {@link #mode()} and fall back to frost, then to an opaque fill, when a shader or the captured
 * backdrop is not available.</p>
 */
public final class Glass {

    public enum Mode { OFF, FROST, LIQUID }

    private Glass() {
    }

    public static Mode mode() {
        if (Theme.liquidGlass()) {
            return Mode.LIQUID;
        }
        if (Theme.frost() || Theme.translucent()) {
            return Mode.FROST;
        }
        return Mode.OFF;
    }

    /** Any glass at all: the screen must capture and blur its backdrop this frame. */
    public static boolean enabled() {
        return mode() != Mode.OFF;
    }

    public static boolean liquid() {
        return mode() == Mode.LIQUID;
    }

    public static boolean frost() {
        return mode() == Mode.FROST;
    }

    /** Glass is on and the blurred backdrop for this frame is ready to sample. */
    public static boolean ready() {
        return enabled() && Blur.ready();
    }

    /** Captures and blurs the backdrop when glass is on. Call before drawing any glass surface. */
    public static void prepare() {
        if (enabled()) {
            Blur.prepare();
        }
    }

    /**
     * Captures the backdrop again after a title/menu screen has drawn its own wallpaper.
     *
     * <p>{@code MenuBackground} blurs the raw still to build the background itself, but that first
     * capture is the untouched artwork: the dock, sheets and cards drawn on top of it must sample
     * the <em>composed</em> background (blurred and tinted) instead, otherwise they show a sharp,
     * untinted wallpaper while the rest of the screen is soft and tinted.</p>
     */
    public static void prepareAfterBackground() {
        if (enabled()) {
            Blur.request();
            Blur.prepare();
        }
    }
}
