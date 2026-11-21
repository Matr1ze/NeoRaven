package xyz.daikirai.render.furiah.fx.kit;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Area;
import java.awt.geom.Path2D.Double;
import java.awt.image.BufferedImage;
import java.util.Random;
import xyz.daikirai.render.furiah.render.Easing;
import xyz.daikirai.render.furiah.render.Vfx;
import xyz.daikirai.render.furiah.render.material.MeshBatch;

public final class Paper {
   public static final int[] PASTEL_SET = new int[]{-19294, -10587, -131146, -3473473, -6555905, -4345089, -14593};
   private static final int KEEL = 0;
   private static final int FRONT = 1;
   private static final int BACK = 2;
   private static final int LEFT = 3;
   private static final int RIGHT = 4;
   private static final int NECK_L = 5;
   private static final int NECK_R = 6;
   private static final int NECK_TIP = 7;
   private static final int HEAD = 8;
   private static final int TAIL_L = 9;
   private static final int TAIL_R = 10;
   private static final int TAIL_TIP = 11;
   private static final int HINGE_F = 12;
   private static final int HINGE_B = 13;
   private static final int WING_L = 14;
   private static final int WING_R = 15;
   private static final int CREASE_L = 16;
   private static final int CREASE_R = 17;
   public static final Paper.Shape CRANE = new Paper.Shape(
      "crane",
      new double[][]{
         {0.0, -0.3, 0.0},
         {0.36, 0.06, 0.0},
         {-0.36, 0.06, 0.0},
         {0.0, 0.05, -0.16},
         {0.0, 0.05, 0.16},
         {0.12, -0.17, -0.045},
         {0.12, -0.17, 0.045},
         {0.88, 0.62, 0.0},
         {1.03, 0.52, 0.0},
         {-0.12, -0.17, -0.045},
         {-0.12, -0.17, 0.045},
         {-0.92, 0.6, 0.0},
         {0.3, 0.08, 0.0},
         {-0.3, 0.08, 0.0},
         {-0.12, 0.3, -0.82},
         {-0.12, 0.3, 0.82},
         {0.0, 0.13, -0.42},
         {0.0, 0.13, 0.42}
      },
      new double[][]{
         {0.0, 0.0, 0.0},
         {0.36, 0.0, 0.0},
         {-0.36, 0.0, 0.0},
         {0.0, 0.0, -0.3},
         {0.0, 0.0, 0.3},
         {0.4, 0.0, -0.18},
         {0.4, 0.0, 0.18},
         {0.96, 0.0, 0.0},
         {0.84, 0.0, 0.0},
         {-0.4, 0.0, -0.18},
         {-0.4, 0.0, 0.18},
         {-0.96, 0.0, 0.0},
         {0.3, 0.0, 0.0},
         {-0.3, 0.0, 0.0},
         {0.0, 0.0, -0.96},
         {0.0, 0.0, 0.96},
         {0.0, 0.0, -0.5},
         {0.0, 0.0, 0.5}
      },
      new int[][]{
         {0, 1, 3},
         {0, 3, 2},
         {0, 2, 4},
         {0, 4, 1},
         {1, 3, 12},
         {1, 12, 4},
         {2, 13, 3},
         {2, 4, 13},
         {5, 1, 7},
         {1, 6, 7},
         {5, 6, 7},
         {0, 5, 6},
         {7, 8, 5},
         {7, 6, 8},
         {9, 2, 11},
         {2, 10, 11},
         {9, 10, 11},
         {0, 10, 9},
         {12, 16, 14},
         {16, 13, 14},
         {12, 13, 16},
         {12, 15, 17},
         {17, 15, 13},
         {12, 17, 13},
         {7, 16, 0},
         {16, 11, 0},
         {11, 17, 0},
         {17, 7, 0},
         {7, 14, 16},
         {14, 11, 16},
         {11, 15, 17},
         {15, 7, 17}
      },
      24,
      0,
      new int[]{14, 15, 16, 17},
      0.08
   );
   public static final Paper.Shape PLANE = new Paper.Shape(
      "plane",
      new double[][]{{1.0, 0.0, 0.0}, {-1.0, 0.0, 0.0}, {-0.85, -0.28, 0.0}, {-1.0, 0.07, -0.62}, {-1.0, 0.07, 0.62}, {0.3, -0.1, 0.0}},
      new double[][]{{1.0, 0.0, 0.0}, {-1.0, 0.0, 0.0}, {-0.85, 0.0, -0.28}, {-1.0, 0.0, -0.9}, {-1.0, 0.0, 0.9}, {0.3, 0.0, -0.1}},
      new int[][]{{0, 1, 3}, {0, 4, 1}, {0, 5, 1}, {5, 2, 1}},
      4,
      0,
      new int[0],
      0.0
   );
   public static final Paper.Shape STAR = star();
   public static final Paper.Shape HEART = heart();
   public static final Paper.Shape BOAT = new Paper.Shape(
      "boat",
      new double[][]{
         {1.0, 0.25, 0.0},
         {-1.0, 0.25, 0.0},
         {0.4, -0.2, 0.0},
         {-0.4, -0.2, 0.0},
         {0.0, 0.18, -0.32},
         {0.0, 0.18, 0.32},
         {0.0, 0.95, 0.0},
         {0.42, 0.22, 0.0},
         {-0.42, 0.22, 0.0}
      },
      new double[][]{
         {1.0, 0.0, 0.0},
         {-1.0, 0.0, 0.0},
         {0.4, 0.0, 0.0},
         {-0.4, 0.0, 0.0},
         {0.0, 0.0, -0.6},
         {0.0, 0.0, 0.6},
         {0.0, 0.0, 0.05},
         {0.42, 0.0, 0.02},
         {-0.42, 0.0, 0.02}
      },
      new int[][]{{0, 2, 4}, {2, 3, 4}, {3, 1, 4}, {0, 5, 2}, {2, 5, 3}, {3, 5, 1}, {7, 6, 8}},
      7,
      2,
      new int[0],
      0.0
   );
   public static final Paper.Shape CONFETTI = new Paper.Shape(
      "confetti",
      new double[][]{{0.5, 0.0, -0.5}, {0.5, 0.0, 0.5}, {-0.5, 0.0, 0.5}, {-0.5, 0.0, -0.5}},
      new double[][]{{0.5, 0.0, -0.5}, {0.5, 0.0, 0.5}, {-0.5, 0.0, 0.5}, {-0.5, 0.0, -0.5}},
      new int[][]{{0, 1, 2}, {0, 2, 3}},
      2,
      0,
      new int[0],
      0.0
   );
   private static final double[] WX = new double[32];
   private static final double[] WY = new double[32];
   private static final double[] WZ = new double[32];
   private static final double[] TX = new double[3];
   private static final double[] TY = new double[3];
   private static final double[] TZ = new double[3];
   private static final int[] TI = new int[3];
   private static final KitTextures.Painted FIBERS = new KitTextures.Painted("paper fibers", Paper::paintFibers);
   private static final KitTextures.Painted CUTOUT_ATLAS = new KitTextures.Painted("paper cutouts", () -> paintCutouts(false));
   private static final KitTextures.Painted PRINTED_ATLAS = new KitTextures.Painted("printed paper cutouts", () -> paintCutouts(true));
   public static final int TREE = 0;
   public static final int FLOWER = 1;
   public static final int CUT_STAR = 2;
   public static final int CUT_HEART = 3;
   public static final int CUTOUTS = 4;

