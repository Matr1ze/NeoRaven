package xyz.daikirai.gui.onyx.render;

import java.awt.geom.Path2D;

/** Geometry and timeline translated from the user-supplied m3-shape-morph.html. */
final class ShapeMorphGeometry {
    static final int SAMPLES = 256;
    static final double TAU = Math.PI * 2, ACTIVE = 1.5, PAUSE = 0.175, CYCLE = ACTIVE + PAUSE;
    private static final double MIN_ARC = .04, AMP = .72, RSTART = .52 * ACTIVE, WHIP = 1.3;
    static final double[][] SHAPES = shapes();
    final double[] radii = new double[SAMPLES], x = new double[SAMPLES + 1], y = new double[SAMPLES + 1];
    final double[] cumulative = new double[SAMPLES + 1];
    double phase, base, head, tail, rotation;
    int laps, shapeIndex;

    ShapeMorphGeometry() { reset(); }
    void reset() { phase = base = rotation = 0; laps = 0; shapeIndex = 2; advance(0); }

    void advance(double dt) {
        dt = Math.max(0, Math.min(.05, dt));
        phase += dt;
        int crossed = (int)Math.floor(phase / CYCLE);
        phase -= crossed * CYCLE; laps += crossed;
        base += dt / 2.4;
        double stretch, catchup;
        if (phase < .42 * ACTIVE) { stretch = 1 - Math.pow(1 - phase / (.42 * ACTIVE), 2.2); catchup = 0; }
        else if (phase < .52 * ACTIVE) { stretch = 1; catchup = 0; }
        else if (phase < ACTIVE) { stretch = 1; catchup = ease((phase - .52 * ACTIVE) / (.48 * ACTIVE)); }
        else { stretch = catchup = 1; }
        tail = base + (AMP - MIN_ARC) * (laps + catchup);
        head = base + MIN_ARC + (AMP - MIN_ARC) * (laps + stretch);
        shapeIndex = (shapeIndex + crossed) % SHAPES.length;
        double mt = ease(clamp((phase - (RSTART + .20 * (ACTIVE - RSTART))) / (.72 * (ACTIVE - RSTART))));
        double[] from = SHAPES[shapeIndex], to = SHAPES[(shapeIndex + 1) % SHAPES.length];
        double mid = 0;
        for (int i = 0; i < SAMPLES; i++) mid += from[i] + to[i];
        mid /= 2 * SAMPLES;
        for (int i = 0; i < SAMPLES; i++) radii[i] = mt < .5 ? from[i] + (mid - from[i]) * mt * 2
                : mid + (to[i] - mid) * (mt - .5) * 2;
        double rate = TAU / 20, multiplier = 1, k = Math.toRadians(225) / (rate * WHIP);
        double whip = (phase - RSTART) / WHIP, previous = (phase + CYCLE - RSTART) / WHIP;
        if (whip >= 0 && whip < 1) multiplier += k * (1 - Math.cos(TAU * whip));
        if (previous >= 0 && previous < 1) multiplier += k * (1 - Math.cos(TAU * previous));
        rotation += dt * rate * multiplier;
        cumulative[0] = 0;
        for (int i = 0; i <= SAMPLES; i++) {
            double theta = (double)i / SAMPLES * TAU + rotation;
            x[i] = radii[i % SAMPLES] * Math.cos(theta); y[i] = radii[i % SAMPLES] * Math.sin(theta);
            if (i > 0) cumulative[i] = cumulative[i - 1] + Math.hypot(x[i] - x[i - 1], y[i] - y[i - 1]);
        }
    }

    void track(Path2D.Double path, double cx, double cy, double radius) {
        path.reset(); path.moveTo(cx + x[0] * radius, cy + y[0] * radius);
        for (int i = 1; i <= SAMPLES; i++) path.lineTo(cx + x[i] * radius, cy + y[i] * radius);
        path.closePath();
    }
    void active(Path2D.Double path, double cx, double cy, double radius) {
        path.reset(); double length = cumulative[SAMPLES];
        double start = (tail - Math.floor(tail)) * length, end = (head - Math.floor(head)) * length;
        if (end <= start + 1e-9) { span(path, start, length, cx, cy, radius); span(path, 0, end, cx, cy, radius); }
        else span(path, start, end, cx, cy, radius);
    }
    private void span(Path2D.Double path, double start, double end, double cx, double cy, double radius) {
        if (end - start < 1e-9) return;
        boolean started = false;
        for (int i = 1; i <= SAMPLES; i++) {
            double a = cumulative[i - 1], b = cumulative[i];
            if (b < start || a > end) continue;
            double segment = b - a == 0 ? 1e-9 : b - a;
            double t0 = (Math.max(a, start) - a) / segment, t1 = (Math.min(b, end) - a) / segment;
            double x0 = x[i - 1] + (x[i] - x[i - 1]) * t0, y0 = y[i - 1] + (y[i] - y[i - 1]) * t0;
            if (!started) { path.moveTo(cx + radius * x0, cy + radius * y0); started = true; }
            else path.lineTo(cx + radius * x0, cy + radius * y0);
            path.lineTo(cx + radius * (x[i - 1] + (x[i] - x[i - 1]) * t1),
                    cy + radius * (y[i - 1] + (y[i] - y[i - 1]) * t1));
        }
    }
    private static double[][] shapes() {
        double[][] result = new double[7][SAMPLES];
        int[] passes = {3, 2, 5, 4, 3, 2, 1};
        for (int shape = 0; shape < 7; shape++) {
            double[] samples = result[shape];
            for (int i = 0; i < SAMPLES; i++) {
                double t = (double)i / SAMPLES * TAU;
                switch (shape) {
                    case 0: samples[i] = 1 + .15 * Math.cos(8 * t); break;
                    case 1: samples[i] = 1 + .13 * Math.cos(9 * t); break;
                    case 2: samples[i] = polygon(t, 5, -Math.PI / 2); break;
                    case 3: samples[i] = polygon(t, 4, -Math.PI / 4); break;
                    case 4: samples[i] = polygon(t, 12, -Math.PI / 2); break;
                    case 5: samples[i] = 1 + .18 * Math.cos(4 * t); break;
                    default: double c = Math.cos(t), s = Math.sin(t) / .72; samples[i] = 1 / Math.sqrt(c * c + s * s);
                }
            }
            for (int p = 0; p < passes[shape]; p++) {
                double[] next = new double[SAMPLES];
                for (int i = 0; i < SAMPLES; i++) next[i] = (samples[(i + SAMPLES - 2) % SAMPLES]
                        + samples[(i + SAMPLES - 1) % SAMPLES] * 2 + samples[i] * 3
                        + samples[(i + 1) % SAMPLES] * 2 + samples[(i + 2) % SAMPLES]) / 9;
                samples = next;
            }
            double max = 0; for (double value : samples) max = Math.max(max, value);
            for (int i = 0; i < SAMPLES; i++) result[shape][i] = samples[i] / max;
        }
        return result;
    }
    private static double polygon(double theta, int n, double rotation) {
        double sector = TAU / n, a = theta - rotation;
        a -= Math.floor(a / sector) * sector; a -= sector / 2;
        return Math.cos(sector / 2) / Math.cos(a);
    }
    private static double clamp(double value) { return Math.max(0, Math.min(1, value)); }
    private static double ease(double t) { return t < .5 ? 2 * t * t : 1 - Math.pow(-2 * t + 2, 2) / 2; }
}
