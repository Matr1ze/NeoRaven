package xyz.daikirai.render.furiah.fx.kit;

import java.util.Arrays;
import java.util.Locale;
import xyz.daikirai.render.furiah.fx.FxFrame;
import xyz.daikirai.render.furiah.fx.look.KeepVisible;
import xyz.daikirai.render.furiah.render.Easing;
import xyz.daikirai.render.furiah.render.material.MeshBatch;

public final class Liquid {
   public static final int MESHED_PER_FRAME = 6;
   private static final int MAX_POINTS = 120000;
   private static final int[] EDGE_A = new int[12];
   private static final int[] EDGE_B = new int[12];
   private static final int[][] CASES = new int[256][];
   private static float[] grid;
   private static int[] stamp;
   private static int[] cellStamp;
   private static int stampNow;
   private static final float[] CORNER;
   private static final int[] CORNER_OFFSET;
   private static final double[] GRAD;
   private static final int EDGE_SLOTS = 16384;
   private static final int[] EDGE_KEY;
   private static final int[] EDGE_VERTEX;
   private static final int[] EDGE_KEPT;
   private static final int[] EDGE_STAMP;
   private static int edgesUsed;
   private static long budgetFrame;
   private static long budgetNanos;
   private static int grantedNow;
   private static double grantUpTo;
   private static final double[] ASKED;
   private static int askedCount;
   private static int meshedNow;
   private static int reusedNow;
   private static int spheresNow;
   private static int meshedLast;
   private static int reusedLast;
   private static int spheresLast;

   private static int edgeOf(int a, int b) {
      for (int e = 0; e < 12; e++) {
         if (EDGE_A[e] == a && EDGE_B[e] == b || EDGE_A[e] == b && EDGE_B[e] == a) {
            return e;
         }
      }

      throw new IllegalStateException("no edge " + a + "-" + b);
   }

   private static int[] triangulate(int mask, int[][] faceCorners, int[][] faceEdges) {
      int[] link1 = new int[12];
      int[] link2 = new int[12];
      Arrays.fill(link1, -1);
      Arrays.fill(link2, -1);

      for (int f = 0; f < 6; f++) {
         boolean[] in = new boolean[4];

         for (int i = 0; i < 4; i++) {
            in[i] = (mask >> faceCorners[f][i] & 1) != 0;
         }

         int crossed = 0;
         int first = -1;
         int second = -1;

         for (int i = 0; i < 4; i++) {
            if (in[i] != in[(i + 1) % 4]) {
               crossed++;
               if (first < 0) {
                  first = faceEdges[f][i];
               } else {
                  second = faceEdges[f][i];
               }
            }
         }

         if (crossed == 2) {
            join(link1, link2, first, second);
         } else if (crossed == 4) {
            if (in[0]) {
               join(link1, link2, faceEdges[f][3], faceEdges[f][0]);
               join(link1, link2, faceEdges[f][1], faceEdges[f][2]);
            } else {
               join(link1, link2, faceEdges[f][0], faceEdges[f][1]);
               join(link1, link2, faceEdges[f][2], faceEdges[f][3]);
            }
         }
      }

      int[] tris = new int[36];
      int count = 0;
      boolean[] used = new boolean[12];
      int[] loop = new int[12];

      for (int s = 0; s < 12; s++) {
         if (link1[s] >= 0 && !used[s]) {
            int len = 0;
            int prev = -1;
            int cur = s;

            int next;
            do {
               loop[len++] = cur;
               used[cur] = true;
               next = link1[cur] != prev ? link1[cur] : link2[cur];
               prev = cur;
               cur = next;
            } while (next != s && next >= 0 && len < 12);

            for (int ix = 1; ix + 1 < len; ix++) {
               tris[count++] = loop[0];
               tris[count++] = loop[ix];
               tris[count++] = loop[ix + 1];
            }
         }
      }

      return Arrays.copyOf(tris, count);
   }

   private static void join(int[] link1, int[] link2, int a, int b) {
      if (link1[a] < 0) {
         link1[a] = b;
      } else {
         link2[a] = b;
      }

      if (link1[b] < 0) {
         link1[b] = a;
      } else {
         link2[b] = a;
      }
   }

