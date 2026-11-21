package xyz.daikirai.render.furiah.module.trails;

import xyz.daikirai.render.furiah.fx.trail.GroundCache;
import xyz.daikirai.render.furiah.fx.trail.TrailShape;
import xyz.daikirai.render.furiah.render.ColorUtil;
import xyz.daikirai.render.furiah.render.Easing;
import xyz.daikirai.render.furiah.render.Vfx;

public final class GroundLayout {
   public static final int MAX = 400;
   public static final int MAX_PIECES = 48;
   public static final double[] X = new double[400];
   public static final double[] Y = new double[400];
   public static final double[] Z = new double[400];
   public static final double[] GROUND = new double[400];
   public static final double[] DIST = new double[400];
   public static final double[] SIDE_X = new double[400];
   public static final double[] SIDE_Z = new double[400];
   public static final float[] WIDTH = new float[400];
   public static final float[] ALPHA = new float[400];
   public static final float[] SHARE = new float[400];
   public static final float[] AGE = new float[400];
   public static final int[] COLOR = new int[400];
   public static final int[] PIECE = new int[400];
   public static final int[] PIECE_FROM = new int[48];
   public static final int[] PIECE_TO = new int[48];
   public static int n;
   public static int pieces;
   public static double step;
   private static final GroundCache GROUND_CACHE = new GroundCache();

   private GroundLayout() {
   }

   public static int lay(TrailStyleBase style, PlayerTrail p, TrailFrame f, int k, double head, float baseWidth, double atStep) {
      n = 0;
      pieces = 0;
      int filled = style.fill(p, f, k, baseWidth, -1.0F, 0.0F);
      if (filled < 2) {
         return 0;
      } else {
         TrailShape s = p.shape;
         int from = s.runStart[k];
         double a1 = head - s.along[from + filled - 1];
         double a0 = head - s.along[from];
         if (a1 - a0 < 0.05) {
            return 0;
         } else {
            double st;
            step = st = Math.max(atStep, (a1 - a0) / 397.0);
            float total = Math.max(0.001F, s.length);
            int j = 0;
            double a = a0;
            double next = (Math.floor(a0 / st) + 1.0) * st;

            int m;
            for (m = 0; m < 400; m++) {
               while (j < filled - 2 && head - s.along[from + j + 1] < a) {
                  j++;
               }

               double ak = head - s.along[from + j + 1];
               double aj = head - s.along[from + j];
               float t = ak - aj > 1.0E-9 ? Easing.clamp01((a - aj) / (ak - aj)) : 0.0F;
               int i1 = j + 1;
               X[m] = Vfx.X[j] + (Vfx.X[i1] - Vfx.X[j]) * t;
               Y[m] = Vfx.Y[j] + (Vfx.Y[i1] - Vfx.Y[j]) * t;
               Z[m] = Vfx.Z[j] + (Vfx.Z[i1] - Vfx.Z[j]) * t;
               WIDTH[m] = Vfx.W[j] + (Vfx.W[i1] - Vfx.W[j]) * t;
               ALPHA[m] = TrailStyleBase.POINT_ALPHA[j] + (TrailStyleBase.POINT_ALPHA[i1] - TrailStyleBase.POINT_ALPHA[j]) * t;
               COLOR[m] = ColorUtil.lerp(Vfx.C[j] | 0xFF000000, Vfx.C[i1] | 0xFF000000, t);
               AGE[m] = s.age[from + j] + (s.age[from + i1] - s.age[from + j]) * t;
               DIST[m] = a;
               SHARE[m] = Math.min(1.0F, (float)((head - a) / total));
               if (a >= a1) {
                  break;
               }

               if (next < a1 - st * 0.25) {
                  a = next;
                  next += st;
               } else {
                  a = a1;
               }
            }

            n = m;

            for (int q = 0; q < m; q++) {
               double wx = X[q] + f.viewX;
               double feet = Y[q] + f.viewY - 0.06;
               double wz = Z[q] + f.viewZ;
               double g = GROUND_CACHE.surface(f.world, wx, feet, wz, 0.6, 3.0);
               GROUND[q] = Double.isNaN(g) ? Double.NaN : g - f.viewY;
               int qa = Math.max(0, q - 1);
               int qb = Math.min(m - 1, q + 1);
               double tx = X[qb] - X[qa];
               double tz;
               double tl = Math.sqrt(tx * tx + (tz = Z[qb] - Z[qa]) * tz);
               if (tl > 1.0E-6) {
                  SIDE_X[q] = -tz / tl;
                  SIDE_Z[q] = tx / tl;
               } else {
                  SIDE_X[q] = q > 0 ? SIDE_X[q - 1] : 1.0;
                  SIDE_Z[q] = q > 0 ? SIDE_Z[q - 1] : 0.0;
               }

               PIECE[q] = -1;
            }

            int b0 = 0;

            while (b0 < m && pieces < 48) {
               while (b0 < m && Double.isNaN(GROUND[b0])) {
                  b0++;
               }

               int b1 = b0 + 1;

               while (b1 < m && !Double.isNaN(GROUND[b1]) && Math.abs(GROUND[b1] - GROUND[b1 - 1]) <= 1.05) {
                  b1++;
               }

               if (b1 - b0 >= 2 && b1 <= m) {
                  for (int q = b0; q < b1; q++) {
                     PIECE[q] = pieces;
                  }

                  PIECE_FROM[pieces] = b0;
                  PIECE_TO[pieces] = b1;
                  pieces++;
               }

               b0 = b1;
            }

            return m;
         }
      }
   }

   public static int indexAt(double d) {
      if (n < 2 || d <= DIST[0]) {
         return 0;
      } else if (d >= DIST[n - 1]) {
         return n - 2;
      } else {
         int i = d < DIST[1] ? 0 : 1 + (int)((d - DIST[1]) / step);
         i = Math.max(0, Math.min(n - 2, i));

         while (i > 0 && DIST[i] > d) {
            i--;
         }

         while (i < n - 2 && DIST[i + 1] <= d) {
            i++;
         }

         return i;
      }
   }
}
