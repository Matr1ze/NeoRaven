package xyz.daikirai.render.furiah.fx.kit;

import xyz.daikirai.render.furiah.fx.physics.PhysicsClock;
import xyz.daikirai.render.furiah.render.material.MeshBatch;

public final class Chains {
   private static final int PATH = 12;
   private static final int SIDES = 6;
   public static final int VERTS = 72;
   public static final double PITCH = 0.72;
   private static final float[] MESH_X = new float[72];
   private static final float[] MESH_Y = new float[72];
   private static final float[] MESH_Z = new float[72];
   private static final float[] NORM_X = new float[72];
   private static final float[] NORM_Y = new float[72];
   private static final float[] NORM_Z = new float[72];
   private static final int COARSE_PATH = 8;
   private static final int COARSE_SIDES = 5;
   public static final int COARSE_VERTS = 40;
   private static final float[] COARSE_X = new float[40];
   private static final float[] COARSE_Y = new float[40];
   private static final float[] COARSE_Z = new float[40];
   private static final float[] COARSE_NX = new float[40];
   private static final float[] COARSE_NY = new float[40];
   private static final float[] COARSE_NZ = new float[40];
   private static final int TINY_PATH = 8;
   private static final int TINY_SIDES = 3;
   private static final int TINY_VERTS = 24;
   private static final float[] TINY_X = new float[24];
   private static final float[] TINY_Y = new float[24];
   private static final float[] TINY_Z = new float[24];
   private static final float[] TINY_NX = new float[24];
   private static final float[] TINY_NY = new float[24];
   private static final float[] TINY_NZ = new float[24];
   private static final int MICRO_PATH = 6;
   private static final int MICRO_SIDES = 3;
   private static final int MICRO_VERTS = 18;
   private static final float[] MICRO_X = new float[18];
   private static final float[] MICRO_Y = new float[18];
   private static final float[] MICRO_Z = new float[18];
   private static final float[] MICRO_NX = new float[18];
   private static final float[] MICRO_NY = new float[18];
   private static final float[] MICRO_NZ = new float[18];
   public static final int FULL = 0;
   public static final int COARSE = 1;
   public static final int TINY = 2;
   public static final int MICRO = 3;
   private static final double[] AXES = new double[9];
   private static final int LOCK_CORNER = 4;
   private static final int LOCK_RING = 16;
   private static final int KEYHOLE = 10;
   private static final double[] LOCK_X = new double[16];
   private static final double[] LOCK_Y = new double[16];
   private static final double[] LOCK_NX = new double[16];
   private static final double[] LOCK_NY = new double[16];
   private static double lpx;
   private static double lpy;
   private static double lpz;
   private static double lrx;
   private static double lry;
   private static double lrz;
   private static double lux;
   private static double luy;
   private static double luz;
   private static double lfx;
   private static double lfy;
   private static double lfz;

   private static void mesh(int path, int sides, float[] mx, float[] my, float[] mz, float[] nxs, float[] nys, float[] nzs) {
      double r = 0.2;
      double a = 0.23;
      double t = 0.07;
      double straight = 2.0 * a;
      double arc = Math.PI * r;
      double total = 2.0 * straight + 2.0 * arc;

      for (int p = 0; p < path; p++) {
         double s = p * total / path;
         double ny;
         double nx;
         double cy;
         double cx;
         if (s < straight) {
            cx = -a + s;
            cy = r;
            nx = 0.0;
            ny = 1.0;
         } else if (s < straight + arc) {
            double ang = (Math.PI / 2) - (s - straight) / r;
            nx = Math.cos(ang);
            ny = Math.sin(ang);
            cx = a + r * nx;
            cy = r * ny;
         } else if (s < 2.0 * straight + arc) {
            cx = a - (s - straight - arc);
            cy = -r;
            nx = 0.0;
            ny = -1.0;
         } else {
            double ang = (-Math.PI / 2) - (s - 2.0 * straight - arc) / r;
            nx = Math.cos(ang);
            ny = Math.sin(ang);
            cx = -a + r * nx;
            cy = r * ny;
         }

         for (int j = 0; j < sides; j++) {
            double phi = j * Math.PI * 2.0 / sides;
            double ox = nx * Math.cos(phi);
            double oy = ny * Math.cos(phi);
            double oz = Math.sin(phi);
            int i = p * sides + j;
            mx[i] = (float)(cx + ox * t);
            my[i] = (float)(cy + oy * t);
            mz[i] = (float)(oz * t);
            nxs[i] = (float)ox;
            nys[i] = (float)oy;
            nzs[i] = (float)oz;
         }
      }
   }