   private Paper() {
   }

   public static int hueTurn(int color, float turns, float[] hsb) {
      Color.RGBtoHSB(color >> 16 & 0xFF, color >> 8 & 0xFF, color & 0xFF, hsb);
      float h = hsb[0] + turns;
      h -= (float)Math.floor(h);
      return color & 0xFF000000 | Color.HSBtoRGB(h, hsb[1], hsb[2]) & 16777215;
   }

   private static Paper.Shape star() {
      double[][] f = new double[12][];
      double[][] s = new double[12][];
      f[0] = new double[]{0.0, 0.24, 0.0};
      f[1] = new double[]{0.0, -0.24, 0.0};
      s[0] = new double[]{0.0, 0.0, 0.0};
      s[1] = new double[]{0.0, 0.0, 0.0};

      for (int k = 0; k < 5; k++) {
         double a = Math.toRadians(72 * k);
         double b = Math.toRadians(72 * k + 36);
         f[2 + k] = new double[]{Math.cos(a), 0.0, Math.sin(a)};
         f[7 + k] = new double[]{Math.cos(b) * 0.42, 0.0, Math.sin(b) * 0.42};
         s[2 + k] = (double[])f[2 + k].clone();
         s[7 + k] = (double[])f[7 + k].clone();
      }

      int[][] facets = new int[20][];

      for (int k = 0; k < 5; k++) {
         int o = 2 + k;
         int in = 7 + k;
         int next = 2 + (k + 1) % 5;
         facets[k * 4] = new int[]{0, o, in};
         facets[k * 4 + 1] = new int[]{0, in, next};
         facets[k * 4 + 2] = new int[]{1, in, o};
         facets[k * 4 + 3] = new int[]{1, next, in};
      }

      return new Paper.Shape("star", f, s, facets, 20, 0, new int[0], 0.0);
   }

