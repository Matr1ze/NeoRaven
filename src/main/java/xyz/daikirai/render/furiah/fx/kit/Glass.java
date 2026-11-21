package xyz.daikirai.render.furiah.fx.kit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import xyz.daikirai.render.furiah.fx.Terrain;
import xyz.daikirai.render.furiah.render.Camera;
import xyz.daikirai.render.furiah.render.ColorUtil;
import xyz.daikirai.render.furiah.render.Textures;
import xyz.daikirai.render.furiah.render.Vfx;
import xyz.daikirai.render.furiah.setting.Displayable;

public final class Glass {
   public static final int RUBY = 0;
   public static final int SAPPHIRE = 1;
   public static final int EMERALD = 2;
   public static final int GOLD = 3;
   public static final int[] CATHEDRAL_SET = new int[]{-4184513, -13935399, -14705042, -868018};
   public static final double LEAD = 0.028;
   public static final double FRAME_IN = 0.875;
   public static final double BAND_IN = 0.655;
   public static final double BAND_OUT = 0.705;
   public static final int SHAFTS = 6;
   public static final int RING_SEGS = 48;
   public static final double[] RING_COS = new double[49];
   public static final double[] RING_SIN = new double[49];
   private static final double TWO_PI = Math.PI * 2;
   private static Glass.Cells rose;
   private static Glass.Cells mandala;
   private static Glass.Cells lancet;
   private static final KitTextures.Painted GLASS = new KitTextures.Painted("glass", Glass::paintTexture);
   public static final int CIRCLE = 0;
   public static final int ARCH = 1;

   static void warmUp() {
      GLASS.warm();
   }

   private Glass() {
   }

   public static void tinted(int glass1, int glass2, int glass3, int[] out) {
      out[0] = glass2;
      out[1] = glass1;
      out[2] = ColorUtil.lerpOklab(glass1, glass3, 0.5F);
      out[3] = glass3;
   }

   private static double edge(double[] e, double theta) {
      if (e[1] == 0.0) {
         return e[0];
      } else {
         double x = (theta - e[3]) / e[2];
         x -= Math.floor(x);
         return e[0] + e[1] * Math.pow(1.0 - Math.abs(2.0 * x - 1.0), e[4]);
      }
   }

   public static double[] arc(double r) {
      return new double[]{r, 0.0, 1.0, 0.0, 1.0};
   }

   public static double[] pointed(double base, double amp, double periodDeg, double phaseDeg, double pow) {
      return new double[]{base, amp, Math.toRadians(periodDeg), Math.toRadians(phaseDeg), pow};
   }

   public static void ring(Glass.Builder b, int n, double startDeg, double[] in, double[] out, int[] colors, boolean turns, int segs) {
      double w = (Math.PI * 2) / n;
      double start = Math.toRadians(startDeg);

      for (int i = 0; i < n; i++) {
         double a0 = start + i * w;
         double[] u = new double[(segs + 1) * 2];
         double[] v = new double[(segs + 1) * 2];

         for (int k = 0; k <= segs; k++) {
            double a = a0 + w * k / segs;
            double r = edge(in, a);
            u[k] = Math.cos(a) * r;
            v[k] = Math.sin(a) * r;
         }

         for (int var25 = 0; var25 <= segs; var25++) {
            double a = a0 + w * (segs - var25) / segs;
            double r = edge(out, a);
            u[segs + 1 + var25] = Math.cos(a) * r;
            v[segs + 1 + var25] = Math.sin(a) * r;
         }

         b.cell(u, v, colors[i % colors.length], turns);
      }
   }

   public static void disc(Glass.Builder b, double cu, double cv, double r, int color, boolean turns) {
      int n = 16;
      double[] u = new double[n];
      double[] v = new double[n];

      for (int k = 0; k < n; k++) {
         u[k] = cu + Math.cos(k * (Math.PI * 2) / n) * r;
         v[k] = cv + Math.sin(k * (Math.PI * 2) / n) * r;
      }

      b.cell(u, v, color, turns);
   }