   private Chains() {
   }

   public static void link(
      double px,
      double py,
      double pz,
      double ax,
      double ay,
      double az,
      double bx,
      double by,
      double bz,
      double qx,
      double qy,
      double qz,
      double scale,
      int color,
      float glint
   ) {
      link(px, py, pz, ax, ay, az, bx, by, bz, qx, qy, qz, scale, color, glint, false);
   }

   public static void link(
      double px,
      double py,
      double pz,
      double ax,
      double ay,
      double az,
      double bx,
      double by,
      double bz,
      double qx,
      double qy,
      double qz,
      double scale,
      int color,
      float glint,
      boolean coarse
   ) {
      int path = coarse ? 8 : 12;
      int sides = coarse ? 5 : 6;
      int verts = path * sides;
      float[] mxs = coarse ? COARSE_X : MESH_X;
      float[] mys = coarse ? COARSE_Y : MESH_Y;
      float[] mzs = coarse ? COARSE_Z : MESH_Z;
      float[] nxs = coarse ? COARSE_NX : NORM_X;
      float[] nys = coarse ? COARSE_NY : NORM_Y;
      float[] nzs = coarse ? COARSE_NZ : NORM_Z;
      int base = MeshBatch.stage(verts);

      for (int i = 0; i < verts; i++) {
         double mx = mxs[i] * scale;
         double my = mys[i] * scale;
         double mz = mzs[i] * scale;
         MeshBatch.vertex(
            base + i,
            px + ax * mx + bx * my + qx * mz,
            py + ay * mx + by * my + qy * mz,
            pz + az * mx + bz * my + qz * mz,
            ax * nxs[i] + bx * nys[i] + qx * nzs[i],
            ay * nxs[i] + by * nys[i] + qy * nzs[i],
            az * nxs[i] + bz * nys[i] + qz * nzs[i],
            color,
            glint
         );
      }

      for (int p = 0; p < path; p++) {
         int p2 = (p + 1) % path;

         for (int j = 0; j < sides; j++) {
            int j2 = (j + 1) % sides;
            MeshBatch.quad(base + p * sides + j, base + p2 * sides + j, base + p2 * sides + j2, base + p * sides + j2);
         }
      }
   }

   public static void linkDetail(
      double px,
      double py,
      double pz,
      double ax,
      double ay,
      double az,
      double bx,
      double by,
      double bz,
      double qx,
      double qy,
      double qz,
      double scale,
      int color,
      float glint,
      int detail
   ) {
      float[] nzs;
      float[] nys;
      float[] nxs;
      float[] mzs;
      float[] mys;
      float[] mxs;
      int sides;
      int path;
      switch (detail) {
         case 0:
            path = 12;
            sides = 6;
            mxs = MESH_X;
            mys = MESH_Y;
            mzs = MESH_Z;
            nxs = NORM_X;
            nys = NORM_Y;
            nzs = NORM_Z;
            break;
         case 1:
            path = 8;
            sides = 5;
            mxs = COARSE_X;
            mys = COARSE_Y;
            mzs = COARSE_Z;
            nxs = COARSE_NX;
            nys = COARSE_NY;
            nzs = COARSE_NZ;
            break;
         case 2:
            path = 8;
            sides = 3;
            mxs = TINY_X;
            mys = TINY_Y;
            mzs = TINY_Z;
            nxs = TINY_NX;
            nys = TINY_NY;
            nzs = TINY_NZ;
            break;
         default:
            path = 6;
            sides = 3;
            mxs = MICRO_X;
            mys = MICRO_Y;
            mzs = MICRO_Z;
            nxs = MICRO_NX;
            nys = MICRO_NY;
            nzs = MICRO_NZ;
      }

      int verts = path * sides;
      int base = MeshBatch.stage(verts);

      for (int i = 0; i < verts; i++) {
         double mx = mxs[i] * scale;
         double my = mys[i] * scale;
         double mz = mzs[i] * scale;
         MeshBatch.vertex(
            base + i,
            px + ax * mx + bx * my + qx * mz,
            py + ay * mx + by * my + qy * mz,
            pz + az * mx + bz * my + qz * mz,
            ax * nxs[i] + bx * nys[i] + qx * nzs[i],
            ay * nxs[i] + by * nys[i] + qy * nzs[i],
            az * nxs[i] + bz * nys[i] + qz * nzs[i],
            color,
            glint
         );
      }

      boolean out = MeshBatch.facingOut(base, base + sides, base + sides + 1);

      for (int p = 0; p < path; p++) {
         int p2 = (p + 1) % path;

         for (int j = 0; j < sides; j++) {
            int j2 = (j + 1) % sides;
            MeshBatch.quad(base + p * sides + j, base + p2 * sides + j, base + p2 * sides + j2, base + p * sides + j2, out);
         }
      }
   }