   private static void frame() {
      long f = FxFrame.currentFrame();
      long now = System.nanoTime();
      if (f != budgetFrame || now - budgetNanos >= 12000000L) {
         budgetFrame = f;
         budgetNanos = now;
         if (askedCount > 6) {
            Arrays.sort(ASKED, 0, askedCount);
            grantUpTo = ASKED[5];
         } else {
            grantUpTo = Double.MAX_VALUE;
         }

         askedCount = 0;
         grantedNow = 0;
         meshedLast = meshedNow;
         reusedLast = reusedNow;
         spheresLast = spheresNow;
         spheresNow = 0;
         reusedNow = 0;
         meshedNow = 0;
      }
   }

   private static boolean grant(double distSq) {
      frame();
      if (askedCount < ASKED.length) {
         ASKED[askedCount++] = distSq;
      }

      if (grantedNow < 6 && !(distSq > grantUpTo)) {
         grantedNow++;
         return true;
      } else {
         return false;
      }
   }

   public static String stateForTest() {
      int tris = 0;
      int most = 0;

      for (int[] t : CASES) {
         tris += t.length / 3;
         most = Math.max(most, t.length / 3);
      }

      return String.format(
         Locale.ROOT,
         "table %d triangles over 256 cases (at most %d a cell); last frame %d meshed, %d reusing their last mesh, %d as spheres (at most %d meshed)",
         tris,
         most,
         meshedLast,
         reusedLast,
         spheresLast,
         6
      );
   }

   private Liquid() {
   }

   static float[] access$402(float[] x0) {
      grid = x0;
      return x0;
   }

   static int[] access$502(int[] x0) {
      stamp = x0;
      return x0;
   }

   static int[] access$602(int[] x0) {
      cellStamp = x0;
      return x0;
   }

   static {
      int e = 0;

      for (int d = 0; d < 3; d++) {
         for (int c = 0; c < 8; c++) {
            if ((c >> d & 1) == 0) {
               EDGE_A[e] = c;
               EDGE_B[e] = c | 1 << d;
               e++;
            }
         }
      }

      int[][] faceCorners = new int[6][];
      int[][] faceEdges = new int[6][];
      int f = 0;

      for (int d = 0; d < 3; d++) {
         int u = (d + 1) % 3;
         int v = (d + 2) % 3;

         for (int side = 0; side < 2; side++) {
            int c0 = side << d;
            int c1 = c0 | 1 << u;
            int c2 = c0 | 1 << u | 1 << v;
            int c3 = c0 | 1 << v;
            faceCorners[f] = new int[]{c0, c1, c2, c3};
            faceEdges[f] = new int[]{edgeOf(c0, c1), edgeOf(c1, c2), edgeOf(c2, c3), edgeOf(c3, c0)};
            f++;
         }
      }

      for (int m = 0; m < 256; m++) {
         CASES[m] = triangulate(m, faceCorners, faceEdges);
      }

      grid = new float[0];
      stamp = new int[0];
      cellStamp = new int[0];
      CORNER = new float[8];
      CORNER_OFFSET = new int[8];
      GRAD = new double[3];
      EDGE_KEY = new int[16384];
      EDGE_VERTEX = new int[16384];
      EDGE_KEPT = new int[16384];
      EDGE_STAMP = new int[16384];
      budgetFrame = Long.MIN_VALUE;
      grantUpTo = Double.MAX_VALUE;
      ASKED = new double[256];
   }

   public static final class Field {
      public static final int MESHED = 0;
      public static final int REUSED = 1;
      public static final int SPHERES = 2;
      public static final int NOTHING = -1;
      private final double[] bx;
      private final double[] by;
      private final double[] bz;
      private final double[] br;
      private final double[] bw;
      private final double[] ax;
      private final double[] ay;
      private final double[] az;
      private final double[] sq;
      private int balls;
      private double ringX;
      private double ringY;
      private double ringZ;
      private double ringRadius;
      private double ringReach;
      private double ringWeight;
      private double ripple;
      private double rippleCos;
      private double rippleSin;
      private double level;
      private double reachRatio = 2.0;
      private double minX = Double.NaN;
      private double minY;
      private double minZ;
      private double maxX;
      private double maxY;
      private double maxZ;
      private double h;
      private double ox;
      private double oy;
      private double oz;
      private int nx;
      private int ny;
      private int nz;
      private float[] kx = new float[0];
      private float[] ky = new float[0];
      private float[] kz = new float[0];
      private float[] knx = new float[0];
      private float[] kny = new float[0];
      private float[] knz = new float[0];
      private int kept;
      private int[] keptTris = new int[0];
      private int keptTriCount;
      private boolean hasKept;
      private double anchorX;
      private double anchorY;
      private double anchorZ;
      public int lastMode = -1;
      public int lastVertices;
      public int lastTriangles;
      public int lastPoints;
      public double lastCell;
      public boolean cover;
      public boolean plain;
      private static final int[] KEPT_INDEX = new int[24576];
      private static final int SEGS = 10;
      private static final int RINGS = 6;

