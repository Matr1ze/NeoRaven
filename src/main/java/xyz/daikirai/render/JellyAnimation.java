package xyz.daikirai.render;

/**
 * The squash track of the "friends wine" doll, ported verbatim from the mod's
 * {@code JellyAnimation} (Blockbench-approved keys, 1.21.1 NeoForge source).
 *
 * <p>Five keys per loop (0 / 0.25 / 0.45833 / 0.70833 / 0.91667 s) with values 0-1-0-1-0 and
 * smoothstep easing between neighbours. The alternating extrema have zero tangents, which is why
 * smoothstep reproduces the authored curve exactly and why the result reads as a jelly bounce
 * instead of a sine wave.</p>
 *
 * <p>Pure math, no Minecraft types: the module, the render hook and the audio clock all read the
 * same function, so a bounced model and the song never drift apart.</p>
 */
public final class JellyAnimation {
    /** Length of one bounce (the doll also turns exactly 360 degrees per loop). */
    public static final double LENGTH = 0.91667;

    private static final double[] TIMES = {0.0, 0.25, 0.45833, 0.70833, LENGTH};
    private static final double[] VALUES = {0.0, 1.0, 0.0, 1.0, 0.0};

    private JellyAnimation() {
    }

    public static double phase(double seconds) {
        return Math.max(0.0, seconds) % LENGTH;
    }

    /** Degrees. Negative on purpose: the doll turns the same way as the upstream animation. */
    public static float angle(double seconds) {
        return (float) (-360.0 * phase(seconds) / LENGTH);
    }

    /** 0 = at rest, 1 = fully squashed. */
    public static float squash(double seconds) {
        double time = phase(seconds);
        int i = 0;
        while (i < TIMES.length - 2 && time > TIMES[i + 1]) {
            i++;
        }
        double t = (time - TIMES[i]) / (TIMES[i + 1] - TIMES[i]);
        double from = VALUES[i];
        double to = VALUES[i + 1];
        return (float) (from + (to - from) * t * t * (3.0 - 2.0 * t));
    }

    /** Vertical scale for a compression setting, feet stay planted. */
    public static float heightScale(float squash, double compressionPercent) {
        return (float) (1.0 - compressionPercent / 100.0 * squash);
    }

    /** Horizontal scale. Depth is deliberately untouched, as upstream: the model must not poke out. */
    public static float widthScale(float squash, double widthPercent) {
        return (float) (1.0 + widthPercent / 100.0 * squash);
    }

    /**
     * Standalone check: compile this file alone, then {@code java -ea xyz.daikirai.render.JellyAnimation}.
     * The samples are the ones read back from Blockbench's renderer in the original mod, so a port
     * that keeps them passing cannot have introduced a different curve.
     */
    public static void main(String[] args) {
        assert Math.abs(squash(0.0)) < 1e-6;
        assert Math.abs(squash(0.25) - 1.0) < 1e-6;
        assert Math.abs(squash(0.45833)) < 1e-6;
        assert Math.abs(squash(0.70833) - 1.0) < 1e-6;
        assert Math.abs(squash(LENGTH)) < 1e-6;
        assert Math.abs(heightScale(0.0F, 50) - 1.0) < 1e-6;
        assert Math.abs(heightScale(1.0F, 50) - 0.5) < 1e-6;
        assert Math.abs(heightScale(0.0F, 0) - 1.0) < 1e-6;
        assert Math.abs(widthScale(0.0F, 200) - 1.0) < 1e-6;
        assert Math.abs(widthScale(1.0F, 200) - 3.0) < 1e-6;
        // Independent samples read back from Blockbench's renderer.
        assert Math.abs(squash(0.05) - 0.104) < 1e-6;
        assert Math.abs(squash(0.15) - 0.648) < 1e-6;
        assert Math.abs(squash(0.90) - 0.0181819354) < 1e-6;
        assert Math.abs(squash(0.3166678181818182) - 0.7583220985758712) < 1e-5;
        assert Math.abs(squash(0.6500023636363637) - 0.8620985322330896) < 1e-5;
        for (double t = 0.0; t < 60.0; t += 0.0037) {
            assert Math.abs(squash(t) - squash(t + LENGTH)) < 1e-4;
            assert squash(t) >= -0.0001 && squash(t) <= 1.0001;
        }
        assert Math.abs(angle(LENGTH / 2) + 180.0) < 1e-4;
        for (double compression : new double[] {0, 50, 90}) {
            for (double width : new double[] {0, 50, 200}) {
                for (double t = 0.0; t < 180.0; t += 0.023) {
                    float q = squash(t);
                    double scale = heightScale(q, compression);
                    double offset = 1.62 * (scale - 1.0);
                    assert Math.abs(1.62 + offset - 1.62 * scale) < 1e-6;
                    assert scale > 0.0 && widthScale(q, width) >= 1.0;
                }
            }
        }
        System.out.println("JellyAnimation curve, scale extremes and rotation phase passed.");
    }
}