   public static void linkBetween(
      double x0, double y0, double z0, double x1, double y1, double z1, double ux, double uy, double uz, boolean turned, double size, int color, float glint
   ) {
      linkBetween(x0, y0, z0, x1, y1, z1, ux, uy, uz, turned, size, color, glint, false);
   }

   public static void linkBetween(
      double x0,
      double y0,
      double z0,
      double x1,
      double y1,
      double z1,
      double ux,
      double uy,
      double uz,
      boolean turned,
      double size,
      int color,
      float glint,
      boolean coarse
   ) {
      if (axesBetween(x0, y0, z0, x1, y1, z1, ux, uy, uz, turned)) {
         link(
            (x0 + x1) * 0.5,
            (y0 + y1) * 0.5,
            (z0 + z1) * 0.5,
            AXES[0],
            AXES[1],
            AXES[2],
            AXES[3],
            AXES[4],
            AXES[5],
            AXES[6],
            AXES[7],
            AXES[8],
            size,
            color,
            glint,
            coarse
         );
      }
   }

   public static void linkBetween(
      double x0,
      double y0,
      double z0,
      double x1,
      double y1,
      double z1,
      double ux,
      double uy,
      double uz,
      boolean turned,
      double size,
      int color,
      float glint,
      int detail
   ) {
      if (axesBetween(x0, y0, z0, x1, y1, z1, ux, uy, uz, turned)) {
         linkDetail(
            (x0 + x1) * 0.5,
            (y0 + y1) * 0.5,
            (z0 + z1) * 0.5,
            AXES[0],
            AXES[1],
            AXES[2],
            AXES[3],
            AXES[4],
            AXES[5],
            AXES[6],
            AXES[7],
            AXES[8],
            size,
            color,
            glint,
            detail
         );
      }
   }

   private static boolean axesBetween(double x0, double y0, double z0, double x1, double y1, double z1, double ux, double uy, double uz, boolean turned) {
      double ax = x1 - x0;
      double ay = y1 - y0;
      double az = z1 - z0;
      double al = Math.sqrt(ax * ax + ay * ay + az * az);
      if (al < 1.0E-6) {
         return false;
      } else {
         double bz;
         double by;
         double d;
         double bx;
         double bl;
         if ((
               bl = Math.sqrt(
                  (bx = ux - (ax = ax / al) * (d = ux * ax + uy * (ay = ay / al) + uz * (az = az / al))) * bx
                     + (by = uy - ay * d) * by
                     + (bz = uz - az * d) * bz
               )
            )
            < 1.0E-6) {
            bx = Math.abs(ay) < 0.9 ? -az : 0.0;
            by = Math.abs(ay) < 0.9 ? 0.0 : az;
            bz = Math.abs(ay) < 0.9 ? ax : -ay;
            bl = Math.sqrt(bx * bx + by * by + bz * bz);
         }

         double var49;
         double var50;
         double qx = ay * (var49 = bz / bl) - az * (var50 = by / bl);
         double var51;
         double qy = az * (var51 = bx / bl) - ax * var49;
         double qz = ax * var50 - ay * var51;
         if (turned) {
            double tx = var51;
            double ty = var50;
            double tz = var49;
            var51 = qx;
            var50 = qy;
            var49 = qz;
            qx = -tx;
            qy = -ty;
            qz = -tz;
         }

         AXES[0] = ax;
         AXES[1] = ay;
         AXES[2] = az;
         AXES[3] = var51;
         AXES[4] = var50;
         AXES[5] = var49;
         AXES[6] = qx;
         AXES[7] = qy;
         AXES[8] = qz;
         return true;
      }
   }

