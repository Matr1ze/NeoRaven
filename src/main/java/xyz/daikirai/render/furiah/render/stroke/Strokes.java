package xyz.daikirai.render.furiah.render.stroke;

import xyz.daikirai.render.furiah.render.Camera;
import xyz.daikirai.render.furiah.render.Vfx;

public final class Strokes {
   public static final int MAX_POINTS = 512;
   public static final double[] X = new double[512];
   public static final double[] Y = new double[512];
   public static final double[] Z = new double[512];
   public static final float[] A = new float[512];
   public static final int[] PC = new int[512];
   public static final double[] NX = new double[512];
   public static final double[] NY = new double[512];
   public static final double[] NZ = new double[512];
   private static final double[] PX = new double[512];
   private static final double[] PY = new double[512];
   private static final double[] PZ = new double[512];
   private static final float[] PA = new float[512];
   private static final int[] PPC = new int[512];
   private static final double[] PNX = new double[512];
   private static final double[] PNY = new double[512];
   private static final double[] PNZ = new double[512];
   private static final double[] LEN = new double[512];
   private static final double[] LX = new double[512];
   private static final double[] LY = new double[512];
   private static final double[] LZ = new double[512];
   private static final double[] RX = new double[512];
   private static final double[] RY = new double[512];
   private static final double[] RZ = new double[512];
   private static final float[] U = new float[512];
   private static final int[] COL = new int[512];

   private Strokes() {
   }

   private static int texture(Strokes.Tool tool) {
      switch (tool) {
         case PENCIL:
            return StrokeTextures.PENCIL;
         case MARKER:
            return StrokeTextures.MARKER;
         case CHALK:
            return StrokeTextures.CHALK;
         default:
            return StrokeTextures.BRUSH;
      }
   }

   public static void wobble(int n, double amount, int seed, double nx, double ny, double nz) {
      if (n >= 2 && !(amount <= 0.0)) {
         lengths(n);
         double total = LEN[n - 1];
         if (!(total < 1.0E-9)) {
            double prevSx = 0.0;
            double prevSy = 0.0;
            double prevSz = 0.0;
            int i = 0;

            while (i < n) {
               int a = Math.max(0, i - 1);
               int b = Math.min(n - 1, i + 1);
               double ty = Y[b] - Y[a];
               double tz;
               double sx = ty * nz - (tz = Z[b] - Z[a]) * ny;
               double sz;
               double tx;
               double sy;
               double l = Math.sqrt(sx * sx + (sy = tz * nx - (tx = X[b] - X[a]) * nz) * sy + (sz = tx * ny - ty * nx) * sz);
               if (l < 1.0E-9) {
                  sx = prevSx;
                  sy = prevSy;
                  sz = prevSz;
               } else {
                  sx /= l;
                  sy /= l;
                  sz /= l;
               }

               prevSx = sx;
               prevSy = sy;
               prevSz = sz;
               double s = LEN[i] / Math.max(0.2, total) * 3.0;
               double off = (noise(s + seed * 7.13) - 0.5) * 1.4 + (noise(s * 3.1 + seed * 3.7 + 11.0) - 0.5) * 0.6;
               X[i] = X[i] + sx * off * amount;
               Y[i] = Y[i] + sy * off * amount;
               int n4 = i++;
               Z[n4] = Z[n4] + sz * off * amount;
            }
         }
      }
   }

