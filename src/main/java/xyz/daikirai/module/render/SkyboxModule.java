package xyz.daikirai.module.render;

import xyz.daikirai.module.setting.ColorSetting;
import xyz.daikirai.module.setting.SliderSetting;

/**
 * Animated custom sky, ported from OpenOnyx's {@code SkyboxModule}.
 *
 * <p>Onyx replaced the vanilla sky with one full-screen shader pass ({@code onyx_sky}) that paints
 * a noise field as a direction function: three presets (drifting clouds, a thunderstorm with
 * procedurally placed lightning bolts, and a pulsar with a star field), a fixed base colour, and
 * an optional quarter-resolution render that is upscaled afterwards. The renderer lives in
 * {@code xyz.daikirai.render.SkyboxRenderer}, which is called from the head of
 * {@code RenderGlobal.renderSky} and cancels the vanilla sky when it draws.</p>
 *
 * <p>Settings that only matter for one preset are kept visible for all of them, unlike Onyx's
 * conditional rows: this client's settings page has no per-setting visibility.</p>
 */
public final class SkyboxModule extends VisualModule {

    public static SkyboxModule instance;

    private static final int OCTAVES_FAST = 3;
    private static final int OCTAVES_BALANCED = 4;
    private static final int OCTAVES_DETAILED = 6;

    private final SliderSetting preset = mode("Preset", 0, "Clouds", "Thunder", "Pulsar");
    private final ColorSetting color = color("Color", -9335297);
    private final SliderSetting speed = slider("Animation speed", 1.0, 0.1, 5.0, 0.1, "x");
    private final SliderSetting scale = slider("Scale", 5.0, 1.0, 20.0, 0.5, "x");
    private final SliderSetting intensity = slider("Intensity", 1.0, 0.1, 5.0, 0.1, "%");
    private final SliderSetting strikeInterval = slider("Strike interval", 4.0, 1.0, 10.0, 0.5, "s");
    private final SliderSetting strikeChance = slider("Strike chance", 0.65, 0.0, 1.0, 0.05, "");
    private final SliderSetting strikeGlow = slider("Strike glow", 1.0, 0.1, 3.0, 0.1, "x");
    private final SliderSetting quality = mode("Quality", 1, "Fast", "Balanced", "Detailed");
    private final SliderSetting resolution = slider("Resolution", 50, 25, 100, 25, "%");

    public SkyboxModule(int key) {
        super("Skybox", key);
        instance = this;
        // A theme-following default would tint the sky with the accent colour; the picker can
        // still switch it over to the theme.
        color.setAppearance(-9335297, false);
    }

    // ------------------------------------------------------------------ //
    // Accessors used by the renderer
    // ------------------------------------------------------------------ //

    /** 0 = clouds, 1 = thunder, 2 = pulsar, matching the shader's preset constants. */
    public int preset() {
        return (int) Math.round(preset.getInput());
    }

    public int color() {
        return color.color();
    }

    public float animationSpeed() {
        return (float) speed.getInput();
    }

    public float scale() {
        return (float) scale.getInput();
    }

    /** The shader expects the intensity as a fraction, Onyx's slider was in percent. */
    public float intensity() {
        return (float) (intensity.getInput() / 100.0D);
    }

    public float strikeInterval() {
        return (float) strikeInterval.getInput();
    }

    public float strikeChance() {
        return (float) strikeChance.getInput();
    }

    public float strikeGlow() {
        return (float) strikeGlow.getInput();
    }

    /** Noise octaves; every extra one roughly doubles the cost of the pass. */
    public int octaves() {
        int index = (int) Math.round(quality.getInput());
        if (index <= 0) {
            return OCTAVES_FAST;
        }
        return index == 1 ? OCTAVES_BALANCED : OCTAVES_DETAILED;
    }

    /** Fraction of the screen the sky is rendered at, 0.25 to 1. */
    public float resolution() {
        return (float) Math.max(0.25D, Math.min(1.0D, resolution.getInput() / 100.0D));
    }
}