   public static Glass.Cells rose() {
      if (rose == null) {
         Glass.Builder b = new Glass.Builder();
         disc(b, 0.0, 0.0, 0.12, 3, true);
         double[] petals = pointed(0.34, 0.12, 45.0, -22.5, 1.4);
         ring(b, 8, -22.5, arc(0.12), petals, new int[]{0, 1}, true, 6);
         ring(b, 16, -22.5, petals, arc(0.655), new int[]{2, 3}, true, 4);
         ring(b, 24, 0.0, arc(0.705), arc(0.875), new int[]{1, 0, 1, 3}, false, 2);
         rose = b.build(false);
      }

      return rose;
   }

   public static Glass.Cells mandala() {
      if (mandala == null) {
         Glass.Builder b = new Glass.Builder();
         disc(b, 0.0, 0.0, 0.1, 3, true);
         double[] a = pointed(0.22, 0.16, 30.0, -15.0, 1.0);
         double[] bb = pointed(0.46, 0.14, 30.0, 0.0, 1.0);
         ring(b, 12, -15.0, arc(0.1), a, new int[]{0, 3}, true, 4);
         ring(b, 12, 0.0, a, bb, new int[]{1}, true, 4);
         ring(b, 24, 0.0, bb, arc(0.655), new int[]{2, 3}, true, 2);
         ring(b, 36, 0.0, arc(0.705), arc(0.875), new int[]{0, 1, 2}, false, 2);
         mandala = b.build(false);
      }

      return mandala;
   }

   public static Glass.Cells lancet() {
      if (lancet == null) {
         Glass.Builder b = new Glass.Builder();
         lancetWindow(b, -0.13, 0.13, -0.52, 0.22, new int[]{1, 0}, 3);
         lancetWindow(b, -0.42, -0.2, -0.42, 0.12, new int[]{2, 3}, 0);
         lancetWindow(b, 0.2, 0.42, -0.42, 0.12, new int[]{2, 3}, 0);
         disc(b, 0.0, 0.555, 0.06, 3, false);
         ring(b, 20, 0.0, arc(0.705), arc(0.875), new int[]{0, 1, 3, 1}, true, 2);
         lancet = b.build(true);
      }

      return lancet;
   }

   public static void lancetWindow(Glass.Builder b, double x0, double x1, double y0, double yTop, int[] panes, int archColor) {
      double xm = (x0 + x1) / 2.0;
      double rowH = (yTop - y0) / 3.0;

      for (int r = 0; r < 3; r++) {
         double ya = y0 + r * rowH;
         double yb = ya + rowH;
         b.cell(new double[]{x0, xm, xm, x0}, new double[]{ya, ya, yb, yb}, panes[r % panes.length], false);
         b.cell(new double[]{xm, x1, x1, xm}, new double[]{ya, ya, yb, yb}, panes[(r + 1) % panes.length], false);
      }

      int steps = 6;
      double w = x1 - x0;
      double[] u = new double[steps * 2 + 1];
      double[] v = new double[steps * 2 + 1];
      int k = 0;

      for (int i = 0; i <= steps; i++) {
         double a = Math.toRadians(180.0 - 60.0 * i / steps);
         u[k] = x1 + Math.cos(a) * w;
         v[k] = yTop + Math.sin(a) * w;
         k++;
      }

      for (int var25 = 1; var25 <= steps; var25++) {
         double a = Math.toRadians(60.0 - 60.0 * var25 / steps);
         u[k] = x0 + Math.cos(a) * w;
         v[k] = yTop + Math.sin(a) * w;
         k++;
      }

      b.cell(u, v, archColor, false);
   }

