package xyz.daikirai.render.furiah.setting;

import xyz.daikirai.gui.onyx.material.Theme;
import xyz.daikirai.render.furiah.core.GameClock;

/** Read-only drawing colors scoped to the host module's Color setting. */
public final class ThemeTint {
    private static Palette active;
    private final float shade;

    public ThemeTint() { this(0); }

    private ThemeTint(float shade) {
        this.shade = shade;
    }

    public static ThemeTint detail(int reference) {
        int value = Math.max(reference >> 16 & 255, Math.max(reference >> 8 & 255, reference & 255));
        return new ThemeTint(value < 112 ? -0.56F : value > 230 ? 0.25F : -0.12F);
    }

    public static ThemeTint highlight() { return new ThemeTint(0.68F); }

    public int getColor(float position) {
        int color = primary();
        if (shade == 0) return color;
        int target = shade < 0 ? 0 : 255;
        float amount = Math.abs(shade);
        return 0xFF000000 | mix(color >> 16 & 255, target, amount) << 16
                | mix(color >> 8 & 255, target, amount) << 8 | mix(color & 255, target, amount);
    }

    public int getColor(float position, double time) { return getColor(position); }
    public static double clock() { return GameClock.seconds(); }
    public static int primary() { return active == null ? Theme.scheme().primary : active.color; }

    /** Carries the host hue through baked style pigments while retaining light, dark and white details. */
    public static int recolor(int argb) {
        if (active == null) return argb;
        int r = argb >> 16 & 255, g = argb >> 8 & 255, b = argb & 255;
        int max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        float range = max - min;
        if (range < 3) return argb;
        return argb & 0xFF000000
                | Math.round(max - range * active.redLoss) << 16
                | Math.round(max - range * active.greenLoss) << 8
                | Math.round(max - range * active.blueLoss);
    }

    private static int mix(int start, int end, float amount) {
        return Math.round(start + (end - start) * amount);
    }

    public static Scope use(int color) {
        Palette previous = active;
        active = new Palette(color);
        return () -> active = previous;
    }

    public interface Scope extends AutoCloseable {
        @Override void close();
    }

    private static final class Palette {
        private final int color;
        private final float redLoss, greenLoss, blueLoss;

        private Palette(int color) {
            this.color = color | 0xFF000000;
            int r = color >> 16 & 255, g = color >> 8 & 255, b = color & 255;
            int max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
            float range = max - min;
            redLoss = range == 0 ? 0.4F : (max - r) / range;
            greenLoss = range == 0 ? 0.4F : (max - g) / range;
            blueLoss = range == 0 ? 0.4F : (max - b) / range;
        }
    }

}