      public Field(int maxBalls) {
         this.bx = new double[maxBalls];
         this.by = new double[maxBalls];
         this.bz = new double[maxBalls];
         this.br = new double[maxBalls];
         this.bw = new double[maxBalls];
         this.ax = new double[maxBalls];
         this.ay = new double[maxBalls];
         this.az = new double[maxBalls];
         this.sq = new double[maxBalls];
      }

      public void begin(double reachRatio) {
         this.reachRatio = reachRatio;
         double k = 1.0 - 1.0 / (reachRatio * reachRatio);
         this.level = k * k * k;
         this.balls = 0;
         this.ringWeight = 0.0;
         this.minX = Double.NaN;
      }

      public int balls() {
         return this.balls;
      }

      public double level() {
         return this.level;
      }

      public void ball(double x, double y, double z, double r, double weight, double sx, double sy, double sz, double squash) {
         if (this.balls < this.bx.length) {
            int b = this.balls++;
            this.bx[b] = x;
            this.by[b] = y;
            this.bz[b] = z;
            this.br[b] = r * this.reachRatio;
            this.bw[b] = weight;
            double l = Math.sqrt(sx * sx + sy * sy + sz * sz);
            if (l < 1.0E-9) {
               sx = 0.0;
               sy = 1.0;
               sz = 0.0;
               l = 1.0;
            }

            this.ax[b] = sx / l;
            this.ay[b] = sy / l;
            this.az[b] = sz / l;
            this.sq[b] = squash * squash - 1.0;
         }
      }

      public void ring(double x, double y, double z, double radius, double tube, double weight, double ripple, double rippleCos, double rippleSin) {
         this.ringX = x;
         this.ringY = y;
         this.ringZ = z;
         this.ringRadius = radius;
         this.ringReach = tube * this.reachRatio;
         this.ringWeight = weight;
         this.ripple = ripple;
         this.rippleCos = rippleCos;
         this.rippleSin = rippleSin;
      }

      public void bounds(double x0, double y0, double z0, double x1, double y1, double z1) {
         this.minX = x0;
         this.minY = y0;
         this.minZ = z0;
         this.maxX = x1;
         this.maxY = y1;
         this.maxZ = z1;
      }

      public void mesh(double cell, double anchorX, double anchorY, double anchorZ, double distSq, float alpha, int tint, KeepVisible keep) {
         if (this.balls == 0 && this.ringWeight <= 0.0) {
            this.lastMode = -1;
         } else {
            this.anchorX = anchorX;
            this.anchorY = anchorY;
            this.anchorZ = anchorZ;
            if (this.plain) {
               this.spheres(alpha, tint, keep);
               Liquid.spheresNow++;
               this.lastMode = 2;
            } else if (Liquid.grant(distSq)) {
               this.march(cell, alpha, tint, keep);
               Liquid.meshedNow++;
               this.lastMode = 0;
            } else if (this.hasKept) {
               this.replay(alpha, tint, keep);
               Liquid.reusedNow++;
               this.lastMode = 1;
            } else {
               this.spheres(alpha, tint, keep);
               Liquid.spheresNow++;
               this.lastMode = 2;
            }
         }
      }