   private static double[][] outline(int shape) {
      if (shape == 1) {
         ArrayList<double[]> pts = new ArrayList<>();
         pts.add(new double[]{-0.5, -1.0});
         pts.add(new double[]{0.5, -1.0});
         pts.add(new double[]{0.5, 0.4});

         for (int i = 1; i <= 10; i++) {
            double a = Math.toRadians(60.0 * i / 10.0);
            pts.add(new double[]{-0.5 + Math.cos(a), 0.4 + Math.sin(a)});
         }

         for (int var11 = 1; var11 < 10; var11++) {
            double a = Math.toRadians(120.0 + 60.0 * var11 / 10.0);
            pts.add(new double[]{0.5 + Math.cos(a), 0.4 + Math.sin(a)});
         }

         pts.add(new double[]{-0.5, 0.4});
         double[] u = new double[pts.size()];
         double[] v = new double[pts.size()];

         for (int i2 = 0; i2 < pts.size(); i2++) {
            u[i2] = pts.get(i2)[0];
            v[i2] = pts.get(i2)[1];
         }

         return new double[][]{u, v};
      } else {
         double[] u = new double[48];
         double[] v = new double[48];

         for (int i = 0; i < 48; i++) {
            u[i] = RING_COS[i];
            v[i] = RING_SIN[i];
         }

         return new double[][]{u, v};
      }
   }

   public static double[][] outlineOf(int shape) {
      return outline(shape);
   }

   public static double outlineArea(int shape) {
      double[][] o = outline(shape);
      double a = 0.0;

      for (int i = 0; i < o[0].length; i++) {
         int j = (i + 1) % o[0].length;
         a += o[0][i] * o[1][j] - o[0][j] * o[1][i];
      }

      return Math.abs(a) * 0.5;
   }

   public static Glass.Cells voronoi(int n, long seed, int shape) {
      Random r = new Random(seed);
      double[][] o = outline(shape);
      double[] boundU = o[0];
      double[] boundV = o[1];
      double u0 = Double.MAX_VALUE;
      double u1 = -Double.MAX_VALUE;
      double v0 = Double.MAX_VALUE;
      double v1 = -Double.MAX_VALUE;

      for (int i = 0; i < boundU.length; i++) {
         u0 = Math.min(u0, boundU[i]);
         u1 = Math.max(u1, boundU[i]);
         v0 = Math.min(v0, boundV[i]);
         v1 = Math.max(v1, boundV[i]);
      }

      double[] su = new double[n];
      double[] sv = new double[n];

      for (int i = 0; i < n; i++) {
         double best = -1.0;

         for (int tries = 0; tries < 40 && (best < 0.0 || tries < 8); tries++) {
            double u = u0 + r.nextDouble() * (u1 - u0);
            double v;
            if (inside(boundU, boundV, u, v = v0 + r.nextDouble() * (v1 - v0))) {
               double near = Double.MAX_VALUE;

               for (int j = 0; j < i; j++) {
                  near = Math.min(near, (u - su[j]) * (u - su[j]) + (v - sv[j]) * (v - sv[j]));
               }

               if (near > best) {
                  best = near;
                  su[i] = u;
                  sv[i] = v;
               }
            }
         }
      }

      Glass.Builder b = new Glass.Builder();
      double[] cu = new double[64];
      double[] cv = new double[64];
      double[] nu = new double[64];
      double[] nv = new double[64];

      for (int i = 0; i < n; i++) {
         int m = boundU.length;
         System.arraycopy(boundU, 0, cu, 0, m);
         System.arraycopy(boundV, 0, cv, 0, m);

         for (int j = 0; j < n && m > 2; j++) {
            if (j != i) {
               double mu = (su[i] + su[j]) * 0.5;
               double mv = (sv[i] + sv[j]) * 0.5;
               double du = su[j] - su[i];
               double dv = sv[j] - sv[i];
               int k2 = 0;

               for (int k = 0; k < m; k++) {
                  int kn = (k + 1) % m;
                  double a = (cu[k] - mu) * du + (cv[k] - mv) * dv;
                  double bb = (cu[kn] - mu) * du + (cv[kn] - mv) * dv;
                  if (a <= 0.0 && k2 < nu.length) {
                     nu[k2] = cu[k];
                     nv[k2] = cv[k];
                     k2++;
                  }

                  if (a < 0.0 != bb < 0.0 && k2 < nu.length) {
                     double t = a / (a - bb);
                     nu[k2] = cu[k] + (cu[kn] - cu[k]) * t;
                     nv[k2] = cv[k] + (cv[kn] - cv[k]) * t;
                     k2++;
                  }
               }

               System.arraycopy(nu, 0, cu, 0, k2);
               System.arraycopy(nv, 0, cv, 0, k2);
               m = k2;
            }
         }

         if (m >= 3) {
            double[] pu = new double[m];
            double[] pv = new double[m];
            System.arraycopy(cu, 0, pu, 0, m);
            System.arraycopy(cv, 0, pv, 0, m);
            b.cell(pu, pv, i % 4, false);
         }
      }

      return b.build(false);
   }