   private static Paper.Shape heart() {
      int n = 12;
      double[][] f = new double[n + 2][];
      double[][] s = new double[n + 2][];
      f[0] = new double[]{0.1, 0.16, 0.0};
      f[1] = new double[]{0.1, -0.16, 0.0};
      s[0] = new double[]{0.1, 0.0, 0.0};
      s[1] = new double[]{0.1, 0.0, 0.0};

      for (int k = 0; k < n; k++) {
         double t = (Math.PI * 2) * k / n;
         double hx = 16.0 * Math.pow(Math.sin(t), 3.0);
         double hy = 13.0 * Math.cos(t) - 5.0 * Math.cos(2.0 * t) - 2.0 * Math.cos(3.0 * t) - Math.cos(4.0 * t);
         f[2 + k] = new double[]{hy / 17.0, 0.0, hx / 17.0};
         s[2 + k] = (double[])f[2 + k].clone();
      }

      int[][] facets = new int[n * 2][];

      for (int k = 0; k < n; k++) {
         int a = 2 + k;
         int b = 2 + (k + 1) % n;
         facets[k * 2] = new int[]{0, a, b};
         facets[k * 2 + 1] = new int[]{1, b, a};
      }

      return new Paper.Shape("heart", f, s, facets, n * 2, 0, new int[0], 0.0);
   }

   public static int facetsForTest(Paper.Shape s) {
      return s.facets.length;
   }

   public static int degenerateFacetsForTest(Paper.Shape s) {
      int bad = 0;

      for (int q = 0; q < s.facets.length; q++) {
         int[] f = s.facets[q];
         if (f.length != 3 || f[0] >= s.folded.length || f[1] >= s.folded.length || f[2] >= s.folded.length) {
            bad++;
         } else {
            double nz;
            double e1x;
            double e2x;
            double ny;
            double e2y;
            double e1z;
            double[] c;
            double e2z;
            double[] a;
            double[] b;
            double e1y;
            double nx;
            if (q < s.flapsFrom
               && Math.sqrt(
                     (
                              nx = (e1y = (b = s.folded[f[1]])[1] - (a = s.folded[f[0]])[1]) * (e2z = (c = s.folded[f[2]])[2] - a[2])
                                 - (e1z = b[2] - a[2]) * (e2y = c[1] - a[1])
                           )
                           * nx
                        + (ny = e1z * (e2x = c[0] - a[0]) - (e1x = b[0] - a[0]) * e2z) * ny
                        + (nz = e1x * e2y - e1y * e2x) * nz
                  )
                  < 1.0E-6) {
               bad++;
            }
         }
      }

      return bad;
   }

   private static void points(Paper.Shape s, Paper.Pose p) {
      double sc = p.scale;

      for (int q = 0; q < s.folded.length; q++) {
         double f = p.pointFold != null ? p.pointFold[q] : p.fold;
         double mx = s.folded[q][0] - s.sheet[q][0];
         double my = s.folded[q][1] - s.sheet[q][1];
         double mz = s.folded[q][2] - s.sheet[q][2];
         double x = s.sheet[q][0] + mx * f;
         double y = s.sheet[q][1] + my * f;
         double z = s.sheet[q][2] + mz * f;
         if (p.arc > 0.0 && f > 0.0 && f < 1.0) {
            y += Math.sin(Math.PI * f) * Math.sqrt(mx * mx + my * my + mz * mz) * p.arc;
         }

         if (p.pointLift != null) {
            y += p.pointLift[q];
         }

         double wingUp = p.flap * f;
         if (s.wing[q]) {
            double reach = Math.abs(z);
            double side = Math.signum(z);
            double dy = y - s.hingeY;
            double r = Math.sqrt(reach * reach + dy * dy);
            double a = Math.atan2(dy, reach) + wingUp;
            y = s.hingeY + Math.sin(a) * r;
            z = side * Math.cos(a) * r;
         }

         WX[q] = p.x + (p.fx * x + p.ux * y + p.rx * z) * sc;
         WY[q] = p.y + (p.fy * x + p.uy * y + p.ry * z) * sc;
         WZ[q] = p.z + (p.fz * x + p.uz * y + p.rz * z) * sc;
      }
   }