      private void layout(double cell) {
         this.h = cell;
         double x0 = Double.MAX_VALUE;
         double y0 = Double.MAX_VALUE;
         double z0 = Double.MAX_VALUE;
         double x1 = -Double.MAX_VALUE;
         double y1 = -Double.MAX_VALUE;
         double z1 = -Double.MAX_VALUE;

         for (int b = 0; b < this.balls; b++) {
            double rx = this.reachAlong(b, 0);
            double ry = this.reachAlong(b, 1);
            double rz = this.reachAlong(b, 2);
            x0 = Math.min(x0, this.bx[b] - rx);
            y0 = Math.min(y0, this.by[b] - ry);
            z0 = Math.min(z0, this.bz[b] - rz);
            x1 = Math.max(x1, this.bx[b] + rx);
            y1 = Math.max(y1, this.by[b] + ry);
            z1 = Math.max(z1, this.bz[b] + rz);
         }

         if (this.ringWeight > 0.0) {
            double reach = this.ringReach + this.ripple;
            double outer = this.ringRadius + reach;
            x0 = Math.min(x0, this.ringX - outer);
            x1 = Math.max(x1, this.ringX + outer);
            y0 = Math.min(y0, this.ringY - reach);
            y1 = Math.max(y1, this.ringY + reach);
            z0 = Math.min(z0, this.ringZ - outer);
            z1 = Math.max(z1, this.ringZ + outer);
         }

         int tries = 0;

         while (true) {
            int i0 = (int)Math.floor((x0 - this.anchorX) / this.h) - 1;
            int i1 = (int)Math.ceil((x1 - this.anchorX) / this.h) + 1;
            int j0 = (int)Math.floor((y0 - this.anchorY) / this.h) - 1;
            int j1 = (int)Math.ceil((y1 - this.anchorY) / this.h) + 1;
            int k0 = (int)Math.floor((z0 - this.anchorZ) / this.h) - 1;
            int k1 = (int)Math.ceil((z1 - this.anchorZ) / this.h) + 1;
            if (!Double.isNaN(this.minX)) {
               i0 = Math.max(i0, (int)Math.round((this.minX - this.anchorX) / this.h));
               i1 = Math.min(i1, (int)Math.round((this.maxX - this.anchorX) / this.h));
               j0 = Math.max(j0, (int)Math.round((this.minY - this.anchorY) / this.h));
               j1 = Math.min(j1, (int)Math.round((this.maxY - this.anchorY) / this.h));
               k0 = Math.max(k0, (int)Math.round((this.minZ - this.anchorZ) / this.h));
               k1 = Math.min(k1, (int)Math.round((this.maxZ - this.anchorZ) / this.h));
            }

            this.nx = Math.max(3, i1 - i0 + 1);
            this.ny = Math.max(3, j1 - j0 + 1);
            this.nz = Math.max(3, k1 - k0 + 1);
            this.ox = this.anchorX + i0 * this.h;
            this.oy = this.anchorY + j0 * this.h;
            this.oz = this.anchorZ + k0 * this.h;
            if ((long)this.nx * this.ny * this.nz <= 120000L || tries > 8) {
               int points;
               this.lastPoints = points = this.nx * this.ny * this.nz;
               this.lastCell = this.h;
               if (Liquid.grid.length < points) {
                  Liquid.access$402(new float[points]);
                  Liquid.access$502(new int[points]);
                  Liquid.access$602(new int[points]);
               }

               Liquid.stampNow++;
               if (Liquid.stampNow == Integer.MAX_VALUE) {
                  Arrays.fill(Liquid.stamp, 0);
                  Arrays.fill(Liquid.cellStamp, 0);
                  Arrays.fill(Liquid.EDGE_STAMP, 0);
                  Liquid.stampNow = 1;
               }

               for (int q = 0; q < 8; q++) {
                  Liquid.CORNER_OFFSET[q] = ((q & 1) * this.ny + (q >> 1 & 1)) * this.nz + (q >> 2 & 1);
               }

               return;
            }

            this.h *= 1.4;
            tries++;
         }
      }

      private double reachAlong(int b, int axis) {
         double a = axis == 0 ? this.ax[b] : (axis == 1 ? this.ay[b] : this.az[b]);
         double r2 = this.br[b] * this.br[b];
         double along2 = r2 / Math.max(0.05, 1.0 + this.sq[b]);
         return Math.sqrt(along2 * a * a + r2 * (1.0 - a * a));
      }