   public static void wobbleAt(int n, double amount, int seed, double nx, double ny, double nz, double[] at) {
      if (n >= 2 && !(amount <= 0.0)) {
         double prevSx = 0.0;
         double prevSy = 0.0;
         double prevSz = 0.0;
         int i = 0;

         while (i < n) {
            int a = Math.max(0, i - 1);
            int b = Math.min(n - 1, i + 1);
            double ty = Y[b] - Y[a];
            double tz;
            double sx = ty * nz - (tz = Z[b] - Z[a]) * ny;
            double sz;
            double tx;
            double sy;
            double l = Math.sqrt(sx * sx + (sy = tz * nx - (tx = X[b] - X[a]) * nz) * sy + (sz = tx * ny - ty * nx) * sz);
            if (l < 1.0E-9) {
               sx = prevSx;
               sy = prevSy;
               sz = prevSz;
            } else {
               sx /= l;
               sy /= l;
               sz /= l;
            }

            prevSx = sx;
            prevSy = sy;
            prevSz = sz;
            double s = at[i] * 0.65;
            double off = (noise(s + seed * 7.13) - 0.5) * 1.4 + (noise(s * 3.1 + seed * 3.7 + 11.0) - 0.5) * 0.6;
            X[i] = X[i] + sx * off * amount;
            Y[i] = Y[i] + sy * off * amount;
            int n4 = i++;
            Z[n4] = Z[n4] + sz * off * amount;
         }
      }
   }

   public static void draw(int n, Strokes.Style s) {
      if (n >= 2 && !(s.alpha <= 0.003F) && !(s.progress <= 0.0F) && !(s.erase >= 1.0F) && !(s.diffuse >= 1.0F)) {
         StrokeTextures.ensureLoaded();
         n = Math.min(n, 512);
         lengths(n);
         double total = LEN[n - 1];
         if (!(total < 1.0E-6)) {
            System.arraycopy(X, 0, PX, 0, n);
            System.arraycopy(Y, 0, PY, 0, n);
            System.arraycopy(Z, 0, PZ, 0, n);
            if (s.pointAlpha) {
               System.arraycopy(A, 0, PA, 0, n);
            }

            if (s.pointColor) {
               System.arraycopy(PC, 0, PPC, 0, n);
            }

            if (s.pointNormal) {
               System.arraycopy(NX, 0, PNX, 0, n);
               System.arraycopy(NY, 0, PNY, 0, n);
               System.arraycopy(NZ, 0, PNZ, 0, n);
            }

            double shown = total * Math.min(1.0F, s.progress);
            int last = n - 1;
            if (s.progress < 1.0F) {
               int k = 1;

               while (k < n - 1 && LEN[k] < shown) {
                  k++;
               }

               double seg = LEN[k] - LEN[k - 1];
               double f = seg < 1.0E-9 ? 1.0 : (shown - LEN[k - 1]) / seg;
               PX[k] = PX[k - 1] + (PX[k] - PX[k - 1]) * f;
               PY[k] = PY[k - 1] + (PY[k] - PY[k - 1]) * f;
               PZ[k] = PZ[k - 1] + (PZ[k] - PZ[k - 1]) * f;
               if (s.pointAlpha) {
                  PA[k] = PA[k - 1] + (PA[k] - PA[k - 1]) * (float)f;
               }

               last = k;
            }

            LEN[last] = Math.min(LEN[last], shown);
            float d = s.diffuse;
            if (d > 0.0F) {
               float spread = d * (1.0F - d * 0.35F);
               ribbon(
                  last,
                  s,
                  total,
                  1.0 + 2.4 * spread,
                  s.alpha * 0.62F * (float)Math.sin(Math.PI * Math.min(1.0F, d * 1.15F)) * (1.0F - d),
                  StrokeTextures.BRUSH_SOFT,
                  true
               );
            }

            float crisp = d > 0.0F ? (float)Math.pow(1.0F - d, 1.6) : 1.0F;
            ribbon(last, s, total, 1.0 + 0.45 * d, s.alpha * crisp, texture(s.tool), false);
         }
      }
   }

