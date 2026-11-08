package xyz.daikirai.gui.onyx.material;

import xyz.daikirai.gui.onyx.menu.MenuTheme;

/**
 * Live theme state for the ported Material You ClickGUI, mirroring Onyx's {@code theme.Util4}.
 *
 * <p>The accent, background tint and interface scale are driven by the "Onyx ClickGUI" module
 * settings, so the whole palette is regenerated whenever one of them changes.</p>
 */
public final class Theme {

    /** Material 3 baseline seed (also Onyx's shipped default accent: {@code 0xFF6750A4}). */
    public static final int DEFAULT_ACCENT = 0xFF6750A4;

    /** Seed presets offered in the module settings. */
    public static final int[] ACCENTS = {
            0xFF6750A4, // purple (Material baseline)
            0xFF3F5F90, // blue
            0xFF3F6B62, // teal
            0xFF4F6B45, // green
            0xFF7A5C1E, // amber
            0xFF8C4A32, // orange
            0xFF8C4767, // pink
            0xFF6B5A4E  // mocha
    };

    public static final String[] ACCENT_NAMES = {
            "Purple", "Blue", "Teal", "Green", "Amber", "Orange", "Pink", "Mocha"
    };

    private static int accent = DEFAULT_ACCENT;
    private static float tint = 0.25F;
    private static float scale = 1.0F;
    private static boolean frost = true;
    private static boolean followGuiScale = true;
    /** Whether HUD labels use the active accent instead of neutral surface ink (off by default). */
    private static boolean hudTextColor = false;
    /**
     * Translucent ("glass") mode: every surface fill keeps {@link #TRANSLUCENT_SURFACE} of its alpha
     * and the frosted backdrop shows through instead. Text and icons stay fully opaque.
     */
    private static boolean translucent = false;

    /** How much of a surface fill survives in translucent mode. */
    public static final float TRANSLUCENT_SURFACE = 0.46F;
    /** Frost coverage while translucent: high enough that the panel still reads as one surface. */
    public static final float TRANSLUCENT_FROST = 0.34F;
    /** The screen scrim lightens in translucent mode so more of the world stays visible. */
    public static final float TRANSLUCENT_SCRIM = 0.45F;

    private static Scheme scheme;
    private static int builtAccent = 0;
    private static float builtTint = -1.0F;
    private static boolean builtDark;
    private static boolean builtTranslucent;

    private Theme() {
    }

    public static void accent(int argb) {
        accent = argb | 0xFF000000;
    }

    public static int accent() {
        return accent;
    }

    public static void tint(float value) {
        tint = Material.clamp(value, 0.0F, 1.0F);
    }

    public static float tint() {
        return tint;
    }

    public static void scale(float value) {
        scale = Material.clamp(value, 0.3F, 1.5F);
    }

    public static float scale() {
        return scale;
    }

    public static void frost(boolean value) {
        frost = value;
    }

    public static boolean frost() {
        return frost;
    }

    public static void hudTextColor(boolean value) {
        hudTextColor = value;
    }

    public static boolean hudTextColor() {
        return hudTextColor;
    }

    public static void translucent(boolean value) {
        translucent = value;
    }

    public static boolean translucent() {
        return translucent;
    }

    /** Chooses the accent when enabled, otherwise preserving the neutral HUD text colour. */
    public static int hudText(int accent, int legacy) {
        return hudTextColor ? accent : legacy;
    }

    /** The shared appearance mode used by the ClickGUI, HUD cards, music player and title screens. */
    public static boolean dark() {
        return MenuTheme.dark();
    }

    /**
     * Whether the rewritten screens measure their design canvases in vanilla GUI units.
     *
     * <p>On (default): one design unit is one GUI unit, so the whole interface grows and shrinks with
     * {@code Options → GUI Scale} exactly like the game's own screens, and only shrinks when the
     * window can no longer hold it. Off: the canvas is stretched to fill the window at any GUI scale
     * (how the Android screens used to look).</p>
     */
    public static void followGuiScale(boolean value) {
        followGuiScale = value;
    }

    public static boolean followGuiScale() {
        return followGuiScale;
    }

    /**
     * Resolves the zoom a rewritten screen draws its design canvas at.
     *
     * @param fit zoom at which the design exactly fills the window
     */
    public static float screenScale(float fit) {
        return followGuiScale ? Math.min(fit, scale) : fit;
    }

    /** Current colour roles (rebuilt lazily when the seed changes). */
    public static Scheme scheme() {
        boolean dark = dark();
        if (scheme == null || builtAccent != accent || builtTint != tint || builtDark != dark
                || builtTranslucent != translucent) {
            scheme = translucent ? Scheme.glass(accent, tint) : Scheme.of(accent, tint, dark);
            builtAccent = accent;
            builtTint = tint;
            builtDark = dark;
            builtTranslucent = translucent;
        }
        return scheme;
    }

    /** {@code withAlpha} using design-time alpha in 0..1. */
    public static int alpha(int argb, float alpha) {
        return Material.withAlpha(argb, alpha);
    }

    public static int surfaceLevel(int level) {
        return scheme().surfaceLevel(level);
    }

    public static float shadowForLevel(int level) {
        return Scheme.shadowForLevel(level);
    }

    /** Corner radius clamped to half of the smallest side (Onyx's {@code theme.Util3}). */
    public static float radius(float radius, float width, float height) {
        return Math.min(radius, Math.min(width, height) / 2.0F);
    }

    /** Scrim colour over the (blurred) world, {@code 0.32 * fade} alpha in Onyx. */
    public static int scrim(float fade) {
        if (translucent) {
            // Glass mode keeps the world undimmed; the blur is what separates the pane from it.
            return 0;
        }
        return Material.withAlpha(scheme().scrim | 0xFF000000, Material.clamp(0.32F * fade, 0.0F, 1.0F));
    }
}