      private void sampleBall(int b) {
         double rx = this.reachAlong(b, 0);
         double ry = this.reachAlong(b, 1);
         double rz = this.reachAlong(b, 2);
         int i0 = clampTo(floorTo((this.bx[b] - rx - this.ox) / this.h), this.nx);
         int i1 = clampTo(ceilTo((this.bx[b] + rx - this.ox) / this.h), this.nx);
         int j0 = clampTo(floorTo((this.by[b] - ry - this.oy) / this.h), this.ny);
         int j1 = clampTo(ceilTo((this.by[b] + ry - this.oy) / this.h), this.ny);
         int k0 = clampTo(floorTo((this.bz[b] - rz - this.oz) / this.h), this.nz);
         int k1 = clampTo(ceilTo((this.bz[b] + rz - this.oz) / this.h), this.nz);
         double r2 = this.br[b] * this.br[b];
         double inv = 1.0 / r2;
         double w = this.bw[b];
         double s2 = this.sq[b];
         double a1 = this.ax[b];
         double a2 = this.ay[b];
         double a3 = this.az[b];

         for (int i = i0; i <= i1; i++) {
            double dx = this.ox + i * this.h - this.bx[b];

            for (int j = j0; j <= j1; j++) {
               double dy = this.oy + j * this.h - this.by[b];
               int row = (i * this.ny + j) * this.nz;

               for (int k = k0; k <= k1; k++) {
                  double dz = this.oz + k * this.h - this.bz[b];
                  int p = row + k;
                  if (Liquid.stamp[p] != Liquid.stampNow) {
                     Liquid.stamp[p] = Liquid.stampNow;
                     Liquid.grid[p] = 0.0F;
                  }

                  double along;
                  double d2;
                  if ((d2 = dx * dx + dy * dy + dz * dz + s2 * (along = dx * a1 + dy * a2 + dz * a3) * along) < r2) {
                     double f = 1.0 - d2 * inv;
                     float[] fArray = Liquid.grid;
                     fArray[p] += (float)(w * f * f * f);
                  }
               }
            }
         }
      }

      private void sampleRing() {
         double reach = this.ringReach + this.ripple;
         double outer = this.ringRadius + reach;
         int i0 = clampTo(floorTo((this.ringX - outer - this.ox) / this.h), this.nx);
         int i1 = clampTo(ceilTo((this.ringX + outer - this.ox) / this.h), this.nx);
         int j0 = clampTo(floorTo((this.ringY - reach - this.oy) / this.h), this.ny);
         int j1 = clampTo(ceilTo((this.ringY + reach - this.oy) / this.h), this.ny);
         int k0 = clampTo(floorTo((this.ringZ - outer - this.oz) / this.h), this.nz);
         int k1 = clampTo(ceilTo((this.ringZ + outer - this.oz) / this.h), this.nz);
         double r2 = this.ringReach * this.ringReach;
         double inv = 1.0 / r2;

         for (int i = i0; i <= i1; i++) {
            double dx = this.ox + i * this.h - this.ringX;

            for (int k = k0; k <= k1; k++) {
               double dz = this.oz + k * this.h - this.ringZ;
               double rho = Math.sqrt(dx * dx + dz * dz);
               double across = rho - this.ringRadius;
               if (!(Math.abs(across) >= this.ringReach) && !(rho < 1.0E-6)) {
                  double lift = this.ripple * this.rippleWave(dx / rho, dz / rho, false);

                  for (int j = j0; j <= j1; j++) {
                     int p = (i * this.ny + j) * this.nz + k;
                     if (Liquid.stamp[p] != Liquid.stampNow) {
                        Liquid.stamp[p] = Liquid.stampNow;
                        Liquid.grid[p] = 0.0F;
                     }

                     double dy;
                     double d2;
                     if ((d2 = across * across + (dy = this.oy + j * this.h - this.ringY - lift) * dy) < r2) {
                        double f = 1.0 - d2 * inv;
                        float[] fArray = Liquid.grid;
                        fArray[p] += (float)(this.ringWeight * f * f * f);
                     }
                  }
               }
            }
         }
      }

      private double rippleWave(double ca, double sa, boolean cosine) {
         double c2 = ca * ca - sa * sa;
         double s2 = 2.0 * ca * sa;
         double c4 = c2 * c2 - s2 * s2;
         double s4 = 2.0 * c2 * s2;
         double c5 = c4 * ca - s4 * sa;
         double s5 = c4 * sa + s4 * ca;
         return cosine ? c5 * this.rippleCos + s5 * this.rippleSin : s5 * this.rippleCos - c5 * this.rippleSin;
      }

