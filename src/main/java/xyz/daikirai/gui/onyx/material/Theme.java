package xyz.daikirai.gui.onyx.material;

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

    private static Scheme scheme;
    private static int builtAccent = 0;
    private static float builtTint = -1.0F;

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
        if (scheme == null || builtAccent != accent || builtTint != tint) {
            scheme = Scheme.of(accent, tint);
            builtAccent = accent;
            builtTint = tint;
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
        return Material.withAlpha(scheme().scrim | 0xFF000000, Material.clamp(0.32F * fade, 0.0F, 1.0F));
    }
}