   private static void ribbon(int last, Strokes.Style s, double total, double grow, float alpha, int texture, boolean soft) {
      if (!(alpha <= 0.003F)) {
         Camera cam = Vfx.camera();
         boolean brush = s.tool == Strokes.Tool.BRUSH || soft;
         double eMin = Double.MAX_VALUE;
         double eMax = -Double.MAX_VALUE;
         if (s.erase > 0.0F) {
            for (int i2 = 0; i2 <= last; i2++) {
               double p = PX[i2] * s.eraseX + PY[i2] * s.eraseY + PZ[i2] * s.eraseZ;
               eMin = Math.min(eMin, p);
               eMax = Math.max(eMax, p);
            }
         }

         int a0 = s.color >>> 24;
         double dryPow = Math.pow(2.0, (0.5 - Math.max(0.0F, Math.min(1.0F, s.dryness))) * 2.0);
         float lr = (s.color >> 16 & 0xFF) * s.light;
         float lg = (s.color >> 8 & 0xFF) * s.light;
         float lb = (s.color & 0xFF) * s.light;
         int rgb = Math.min(255, Math.round(lr)) << 16 | Math.min(255, Math.round(lg)) << 8 | Math.min(255, Math.round(lb));
         double lastSx = 0.0;
         double lastSy = 0.0;
         double lastSz = 0.0;

         for (int i = 0; i <= last; i++) {
            double at = LEN[i] / total;
            double press = (0.55 + 0.45 * smooth(0.0, 0.05, at))
               * (1.0 + 0.28 * s.heavyStart * Math.exp(-((at - 0.06) / 0.06) * ((at - 0.06) / 0.06)))
               * (1.0 - s.thinEnd * smooth(0.35, 1.0, at));
            if (!brush) {
               press = 0.75 + 0.25 * smooth(0.0, 0.04, at) * (1.0 - smooth(0.96, 1.0, at));
            }

            double hw = s.width * 0.5 * grow * Math.max(0.05, press);
            int ia = Math.min(last, i + 1);
            int ib = Math.max(0, i - 1);
            double tx = PX[ia] - PX[ib];
            double ty = PY[ia] - PY[ib];
            double tz = PZ[ia] - PZ[ib];
            double nz;
            double ny;
            double nx;
            if (s.flat && s.pointNormal) {
               nx = PNX[i];
               ny = PNY[i];
               nz = PNZ[i];
            } else if (s.flat) {
               nx = s.normalX;
               ny = s.normalY;
               nz = s.normalZ;
            } else {
               nx = cam.posX - PX[i];
               ny = cam.posY - PY[i];
               nz = cam.posZ - PZ[i];
            }

            double sx = ty * nz - tz * ny;
            double sy = tz * nx - tx * nz;
            double sz = tx * ny - ty * nx;
            double l = Math.sqrt(sx * sx + sy * sy + sz * sz);
            if (l < 1.0E-9) {
               sx = lastSx;
               sy = lastSy;
               sz = lastSz;
            } else {
               sx /= l;
               sy /= l;
               sz /= l;
               if (i > 0 && sx * lastSx + sy * lastSy + sz * lastSz < 0.0) {
                  sx = -sx;
                  sy = -sy;
                  sz = -sz;
               }
            }

            lastSx = sx;
            lastSy = sy;
            lastSz = sz;
            LX[i] = PX[i] - sx * hw;
            LY[i] = PY[i] - sy * hw;
            LZ[i] = PZ[i] - sz * hw;
            RX[i] = PX[i] + sx * hw;
            RY[i] = PY[i] + sy * hw;
            RZ[i] = PZ[i] + sz * hw;
            U[i] = brush ? (float)Math.pow(at, dryPow) : (float)(LEN[i] / Math.max(0.05, s.tile));
            float k = alpha;
            if (brush) {
               k = alpha * (float)(1.0 - (0.15 + 0.3 * s.dryness) * smooth(0.6, 1.0, at));
            }

            if (i == last && s.progress < 1.0F) {
               k *= 0.6F;
            }

            if (s.pointAlpha) {
               k *= PA[i];
            }

            if (s.erase > 0.0F && eMax > eMin) {
               double f = (PX[i] * s.eraseX + PY[i] * s.eraseY + PZ[i] * s.eraseZ - eMin) / (eMax - eMin);
               k *= (float)smooth(s.erase - 0.06, s.erase + 0.06, f);
            }

            if (s.pointColor) {
               int pc = PPC[i];
               int pr = Math.min(255, Math.round((pc >> 16 & 0xFF) * s.light));
               int pg = Math.min(255, Math.round((pc >> 8 & 0xFF) * s.light));
               int pb = Math.min(255, Math.round((pc & 0xFF) * s.light));
               int pa = (pc >>> 24) * a0 / 255;
               COL[i] = Math.min(255, Math.round(pa * Math.max(0.0F, Math.min(1.0F, k)))) << 24 | pr << 16 | pg << 8 | pb;
            } else {
               COL[i] = Math.min(255, Math.round(a0 * Math.max(0.0F, Math.min(1.0F, k)))) << 24 | rgb;
            }
         }

         for (int var63 = 0; var63 < last; var63++) {
            Vfx.quadUV(
               texture,
               LX[var63],
               LY[var63],
               LZ[var63],
               U[var63],
               0.0,
               COL[var63],
               RX[var63],
               RY[var63],
               RZ[var63],
               U[var63],
               1.0,
               COL[var63],
               RX[var63 + 1],
               RY[var63 + 1],
               RZ[var63 + 1],
               U[var63 + 1],
               1.0,
               COL[var63 + 1],
               LX[var63 + 1],
               LY[var63 + 1],
               LZ[var63 + 1],
               U[var63 + 1],
               0.0,
               COL[var63 + 1]
            );
         }
      }
   }

