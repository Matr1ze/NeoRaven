package xyz.daikirai.render.furiah.fx.kit;

import java.util.Arrays;
import xyz.daikirai.render.furiah.render.Camera;
import xyz.daikirai.render.furiah.render.ColorUtil;
import xyz.daikirai.render.furiah.render.Easing;
import xyz.daikirai.render.furiah.render.material.MeshBatch;

public final class Vines {
   private static final double TWO_PI = Math.PI * 2;
   public static final int SIDES = 8;
   private static final double[] SIDE_COS = new double[8];
   private static final double[] SIDE_SIN = new double[8];
   private static final double[] MID_COS = new double[8];
   private static final double[] MID_SIN = new double[8];
   private static final double FAR_SIDE = -0.37;
   public static final int COARSE_SIDES = 5;
   private static final double[] COARSE_COS = new double[5];
   private static final double[] COARSE_SIN = new double[5];
   private static final double[] COARSE_MID_COS = new double[5];
   private static final double[] COARSE_MID_SIN = new double[5];
   private static final double MID_LENGTH = 1.85;
   private static final double COARSE_MID_LENGTH = 2.0 * Math.cos(Math.PI / 5);
   private static final double[] THORN_COS = new double[]{1.0, -0.5, -0.5};
   private static final double[] THORN_SIN = new double[]{0.0, Math.sqrt(0.75), -Math.sqrt(0.75)};
   private static final int[] LAYER_PETALS = new int[]{3, 5, 5};
   private static final float[] LAYER_CLOSED = new float[]{0.1F, 0.25F, 0.42F};
   private static final float[] LAYER_OPEN = new float[]{0.5F, 0.98F, 1.38F};
   private static final float[] LAYER_LENGTH = new float[]{0.72F, 0.92F, 1.05F};
   private static final float[] LAYER_WIDTH = new float[]{0.7F, 0.82F, 0.95F};
   private static final float[] LAYER_RING = new float[]{0.06F, 0.2F, 0.34F};
   private static final float[] LAYER_LIFT = new float[]{0.26F, 0.13F, 0.0F};
   private static final double[] ROW_AT = new double[]{0.0, 0.55, 1.0};
   private static final double[] ROW_HALF = new double[]{0.16, 0.5, 0.34};
   private static final double[] ROW_CUP = new double[]{0.0, 0.14, 0.09};
   private static final double[][] PETAL_COS = new double[3][];
   private static final double[][] PETAL_SIN = new double[3][];
   public static final int WITHERED = -15856630;
   public final double[] px;
   public final double[] py;
   public final double[] pz;
   public final double[] tx;
   public final double[] ty;
   public final double[] tz;
   public final double[] nx;
   public final double[] ny;
   public final double[] nz;
   public final double[] bx;
   public final double[] by;
   public final double[] bz;
   public final double[] rad;
   public final int[] col;
   public final float[] keep;
   private final double[] rowCos = new double[3];
   private final double[] rowSin = new double[3];

   public Vines(int capacity) {
      this.px = new double[capacity];
      this.py = new double[capacity];
      this.pz = new double[capacity];
      this.tx = new double[capacity];
      this.ty = new double[capacity];
      this.tz = new double[capacity];
      this.nx = new double[capacity];
      this.ny = new double[capacity];
      this.nz = new double[capacity];
      this.bx = new double[capacity];
      this.by = new double[capacity];
      this.bz = new double[capacity];
      this.rad = new double[capacity];
      this.col = new int[capacity];
      this.keep = new float[capacity];
      Arrays.fill(this.keep, 1.0F);
   }