   public static Glass.Cells periodic(int n, long seed, double tileU, double tileV) {
      Random r = new Random(seed);
      double[] su = new double[n];
      double[] sv = new double[n];

      for (int i = 0; i < n; i++) {
         double best = -1.0;

         for (int tries = 0; tries < 12; tries++) {
            double u = r.nextDouble() * tileU;
            double v = r.nextDouble() * tileV;
            double near = Double.MAX_VALUE;

            for (int j = 0; j < i; j++) {
               double du = Math.abs(u - su[j]);
               double dv = Math.abs(v - sv[j]);
               du = Math.min(du, tileU - du);
               dv = Math.min(dv, tileV - dv);
               near = Math.min(near, du * du + dv * dv);
            }

            if (near > best) {
               best = near;
               su[i] = u;
               sv[i] = v;
            }
         }
      }

      double[][] polyU = new double[n][];
      double[][] polyV = new double[n][];
      double[] cu = new double[64];
      double[] cv = new double[64];
      double[] nu = new double[64];
      double[] nv = new double[64];

      for (int i = 0; i < n; i++) {
         int m = 4;
         cu[0] = su[i] - tileU;
         cv[0] = sv[i] - tileV;
         cu[1] = su[i] + tileU;
         cv[1] = sv[i] - tileV;
         cu[2] = su[i] + tileU;
         cv[2] = sv[i] + tileV;
         cu[3] = su[i] - tileU;
         cv[3] = sv[i] + tileV;

         for (int ty = -1; ty <= 1 && m > 2; ty++) {
            for (int tx = -1; tx <= 1 && m > 2; tx++) {
               for (int j = 0; j < n && m > 2; j++) {
                  if (j != i || tx != 0 || ty != 0) {
                     double ou = su[j] + tx * tileU;
                     double ov = sv[j] + ty * tileV;
                     double mu = (su[i] + ou) * 0.5;
                     double mv = (sv[i] + ov) * 0.5;
                     double du = ou - su[i];
                     double dv = ov - sv[i];
                     int k2 = 0;

                     for (int k = 0; k < m; k++) {
                        int kn = (k + 1) % m;
                        double a = (cu[k] - mu) * du + (cv[k] - mv) * dv;
                        double bb = (cu[kn] - mu) * du + (cv[kn] - mv) * dv;
                        if (a <= 0.0 && k2 < nu.length) {
                           nu[k2] = cu[k];
                           nv[k2] = cv[k];
                           k2++;
                        }

                        if (a < 0.0 != bb < 0.0 && k2 < nu.length) {
                           double t = a / (a - bb);
                           nu[k2] = cu[k] + (cu[kn] - cu[k]) * t;
                           nv[k2] = cv[k] + (cv[kn] - cv[k]) * t;
                           k2++;
                        }
                     }

                     System.arraycopy(nu, 0, cu, 0, k2);
                     System.arraycopy(nv, 0, cv, 0, k2);
                     m = k2;
                  }
               }
            }
         }

         if (m >= 3) {
            polyU[i] = new double[m];
            polyV[i] = new double[m];
            System.arraycopy(cu, 0, polyU[i], 0, m);
            System.arraycopy(cv, 0, polyV[i], 0, m);
         }
      }

      int[] color = new int[n];
      Arrays.fill(color, -1);
      Glass.Builder b = new Glass.Builder();

      for (int i = 0; i < n; i++) {
         if (polyU[i] != null) {
            int used = 0;

            for (int jx = 0; jx < i; jx++) {
               if (polyU[jx] != null && color[jx] >= 0 && touches(polyU[i], polyV[i], polyU[jx], polyV[jx], tileU, tileV)) {
                  used |= 1 << color[jx];
               }
            }

            int start;
            int pick = start = r.nextInt(4);

            for (int k = 0; k < 4; k++) {
               int c = (start + k) % 4;
               if ((used & 1 << c) == 0) {
                  pick = c;
                  break;
               }
            }

            color[i] = pick;
            b.cell(polyU[i], polyV[i], pick, false);
         }
      }

      return b.build(false);
   }