   private static double flapsShow(double fold) {
      return 1.0 - Easing.smoothstep(0.55F, 0.95F, (float)fold);
   }

   public static void facets(Paper.Shape s, Paper.Pose p, int col) {
      if (!(p.scale <= 1.0E-4) && col >>> 24 != 0) {
         points(s, p);
         double show = flapsShow(p.fold);
         int facets = show > 0.01 ? s.facets.length : s.flapsFrom;

         for (int q = 0; q < facets; q++) {
            int[] tri = s.facets[q];
            int a0 = tri[0];
            int b0 = tri[1];
            int c0 = tri[2];
            double ax = WX[a0];
            double ay = WY[a0];
            double az = WZ[a0];
            double bx = WX[b0];
            double by = WY[b0];
            double bz = WZ[b0];
            double qx = WX[c0];
            double qy = WY[c0];
            double qz = WZ[c0];
            if (q >= s.flapsFrom) {
               double kx = WX[s.keel];
               double ky = WY[s.keel];
               double kz = WZ[s.keel];
               ax = kx + (ax - kx) * show;
               ay = ky + (ay - ky) * show;
               az = kz + (az - kz) * show;
               bx = kx + (bx - kx) * show;
               by = ky + (by - ky) * show;
               bz = kz + (bz - kz) * show;
               qx = kx + (qx - kx) * show;
               qy = ky + (qy - ky) * show;
               qz = kz + (qz - kz) * show;
            }

            double nz;
            double e1x;
            double e2x;
            double ny;
            double e2y;
            double e1z;
            double e2z;
            double e1y;
            double nx;
            double l;
            if (!(
               (
                     l = Math.sqrt(
                        (nx = (e1y = by - ay) * (e2z = qz - az) - (e1z = bz - az) * (e2y = qy - ay)) * nx
                           + (ny = e1z * (e2x = qx - ax) - (e1x = bx - ax) * e2z) * ny
                           + (nz = e1x * e2y - e1y * e2x) * nz
                     )
                  )
                  < 1.0E-12
            )) {
               int base = MeshBatch.stage(3);
               double var55;
               double var56;
               double var57;
               MeshBatch.vertex(base, ax, ay, az, var57 = nx / l, var56 = ny / l, var55 = nz / l, col, 0.0F);
               MeshBatch.vertex(base + 1, bx, by, bz, var57, var56, var55, col, 0.0F);
               MeshBatch.vertex(base + 2, qx, qy, qz, var57, var56, var55, col, 0.0F);
               MeshBatch.tri(base, base + 1, base + 2);
            }
         }
      }
   }

   private static boolean backwards(Paper.Shape s, int q) {
      int[] tri = s.facets[q];
      double[] b = s.sheet[tri[1]];
      double[] a = s.sheet[tri[0]];
      double[] c = s.sheet[tri[2]];
      return (b[2] - a[2]) * (c[0] - a[0]) - (b[0] - a[0]) * (c[2] - a[2]) < 0.0;
   }

   private static void corners(Paper.Shape s, int q, double show) {
      int[] tri = s.facets[q];
      int a0 = tri[0];
      int b0 = tri[1];
      int c0 = tri[2];
      if (backwards(s, q)) {
         int t = b0;
         b0 = c0;
         c0 = t;
      }

      TI[0] = a0;
      TI[1] = b0;
      TI[2] = c0;

      for (int k = 0; k < 3; k++) {
         int i = TI[k];
         TX[k] = WX[i];
         TY[k] = WY[i];
         TZ[k] = WZ[i];
         if (q >= s.flapsFrom) {
            double kx = WX[s.keel];
            double ky = WY[s.keel];
            double kz = WZ[s.keel];
            TX[k] = kx + (TX[k] - kx) * show;
            TY[k] = ky + (TY[k] - ky) * show;
            TZ[k] = kz + (TZ[k] - kz) * show;
         }
      }
   }