   public void frames(int at, int n, boolean closed, double refX, double refZ) {
      for (int j = 0; j < n; j++) {
         int a = at + (j > 0 ? j - 1 : (closed ? n - 1 : 0));
         int b = at + (j < n - 1 ? j + 1 : (closed ? 0 : n - 1));
         int p = at + j;
         double dx = this.px[b] - this.px[a];
         double dy = this.py[b] - this.py[a];
         double dz = this.pz[b] - this.pz[a];
         double l = Math.sqrt(dx * dx + dy * dy + dz * dz);
         if (l < 1.0E-9) {
            dx = 0.0;
            dy = 1.0;
            dz = 0.0;
            l = 1.0;
         }

         dx /= l;
         dy /= l;
         dz /= l;
         double ox = this.px[p] - refX;
         double oz = this.pz[p] - refZ;
         double ol = Math.sqrt(ox * ox + oz * oz);
         if (ol < 1.0E-6) {
            ox = 1.0;
            oz = 0.0;
            ol = 1.0;
         }

         double ez;
         double ey;
         double d;
         double ex;
         double el;
         if ((el = Math.sqrt((ex = (ox = ox / ol) - dx * (d = ox * dx + (oz = oz / ol) * dz)) * ex + (ey = -dy * d) * ey + (ez = oz - dz * d) * ez)) < 1.0E-6) {
            ex = 0.0;
            ey = 1.0;
            ez = 0.0;
            el = 1.0;
         }

         ex /= el;
         ey /= el;
         ez /= el;
         this.tx[p] = dx;
         this.ty[p] = dy;
         this.tz[p] = dz;
         this.nx[p] = ex;
         this.ny[p] = ey;
         this.nz[p] = ez;
         this.bx[p] = dy * ez - dz * ey;
         this.by[p] = dz * ex - dx * ez;
         this.bz[p] = dx * ey - dy * ex;
      }
   }

   public void groundFrames(int at, int n) {
      for (int j = 0; j < n; j++) {
         int a = at + (j > 0 ? j - 1 : 0);
         int b = at + (j < n - 1 ? j + 1 : n - 1);
         int p = at + j;
         double dx = this.px[b] - this.px[a];
         double dy = this.py[b] - this.py[a];
         double dz = this.pz[b] - this.pz[a];
         double l = Math.sqrt(dx * dx + dy * dy + dz * dz);
         if (l < 1.0E-9) {
            dx = 1.0;
            dy = 0.0;
            dz = 0.0;
            l = 1.0;
         }

         double ez;
         double ey;
         double ex;
         double el;
         if ((el = Math.sqrt((ex = -(dx = dx / l) * (dy = dy / l)) * ex + (ey = 1.0 - dy * dy) * ey + (ez = -(dz = dz / l) * dy) * ez)) < 1.0E-6) {
            ex = 1.0;
            ey = 0.0;
            ez = 0.0;
            el = 1.0;
         }

         ex /= el;
         ey /= el;
         ez /= el;
         this.tx[p] = dx;
         this.ty[p] = dy;
         this.tz[p] = dz;
         this.nx[p] = ex;
         this.ny[p] = ey;
         this.nz[p] = ez;
         this.bx[p] = dy * ez - dz * ey;
         this.by[p] = dz * ex - dx * ez;
         this.bz[p] = dx * ey - dy * ex;
      }
   }

   public int followGround(int at, int n, Surface ground, double ox, double oy, double oz, double lift) {
      for (int j = 0; j < n; j++) {
         int i = at + j;
         double gy = ground.at(this.px[i] + ox, this.py[i] + oy, this.pz[i] + oz);
         if (Double.isNaN(gy)) {
            return j;
         }

         this.py[i] = gy - oy + this.rad[i] + lift;
      }

      return n;
   }

   public void tube(int at, int n, boolean closed, Camera cam, float alpha) {
      this.tube(at, n, closed, cam, alpha, false);
   }