   private static boolean touches(double[] au, double[] av, double[] bu, double[] bv, double tileU, double tileV) {
      for (int ty = -1; ty <= 1; ty++) {
         for (int tx = -1; tx <= 1; tx++) {
            int shared = 0;

            for (int a = 0; a < au.length && shared < 2; a++) {
               for (int c = 0; c < bu.length; c++) {
                  double du = au[a] - (bu[c] + tx * tileU);
                  double dv = av[a] - (bv[c] + ty * tileV);
                  if (du * du + dv * dv < 1.0E-10) {
                     shared++;
                     break;
                  }
               }
            }

            if (shared >= 2) {
               return true;
            }
         }
      }

      return false;
   }

   private static boolean inside(double[] pu, double[] pv, double u, double v) {
      boolean in = false;
      int i = 0;

      for (int j = pu.length - 1; i < pu.length; j = i++) {
         if (pv[i] > v != pv[j] > v && u < (pu[j] - pu[i]) * (v - pv[i]) / (pv[j] - pv[i]) + pu[i]) {
            in = !in;
         }
      }

      return in;
   }

   public static void glass(Glass.Pane p, double[] u, double[] v, int n, double cu, double cv, int mid, int edge) {
      int tex = texture();
      double cx = p.x(cu, cv);
      double cy = p.y(cu, cv);
      double cz = p.z(cu, cv);

      for (int k = 0; k < n; k++) {
         int k2 = (k + 1) % n;
         Vfx.quadUV(
            tex,
            cx,
            cy,
            cz,
            cu * 1.6,
            cv * 1.6,
            mid,
            p.x(u[k], v[k]),
            p.y(u[k], v[k]),
            p.z(u[k], v[k]),
            u[k] * 1.6,
            v[k] * 1.6,
            edge,
            p.x(u[k2], v[k2]),
            p.y(u[k2], v[k2]),
            p.z(u[k2], v[k2]),
            u[k2] * 1.6,
            v[k2] * 1.6,
            edge,
            p.x(u[k2], v[k2]),
            p.y(u[k2], v[k2]),
            p.z(u[k2], v[k2]),
            u[k2] * 1.6,
            v[k2] * 1.6,
            edge
         );
      }
   }