      private void march(double cell, float alpha, int tint, KeepVisible keep) {
         this.layout(cell);

         for (int b = 0; b < this.balls; b++) {
            this.sampleBall(b);
         }

         if (this.ringWeight > 0.0) {
            this.sampleRing();
         }

         Liquid.edgesUsed = 0;
         this.kept = 0;
         this.keptTriCount = 0;

         for (int var13 = 0; var13 < this.balls; var13++) {
            double rx = this.reachAlong(var13, 0);
            double ry = this.reachAlong(var13, 1);
            double rz = this.reachAlong(var13, 2);
            this.cells(
               floorTo((this.bx[var13] - rx - this.ox) / this.h),
               ceilTo((this.bx[var13] + rx - this.ox) / this.h),
               floorTo((this.by[var13] - ry - this.oy) / this.h),
               ceilTo((this.by[var13] + ry - this.oy) / this.h),
               floorTo((this.bz[var13] - rz - this.oz) / this.h),
               ceilTo((this.bz[var13] + rz - this.oz) / this.h),
               alpha,
               tint,
               keep
            );
         }

         if (this.ringWeight > 0.0) {
            double reach = this.ringReach + this.ripple;
            double outer = this.ringRadius + reach;
            this.cells(
               floorTo((this.ringX - outer - this.ox) / this.h),
               ceilTo((this.ringX + outer - this.ox) / this.h),
               floorTo((this.ringY - reach - this.oy) / this.h),
               ceilTo((this.ringY + reach - this.oy) / this.h),
               floorTo((this.ringZ - outer - this.oz) / this.h),
               ceilTo((this.ringZ + outer - this.oz) / this.h),
               alpha,
               tint,
               keep
            );
         }

         this.hasKept = this.keptTriCount > 0;
         this.lastVertices = this.kept;
         this.lastTriangles = this.keptTriCount / 3;
      }

      private void cells(int i0, int i1, int j0, int j1, int k0, int k1, float alpha, int tint, KeepVisible keep) {
         i0 = Math.max(0, i0 - 1);
         j0 = Math.max(0, j0 - 1);
         k0 = Math.max(0, k0 - 1);
         i1 = Math.min(this.nx - 2, i1);
         j1 = Math.min(this.ny - 2, j1);
         k1 = Math.min(this.nz - 2, k1);
         float lv = (float)this.level;

         for (int i = i0; i <= i1; i++) {
            for (int j = j0; j <= j1; j++) {
               int row = (i * this.ny + j) * this.nz;

               for (int k = k0; k <= k1; k++) {
                  int c = row + k;
                  if (Liquid.cellStamp[c] != Liquid.stampNow) {
                     Liquid.cellStamp[c] = Liquid.stampNow;
                     int mask = 0;

                     for (int q = 0; q < 8; q++) {
                        int p = c + Liquid.CORNER_OFFSET[q];
                        float v;
                        Liquid.CORNER[q] = v = Liquid.stamp[p] == Liquid.stampNow ? Liquid.grid[p] : 0.0F;
                        if (v > lv) {
                           mask |= 1 << q;
                        }
                     }

                     if (mask != 0 && mask != 255) {
                        int[] tris = Liquid.CASES[mask];

                        for (int qx = 0; qx < tris.length; qx += 3) {
                           int va = this.vertexOn(c, i, j, k, tris[qx], lv, alpha, tint, keep);
                           int vb = this.vertexOn(c, i, j, k, tris[qx + 1], lv, alpha, tint, keep);
                           int vc = this.vertexOn(c, i, j, k, tris[qx + 2], lv, alpha, tint, keep);
                           if (va >= 0 && vb >= 0 && vc >= 0) {
                              MeshBatch.tri(va, vb, vc);
                              this.keepTri(lastKept(va), lastKept(vb), lastKept(vc));
                           }
                        }
                     }
                  }
               }
            }
         }
      }

      private static int lastKept(int v) {
         return v < 0 ? -1 : KEPT_INDEX[v];
      }

      private void keepTri(int a, int b, int c) {
         if (a >= 0 && b >= 0 && c >= 0) {
            if (this.keptTriCount + 3 > this.keptTris.length) {
               this.keptTris = Arrays.copyOf(this.keptTris, Math.max(256, this.keptTris.length * 2));
            }

            this.keptTris[this.keptTriCount++] = a;
            this.keptTris[this.keptTriCount++] = b;
            this.keptTris[this.keptTriCount++] = c;
         }
      }