   public static void sides(Paper.Shape s, Paper.Pose p, int front, int back) {
      if (!(p.scale <= 1.0E-4) && (front >>> 24 != 0 || back >>> 24 != 0)) {
         points(s, p);
         double show = flapsShow(p.fold);
         int facets = show > 0.01 ? s.facets.length : s.flapsFrom;

         for (int q = 0; q < facets; q++) {
            corners(s, q, show);
            double e1x = TX[1] - TX[0];
            double e1y = TY[1] - TY[0];
            double e1z = TZ[1] - TZ[0];
            double e2x = TX[2] - TX[0];
            double e2y = TY[2] - TY[0];
            double e2z = TZ[2] - TZ[0];
            double nx = e1y * e2z - e1z * e2y;
            double ny = e1z * e2x - e1x * e2z;
            double nz = e1x * e2y - e1y * e2x;
            double l = Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (!(l < 1.0E-12)) {
               nx /= l;
               ny /= l;
               nz /= l;
               int base = MeshBatch.stage(6);

               for (int k = 0; k < 3; k++) {
                  MeshBatch.vertex(base + k, TX[k], TY[k], TZ[k], nx, ny, nz, front, 0.0F);
                  MeshBatch.vertex(base + 3 + k, TX[k], TY[k], TZ[k], -nx, -ny, -nz, back, 0.0F);
               }

               MeshBatch.tri(base, base + 1, base + 2);
               MeshBatch.tri(base + 3, base + 4, base + 5);
            }
         }
      }
   }

   public static void print(Paper.Shape s, Paper.Pose p, int texture, double[] map, int col) {
      if (!(p.scale <= 1.0E-4) && col >>> 24 != 0) {
         points(s, p);
         double show = flapsShow(p.fold);
         int facets = show > 0.01 ? s.facets.length : s.flapsFrom;

         for (int q = 0; q < facets; q++) {
            corners(s, q, show);
            double e1x = TX[1] - TX[0];
            double e1y = TY[1] - TY[0];
            double e1z = TZ[1] - TZ[0];
            double e2x = TX[2] - TX[0];
            double e2y = TY[2] - TY[0];
            double e2z = TZ[2] - TZ[0];
            double nx = e1y * e2z - e1z * e2y;
            double ny = e1z * e2x - e1x * e2z;
            double nz = e1x * e2y - e1y * e2x;
            double l = Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (!(l < 1.0E-12)) {
               float k = (float)(0.72 + 0.28 * Math.max(-1.0, Math.min(1.0, ny / l)));
               int c = col & 0xFF000000 | (int)((col >> 16 & 0xFF) * k) << 16 | (int)((col >> 8 & 0xFF) * k) << 8 | (int)((col & 0xFF) * k);
               double u0 = mapU(s, TI[0], map);
               double v0 = mapV(s, TI[0], map);
               double u1 = mapU(s, TI[1], map);
               double v1 = mapV(s, TI[1], map);
               double u2 = mapU(s, TI[2], map);
               double v2 = mapV(s, TI[2], map);
               Vfx.quadUV(
                  texture, TX[0], TY[0], TZ[0], u0, v0, c, TX[1], TY[1], TZ[1], u1, v1, c, TX[2], TY[2], TZ[2], u2, v2, c, TX[2], TY[2], TZ[2], u2, v2, c
               );
            }
         }
      }
   }

   private static double mapU(Paper.Shape s, int i, double[] map) {
      return map[0] * s.sheet[i][0] + map[1] * s.sheet[i][2] + map[2];
   }

   private static double mapV(Paper.Shape s, int i, double[] map) {
      return map[3] * s.sheet[i][0] + map[4] * s.sheet[i][2] + map[5];
   }