   public static void padlock(
      double px, double py, double pz, double fx, double fy, double fz, double ux, double uy, double uz, double size, double open, int color, float glint
   ) {
      double rx = uy * fz - uz * fy;
      double ry = uz * fx - ux * fz;
      double rz = ux * fy - uy * fx;
      double hw = size * 0.5;
      double hh = size * 0.42;
      double hd = size * 0.2;
      lockBody(px, py, pz, rx, ry, rz, ux, uy, uz, fx, fy, fz, hw, hh, hd, size, color, glint);
      double lift = size * 0.32 * open;
      double turn = (Math.PI / 2) * open;
      double ring = hw * 0.62;
      double bar = size * 0.075;
      int segs = 12;
      int sides = 6;
      int base = MeshBatch.stage((segs + 1) * sides);
      double ct = Math.cos(turn);
      double st = Math.sin(turn);
      double ax = rx * ct + fx * st;
      double ay = ry * ct + fy * st;
      double az = rz * ct + fz * st;
      double nx0 = fx * ct - rx * st;
      double ny0 = fy * ct - ry * st;
      double nz0 = fz * ct - rz * st;
      double cx = px + ux * (hh + lift);
      double cy = py + uy * (hh + lift);
      double cz = pz + uz * (hh + lift);

      for (int i = 0; i <= segs; i++) {
         double a = Math.PI * i / segs;
         double ca = Math.cos(a);
         double sa = Math.sin(a);
         double ox = ax * ca + ux * sa;
         double oy = ay * ca + uy * sa;
         double oz = az * ca + uz * sa;
         double sx = cx + ox * ring;
         double sy = cy + oy * ring;
         double sz = cz + oz * ring;

         for (int j = 0; j < sides; j++) {
            double phi = (Math.PI * 2) * j / sides;
            double cp = Math.cos(phi);
            double sp = Math.sin(phi);
            double nx = ox * cp + nx0 * sp;
            double ny = oy * cp + ny0 * sp;
            double nz = oz * cp + nz0 * sp;
            MeshBatch.vertex(base + i * sides + j, sx + nx * bar, sy + ny * bar, sz + nz * bar, nx, ny, nz, color, glint);
         }
      }

      for (int var101 = 0; var101 < segs; var101++) {
         for (int j = 0; j < sides; j++) {
            int j2 = (j + 1) % sides;
            MeshBatch.quad(base + var101 * sides + j, base + (var101 + 1) * sides + j, base + (var101 + 1) * sides + j2, base + var101 * sides + j2);
         }
      }
   }