   private static void lengths(int n) {
      LEN[0] = 0.0;

      for (int i = 1; i < n; i++) {
         double dx = X[i] - X[i - 1];
         double dy = Y[i] - Y[i - 1];
         double dz = Z[i] - Z[i - 1];
         LEN[i] = LEN[i - 1] + Math.sqrt(dx * dx + dy * dy + dz * dz);
      }
   }

   private static double noise(double x) {
      double fl = Math.floor(x);
      int i = (int)fl;
      double f = x - fl;
      f = f * f * (3.0 - 2.0 * f);
      return hash(i) + (hash(i + 1) - hash(i)) * f;
   }

   private static double hash(int i) {
      int h = i * 668265261 ^ 374761393;
      h ^= h >>> 15;
      h *= -2048144789;
      h ^= h >>> 13;
      return (h >>> 8 & 16777215) / 1.6777216E7;
   }

   private static double smooth(double e0, double e1, double x) {
      double t = (x - e0) / (e1 - e0);
      t = t < 0.0 ? 0.0 : (t > 1.0 ? 1.0 : t);
      return t * t * (3.0 - 2.0 * t);
   }

   public static final class Style {
      public Strokes.Tool tool = Strokes.Tool.BRUSH;
      public int color = -15461356;
      public double width = 0.18;
      public float alpha = 1.0F;
      public float progress = 1.0F;
      public float heavyStart = 1.0F;
      public float thinEnd = 0.55F;
      public float dryness = 0.5F;
      public float diffuse;
      public float erase;
      public double eraseX = 1.0;
      public double eraseY;
      public double eraseZ;
      public boolean flat;
      public double normalX;
      public double normalY = 1.0;
      public double normalZ;
      public double tile = 0.5;
      public float light = 1.0F;
      public boolean pointAlpha;
      public boolean pointColor;
      public boolean pointNormal;

      public Strokes.Style reset() {
         this.progress = 1.0F;
         this.diffuse = 0.0F;
         this.erase = 0.0F;
         this.flat = false;
         this.alpha = 1.0F;
         this.light = 1.0F;
         this.pointAlpha = false;
         this.pointColor = false;
         this.pointNormal = false;
         return this;
      }
   }

   public static enum Tool {
      BRUSH,
      PENCIL,
      MARKER,
      CHALK;
   }
}
