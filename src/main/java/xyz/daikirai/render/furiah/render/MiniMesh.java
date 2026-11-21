package xyz.daikirai.render.furiah.render;

public final class MiniMesh {
   private static final double LX = 0.36;
   private static final double LY = 0.86;
   private static final double LZ = 0.36;
   private static double m00;
   private static double m01;
   private static double m02;
   private static double m10;
   private static double m11;
   private static double m12;
   private static double m20;
   private static double m21;
   private static double m22;
   private static final double[] CX = new double[8];
   private static final double[] CY = new double[8];
   private static final double[] CZ = new double[8];
   private static final double[] NX = new double[8];
   private static final double[] NY = new double[8];
   private static final double[] NZ = new double[8];
   private static final int[][] FACES = new int[][]{{0, 2, 6, 4}, {1, 5, 7, 3}, {0, 4, 5, 1}, {2, 3, 7, 6}, {0, 1, 3, 2}, {4, 6, 7, 5}};
   private static final int[][] EDGES = new int[][]{
      {0, 1, 2, 4},
      {2, 3, 3, 4},
      {4, 5, 2, 5},
      {6, 7, 3, 5},
      {0, 2, 0, 4},
      {1, 3, 1, 4},
      {4, 6, 0, 5},
      {5, 7, 1, 5},
      {0, 4, 0, 2},
      {1, 5, 1, 2},
      {2, 6, 0, 3},
      {3, 7, 1, 3}
   };
   private static final boolean[] VISIBLE = new boolean[6];
   private static final double[] TX = new double[3];
   private static final double[] TY = new double[3];
   private static final double[] TZ = new double[3];
   private static double tnx;
   private static double tny;
   private static double tnz;

   private MiniMesh() {
   }

   public static void rotation(double ax, double ay, double az, double angle) {
      double len = Math.sqrt(ax * ax + ay * ay + az * az);
      if (len < 1.0E-9) {
         ax = 0.0;
         ay = 1.0;
         az = 0.0;
      } else {
         ax /= len;
         ay /= len;
         az /= len;
      }

      double c = Math.cos(angle);
      double s = Math.sin(angle);
      double t = 1.0 - c;
      m00 = t * ax * ax + c;
      m01 = t * ax * ay - s * az;
      m02 = t * ax * az + s * ay;
      m10 = t * ax * ay + s * az;
      m11 = t * ay * ay + c;
      m12 = t * ay * az - s * ax;
      m20 = t * ax * az - s * ay;
      m21 = t * ay * az + s * ax;
      m22 = t * az * az + c;
   }

   public static double rx(double x, double y, double z) {
      return m00 * x + m01 * y + m02 * z;
   }

   public static double ry(double x, double y, double z) {
      return m10 * x + m11 * y + m12 * z;
   }

   public static double rz(double x, double y, double z) {
      return m20 * x + m21 * y + m22 * z;
   }

   private static void buildCube(double cx, double cy, double cz, double half) {
      for (int i = 0; i < 8; i++) {
         double lx = (i & 1) == 0 ? -1.0 : 1.0;
         double ly = (i & 2) == 0 ? -1.0 : 1.0;
         double lz = (i & 4) == 0 ? -1.0 : 1.0;
         double wx = rx(lx, ly, lz);
         double wy = ry(lx, ly, lz);
         double wz = rz(lx, ly, lz);
         CX[i] = cx + wx * half;
         CY[i] = cy + wy * half;
         CZ[i] = cz + wz * half;
         NX[i] = wx * 0.57735;
         NY[i] = wy * 0.57735;
         NZ[i] = wz * 0.57735;
      }

      Camera cam = Vfx.camera();

      for (int f = 0; f < 6; f++) {
         int axis = f >> 1;
         double sign = (f & 1) == 0 ? -1.0 : 1.0;
         double nx = axis == 0 ? rx(sign, 0.0, 0.0) : (axis == 1 ? rx(0.0, sign, 0.0) : rx(0.0, 0.0, sign));
         double ny = axis == 0 ? ry(sign, 0.0, 0.0) : (axis == 1 ? ry(0.0, sign, 0.0) : ry(0.0, 0.0, sign));
         double nz = axis == 0 ? rz(sign, 0.0, 0.0) : (axis == 1 ? rz(0.0, sign, 0.0) : rz(0.0, 0.0, sign));
         double fx = cx + nx * half - cam.posX;
         double fy = cy + ny * half - cam.posY;
         double fz = cz + nz * half - cam.posZ;
         VISIBLE[f] = nx * fx + ny * fy + nz * fz < 0.0;
      }
   }

   private static int shadeCorner(int color, int i, float alpha) {
      double diffuse = NX[i] * 0.36 + NY[i] * 0.86 + NZ[i] * 0.36;
      float light = (float)(0.52 + 0.48 * Math.max(0.0, diffuse) + 0.12 * Math.min(0.0, diffuse));
      return ColorUtil.mulAlpha(ColorUtil.shade(color, Math.min(1.1F, light)), alpha);
   }