   public static void fibers(Paper.Shape s, Paper.Pose p, int ink) {
      if (!(p.scale <= 1.0E-4) && ink >>> 24 != 0) {
         points(s, p);
         int tex = fiberTexture();
         double show = flapsShow(p.fold);
         int facets = show > 0.01 ? s.facets.length : s.flapsFrom;

         for (int q = 0; q < Math.min(facets, s.flapsFrom); q++) {
            int[] tri = s.facets[q];
            int p0 = tri[0];
            int p1 = tri[1];
            int p2 = tri[2];
            double s0u = s.sheet[p0][0] * 1.1;
            double s0v = s.sheet[p0][2] * 1.1;
            double s1u = s.sheet[p1][0] * 1.1;
            double s1v = s.sheet[p1][2] * 1.1;
            double s2u = s.sheet[p2][0] * 1.1;
            double s2v = s.sheet[p2][2] * 1.1;
            Vfx.quadUV(
               tex,
               WX[p0],
               WY[p0],
               WZ[p0],
               s0u,
               s0v,
               ink,
               WX[p1],
               WY[p1],
               WZ[p1],
               s1u,
               s1v,
               ink,
               WX[p2],
               WY[p2],
               WZ[p2],
               s2u,
               s2v,
               ink,
               WX[p2],
               WY[p2],
               WZ[p2],
               s2u,
               s2v,
               ink
            );
         }
      }
   }

   static void warmUp() {
      FIBERS.warm();
      CUTOUT_ATLAS.warm();
      PRINTED_ATLAS.warm();
   }

   public static int fiberTexture() {
      return FIBERS.texture();
   }

   private static KitTextures.Image[] paintFibers() {
      int size = 512;
      float[] a = fiberField(size);
      int[] px = new int[size * size];

      for (int i = 0; i < px.length; i++) {
         px[i] = Math.round(Math.min(1.0F, a[i]) * 255.0F) << 24 | 16777215;
      }

      return new KitTextures.Image[]{KitTextures.Image.argb(px, size, size, true, true)};
   }

   private static float[] fiberField(int size) {
      float[] a = new float[size * size];
      Random r = new Random(633314L);

      for (int i = 0; i < a.length; i++) {
         a[i] = r.nextFloat() * 0.08F;
      }

      for (int f = 0; f < 900; f++) {
         double x = r.nextDouble() * size;
         double y = r.nextDouble() * size;
         double ang = r.nextDouble() * Math.PI * 2.0;
         int len = 20 + r.nextInt(60);
         float dark = 0.12F + r.nextFloat() * 0.25F;

         for (int s = 0; s < len; s++) {
            int ix = ((int)Math.floor(x += Math.cos(ang += (r.nextDouble() - 0.5) * 0.35)) % size + size) % size;
            int iy = ((int)Math.floor(y += Math.sin(ang)) % size + size) % size;
            int p = iy * size + ix;
            a[p] = Math.min(1.0F, a[p] + dark);
         }
      }

      return a;
   }

   public static int cutouts() {
      return cutouts(false);
   }

   public static int cutouts(boolean printed) {
      return printed ? PRINTED_ATLAS.texture() : CUTOUT_ATLAS.texture();
   }

   private static KitTextures.Image[] paintCutouts(boolean printed) {
      int cell = 256;
      int w = cell * 4;
      BufferedImage img = new BufferedImage(w, cell, 2);
      Graphics2D g = img.createGraphics();
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
      g.setColor(Color.WHITE);

      for (int k = 0; k < 4; k++) {
         g.fill(cutout(k, k * cell, cell));
      }

      g.dispose();
      int[] px = img.getRGB(0, 0, w, cell, null, 0, w);
      float[] fibers = printed ? fiberField(512) : null;
      byte[] la = new byte[w * cell * 2];

      for (int i = 0; i < px.length; i++) {
         int lum = 255;
         if (fibers != null) {
            int x = i % w;
            int y = i / w;
            lum = Math.round(255.0F * (1.0F - 0.3F * Math.min(1.0F, fibers[y % 512 * 512 + x % 512])));
         }

         la[i * 2] = (byte)lum;
         la[i * 2 + 1] = (byte)(px[i] >>> 24);
      }

      return new KitTextures.Image[]{KitTextures.Image.la(la, w, cell, false)};
   }