      private int vertexOn(int c, int i, int j, int k, int e, float lv, float alpha, int tint, KeepVisible keep) {
         int a = Liquid.EDGE_A[e];
         int axis = Integer.numberOfTrailingZeros(Liquid.EDGE_B[e] ^ a);
         int key = (c + Liquid.CORNER_OFFSET[a]) * 3 + axis;

         int slot;
         for (slot = key * -1640531535 >>> 18; Liquid.EDGE_STAMP[slot] == Liquid.stampNow; slot = slot + 1 & 16383) {
            if (Liquid.EDGE_KEY[slot] == key) {
               return Liquid.EDGE_VERTEX[slot];
            }
         }

         if (Liquid.edgesUsed < 8192 && MeshBatch.room() >= 1) {
            int v = MeshBatch.stage(1);
            Liquid.EDGE_STAMP[slot] = Liquid.stampNow;
            Liquid.EDGE_KEY[slot] = key;
            Liquid.EDGE_VERTEX[slot] = v;
            Liquid.edgesUsed++;
            this.surfaceVertex(v, i, j, k, e, lv, alpha, tint, keep);
            return v;
         } else {
            return -1;
         }
      }

      private void surfaceVertex(int v, int i, int j, int k, int e, float lv, float alpha, int tint, KeepVisible keep) {
         int a = Liquid.EDGE_A[e];
         int b = Liquid.EDGE_B[e];
         float va = Liquid.CORNER[a];
         float vb = Liquid.CORNER[b];
         double t = Math.abs(vb - va) < 1.0E-9F ? 0.5 : (lv - va) / (vb - va);
         double xa = this.ox + (i + (a & 1)) * this.h;
         double ya = this.oy + (j + (a >> 1 & 1)) * this.h;
         double za = this.oz + (k + (a >> 2 & 1)) * this.h;
         double xb = this.ox + (i + (b & 1)) * this.h;
         double yb = this.oy + (j + (b >> 1 & 1)) * this.h;
         double zb = this.oz + (k + (b >> 2 & 1)) * this.h;
         double x = xa + (xb - xa) * t;
         double y = ya + (yb - ya) * t;
         double z = za + (zb - za) * t;
         this.gradient(x, y, z);
         double nxv = -Liquid.GRAD[0];
         double nyv = -Liquid.GRAD[1];
         double nzv = -Liquid.GRAD[2];
         double l = Math.sqrt(nxv * nxv + nyv * nyv + nzv * nzv);
         if (l < 1.0E-12) {
            nxv = 0.0;
            nyv = 1.0;
            nzv = 0.0;
            l = 1.0;
         }

         double var43;
         double var44;
         double var45;
         MeshBatch.vertex(v, x, y, z, var43 = nxv / l, var44 = nyv / l, var45 = nzv / l, this.color(x, y, z, alpha, tint, keep), 0.0F);
         if (this.kept == this.kx.length) {
            int n = Math.max(256, this.kept * 2);
            this.kx = Arrays.copyOf(this.kx, n);
            this.ky = Arrays.copyOf(this.ky, n);
            this.kz = Arrays.copyOf(this.kz, n);
            this.knx = Arrays.copyOf(this.knx, n);
            this.kny = Arrays.copyOf(this.kny, n);
            this.knz = Arrays.copyOf(this.knz, n);
         }

         this.kx[this.kept] = (float)(x - this.anchorX);
         this.ky[this.kept] = (float)(y - this.anchorY);
         this.kz[this.kept] = (float)(z - this.anchorZ);
         this.knx[this.kept] = (float)var43;
         this.kny[this.kept] = (float)var44;
         this.knz[this.kept] = (float)var45;
         KEPT_INDEX[v] = this.kept++;
      }

      private int color(double x, double y, double z, float alpha, int tint, KeepVisible keep) {
         float k = keep == null ? alpha : alpha * (this.cover ? keep.cover(x, y, z) : keep.solid(x, y, z));
         return Math.round(255.0F * Easing.clamp01(k)) << 24 | tint & 16777215;
      }

