package xyz.daikirai.gui.onyx.material;

/**
 * Material You colour engine, ported from OpenOnyx's {@code client.onyx.theme} package
 * (HCT / CAM16 + tonal palettes + ARGB helpers).
 *
 * <p>Everything here is pure math and stays independent from Minecraft, which keeps the
 * Material palette identical to the one the Onyx ClickGUI generated at runtime.</p>
 */
public final class Material {

    /** D65 white point, as used by the original implementation. */
    public static final double[] WHITE_POINT_D65 = {95.047, 100.0, 108.883};

    /** sRGB (0..100) to linear RGB (0..100). */
    private static final double[][] SRGB_TO_LINEAR = {
            {0.41233895, 0.35762064, 0.18051042},
            {0.2126, 0.7152, 0.0722},
            {0.01932141, 0.11916382, 0.95034478}
    };

    /** Linear RGB (0..100) to sRGB (0..100). */
    private static final double[][] LINEAR_TO_SRGB = {
            {3.2413774792388685, -1.5376652402851851, -0.49885366846268053},
            {-0.9691452513005321, 1.8758853451067872, 0.04156585616912061},
            {0.05562093689691305, -0.20395524564742123, 1.0571799111220335}
    };

    private Material() {
    }

    // ------------------------------------------------------------------ //
    // ARGB helpers
    // ------------------------------------------------------------------ //

    public static int alpha(int argb) {
        return argb >>> 24;
    }

    public static int red(int argb) {
        return argb >> 16 & 0xFF;
    }

    public static int green(int argb) {
        return argb >> 8 & 0xFF;
    }

    public static int blue(int argb) {
        return argb & 0xFF;
    }

    public static int argb(int a, int r, int g, int b) {
        return (a & 0xFF) << 24 | (r & 0xFF) << 16 | (g & 0xFF) << 8 | b & 0xFF;
    }

    public static int rgb(int r, int g, int b) {
        return argb(255, r, g, b);
    }

    /** {@code #RRGGBB} form of a colour, handy for logs and tooltips. */
    public static String hex(int argb) {
        return String.format("#%06X", argb & 0xFFFFFF);
    }

    public static int withAlpha(int argb, float alpha) {
        return argb & 0xFFFFFF | clamp(Math.round(alpha * 255.0F), 0, 255) << 24;
    }

    public static int withAlpha(int argb, int alpha) {
        return argb & 0xFFFFFF | clamp(alpha, 0, 255) << 24;
    }

    public static int scaleAlpha(int argb, float factor) {
        return withAlpha(argb, Math.round(alpha(argb) * clamp(factor, 0.0F, 1.0F)));
    }

    /** Linear-RGB blend of two colours, matching {@code misc.Cls2.getIntForInt}. */
    public static int mix(int from, int to, float t) {
        t = clamp(t, 0.0F, 1.0F);
        double[] a = linearRgb(from);
        double[] b = linearRgb(to);
        int r = srgbChannel(a[0] + (b[0] - a[0]) * t);
        int g = srgbChannel(a[1] + (b[1] - a[1]) * t);
        int bl = srgbChannel(a[2] + (b[2] - a[2]) * t);
        int al = Math.round(alpha(from) + (alpha(to) - alpha(from)) * t);
        return argb(al, r, g, bl);
    }

    // ------------------------------------------------------------------ //
    // Numeric helpers
    // ------------------------------------------------------------------ //