   public void tube(int at, int n, boolean closed, Camera cam, float alpha, boolean coarse) {
      int sides = coarse ? 5 : 8;
      double[] cs = coarse ? COARSE_COS : SIDE_COS;
      double[] sn = coarse ? COARSE_SIN : SIDE_SIN;
      double[] mc = coarse ? COARSE_MID_COS : MID_COS;
      double[] ms = coarse ? COARSE_MID_SIN : MID_SIN;
      double midLength = coarse ? COARSE_MID_LENGTH : 1.85;
      int base = MeshBatch.stage(n * sides);

      for (int j = 0; j < n; j++) {
         int p = at + j;
         double r = this.rad[p];
         int cc = this.col[p];
         float a = alpha * ((cc >>> 24) / 255.0F) * this.keep[p];
         int vc = Math.round(255.0F * Easing.clamp01(a)) << 24 | cc & 16777215;
         int o = base + j * sides;

         for (int k = 0; k < sides; k++) {
            double dx = this.nx[p] * cs[k] + this.bx[p] * sn[k];
            double dy = this.ny[p] * cs[k] + this.by[p] * sn[k];
            double dz = this.nz[p] * cs[k] + this.bz[p] * sn[k];
            MeshBatch.vertex(o + k, this.px[p] + dx * r, this.py[p] + dy * r, this.pz[p] + dz * r, dx, dy, dz, vc, 0.0F);
         }
      }

      int sections = closed ? n : n - 1;

      for (int j = 0; j < sections; j++) {
         int p = at + j;
         int j2 = j + 1 < n ? j + 1 : 0;
         double vx = cam.posX - this.px[p];
         double vy = cam.posY - this.py[p];
         double vz = cam.posZ - this.pz[p];
         double far = -0.37 * midLength * Math.sqrt(vx * vx + vy * vy + vz * vz);
         double vn = vx * this.nx[p] + vy * this.ny[p] + vz * this.nz[p];
         double vb = vx * this.bx[p] + vy * this.by[p] + vz * this.bz[p];
         int o = base + j * sides;
         int o2 = base + j2 * sides;

         for (int k = 0; k < sides; k++) {
            if (!(vn * mc[k] + vb * ms[k] < far)) {
               int k2 = (k + 1) % sides;
               MeshBatch.quad(o + k, o + k2, o2 + k2, o2 + k);
            }
         }
      }
   }

   public void thorn(int at, int n, double f, double around, double len, int tipColor, double wither, float alpha) {
      int j = Math.max(0, Math.min(n - 2, (int)f));
      int p = at + j;
      double w;
      int q;
      double r = this.rad[p] + (this.rad[q = at + j + 1] - this.rad[p]) * (w = f - j);
      if (!(r <= 1.0E-4)) {
         double ox = this.px[p] + (this.px[q] - this.px[p]) * w;
         double oy = this.py[p] + (this.py[q] - this.py[p]) * w;
         double oz = this.pz[p] + (this.pz[q] - this.pz[p]) * w;
         double ca = Math.cos(around);
         double sa = Math.sin(around);
         double dx = this.nx[p] * ca + this.bx[p] * sa;
         double dy = this.ny[p] * ca + this.by[p] * sa;
         double dz = this.nz[p] * ca + this.bz[p] * sa;
         double ax = dx * 0.85 - this.tx[p] * 0.5;
         double ay = dy * 0.85 - this.ty[p] * 0.5;
         double az = dz * 0.85 - this.tz[p] * 0.5;
         double al = Math.sqrt(ax * ax + ay * ay + az * az);
         ax /= al;
         ay /= al;
         az /= al;
         double br = r * 0.62;
         double ux = ay * this.tz[p] - az * this.ty[p];
         double uy = az * this.tx[p] - ax * this.tz[p];
         double uz = ax * this.ty[p] - ay * this.tx[p];
         double ul = Math.sqrt(ux * ux + uy * uy + uz * uz);
         if (!(ul < 1.0E-6)) {
            double var82;
            double var83;
            double vx = ay * (var83 = uz / ul) - az * (var82 = uy / ul);
            double var81;
            double vy = az * (var81 = ux / ul) - ax * var83;
            double vz = ax * var82 - ay * var81;
            double bxp = ox + dx * r * 0.7;
            double byp = oy + dy * r * 0.7;
            double bzp = oz + dz * r * 0.7;
            float a = alpha * (this.keep[p] + (this.keep[q] - this.keep[p]) * (float)w);
            int barkColor = this.col[p];
            int baseC = Math.round(255.0F * Easing.clamp01(a * ((barkColor >>> 24) / 255.0F))) << 24 | barkColor & 16777215;
            int tipC = baseC & 0xFF000000 | ColorUtil.lerp(tipColor, -15856630, (float)(0.5 * wither)) & 16777215;
            int base = MeshBatch.stage(4);

            for (int e = 0; e < 3; e++) {
               double cx = var81 * THORN_COS[e] + vx * THORN_SIN[e];
               double cy = var82 * THORN_COS[e] + vy * THORN_SIN[e];
               double cz = var83 * THORN_COS[e] + vz * THORN_SIN[e];
               MeshBatch.vertex(
                  base + e, bxp + cx * br, byp + cy * br, bzp + cz * br, cx * 0.9 + ax * 0.44, cy * 0.9 + ay * 0.44, cz * 0.9 + az * 0.44, baseC, 0.0F
               );
            }

            MeshBatch.vertex(base + 3, bxp + ax * len, byp + ay * len, bzp + az * len, ax, ay, az, tipC, 0.0F);
            MeshBatch.tri(base, base + 1, base + 3);
            MeshBatch.tri(base + 1, base + 2, base + 3);
            MeshBatch.tri(base + 2, base, base + 3);
         }
      }
   }