      private void gradient(double x, double y, double z) {
         double gx = 0.0;
         double gy = 0.0;
         double gz = 0.0;

         for (int b = 0; b < this.balls; b++) {
            double dx2 = x - this.bx[b];
            double dy2 = y - this.by[b];
            double dz2 = z - this.bz[b];
            double along = dx2 * this.ax[b] + dy2 * this.ay[b] + dz2 * this.az[b];
            double d22 = dx2 * dx2 + dy2 * dy2 + dz2 * dz2 + this.sq[b] * along * along;
            double r22 = this.br[b] * this.br[b];
            if (!(d22 >= r22)) {
               double f = 1.0 - d22 / r22;
               double k = -3.0 * this.bw[b] * f * f / r22 * 2.0;
               double extra = this.sq[b] * along;
               gx += k * (dx2 + extra * this.ax[b]);
               gy += k * (dy2 + extra * this.ay[b]);
               gz += k * (dz2 + extra * this.az[b]);
            }
         }

         double r2;
         double sa;
         double ca;
         double dy;
         double across;
         double d2;
         double dz;
         double dx;
         double rho;
         if (this.ringWeight > 0.0
            && (rho = Math.sqrt((dx = x - this.ringX) * dx + (dz = z - this.ringZ) * dz)) > 1.0E-6
            && (
                  d2 = (across = rho - this.ringRadius) * across
                     + (dy = y - this.ringY - this.ripple * this.rippleWave(ca = dx / rho, sa = dz / rho, false)) * dy
               )
               < (r2 = this.ringReach * this.ringReach)) {
            double f = 1.0 - d2 / r2;
            double k = -3.0 * this.ringWeight * f * f / r2 * 2.0;
            double slope = 5.0 * this.ripple * this.rippleWave(ca, sa, true) / rho;
            gx += k * (across * ca + dy * slope * sa);
            gy += k * dy;
            gz += k * (across * sa - dy * slope * ca);
         }

         Liquid.GRAD[0] = gx;
         Liquid.GRAD[1] = gy;
         Liquid.GRAD[2] = gz;
      }

      private void replay(float alpha, int tint, KeepVisible keep) {
         if (this.kept > 24576) {
            this.spheres(alpha, tint, keep);
         } else {
            int base = MeshBatch.stage(this.kept);

            for (int v = 0; v < this.kept; v++) {
               double x = this.anchorX + this.kx[v];
               double y = this.anchorY + this.ky[v];
               double z = this.anchorZ + this.kz[v];
               MeshBatch.vertex(base + v, x, y, z, this.knx[v], this.kny[v], this.knz[v], this.color(x, y, z, alpha, tint, keep), 0.0F);
            }

            for (int t = 0; t < this.keptTriCount; t += 3) {
               MeshBatch.tri(base + this.keptTris[t], base + this.keptTris[t + 1], base + this.keptTris[t + 2]);
            }

            this.lastVertices = this.kept;
            this.lastTriangles = this.keptTriCount / 3;
         }
      }

      private void spheres(float alpha, int tint, KeepVisible keep) {
         int verts = 0;
         int tris = 0;

         for (int b = 0; b < this.balls; b++) {
            if (!(this.bw[b] <= this.level)) {
               double r = this.br[b] * Math.sqrt(1.0 - Math.cbrt(this.level / this.bw[b]));
               int base = MeshBatch.stage(77);

               for (int p = 0; p <= 6; p++) {
                  double phi = Math.PI * p / 6.0;
                  double sp = Math.sin(phi);
                  double cp = Math.cos(phi);

                  for (int s = 0; s <= 10; s++) {
                     double th = (Math.PI * 2) * s / 10.0;
                     double nxv = sp * Math.cos(th);
                     double nzv = sp * Math.sin(th);
                     double x = this.bx[b] + nxv * r;
                     double y = this.by[b] + cp * r;
                     double z = this.bz[b] + nzv * r;
                     MeshBatch.vertex(base + p * 11 + s, x, y, z, nxv, cp, nzv, this.color(x, y, z, alpha, tint, keep), 0.0F);
                  }
               }

               for (int var32 = 0; var32 < 6; var32++) {
                  for (int s = 0; s < 10; s++) {
                     int a = base + var32 * 11 + s;
                     int c = a + 10 + 1;
                     MeshBatch.quad(a, a + 1, c + 1, c);
                  }
               }

               verts += 77;
               tris += 120;
            }
         }

         this.lastVertices = verts;
         this.lastTriangles = tris;
      }

      private static int floorTo(double v) {
         return (int)Math.floor(v);
      }

      private static int ceilTo(double v) {
         return (int)Math.ceil(v);
      }

      private static int clampTo(int v, int n) {
         return v < 1 ? 1 : (v > n - 2 ? n - 2 : v);
      }
   }
}