   private static java.awt.Shape cutout(int k, double x0, double c) {
      double cx = x0 + c / 2.0;
      double bottom = c * 0.97;
      Area a = new Area();
      switch (k) {
         case 0:
            for (int t = 0; t < 3; t++) {
               double top = c * (0.06 + 0.22 * t);
               double base = c * (0.42 + 0.2 * t);
               double half = c * (0.2 + 0.08 * t);
               Double p = new Double();
               p.moveTo(cx, top);
               p.lineTo(cx + half, base);
               p.lineTo(cx - half, base);
               p.closePath();
               a.add(new Area(p));
            }

            a.add(new Area(new java.awt.geom.Rectangle2D.Double(cx - c * 0.05, c * 0.8, c * 0.1, bottom - c * 0.8)));
            break;
         case 1:
            double fy = c * 0.3;
            double r = c * 0.11;

            for (int ixx = 0; ixx < 6; ixx++) {
               double ang = (Math.PI * 2) * ixx / 6.0;
               a.add(new Area(new java.awt.geom.Ellipse2D.Double(cx + Math.cos(ang) * r * 1.25 - r, fy + Math.sin(ang) * r * 1.25 - r, r * 2.0, r * 2.0)));
            }

            a.add(new Area(new java.awt.geom.Ellipse2D.Double(cx - r, fy - r, r * 2.0, r * 2.0)));
            a.add(new Area(new java.awt.geom.Rectangle2D.Double(cx - c * 0.025, fy, c * 0.05, bottom - fy)));
            a.add(new Area(new java.awt.geom.Ellipse2D.Double(cx, c * 0.62, c * 0.2, c * 0.08)));
            a.add(new Area(new java.awt.geom.Ellipse2D.Double(cx - c * 0.2, c * 0.72, c * 0.2, c * 0.08)));
            break;
         case 2:
            Double starPath = new Double();
            double sy = c * 0.5;
            double ro = c * 0.44;
            double ri = c * 0.18;

            for (int ix = 0; ix < 10; ix++) {
               double ang = (-Math.PI / 2) + Math.PI * ix / 5.0;
               double starRadius = (ix & 1) == 0 ? ro : ri;
               double x = cx + Math.cos(ang) * starRadius;
               double y = sy + Math.sin(ang) * starRadius + (bottom - (sy + ro * 0.81));
               if (ix == 0) {
                  starPath.moveTo(x, y);
               } else {
                  starPath.lineTo(x, y);
               }
            }

            starPath.closePath();
            a.add(new Area(starPath));
            break;
         default:
            Double heartPath = new Double();
            int n = 48;

            for (int i = 0; i < n; i++) {
               double t = (Math.PI * 2) * i / n;
               double hx = 16.0 * Math.pow(Math.sin(t), 3.0);
               double hy = 13.0 * Math.cos(t) - 5.0 * Math.cos(2.0 * t) - 2.0 * Math.cos(3.0 * t) - Math.cos(4.0 * t);
               double x = cx + hx * c * 0.026;
               double y = c * 0.5 - hy * c * 0.026 + c * 0.05;
               if (i == 0) {
                  heartPath.moveTo(x, y);
               } else {
                  heartPath.lineTo(x, y);
               }
            }

            heartPath.closePath();
            a.add(new Area(heartPath));
      }

      return a;
   }

   public static void popup(int kind, double x, double y, double z, double fx, double fz, double width, double height, double stand, int col) {
      popup(cutouts(), kind, x, y, z, fx, fz, width, height, stand, col);
   }

   public static void popup(int atlas, int kind, double x, double y, double z, double fx, double fz, double width, double height, double stand, int col) {
      if (col >>> 24 != 0 && !(height <= 1.0E-4)) {
         double ax = -fz * width * 0.5;
         double az = fx * width * 0.5;
         double lean = (1.0 - Easing.clamp01((float)stand)) * Math.PI * 0.5;
         double ux = -fx * Math.sin(lean) * height;
         double uy = Math.cos(lean) * height;
         double uz = -fz * Math.sin(lean) * height;
         float u0 = kind / 4.0F;
         float u1 = (kind + 1) / 4.0F;
         Vfx.quadUV(
            atlas,
            x - ax,
            y,
            z - az,
            u0,
            1.0,
            col,
            x + ax,
            y,
            z + az,
            u1,
            1.0,
            col,
            x + ax + ux,
            y + uy,
            z + az + uz,
            u1,
            0.0,
            col,
            x - ax + ux,
            y + uy,
            z - az + uz,
            u0,
            0.0,
            col
         );
      }
   }

   public static final class Flutter {
      public double x;
      public double y;
      public double z;
      public double vx;
      public double vy;
      public double vz;
      public double yaw;
      public double spin;
      public double age;
      public float phase;
      public float sway;
      public boolean landed;