   private static void lockBody(
      double px,
      double py,
      double pz,
      double rx,
      double ry,
      double rz,
      double ux,
      double uy,
      double uz,
      double fx,
      double fy,
      double fz,
      double hw,
      double hh,
      double hd,
      double size,
      int color,
      float glint
   ) {
      lpx = px;
      lpy = py;
      lpz = pz;
      lrx = rx;
      lry = ry;
      lrz = rz;
      lux = ux;
      luy = uy;
      luz = uz;
      lfx = fx;
      lfy = fy;
      lfz = fz;
      double corner = size * 0.13;
      double bevel = size * 0.05;

      for (int c = 0; c < 4; c++) {
         double cx = c != 0 && c != 3 ? -(hw - corner) : hw - corner;
         double cy = c < 2 ? hh - corner : -(hh - corner);

         for (int j = 0; j < 4; j++) {
            double a = (c + j / 3.0) * Math.PI * 0.5;
            int k = c * 4 + j;
            LOCK_NX[k] = Math.cos(a);
            LOCK_NY[k] = Math.sin(a);
            LOCK_X[k] = cx + LOCK_NX[k] * corner;
            LOCK_Y[k] = cy + LOCK_NY[k] * corner;
         }
      }

      int n = 16;
      int v = MeshBatch.stage(6 * n + 2);
      double s2 = Math.sqrt(0.5);

      for (int k = 0; k < n; k++) {
         double x = LOCK_X[k];
         double y = LOCK_Y[k];
         double nx = LOCK_NX[k];
         double ny = LOCK_NY[k];
         double ix = x - nx * bevel;
         double iy = y - ny * bevel;
         double edge = hd - bevel;
         lockVertex(v + k, ix, iy, hd, 0.0, 0.0, 1.0, color, glint);
         lockVertex(v + n + k, x, y, edge, nx * s2, ny * s2, s2, color, glint);
         lockVertex(v + 2 * n + k, x, y, edge, nx, ny, 0.0, color, glint);
         lockVertex(v + 3 * n + k, x, y, -edge, nx, ny, 0.0, color, glint);
         lockVertex(v + 4 * n + k, x, y, -edge, nx * s2, ny * s2, -s2, color, glint);
         lockVertex(v + 5 * n + k, ix, iy, -hd, 0.0, 0.0, -1.0, color, glint);
      }

      int front = v + 6 * n;
      int back = front + 1;
      lockVertex(front, 0.0, 0.0, hd, 0.0, 0.0, 1.0, color, glint);
      lockVertex(back, 0.0, 0.0, -hd, 0.0, 0.0, -1.0, color, glint);

      for (int k = 0; k < n; k++) {
         int k2 = (k + 1) % n;
         MeshBatch.tri(front, v + k, v + k2);
         MeshBatch.quad(v + k, v + n + k, v + n + k2, v + k2);
         MeshBatch.quad(v + 2 * n + k, v + 3 * n + k, v + 3 * n + k2, v + 2 * n + k2);
         MeshBatch.quad(v + 4 * n + k, v + 5 * n + k, v + 5 * n + k2, v + 4 * n + k2);
         MeshBatch.tri(back, v + 5 * n + k2, v + 5 * n + k);
      }

      int dark = color & 0xFF000000 | (color >> 16 & 0xFF) * 3 / 16 << 16 | (color >> 8 & 0xFF) * 3 / 16 << 8 | (color & 0xFF) * 3 / 16;
      double z = hd + size * 0.004;
      double hole = size * 0.075;
      double holeY = size * 0.05;
      int kv = MeshBatch.stage(15);
      lockVertex(kv, 0.0, holeY, z, 0.0, 0.0, 1.0, dark, 0.0F);

      for (int i = 0; i < 10; i++) {
         double a = i * Math.PI * 2.0 / 10.0;
         lockVertex(kv + 1 + i, Math.cos(a) * hole, holeY + Math.sin(a) * hole, z, 0.0, 0.0, 1.0, dark, 0.0F);
      }

      for (int var58 = 0; var58 < 10; var58++) {
         MeshBatch.tri(kv, kv + 1 + var58, kv + 1 + (var58 + 1) % 10);
      }

      int sv = kv + 10 + 1;
      lockVertex(sv, -size * 0.03, holeY, z, 0.0, 0.0, 1.0, dark, 0.0F);
      lockVertex(sv + 1, size * 0.03, holeY, z, 0.0, 0.0, 1.0, dark, 0.0F);
      lockVertex(sv + 2, size * 0.045, -size * 0.16, z, 0.0, 0.0, 1.0, dark, 0.0F);
      lockVertex(sv + 3, -size * 0.045, -size * 0.16, z, 0.0, 0.0, 1.0, dark, 0.0F);
      MeshBatch.quad(sv, sv + 1, sv + 2, sv + 3);
   }

   private static void lockVertex(int i, double x, double y, double z, double nx, double ny, double nz, int color, float glint) {
      MeshBatch.vertex(
         i,
         lpx + lrx * x + lux * y + lfx * z,
         lpy + lry * x + luy * y + lfy * z,
         lpz + lrz * x + luz * y + lfz * z,
         lrx * nx + lux * ny + lfx * nz,
         lry * nx + luy * ny + lfy * nz,
         lrz * nx + luz * ny + lfz * nz,
         color,
         glint
      );
   }

   public static void box(
      double cx,
      double cy,
      double cz,
      double ax,
      double ay,
      double az,
      double bx,
      double by,
      double bz,
      double qx,
      double qy,
      double qz,
      double ha,
      double hb,
      double hq,
      int color,
      float glint
   ) {
      int v = MeshBatch.stage(24);

      for (int axis = 0; axis < 3; axis++) {
         for (int sign = -1; sign <= 1; sign += 2) {
            double nx = axis == 0 ? ax : (axis == 1 ? bx : qx);
            double ny = axis == 0 ? ay : (axis == 1 ? by : qy);
            double nz = axis == 0 ? az : (axis == 1 ? bz : qz);
            nx *= sign;
            ny *= sign;
            nz *= sign;
            double h = axis == 0 ? ha : (axis == 1 ? hb : hq);
            double ux = axis == 0 ? bx : ax;
            double uy = axis == 0 ? by : ay;
            double uz = axis == 0 ? bz : az;
            double hu = axis == 0 ? hb : ha;
            double wx = axis == 2 ? bx : qx;
            double wy = axis == 2 ? by : qy;
            double wz = axis == 2 ? bz : qz;
            double hw = axis == 2 ? hb : hq;
            double fx = cx + nx * h;
            double fy = cy + ny * h;
            double fz = cz + nz * h;
            int f = v;

            for (int k = 0; k < 4; k++) {
               double su = k != 1 && k != 2 ? -1.0 : 1.0;
               double sw = k >= 2 ? 1.0 : -1.0;
               MeshBatch.vertex(
                  v++, fx + ux * hu * su + wx * hw * sw, fy + uy * hu * su + wy * hw * sw, fz + uz * hu * su + wz * hw * sw, nx, ny, nz, color, glint
               );
            }

            MeshBatch.quad(f, f + 1, f + 2, f + 3);
         }
      }
   }