    public static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    public static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    public static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    public static long clamp(long v, long lo, long hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    public static double sanitizeDegrees(double degrees) {
        double d = degrees % 360.0;
        return d < 0.0 ? d + 360.0 : d;
    }

    /** sRGB channel byte to linear RGB (0..100). */
    public static double linearize(int channel) {
        double v = channel / 255.0;
        return v <= 0.040449936 ? v / 12.92 * 100.0 : Math.pow((v + 0.055) / 1.055, 2.4) * 100.0;
    }

    /** Linear RGB (0..100) back to an sRGB channel byte. */
    public static int srgbChannel(double linear) {
        double v = linear / 100.0;
        double srgb = v <= 0.0031308 ? v * 12.92 : 1.055 * Math.pow(v, 0.4166666666666667) - 0.055;
        return clamp((int) Math.round(srgb * 255.0), 0, 255);
    }

    /** ARGB to linear RGB (0..100), matching {@code theme.impl.Util2.getDoubleArrayForInt}. */
    public static double[] linearRgb(int argb) {
        double r = linearize(red(argb));
        double g = linearize(green(argb));
        double b = linearize(blue(argb));
        return new double[]{
                SRGB_TO_LINEAR[0][0] * r + SRGB_TO_LINEAR[0][1] * g + SRGB_TO_LINEAR[0][2] * b,
                SRGB_TO_LINEAR[1][0] * r + SRGB_TO_LINEAR[1][1] * g + SRGB_TO_LINEAR[1][2] * b,
                SRGB_TO_LINEAR[2][0] * r + SRGB_TO_LINEAR[2][1] * g + SRGB_TO_LINEAR[2][2] * b
        };
    }

    /** Linear RGB (0..100) to ARGB. */
    public static int fromLinearRgb(double r, double g, double b) {
        return argb(255,
                srgbChannel(LINEAR_TO_SRGB[0][0] * r + LINEAR_TO_SRGB[0][1] * g + LINEAR_TO_SRGB[0][2] * b),
                srgbChannel(LINEAR_TO_SRGB[1][0] * r + LINEAR_TO_SRGB[1][1] * g + LINEAR_TO_SRGB[1][2] * b),
                srgbChannel(LINEAR_TO_SRGB[2][0] * r + LINEAR_TO_SRGB[2][1] * g + LINEAR_TO_SRGB[2][2] * b));
    }

    /** L* of a Y value (0..100). */
    public static double lstarFromY(double y) {
        double yn = y / 100.0;
        return yn > 0.008856451679035631 ? Math.cbrt(yn) * 116.0 - 16.0 : 903.2962962962963 * yn;
    }

    /** Y (0..100) of an L* value. */
    public static double yFromLstar(double lstar) {
        double unit = lstar / 100.0;
        double y = unit * unit * unit;
        return 100.0 * (y > 0.008856451679035631 ? y : (116.0 * unit - 16.0) / 903.2962962962963);
    }

    /** L* of an ARGB colour. */
    public static double lstarFromArgb(int argb) {
        double y = 0.2126 * linearize(red(argb)) + 0.7152 * linearize(green(argb)) + 0.0722 * linearize(blue(argb));
        return lstarFromY(y);
    }

    /** Neutral grey with the given L*. */
    public static int argbFromLstar(double lstar) {
        int v = srgbChannel(yFromLstar(lstar));
        return argb(255, v, v, v);
    }

    // ------------------------------------------------------------------ //
    // HSV helpers (the accent seed is read through these)
    // ------------------------------------------------------------------ //

    /** Returns {hue 0..360, saturation 0..1, value 0..1}. */
    public static float[] hsv(int argb) {
        float r = red(argb) / 255.0F;
        float g = green(argb) / 255.0F;
        float b = blue(argb) / 255.0F;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float delta = max - min;
        float hue = 0.0F;
        if (delta > 0.0F) {
            if (max == r) {
                hue = 60.0F * ((g - b) / delta % 6.0F);
            } else if (max == g) {
                hue = 60.0F * ((b - r) / delta + 2.0F);
            } else {
                hue = 60.0F * ((r - g) / delta + 4.0F);
            }
        }
        float saturation = max == 0.0F ? 0.0F : delta / max;
        return new float[]{(float) sanitizeDegrees(hue), saturation, max};
    }

    /** HSV (hue degrees, 0..1 saturation/value) to ARGB. */
    public static int fromHsv(float hue, float saturation, float value, int alpha) {
        float h = (float) sanitizeDegrees(hue) / 60.0F;
        float chroma = value * clamp(saturation, 0.0F, 1.0F);
        float x = chroma * (1.0F - Math.abs(h % 2.0F - 1.0F));
        float m = value - chroma;
        float r;
        float g;
        float b;
        switch ((int) h % 6) {
            case 0:
                r = chroma;
                g = x;
                b = 0.0F;
                break;
            case 1:
                r = x;
                g = chroma;
                b = 0.0F;
                break;
            case 2:
                r = 0.0F;
                g = chroma;
                b = x;
                break;
            case 3:
                r = 0.0F;
                g = x;
                b = chroma;
                break;
            case 4:
                r = x;
                g = 0.0F;
                b = chroma;
                break;
            default:
                r = chroma;
                g = 0.0F;
                b = x;
                break;
        }
        return argb(alpha,
                Math.round((r + m) * 255.0F),
                Math.round((g + m) * 255.0F),
                Math.round((b + m) * 255.0F));
    }

    // ------------------------------------------------------------------ //
    // Viewing conditions (CAM16)
    // ------------------------------------------------------------------ //

    /** Port of {@code theme.impl.Util}. */
    public static final class ViewingConditions {
        /** "n" (chroma scaling). */
        public final double n;
        /** "aw" (achromatic response of white). */
        public final double aw;
        /** "nbb". */
        public final double nbb;
        /** "c" (surround impact). */
        public final double c;
        /** "nc" (chroma induction). */
        public final double nc;
        /** "fl" (luminance level adaptation factor). */
        public final double fl;
        /** pow(fl, 0.25). */
        public final double flRoot;
        /** "z" (base exponential nonlinearity). */
        public final double z;
        public final double[] rgbD;

        public static final ViewingConditions DEFAULT = create(
                WHITE_POINT_D65,
                63.66197723675813 * yFromLstar(50.0) / 100.0,
                50.0,
                2.0,
                false);

        private ViewingConditions(double n, double aw, double nbb, double c, double nc,
                                  double fl, double flRoot, double z, double[] rgbD) {
            this.n = n;
            this.aw = aw;
            this.nbb = nbb;
            this.c = c;
            this.nc = nc;
            this.fl = fl;
            this.flRoot = flRoot;
            this.z = z;
            this.rgbD = rgbD;
        }

        public static ViewingConditions create(double[] whitePoint, double adaptingLuminance,
                                               double backgroundLstar, double surround,
                                               boolean discountingIlluminant) {
            double rW = whitePoint[0] * 0.401288 + whitePoint[1] * 0.650173 + whitePoint[2] * -0.051461;
            double gW = whitePoint[0] * -0.250268 + whitePoint[1] * 1.204414 + whitePoint[2] * 0.045854;
            double bW = whitePoint[0] * -0.002079 + whitePoint[1] * 0.048952 + whitePoint[2] * 0.953127;

            double f = 0.8 + surround / 10.0;
            double c = f >= 0.9
                    ? lerp(0.59, 0.69, (f - 0.9) * 10.0)
                    : lerp(0.525, 0.59, (f - 0.8) * 10.0);

            double d = discountingIlluminant
                    ? 1.0
                    : f * (1.0 - 0.2777777777777778 * Math.exp((-adaptingLuminance - 42.0) / 92.0));
            d = clamp(d, 0.0, 1.0);

            double[] rgbD = {
                    d * (100.0 / rW) + 1.0 - d,
                    d * (100.0 / gW) + 1.0 - d,
                    d * (100.0 / bW) + 1.0 - d
            };

            double k = 1.0 / (5.0 * adaptingLuminance + 1.0);
            double k4 = k * k * k * k;
            double fl = k4 * adaptingLuminance + 0.1 * (1.0 - k4) * (1.0 - k4) * Math.cbrt(5.0 * adaptingLuminance);

            double n = yFromLstar(backgroundLstar) / whitePoint[1];
            double z = 1.48 + Math.sqrt(n);
            double nbb = 0.725 / Math.pow(n, 0.2);

            double[] rgbAFactors = {
                    Math.pow(fl * rgbD[0] * rW / 100.0, 0.42),
                    Math.pow(fl * rgbD[1] * gW / 100.0, 0.42),
                    Math.pow(fl * rgbD[2] * bW / 100.0, 0.42)
            };
            double[] rgbA = {
                    400.0 * rgbAFactors[0] / (rgbAFactors[0] + 27.13),
                    400.0 * rgbAFactors[1] / (rgbAFactors[1] + 27.13),
                    400.0 * rgbAFactors[2] / (rgbAFactors[2] + 27.13)
            };
            double aw = (2.0 * rgbA[0] + rgbA[1] + 0.05 * rgbA[2]) * nbb;

            return new ViewingConditions(n, aw, nbb, c, f, fl, Math.pow(fl, 0.25), z, rgbD);
        }

        private static double lerp(double start, double stop, double amount) {
            return (1.0 - amount) * start + amount * stop;
        }
    }

    // ------------------------------------------------------------------ //
    // HCT (CAM16 hue / chroma + L* tone)
    // ------------------------------------------------------------------ //

    /** Port of {@code theme.impl.Cls3}. */
    public static final class Hct {
        public final double hue;
        public final double chroma;
        public final double tone;
        private final double t;
        private final double alphaC;
        private final double alphaS;

        private Hct(double hue, double chroma, double tone, double t, double alphaC, double alphaS) {
            this.hue = hue;
            this.chroma = chroma;
            this.tone = tone;
            this.t = t;
            this.alphaC = alphaC;
            this.alphaS = alphaS;
        }

        /** Creates a point in HCT space for a given tone / chroma / hue (a CAM16 guess). */
        public static Hct of(double tone, double chroma, double hue) {
            ViewingConditions vc = ViewingConditions.DEFAULT;
            double alpha = chroma;
            double t = 1.7 * tone / (1.0 + 0.007 * tone);
            double scaled = 43.859649122807014 * Math.log1p(0.0228 * alpha);
            double hueRad = Math.toRadians(hue);
            return new Hct(hue, chroma, tone, t, scaled * Math.cos(hueRad), scaled * Math.sin(hueRad));
        }

        public static Hct fromInt(int argb) {
            return fromLinearRgb(linearRgb(argb), ViewingConditions.DEFAULT);
        }

        /** Port of {@code Cls3.getCls3ForInt}. */
        public static Hct fromLinearRgb(double[] rgb, ViewingConditions vc) {
            double r = rgb[0];
            double g = rgb[1];
            double b = rgb[2];

            double x = 0.401288 * r + 0.650173 * g - 0.051461 * b;
            double y = -0.250268 * r + 1.204414 * g + 0.045854 * b;
            double z = -0.002079 * r + 0.048952 * g + 0.953127 * b;

            double rA = connResponse(vc.rgbD[0] * x, vc.fl);
            double gA = connResponse(vc.rgbD[1] * y, vc.fl);
            double bA = connResponse(vc.rgbD[2] * z, vc.fl);

            x = (11.0 * rA - 12.0 * gA + bA) / 11.0;
            y = (rA + gA - 2.0 * bA) / 9.0;
            double zz = (20.0 * rA + 20.0 * gA + 21.0 * bA) / 20.0;
            double p2 = (40.0 * rA + 20.0 * gA + bA) / 20.0;

            double hue = sanitizeDegrees(Math.toDegrees(Math.atan2(y, x)));
            double hueRad = Math.toRadians(hue);

            p2 *= vc.nbb;
            double tone = 100.0 * Math.pow(p2 / vc.aw, vc.c * vc.z);

            double huePrime = hue < 20.14 ? hue + 360.0 : hue;
            double eHue = 0.25 * (Math.cos(Math.toRadians(huePrime) + 2.0) + 3.8);
            double raw = Math.pow(3846.153846153846 * eHue * vc.nc * vc.nbb * Math.hypot(x, y) / (zz + 0.305), 0.9)
                    * Math.pow(1.64 - Math.pow(0.29, vc.n), 0.73)
                    * Math.sqrt(tone / 100.0);
            double chroma = raw;

            double t = 1.7 * tone / (1.0 + 0.007 * tone);
            double alpha = 43.859649122807014 * Math.log1p(0.0228 * chroma);
            return new Hct(hue, chroma, tone, t, alpha * Math.cos(hueRad), alpha * Math.sin(hueRad));
        }

        /** HCT to ARGB, solving for the most chromatic in-gamut colour at that tone. */
        public static int toInt(double hue, double chroma, double tone) {
            if (chroma < 1.0 || Math.round(tone) <= 0L || Math.round(tone) >= 100L) {
                return argbFromLstar(tone);
            }
            hue = sanitizeDegrees(hue);

            double lo = 0.0;
            double hi = chroma;
            double current = chroma;
            boolean first = true;
            Hct answer = null;

            while (Math.abs(lo - hi) >= 0.4) {
                Hct candidate = solve(hue, current, tone);
                if (first) {
                    if (candidate != null) {
                        return candidate.toInt();
                    }
                    first = false;
                    current = lo + (hi - lo) / 2.0;
                } else {
                    if (candidate == null) {
                        hi = current;
                    } else {
                        answer = candidate;
                        lo = current;
                    }
                    current = lo + (hi - lo) / 2.0;
                }
            }
            return answer == null ? argbFromLstar(tone) : answer.toInt();
        }

        /**
         * Port of {@code Cls.getCls3ForDouble}: searches for a colour of the requested hue and
         * chroma whose actual L* matches {@code tone}, and rejects candidates that drift too far
         * from the CAM16 estimate (i.e. that fell out of gamut).
         */
        private static Hct solve(double hue, double chroma, double tone) {
            double lo = 0.0;
            double hi = 100.0;
            double best = 1000.0;
            Hct answer = null;

            while (Math.abs(lo - hi) > 0.01) {
                double mid = lo + (hi - lo) / 2.0;
                int argb = of(mid, chroma, hue).toInt();
                double lstar = lstarFromArgb(argb);
                if (Math.abs(tone - lstar) < 0.2) {
                    Hct found = fromInt(argb);
                    double delta = found.distanceTo(of(found.tone, chroma, hue));
                    if (delta <= 1.0 && delta <= best) {
                        best = delta;
                        answer = found;
                    }
                }
                if (lstar < tone) {
                    lo = mid;
                } else {
                    hi = mid;
                }
            }
            return answer;
        }

        private double distanceTo(Hct other) {
            double dt = t - other.t;
            double dc = alphaC - other.alphaC;
            double ds = alphaS - other.alphaS;
            return 1.41 * Math.pow(Math.sqrt(dt * dt + dc * dc + ds * ds), 0.63);
        }

        public int toInt() {
            return toInt(ViewingConditions.DEFAULT);
        }

        /** Port of {@code Cls3.getInt2}. */
        public int toInt(ViewingConditions vc) {
            double a = chroma != 0.0 && tone != 0.0 ? chroma / Math.sqrt(tone / 100.0) : 0.0;
            a = Math.pow(a / Math.pow(1.64 - Math.pow(0.29, vc.n), 0.73), 1.1111111111111112);

            double hueRad = Math.toRadians(hue);
            double eHue = 0.25 * (Math.cos(hueRad + 2.0) + 3.8);
            double awScaled = vc.aw * Math.pow(tone / 100.0, 1.0 / vc.c / vc.z);
            eHue = eHue * 3846.153846153846 * vc.nc * vc.nbb;
            awScaled /= vc.nbb;

            double sin = Math.sin(hueRad);
            double cos = Math.cos(hueRad);
            double coef = 23.0 * (awScaled + 0.305) * a / (23.0 * eHue + 11.0 * a * cos + 108.0 * a * sin);
            double rA = coef * cos;
            double gA = coef * sin;

            double r = (460.0 * awScaled + 451.0 * rA + 288.0 * gA) / 1403.0;
            double g = (460.0 * awScaled - 891.0 * rA - 261.0 * gA) / 1403.0;
            double b = (460.0 * awScaled - 220.0 * rA - 6300.0 * gA) / 1403.0;

            r = inverseConn(r, vc.fl) / vc.rgbD[0];
            g = inverseConn(g, vc.fl) / vc.rgbD[1];
            b = inverseConn(b, vc.fl) / vc.rgbD[2];

            double x = 1.86206786 * r - 1.01125463 * g + 0.14918677 * b;
            double y = 0.38752654 * r + 0.62144744 * g - 0.00897398 * b;
            double z = -0.0158415 * r - 0.03412294 * g + 1.04996444 * b;
            return Material.fromLinearRgb(x, y, z);
        }

        /** Linear RGB to CAM16 cone response. */
        private static double connResponse(double value, double fl) {
            double v = Math.pow(fl * Math.abs(value) / 100.0, 0.42);
            return Math.signum(value) * 400.0 * v / (v + 27.13);
        }

        /** CAM16 cone response back to linear RGB. */
        private static double inverseConn(double value, double fl) {
            double v = Math.max(0.0, 27.13 * Math.abs(value) / (400.0 - Math.abs(value)));
            return Math.signum(value) * (100.0 / fl) * Math.pow(v, 2.380952380952381);
        }
    }

    // ------------------------------------------------------------------ //
    // Tonal palette
    // ------------------------------------------------------------------ //

    /** Port of {@code theme.impl.Cls2}: a hue/chroma pair sampled at any tone. */
    public static final class TonalPalette {
        public final double hue;
        public final double chroma;

        private TonalPalette(double hue, double chroma) {
            this.hue = hue;
            this.chroma = chroma;
        }

        public static TonalPalette of(double hue, double chroma) {
            return new TonalPalette(hue, chroma);
        }

        public int tone(double tone) {
            return Hct.toInt(hue, chroma, clamp(tone, 0.0, 100.0));
        }

        /** Samples the palette while shrinking the tone by a percentage (Onyx's neutral cut). */
        public int tone(double tone, double cut) {
            return tone(tone - cut * tone / 100.0);
        }
    }
}