   public static void lead(Glass.Pane p, double[] u, double[] v, int n, double width, int col) {
      double hw = width * 0.5;

      for (int k = 0; k < n; k++) {
         int k2 = (k + 1) % n;
         double eu = u[k2] - u[k];
         double ev = v[k2] - v[k];
         double el = Math.sqrt(eu * eu + ev * ev);
         if (!(el < 1.0E-6)) {
            double var30;
            double pu = -(var30 = ev / el) * hw;
            double var29;
            double pv = (var29 = eu / el) * hw;
            double u0 = u[k] - var29 * hw;
            double v0 = v[k] - var30 * hw;
            double u1 = u[k2] + var29 * hw;
            double v1 = v[k2] + var30 * hw;
            Vfx.quadUV(
               Textures.WHITE,
               p.x(u0 - pu, v0 - pv),
               p.y(u0 - pu, v0 - pv),
               p.z(u0 - pu, v0 - pv),
               0.5,
               0.5,
               col,
               p.x(u1 - pu, v1 - pv),
               p.y(u1 - pu, v1 - pv),
               p.z(u1 - pu, v1 - pv),
               0.5,
               0.5,
               col,
               p.x(u1 + pu, v1 + pv),
               p.y(u1 + pu, v1 + pv),
               p.z(u1 + pu, v1 + pv),
               0.5,
               0.5,
               col,
               p.x(u0 + pu, v0 + pv),
               p.y(u0 + pu, v0 + pv),
               p.z(u0 + pu, v0 + pv),
               0.5,
               0.5,
               col
            );
         }
      }
   }

   public static void ringLead(Glass.Pane p, double r0, double r1, int col) {
      if (col >>> 24 != 0) {
         for (int i = 0; i < 48; i++) {
            double c0 = RING_COS[i];
            double s0 = RING_SIN[i];
            double c1 = RING_COS[i + 1];
            double s1 = RING_SIN[i + 1];
            Vfx.quadUV(
               Textures.WHITE,
               p.x(c0 * r0, s0 * r0),
               p.y(c0 * r0, s0 * r0),
               p.z(c0 * r0, s0 * r0),
               0.5,
               0.5,
               col,
               p.x(c1 * r0, s1 * r0),
               p.y(c1 * r0, s1 * r0),
               p.z(c1 * r0, s1 * r0),
               0.5,
               0.5,
               col,
               p.x(c1 * r1, s1 * r1),
               p.y(c1 * r1, s1 * r1),
               p.z(c1 * r1, s1 * r1),
               0.5,
               0.5,
               col,
               p.x(c0 * r1, s0 * r1),
               p.y(c0 * r1, s0 * r1),
               p.z(c0 * r1, s0 * r1),
               0.5,
               0.5,
               col
            );
         }
      }
   }

   public static int shard(Glass.Cells c, int i, double spin, double scale, double cu, double cv, double[] outU, double[] outV) {
      int from = c.start[i];
      int n = Math.min(c.count[i], outU.length);
      double cs = Math.cos(spin);
      double ss = Math.sin(spin);

      for (int k = 0; k < n; k++) {
         double ru = c.u[from + k] - c.cu[i];
         double rv = c.v[from + k] - c.cv[i];
         outU[k] = cu + (ru * cs - rv * ss) * scale;
         outV[k] = cv + (ru * ss + rv * cs) * scale;
      }

      return n;
   }

   public static void shaft(Camera cam, double x0, double y0, double z0, double x1, double y1, double z1, double w0, double w1, int col) {
      double dy2 = y1 - y0;
      double mz = (z0 + z1) * 0.5;
      double vz = cam.posZ - mz;
      double dz2 = z1 - z0;
      double my = (y0 + y1) * 0.5;
      double vy = cam.posY - my;
      double qx = dy2 * vz - dz2 * vy;
      double mx = (x0 + x1) * 0.5;
      double vx = cam.posX - mx;
      double dx = x1 - x0;
      double qy = dz2 * vx - dx * vz;
      double qz = dx * vy - dy2 * vx;
      double ql = Math.sqrt(qx * qx + qy * qy + qz * qz);
      if (!(ql < 1.0E-9)) {
         double a = w0 * 0.5;
         double b = w1 * 0.5;
         double var48;
         double var49;
         double var50;
         Vfx.quadUV(
            Textures.RAY,
            x0 - (var48 = qx / ql) * a,
            y0 - (var49 = qy / ql) * a,
            z0 - (var50 = qz / ql) * a,
            0.0,
            0.0,
            col,
            x0 + var48 * a,
            y0 + var49 * a,
            z0 + var50 * a,
            1.0,
            0.0,
            col,
            x1 + var48 * b,
            y1 + var49 * b,
            z1 + var50 * b,
            1.0,
            1.0,
            col,
            x1 - var48 * b,
            y1 - var49 * b,
            z1 - var50 * b,
            0.0,
            1.0,
            col
         );
      }
   }