   public void rose(int r, double ox, double oy, double oz, double ax, double ay, double az, double size, double open, int roseColor, float alpha) {
      double e1x = -az;
      double e1y = 0.0;
      double e1z = ax;
      double l1 = Math.sqrt(e1x * e1x + e1y * e1y + ax * ax);
      if (l1 < 1.0E-6) {
         e1x = 1.0;
         e1y = 0.0;
         e1z = 0.0;
         l1 = 1.0;
      }

      double var62;
      double var63;
      double e2x = ay * (var63 = e1z / l1) - az * (var62 = e1y / l1);
      double var61;
      double e2y = az * (var61 = e1x / l1) - ax * var63;
      double e2z = ax * var62 - ay * var61;
      int deep = ColorUtil.lerp(-16777216, roseColor | 0xFF000000, 0.62F);

      for (int layer = 0; layer < 3; layer++) {
         int petalsHere = LAYER_PETALS[layer];
         this.petalShape(LAYER_CLOSED[layer] + (LAYER_OPEN[layer] - LAYER_CLOSED[layer]) * open, layer == 2 ? 0.35 * open : (layer == 1 ? 0.15 * open : 0.0));
         double len = size * LAYER_LENGTH[layer];
         int inner = ColorUtil.lerp(deep, roseColor | 0xFF000000, 0.35F + 0.3F * layer);
         double phi = layer * 0.6 + r * 0.9;
         double cf = Math.cos(phi);
         double sf = Math.sin(phi);
         double ring = size * LAYER_RING[layer];
         double lift = size * LAYER_LIFT[layer];

         for (int i = 0; i < petalsHere; i++) {
            double cp = cf * PETAL_COS[layer][i] - sf * PETAL_SIN[layer][i];
            double sp = sf * PETAL_COS[layer][i] + cf * PETAL_SIN[layer][i];
            double ex = var61 * cp + e2x * sp;
            double ey = var62 * cp + e2y * sp;
            double ez = var63 * cp + e2z * sp;
            this.petal(
               ox + ex * ring + ax * lift,
               oy + ey * ring + ay * lift,
               oz + ez * ring + az * lift,
               ax,
               ay,
               az,
               ex,
               ey,
               ez,
               len,
               LAYER_WIDTH[layer],
               inner,
               roseColor | 0xFF000000,
               alpha
            );
         }
      }
   }

   public void petalShape(double beta, double curl) {
      for (int k = 0; k < 3; k++) {
         double b = beta + (k == 2 ? curl : (k == 1 ? curl * 0.3 : 0.0));
         this.rowCos[k] = Math.cos(b);
         this.rowSin[k] = Math.sin(b);
      }
   }