   public static void cubeFaces(double cx, double cy, double cz, double half, int color, float alpha) {
      if (!(alpha <= 0.003F) && !(half <= 0.0)) {
         buildCube(cx, cy, cz, half);

         for (int f = 0; f < 6; f++) {
            if (VISIBLE[f]) {
               int[] q = FACES[f];
               Vfx.quad(
                  CX[q[0]],
                  CY[q[0]],
                  CZ[q[0]],
                  CX[q[1]],
                  CY[q[1]],
                  CZ[q[1]],
                  CX[q[2]],
                  CY[q[2]],
                  CZ[q[2]],
                  CX[q[3]],
                  CY[q[3]],
                  CZ[q[3]],
                  shadeCorner(color, q[0], alpha),
                  shadeCorner(color, q[1], alpha),
                  shadeCorner(color, q[2], alpha),
                  shadeCorner(color, q[3], alpha)
               );
            }
         }
      }
   }

   public static void cubeEdges(double cx, double cy, double cz, double half, int color, float alpha, double width) {
      if (!(alpha <= 0.003F) && !(half <= 0.0)) {
         buildCube(cx, cy, cz, half);

         for (int[] e : EDGES) {
            int front = (VISIBLE[e[2]] ? 1 : 0) + (VISIBLE[e[3]] ? 1 : 0);
            float k = front == 1 ? 1.0F : (front == 2 ? 0.55F : 0.18F);
            int c = ColorUtil.mulAlpha(color, alpha * k);
            Vfx.line(Textures.LINE_CORE, CX[e[0]], CY[e[0]], CZ[e[0]], CX[e[1]], CY[e[1]], CZ[e[1]], width, c, c);
         }
      }
   }

   public static void placeShard(
      double cx, double cy, double cz, float[] local, int offset, double rX, double rY, double rZ, double uX, double uY, double uZ, double scale
   ) {
      for (int k = 0; k < 3; k++) {
         double lx = local[offset + k * 2] * scale;
         double ly = local[offset + k * 2 + 1] * scale;
         double px = rX * lx + uX * ly;
         double py = rY * lx + uY * ly;
         double pz = rZ * lx + uZ * ly;
         TX[k] = cx + rx(px, py, pz);
         TY[k] = cy + ry(px, py, pz);
         TZ[k] = cz + rz(px, py, pz);
      }

      double ay = TY[1] - TY[0];
      double bz = TZ[2] - TZ[0];
      double az = TZ[1] - TZ[0];
      double by = TY[2] - TY[0];
      tnx = ay * bz - az * by;
      double bx = TX[2] - TX[0];
      double ax = TX[1] - TX[0];
      tny = az * bx - ax * bz;
      tnz = ax * by - ay * bx;
      double len = Math.sqrt(tnx * tnx + tny * tny + tnz * tnz);
      if (len > 1.0E-12) {
         tnx /= len;
         tny /= len;
         tnz /= len;
      }
   }

   public static float shardGlint() {
      Camera cam = Vfx.camera();
      double vx = cam.posX - (TX[0] + TX[1] + TX[2]) / 3.0;
      double vy = cam.posY - (TY[0] + TY[1] + TY[2]) / 3.0;
      double vz = cam.posZ - (TZ[0] + TZ[1] + TZ[2]) / 3.0;
      double vl = Math.sqrt(vx * vx + vy * vy + vz * vz);
      if (vl < 1.0E-9) {
         return 0.0F;
      } else {
         double hx = 0.36 + vx / vl;
         double hy = 0.86 + vy / vl;
         double hz = 0.36 + vz / vl;
         double hl = Math.sqrt(hx * hx + hy * hy + hz * hz);
         double d = Math.abs((tnx * hx + tny * hy + tnz * hz) / hl);
         return (float)Math.pow(d, 24.0);
      }
   }

   public static void shardFace(int color, float alpha) {
      if (!(alpha <= 0.003F)) {
         float light = (float)(0.6 + 0.4 * Math.abs(tnx * 0.36 + tny * 0.86 + tnz * 0.36));
         int c = ColorUtil.mulAlpha(ColorUtil.shade(color, light), alpha);
         int c2 = ColorUtil.mulAlpha(ColorUtil.brighten(ColorUtil.shade(color, light), 0.35F), alpha);
         Vfx.triangle(TX[0], TY[0], TZ[0], TX[1], TY[1], TZ[1], TX[2], TY[2], TZ[2], c2, c, c);
      }
   }

   public static void shardEdges(int color, float alpha, double width) {
      if (!(alpha <= 0.003F)) {
         int c = ColorUtil.mulAlpha(color, alpha);

         for (int k = 0; k < 3; k++) {
            int n = (k + 1) % 3;
            Vfx.line(Textures.LINE_CORE, TX[k], TY[k], TZ[k], TX[n], TY[n], TZ[n], width, c, c);
         }
      }
   }

   public static double shardCenterX() {
      return (TX[0] + TX[1] + TX[2]) / 3.0;
   }

   public static double shardCenterY() {
      return (TY[0] + TY[1] + TY[2]) / 3.0;
   }

   public static double shardCenterZ() {
      return (TZ[0] + TZ[1] + TZ[2]) / 3.0;
   }
}