      public void start(double x, double y, double z, double vx, double vy, double vz, float phase, float sway, double spin) {
         this.x = x;
         this.y = y;
         this.z = z;
         this.vx = vx;
         this.vy = vy;
         this.vz = vz;
         this.phase = phase;
         this.sway = sway;
         this.spin = spin;
         this.yaw = phase * 6.28;
         this.age = 0.0;
         this.landed = false;
      }

      public void step(double dt, Surface ground, double offX, double offY, double offZ) {
         this.step(dt, ground, offX, offY, offZ, 0.0, 0.0, 0.0);
      }

      public void step(double dt, Surface ground, double offX, double offY, double offZ, double wx, double wy, double wz) {
         this.age += dt;
         if (this.landed) {
            double damp = Math.exp(-6.0 * dt);
            this.vx *= damp;
            this.vz *= damp;
            this.x = this.x + this.vx * dt;
            this.z = this.z + this.vz * dt;
         } else {
            this.vy -= 2.6 * dt;
            double damp = Math.exp(-2.9 * dt);
            this.vx *= damp;
            this.vy *= damp;
            this.vz *= damp;
            double side = Math.sqrt(this.vx * this.vx + this.vz * this.vz);
            this.vy = this.vy + Math.min(side * 0.9, 2.2) * dt;
            float w = 3.1F + this.phase % 1.0F;
            double s = this.sway * w * Math.cos(this.age * w + this.phase) * dt;
            this.vx = this.vx + s * Math.cos(this.phase * 7.3);
            this.vz = this.vz + s * Math.sin(this.phase * 7.3);
            this.x = this.x + (this.vx + wx) * dt;
            this.y = this.y + (this.vy + wy) * dt;
            this.z = this.z + (this.vz + wz) * dt;
            this.yaw = this.yaw + this.spin * dt;
            double g;
            if (ground != null
               && this.vy <= 0.0
               && !java.lang.Double.isNaN(g = ground.at(this.x + offX, this.y + offY, this.z + offZ))
               && this.y + offY <= g + 0.01) {
               this.y = g + 0.01 - offY;
               this.vy = 0.0;
               this.landed = true;
               this.spin = 0.0;
            }
         }
      }

      public double rock() {
         return this.landed ? 0.0 : 0.55 * Math.sin(this.age * 5.3 + this.phase * 6.0);
      }

      public void pose(Paper.Pose p, double scale, double fold) {
         double cy = Math.cos(this.yaw);
         double sy = Math.sin(this.yaw);
         double r = this.rock();
         double cr = Math.cos(r);
         double sr = Math.sin(r);
         p.x = this.x;
         p.y = this.y;
         p.z = this.z;
         p.fx = cy;
         p.fy = 0.0;
         p.fz = sy;
         double ux0 = 0.0;
         double uy0 = 1.0;
         double uz0 = 0.0;
         double rx0 = -sy;
         double ry0 = 0.0;
         p.ux = ux0 * cr + rx0 * sr;
         p.uy = uy0 * cr + ry0 * sr;
         p.uz = uz0 * cr + cy * sr;
         p.rx = rx0 * cr - ux0 * sr;
         p.ry = ry0 * cr - uy0 * sr;
         p.rz = cy * cr - uz0 * sr;
         p.scale = scale;
         p.fold = fold;
         p.flap = 0.0;
      }
   }

   public static final class Pose {
      public double x;
      public double y;
      public double z;
      public double fx;
      public double fy;
      public double fz;
      public double ux;
      public double uy;
      public double uz;
      public double rx;
      public double ry;
      public double rz;
      public double scale;
      public double fold;
      public double flap;
      public float[] pointFold;
      public float[] pointLift;
      public double arc;
   }

   public static final class Shape {
      public final String name;
      final double[][] folded;
      final double[][] sheet;
      final int[][] facets;
      final int flapsFrom;
      final int keel;
      final boolean[] wing;
      final double hingeY;

      Shape(String name, double[][] folded, double[][] sheet, int[][] facets, int flapsFrom, int keel, int[] wingPoints, double hingeY) {
         this.name = name;
         this.folded = folded;
         this.sheet = sheet;
         this.facets = facets;
         this.flapsFrom = flapsFrom;
         this.keel = keel;
         this.wing = new boolean[folded.length];

         for (int p : wingPoints) {
            this.wing[p] = true;
         }

         this.hingeY = hingeY;
      }

      public int points() {
         return this.folded.length;
      }
   }
}