   public static void patch(double x, double y, double z, float size, int col) {
      double h = size * 0.5;
      Vfx.quadUV(Textures.GLOW, x - h, y, z - h, 0.0, 0.0, col, x + h, y, z - h, 1.0, 0.0, col, x + h, y, z + h, 1.0, 1.0, col, x - h, y, z + h, 0.0, 1.0, col);
   }

   public static int texture() {
      return GLASS.texture();
   }

   private static KitTextures.Image[] paintTexture() {
      int size = 256;
      float[] g = new float[size * size];
      Random r = new Random(399957L);

      for (int oct = 0; oct < 2; oct++) {
         int cells = oct == 0 ? 8 : 24;
         float amp = oct == 0 ? 0.6F : 0.4F;
         float[] grid = new float[cells * cells];

         for (int i = 0; i < grid.length; i++) {
            grid[i] = r.nextFloat();
         }

         for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
               float fx = (float)(x * cells) / size;
               float fy = (float)(y * cells) / size;
               int x0 = (int)fx;
               int y0 = (int)fy;
               float tx = fx - x0;
               float ty = fy - y0;
               tx = tx * tx * (3.0F - 2.0F * tx);
               ty = ty * ty * (3.0F - 2.0F * ty);
               int x1 = (x0 + 1) % cells;
               int y1 = (y0 + 1) % cells;
               float a = grid[y0 * cells + x0];
               float b = grid[y0 * cells + x1];
               float cc = grid[y1 * cells + x0];
               float d = grid[y1 * cells + x1];
               int n = y * size + x;
               g[n] += amp * ((a + (b - a) * tx) * (1.0F - ty) + (cc + (d - cc) * tx) * ty);
            }
         }
      }

      for (int k = 0; k < 120; k++) {
         int bx = r.nextInt(size);
         int by = r.nextInt(size);

         for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
               int n = (by + dy + size) % size * size + (bx + dx + size) % size;
               g[n] += dx == 0 && dy == 0 ? 0.5F : 0.2F;
            }
         }
      }

      int[] px = new int[size * size];

      for (int i = 0; i < px.length; i++) {
         int v = Math.round((0.74F + 0.26F * Math.min(1.0F, g[i])) * 255.0F);
         px[i] = 0xFF000000 | v << 16 | v << 8 | v;
      }

      return new KitTextures.Image[]{KitTextures.Image.argb(px, size, size, true, true)};
   }

   static {
      for (int i = 0; i <= 48; i++) {
         RING_COS[i] = Math.cos(i * (Math.PI * 2) / 48.0);
         RING_SIN[i] = Math.sin(i * (Math.PI * 2) / 48.0);
      }
   }

   public static final class Builder {
      private final List<double[]> us = new ArrayList<>();
      private final List<double[]> vs = new ArrayList<>();
      private final List<Integer> colors = new ArrayList<>();
      private final List<Boolean> turns = new ArrayList<>();

      public void cell(double[] u, double[] v, int color, boolean turn) {
         this.us.add(u);
         this.vs.add(v);
         this.colors.add(color);
         this.turns.add(turn);
      }

      public Glass.Cells build(boolean stone) {
         Glass.Cells c = new Glass.Cells();
         c.n = this.us.size();
         int total = 0;

         for (double[] u : this.us) {
            total += u.length;
         }

         c.u = new double[total];
         c.v = new double[total];
         c.start = new int[c.n];
         c.count = new int[c.n];
         c.cu = new double[c.n];
         c.cv = new double[c.n];
         c.rad = new double[c.n];
         c.color = new int[c.n];
         c.turns = new boolean[c.n];
         int at = 0;

         for (int i = 0; i < c.n; i++) {
            double[] u = this.us.get(i);
            double[] v = this.vs.get(i);
            c.start[i] = at;
            c.count[i] = u.length;
            double su = 0.0;
            double sv = 0.0;

            for (int k = 0; k < u.length; k++) {
               c.u[at + k] = u[k];
               c.v[at + k] = v[k];
               su += u[k];
               sv += v[k];
            }

            c.cu[i] = su / u.length;
            c.cv[i] = sv / u.length;
            c.rad[i] = Math.sqrt(c.cu[i] * c.cu[i] + c.cv[i] * c.cv[i]);
            c.color[i] = this.colors.get(i);
            c.turns[i] = this.turns.get(i);
            at += u.length;
         }

         c.stone = stone;
         c.shaftCells = new int[6];

         for (int k = 0; k < 6; k++) {
            double want = Math.toRadians(-25.0 + 230.0 * k / 5.0);
            int best = 0;
            double bestD = Double.MAX_VALUE;

            for (int i = 0; i < c.n; i++) {
               double d;
               if (!(c.rad[i] < 0.705)
                  && (d = Math.abs(Math.atan2(Math.sin(Math.atan2(c.cv[i], c.cu[i]) - want), Math.cos(Math.atan2(c.cv[i], c.cu[i]) - want)))) < bestD) {
                  bestD = d;
                  best = i;
               }
            }

            c.shaftCells[k] = best;
         }

         return c;
      }
   }

   public static final class Cells {
      public double[] u;
      public double[] v;
      public double[] cu;
      public double[] cv;
      public double[] rad;
      public int[] start;
      public int[] count;
      public int[] color;
      public boolean[] turns;
      public int n;
      public boolean stone;
      public int[] shaftCells;
   }

   public static enum GlassColors implements Displayable {
      CATHEDRAL("Cathedral", "Ruby, sapphire, emerald and gold"),
      TINTED("Tinted", "The three glass colors");

      private final String displayName;
      private final String description;

      private GlassColors(String displayName, String description) {
         this.displayName = displayName;
         this.description = description;
      }

      @Override
      public String displayName() {
         return this.displayName;
      }

      @Override
      public String description() {
         return this.description;
      }
   }

   public static final class Pane {
      public double ox;
      public double oy;
      public double oz;
      public double rx;
      public double ry;
      public double rz;
      public double ux;
      public double uy;
      public double uz;
      public double nx;
      public double ny;
      public double nz;
      public double scale = 1.0;
      public double offX;
      public double offY;
      public double offZ;
      private Terrain.HeightField ground;
      private double viewX;
      private double viewY;
      private double viewZ;
      private double lift;

      public void onGround(Terrain.HeightField ground, double viewX, double viewY, double viewZ, double lift) {
         this.ground = ground;
         this.viewX = viewX;
         this.viewY = viewY;
         this.viewZ = viewZ;
         this.lift = lift;
      }

      public double x(double u, double v) {
         return this.ox + this.rx * u * this.scale + this.ux * v * this.scale + this.offX;
      }

      public double y(double u, double v) {
         if (this.ground != null) {
            double h = this.ground.at(this.x(u, v) + this.viewX, this.z(u, v) + this.viewZ);
            return (Double.isNaN(h) ? this.oy + this.viewY : h) - this.viewY + this.lift + this.offY;
         } else {
            return this.oy + this.ry * u * this.scale + this.uy * v * this.scale + this.offY;
         }
      }

      public double z(double u, double v) {
         return this.oz + this.rz * u * this.scale + this.uz * v * this.scale + this.offZ;
      }
   }
}