   static {
      mesh(12, 6, MESH_X, MESH_Y, MESH_Z, NORM_X, NORM_Y, NORM_Z);
      mesh(8, 5, COARSE_X, COARSE_Y, COARSE_Z, COARSE_NX, COARSE_NY, COARSE_NZ);
      mesh(8, 3, TINY_X, TINY_Y, TINY_Z, TINY_NX, TINY_NY, TINY_NZ);
      mesh(6, 3, MICRO_X, MICRO_Y, MICRO_Z, MICRO_NX, MICRO_NY, MICRO_NZ);
   }

   public static final class Rope {
      public static final int MAX = 40;
      private static final double GRAVITY = 19.6;
      private static final double DAMPING = 0.995;
      private static final double BOUNCE = 0.35;
      private static final double FRICTION = 0.6;
      private static final int ITERATIONS = 6;
      private final double[] x = new double[40];
      private final double[] y = new double[40];
      private final double[] z = new double[40];
      private final double[] lx = new double[40];
      private final double[] ly = new double[40];
      private final double[] lz = new double[40];
      private final double[] px = new double[40];
      private final double[] py = new double[40];
      private final double[] pz = new double[40];
      private final boolean[] pinned = new boolean[40];
      private final boolean[] loose = new boolean[40];
      private final boolean[] settled = new boolean[40];
      private final int[] bounces = new int[40];
      private final PhysicsClock clock = new PhysicsClock();
      private int n;
      private double rest;
      private double gravity = 19.6;

      public Chains.Rope reset(int count, double rest, double x0, double y0, double z0, double dx, double dy, double dz) {
         this.n = Math.max(2, Math.min(40, count));
         this.rest = rest;
         double l = Math.sqrt(dx * dx + dy * dy + dz * dz);
         if (l < 1.0E-9) {
            dx = 0.0;
            dy = -1.0;
            dz = 0.0;
            l = 1.0;
         }

         for (int i = 0; i < this.n; i++) {
            double t = i * rest / l;
            this.lx[i] = this.px[i] = x0 + dx * t;
            this.x[i] = this.px[i];
            this.ly[i] = this.py[i] = y0 + dy * t;
            this.y[i] = this.py[i];
            this.lz[i] = this.pz[i] = z0 + dz * t;
            this.z[i] = this.pz[i];
            this.settled[i] = false;
            this.loose[i] = false;
            this.pinned[i] = false;
            this.bounces[i] = 0;
         }

         return this;
      }

      public void place(int i, double nx, double ny, double nz) {
         this.lx[i] = this.px[i] = nx;
         this.x[i] = this.px[i];
         this.ly[i] = this.py[i] = ny;
         this.y[i] = this.py[i];
         this.lz[i] = this.pz[i] = nz;
         this.z[i] = this.pz[i];
      }

      public void push(int i, double vx, double vy, double vz) {
         this.lx[i] = this.x[i] - vx * 0.008333333333333333;
         this.ly[i] = this.y[i] - vy * 0.008333333333333333;
         this.lz[i] = this.z[i] - vz * 0.008333333333333333;
      }

      public void pin(int i, boolean on, double ax, double ay, double az) {
         this.pinned[i] = on;
         if (on) {
            this.x[i] = ax;
            this.y[i] = ay;
            this.z[i] = az;
         }
      }

      public void loosen(int i) {
         this.loose[i] = true;
         this.pinned[i] = false;
      }

      public boolean isLoose(int i) {
         return this.loose[i];
      }

      public void gravity(double g) {
         this.gravity = g;
      }

      public int count() {
         return this.n;
      }

      public double rest() {
         return this.rest;
      }

