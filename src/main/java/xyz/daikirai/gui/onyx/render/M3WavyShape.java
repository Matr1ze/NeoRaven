/*
 * Copyright (C) 2020, 2024 The Android Open Source Project
 * Copyright 2026 M3 Wavy Minecraft adaptation contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Adapted from Material Components Android CircularDrawingDelegate and
 * CircularIndeterminateRetreatAnimatorDelegate. See NOTICE and LICENSE.
 */
package xyz.daikirai.gui.onyx.render;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Platform-independent port in a fixed 48 x 48 logical coordinate space. */
final class M3WavyShape {
    static final double SIZE = 48, THICKNESS = 4, RADIUS = 22;
    static final double GAP = 1, AMPLITUDE = 1.6, WAVELENGTH = 15;
    private static final double TAU = Math.PI * 2;
    private static final double KAPPA = 0.5522848, SMOOTHNESS = 0.48;
    private double thickness = THICKNESS, radius = RADIUS;
    private MeasuredPath base = new MeasuredPath(baseCircle(RADIUS));

    boolean setThickness(double value) {
        value = Math.max(1, Math.min(8, value));
        if (thickness == value) return false;
        thickness = value; radius = SIZE / 2 - thickness / 2;
        base = new MeasuredPath(baseCircle(radius));
        wave = null; cachedAmplitude = -1;
        return true;
    }
    private MeasuredPath wave;
    private double cachedAmplitude = -1;

    void paint(Graphics2D target, double seconds, double progress, boolean indeterminate,
               double amplitudeFraction, int primary, int secondaryContainer) {
        Graphics2D g = (Graphics2D) target.create();
        try {
            g.translate(SIZE / 2, SIZE / 2);
            double span = progress;
            double rotation = 0;
            if (indeterminate) {
                double ms = (seconds * 1000) % 6000;
                rotation = 1080 * ms / 6000;
                for (int delay : new int[] {0, 1500, 3000, 4500}) {
                    rotation += 90 * standard((ms - delay) / 500);
                }
                span = 0.10 + 0.77 * (standard(ms / 3000) - standard((ms - 3000) / 3000));
                amplitudeFraction = 1;
            }
            g.rotate(Math.toRadians(rotation - 90));
            // The track is flat; only the active indicator is wavy.
            double joinGap = Math.min(GAP, thickness * .35);
            double gap = indeterminate ? joinGap : joinGap * clamp(progress / 0.01);
            arc(g, span, 1 - span, gap, 0, false, secondaryContainer);
            arc(g, 0, span, 0, AMPLITUDE * amplitudeFraction, true, primary);
        } finally {
            g.dispose();
        }
    }