   public void petal(
      double bx0,
      double by0,
      double bz0,
      double ax,
      double ay,
      double az,
      double ex,
      double ey,
      double ez,
      double len,
      double width,
      int baseColor,
      int tipColor,
      float alpha
   ) {
      double wx = ay * ez - az * ey;
      double wy = az * ex - ax * ez;
      double wz = ax * ey - ay * ex;
      int base = MeshBatch.stage(9);
      double qx = bx0;
      double qy = by0;
      double qz = bz0;
      int a8 = Math.round(255.0F * Easing.clamp01(alpha)) << 24;

      for (int k = 0; k < 3; k++) {
         double cb = this.rowCos[k];
         double sb = this.rowSin[k];
         double dx = ax * cb + ex * sb;
         double dy = ay * cb + ey * sb;
         double dz = az * cb + ez * sb;
         double inx = ax * sb - ex * cb;
         double iny = ay * sb - ey * cb;
         double inz = az * sb - ez * cb;
         if (k > 0) {
            double step = len * (ROW_AT[k] - ROW_AT[k - 1]);
            qx += dx * step;
            qy += dy * step;
            qz += dz * step;
         }

         double hw = len * width * ROW_HALF[k];
         double cup = len * ROW_CUP[k];
         int cc = a8 | ColorUtil.lerp(baseColor, tipColor, (float)ROW_AT[k]) & 16777215;
         int o = base + k * 3;
         vertexN(o, qx - wx * hw + inx * cup, qy - wy * hw + iny * cup, qz - wz * hw + inz * cup, inx + wx * 0.6, iny + wy * 0.6, inz + wz * 0.6, cc);
         vertexN(o + 1, qx, qy, qz, inx, iny, inz, cc);
         vertexN(o + 2, qx + wx * hw + inx * cup, qy + wy * hw + iny * cup, qz + wz * hw + inz * cup, inx - wx * 0.6, iny - wy * 0.6, inz - wz * 0.6, cc);
      }

      for (int var63 = 0; var63 < 2; var63++) {
         int o = base + var63 * 3;
         int o2 = o + 3;
         MeshBatch.quad(o, o + 1, o2 + 1, o2);
         MeshBatch.quad(o + 1, o + 2, o2 + 2, o2 + 1);
      }
   }

   public void tumblingPetal(double x, double y, double z, double spin, double tumble, double size, int color, float alpha) {
      if (!(alpha <= 0.01F)) {
         double ex = Math.cos(spin);
         double ez = Math.sin(spin);
         int deep = ColorUtil.lerp(-16777216, color, 0.7F);
         double ct = Math.cos(tumble);
         double st = Math.sin(tumble);
         double ax = -ez * st;
         double az = ex * st;
         this.petalShape(1.2, 0.25);
         this.petal(x, y, z, ax, ct, az, ex, 0.0, ez, size, 0.9, deep, color, alpha);
      }
   }

   private static void vertexN(int i, double x, double y, double z, double nx, double ny, double nz, int color) {
      double l = Math.sqrt(nx * nx + ny * ny + nz * nz);
      if (l < 1.0E-9) {
         l = 1.0;
      }

      MeshBatch.vertex(i, x, y, z, nx / l, ny / l, nz / l, color, 0.0F);
   }

   static {
      for (int k = 0; k < 8; k++) {
         SIDE_COS[k] = Math.cos(k * (Math.PI * 2) / 8.0);
         SIDE_SIN[k] = Math.sin(k * (Math.PI * 2) / 8.0);
      }

      for (int var4 = 0; var4 < 8; var4++) {
         MID_COS[var4] = SIDE_COS[var4] + SIDE_COS[(var4 + 1) % 8];
         MID_SIN[var4] = SIDE_SIN[var4] + SIDE_SIN[(var4 + 1) % 8];
      }

      for (int var5 = 0; var5 < 5; var5++) {
         COARSE_COS[var5] = Math.cos(var5 * (Math.PI * 2) / 5.0);
         COARSE_SIN[var5] = Math.sin(var5 * (Math.PI * 2) / 5.0);
      }

      for (int var6 = 0; var6 < 5; var6++) {
         COARSE_MID_COS[var6] = COARSE_COS[var6] + COARSE_COS[(var6 + 1) % 5];
         COARSE_MID_SIN[var6] = COARSE_SIN[var6] + COARSE_SIN[(var6 + 1) % 5];
      }

      for (int layer = 0; layer < 3; layer++) {
         int n = LAYER_PETALS[layer];
         PETAL_COS[layer] = new double[n];
         PETAL_SIN[layer] = new double[n];

         for (int i = 0; i < n; i++) {
            PETAL_COS[layer][i] = Math.cos((Math.PI * 2) * i / n);
            PETAL_SIN[layer][i] = Math.sin((Math.PI * 2) * i / n);
         }
      }
   }
}