      public void step(double dt, Surface ground) {
         int steps = this.clock.advance(dt);

         for (int s = 0; s < steps; s++) {
            System.arraycopy(this.x, 0, this.px, 0, this.n);
            System.arraycopy(this.y, 0, this.py, 0, this.n);
            System.arraycopy(this.z, 0, this.pz, 0, this.n);
            double g = this.gravity * 0.008333333333333333 * 0.008333333333333333;

            for (int i = 0; i < this.n; i++) {
               if (!this.pinned[i] && !this.settled[i]) {
                  double vx = (this.x[i] - this.lx[i]) * 0.995;
                  double vy = (this.y[i] - this.ly[i]) * 0.995;
                  double vz = (this.z[i] - this.lz[i]) * 0.995;
                  this.lx[i] = this.x[i];
                  this.ly[i] = this.y[i];
                  this.lz[i] = this.z[i];
                  this.x[i] = this.x[i] + vx;
                  this.y[i] = this.y[i] + (vy - g);
                  this.z[i] = this.z[i] + vz;
               }
            }

            for (int it = 0; it < 6; it++) {
               for (int i2 = 1; i2 < this.n; i2++) {
                  if (!this.loose[i2]) {
                     this.constrain(i2 - 1, i2);
                  }
               }
            }

            if (ground != null) {
               for (int var18 = 0; var18 < this.n; var18++) {
                  if (!this.pinned[var18] && !this.settled[var18]) {
                     this.collide(var18, ground);
                  }
               }
            }
         }
      }

      private void constrain(int a, int b) {
         double dx = this.x[b] - this.x[a];
         double dy = this.y[b] - this.y[a];
         double dz = this.z[b] - this.z[a];
         double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
         if (!(d < 1.0E-9)) {
            double diff = (d - this.rest) / d;
            boolean pa = this.pinned[a] || this.settled[a];
            boolean pb = this.pinned[b] || this.settled[b];
            if (!pa || !pb) {
               double ka = pa ? 0.0 : (pb ? 1.0 : 0.5);
               double kb = pb ? 0.0 : (pa ? 1.0 : 0.5);
               this.x[a] = this.x[a] + dx * diff * ka;
               this.y[a] = this.y[a] + dy * diff * ka;
               this.z[a] = this.z[a] + dz * diff * ka;
               this.x[b] = this.x[b] - dx * diff * kb;
               this.y[b] = this.y[b] - dy * diff * kb;
               this.z[b] = this.z[b] - dz * diff * kb;
            }
         }
      }

      private void collide(int i, Surface ground) {
         double gy = ground.at(this.x[i], this.y[i], this.z[i]);
         if (!Double.isNaN(gy)) {
            double floor = gy + this.rest * 0.12;
            if (!(this.y[i] >= floor)) {
               double vy = this.y[i] - this.ly[i];
               this.y[i] = floor;
               this.lx[i] = this.x[i] - (this.x[i] - this.lx[i]) * 0.6;
               this.lz[i] = this.z[i] - (this.z[i] - this.lz[i]) * 0.6;
               if (this.loose[i] && this.bounces[i] == 0 && vy < -0.01) {
                  this.bounces[i]++;
                  this.ly[i] = this.y[i] + vy * 0.35;
               } else {
                  this.ly[i] = this.y[i];
                  if (this.loose[i] && Math.abs(this.x[i] - this.lx[i]) + Math.abs(this.z[i] - this.lz[i]) < 1.0E-4) {
                     this.settled[i] = true;
                  }
               }
            }
         }
      }

      public double x(int i) {
         return this.px[i] + (this.x[i] - this.px[i]) * this.clock.alpha();
      }

      public double y(int i) {
         return this.py[i] + (this.y[i] - this.py[i]) * this.clock.alpha();
      }

      public double z(int i) {
         return this.pz[i] + (this.z[i] - this.pz[i]) * this.clock.alpha();
      }

      public double stretchForTest() {
         double worst = 0.0;

         for (int i = 1; i < this.n; i++) {
            if (!this.loose[i]) {
               double dx = this.x[i] - this.x[i - 1];
               double dy = this.y[i] - this.y[i - 1];
               double dz = this.z[i] - this.z[i - 1];
               worst = Math.max(worst, Math.abs(Math.sqrt(dx * dx + dy * dy + dz * dz) - this.rest));
            }
         }

         return worst;
      }
   }
}