    private void arc(Graphics2D g, double start, double span, double gap,
                     double amplitude, boolean active, int color) {
        if (span <= 0 || ((color >>> 24) & 255) == 0) return;
        final double capAngle = (thickness / 2) / radius;
        // Port of the >99% round-cap correction, including the 0% track seam.
        if (span >= 0.99) {
            double extra = (span - 0.99) / 0.01 * capAngle / Math.PI;
            span += extra;
            if (!active) start -= extra / 2;
        }
        double beginAngle = start * TAU + gap / radius;
        double sweepAngle = span * TAU - 2 * gap / radius;
        if (sweepAngle <= 0) return;
        g.setColor(new Color(color, true));
        boolean wavy = active && amplitude > 0;
        MeasuredPath path = wavy ? wave(amplitude) : null;
        if (sweepAngle < 2 * capAngle) {
            // Tiny progress must shrink the cap, rather than display a full-width dot.
            double ratio = sweepAngle / (2 * capAngle);
            double angle = beginAngle + sweepAngle / 2;
            Point2D.Double p = wavy
                    ? path.at(mod1(angle / TAU) * path.length / 2)
                    : new Point2D.Double(radius * Math.cos(angle), radius * Math.sin(angle));
            double capRadius = thickness / 2 * ratio;
            g.fill(new Ellipse2D.Double(p.x - capRadius, p.y - capRadius, capRadius * 2, capRadius * 2));
            return;
        }
        double trimmedStart = beginAngle + capAngle;
        double trimmedSweep = sweepAngle - 2 * capAngle;
        Shape visible;
        if (wavy) {
            visible = wavyArc(path, trimmedStart, trimmedSweep);
        } else {
            visible = new Arc2D.Double(-radius, -radius, radius * 2, radius * 2,
                    -Math.toDegrees(trimmedStart), -Math.toDegrees(trimmedSweep), Arc2D.OPEN);
        }
        g.setStroke(new BasicStroke((float) thickness, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(visible);
    }

    /** Align the wave with the track by polar angle and taper its ends onto the circle. */
    private Shape wavyArc(MeasuredPath wave, double start, double sweep) {
        Path2D.Double result = new Path2D.Double();
        int steps = Math.max(2, (int)Math.ceil(sweep / TAU * 256));
        for (int i=0;i<=steps;i++) {
            double angle = start + sweep * i / steps;
            double edgeDistance = Math.min(i, steps-i) * sweep / steps * radius;
            double taper = clamp(edgeDistance / (WAVELENGTH * .5));
            taper = taper * taper * (3 - 2 * taper);
            double r = radius + (wave.radiusAtAngle(angle) - radius) * taper;
            double x = r * Math.cos(angle), y = r * Math.sin(angle);
            if (i==0) result.moveTo(x,y); else result.lineTo(x,y);
        }
        return result;
    }

    private MeasuredPath wave(double amplitude) {
        if (wave != null && cachedAmplitude == amplitude) return wave;
        cachedAmplitude = amplitude;
        // The base consists of TWO circles, exactly as in the Android delegate.
        int cycleCount = 2 * Math.max(3, (int) (base.length / WAVELENGTH / 2));
        double wavelength = base.length / cycleCount;
        int count = cycleCount * 2;
        Point2D.Double[] anchors = new Point2D.Double[count + 1];
        Point2D.Double[] tangents = new Point2D.Double[count + 1];
        for (int i = 0; i < count; i++) {
            double distance = wavelength * i / 2;
            Point2D.Double p = base.at(distance);
            Point2D.Double t = base.tangent(distance);
            if ((i & 1) == 1) {
                p.x -= t.y * amplitude * 2;
                p.y += t.x * amplitude * 2;
            }
            anchors[i] = p;
            tangents[i] = t;
        }
        anchors[count] = anchors[0];
        tangents[count] = tangents[0];
        double handle = wavelength / 2 * SMOOTHNESS;
        Path2D.Double p = new Path2D.Double();
        p.moveTo(anchors[0].x, anchors[0].y);
        for (int i = 1; i <= count; i++) {
            Point2D.Double a = anchors[i - 1], b = anchors[i];
            Point2D.Double u = tangents[i - 1], v = tangents[i];
            p.curveTo(a.x + handle * u.x, a.y + handle * u.y,
                    b.x - handle * v.x, b.y - handle * v.y, b.x, b.y);
        }
        wave = new MeasuredPath(p);
        return wave;
    }

    private static Path2D.Double baseCircle(double radius) {
        Path2D.Double p = new Path2D.Double();
        p.moveTo(1, 0);
        for (int i = 0; i < 2; i++) {
            p.curveTo(1, KAPPA, KAPPA, 1, 0, 1);
            p.curveTo(-KAPPA, 1, -1, KAPPA, -1, 0);
            p.curveTo(-1, -KAPPA, -KAPPA, -1, 0, -1);
            p.curveTo(KAPPA, -1, 1, -KAPPA, 1, 0);
        }
        p.transform(AffineTransform.getScaleInstance(radius, radius));
        return p;
    }

    static double clamp(double x) { return Math.max(0, Math.min(1, x)); }
    private static double mod1(double x) { return x - Math.floor(x); }

    static double standard(double t) { return easing(t, 0.2, 0, 0, 1); }
    static double accelerate(double t) { return easing(t, 0.3, 0, 0.8, 0.2); }

    private static double easing(double x, double x1, double y1, double x2, double y2) {
        x = clamp(x);
        if (x == 0 || x == 1) return x;
        double low = 0, high = 1;
        // Solve x(t) first. Using input time directly as Bezier t is incorrect.
        for (int i = 0; i < 30; i++) {
            double t = (low + high) / 2;
            if (cubic(t, x1, x2) < x) low = t; else high = t;
        }
        return cubic((low + high) / 2, y1, y2);
    }

    private static double cubic(double t, double a, double b) {
        return 3 * (1 - t) * (1 - t) * t * a + 3 * (1 - t) * t * t * b + t * t * t;
    }

    /** Java2D equivalent of the small subset of Android PathMeasure used here. */
    private static final class MeasuredPath {
        final double[] x, y, distance, angles;
        final double length;

        MeasuredPath(Shape path) {
            List<Point2D.Double> points = new ArrayList<Point2D.Double>();
            PathIterator it = path.getPathIterator(null, 0.0005);
            double[] c = new double[6];
            while (!it.isDone()) {
                int type = it.currentSegment(c);
                if (type == PathIterator.SEG_MOVETO || type == PathIterator.SEG_LINETO) {
                    Point2D.Double p = new Point2D.Double(c[0], c[1]);
                    if (points.isEmpty() || points.get(points.size() - 1).distance(p) > 1e-12) points.add(p);
                }
                it.next();
            }
            x = new double[points.size()]; y = new double[x.length]; distance = new double[x.length]; angles = new double[x.length];
            for (int i = 0; i < x.length; i++) {
                x[i] = points.get(i).x; y[i] = points.get(i).y;
                double angle = Math.atan2(y[i],x[i]);
                if (i>0) {
                    while (angle < angles[i-1]-Math.PI) angle += TAU;
                    while (angle > angles[i-1]+Math.PI) angle -= TAU;
                }
                angles[i] = angle;
                if (i > 0) distance[i] = distance[i - 1] + Math.hypot(x[i] - x[i - 1], y[i] - y[i - 1]);
            }
            length = distance[distance.length - 1];
        }

        double radiusAtAngle(double angle) {
            angle = mod1(angle / TAU) * TAU;
            int at = Arrays.binarySearch(angles, angle);
            int i = Math.max(1, Math.min(angles.length-1, at>=0 ? at : -at-1));
            double span = angles[i]-angles[i-1];
            double t = span>1e-12 ? (angle-angles[i-1])/span : 0;
            return Math.hypot(x[i-1]+(x[i]-x[i-1])*t,y[i-1]+(y[i]-y[i-1])*t);
        }

        private int index(double d) {
            int i = Arrays.binarySearch(distance, Math.max(0, Math.min(length, d)));
            return Math.max(1, Math.min(distance.length - 1, i >= 0 ? i : -i - 1));
        }

        Point2D.Double at(double d) {
            d = Math.max(0, Math.min(length, d));
            int i = index(d);
            double t = (d - distance[i - 1]) / (distance[i] - distance[i - 1]);
            return new Point2D.Double(x[i - 1] + t * (x[i] - x[i - 1]), y[i - 1] + t * (y[i] - y[i - 1]));
        }

        Point2D.Double tangent(double d) {
            // Symmetric sampling avoids choosing one side of a flattened vertex.
            Point2D.Double a = at(d <= 0.005 ? length + d - 0.005 : d - 0.005);
            Point2D.Double b = at(d + 0.005 >= length ? d + 0.005 - length : d + 0.005);
            double n = a.distance(b);
            return new Point2D.Double((b.x - a.x) / n, (b.y - a.y) / n);
        }

        Path2D.Double segment(double start, double span) {
            Path2D.Double p = new Path2D.Double();
            Point2D.Double a = at(start), b = at(start + span);
            p.moveTo(a.x, a.y);
            for (int i = index(start); i < x.length && distance[i] < start + span; i++) p.lineTo(x[i], y[i]);
            p.lineTo(b.x, b.y);
            return p;
        }
    }
}
