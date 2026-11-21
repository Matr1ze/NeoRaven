package xyz.daikirai.render.furiah.render;

public final class Polyhedra {
   private static final float[][] VERTS = new float[3][];
   private static final int[][] EDGES = new int[3][];
   private static final int[][] FACES = new int[3][];
   private static final double LX = 0.36;
   private static final double LY = 0.86;
   private static final double LZ = 0.36;
   private static final double[] PX = new double[12];
   private static final double[] PY = new double[12];
   private static final double[] PZ = new double[12];

   private Polyhedra() {
   }

   private static int[] edgesOf(int[] faces) {
      int[] out = new int[faces.length * 2];
      int count = 0;

      for (int f = 0; f < faces.length; f += 3) {
         for (int e = 0; e < 3; e++) {
            int a = faces[f + e];
            int b = faces[f + (e + 1) % 3];
            int lo = Math.min(a, b);
            int hi = Math.max(a, b);
            boolean known = false;

            for (int i = 0; i < count; i += 2) {
               if (out[i] == lo && out[i + 1] == hi) {
                  known = true;
                  break;
               }
            }

            if (!known) {
               out[count++] = lo;
               out[count++] = hi;
            }
         }
      }

      int[] edges = new int[count];
      System.arraycopy(out, 0, edges, 0, count);
      return edges;
   }

   private static int place(Polyhedra.Shape s, double cx, double cy, double cz, double radius) {
      float[] v = VERTS[s.ordinal()];
      int count = v.length / 3;

      for (int i = 0; i < count; i++) {
         double x = v[i * 3];
         double y = v[i * 3 + 1];
         double z = v[i * 3 + 2];
         PX[i] = cx + MiniMesh.rx(x, y, z) * radius;
         PY[i] = cy + MiniMesh.ry(x, y, z) * radius;
         PZ[i] = cz + MiniMesh.rz(x, y, z) * radius;
      }

      return count;
   }

   public static void faces(Polyhedra.Shape s, double cx, double cy, double cz, double radius, int color, float alpha) {
      if (!(alpha <= 0.003F) && !(radius <= 0.0)) {
         place(s, cx, cy, cz, radius);
         int[] f = FACES[s.ordinal()];

         for (int i = 0; i < f.length; i += 3) {
            int b = f[i + 1];
            int a = f[i];
            double uy = PY[b] - PY[a];
            int c = f[i + 2];
            double vz = PZ[c] - PZ[a];
            double uz = PZ[b] - PZ[a];
            double vy = PY[c] - PY[a];
            double nx = uy * vz - uz * vy;
            double vx = PX[c] - PX[a];
            double ux = PX[b] - PX[a];
            double ny = uz * vx - ux * vz;
            double nz = ux * vy - uy * vx;
            double len = Math.sqrt(nx * nx + ny * ny + nz * nz);
            double lit = len < 1.0E-12 ? 0.5 : Math.abs(nx * 0.36 + ny * 0.86 + nz * 0.36) / len;
            int shaded = ColorUtil.mulAlpha(ColorUtil.shade(color, (float)(0.6 + 0.5 * lit)), alpha);
            Vfx.triangle(PX[a], PY[a], PZ[a], PX[b], PY[b], PZ[b], PX[c], PY[c], PZ[c], shaded, shaded, shaded);
         }
      }
   }

   public static void edges(Polyhedra.Shape s, double cx, double cy, double cz, double radius, int texture, double width, int color, float alpha) {
      if (!(alpha <= 0.003F) && !(radius <= 0.0)) {
         place(s, cx, cy, cz, radius);
         Camera cam = Vfx.camera();
         double vx = cx - cam.posX;
         double vy = cy - cam.posY;
         double vz = cz - cam.posZ;
         double vl = Math.sqrt(vx * vx + vy * vy + vz * vz);
         if (vl > 1.0E-9) {
            vx /= vl;
            vy /= vl;
            vz /= vl;
         }

         int[] e = EDGES[s.ordinal()];

         for (int i = 0; i < e.length; i += 2) {
            int a = e[i];
            int b = e[i + 1];
            double mx = (PX[a] + PX[b]) * 0.5 - cx;
            double my = (PY[a] + PY[b]) * 0.5 - cy;
            double mz = (PZ[a] + PZ[b]) * 0.5 - cz;
            double depth = (mx * vx + my * vy + mz * vz) / radius;
            float k = depth > 0.0 ? (float)(1.0 - 0.5 * Math.min(1.0, depth * 1.4)) : 1.0F;
            int c = ColorUtil.mulAlpha(color, alpha * k);
            Vfx.line(texture, PX[a], PY[a], PZ[a], PX[b], PY[b], PZ[b], width, c, c);
         }
      }
   }

   public static double placedX(int i) {
      return PX[i];
   }

   public static double placedY(int i) {
      return PY[i];
   }

   public static double placedZ(int i) {
      return PZ[i];
   }

   public static int vertexCount(Polyhedra.Shape s) {
      return VERTS[s.ordinal()].length / 3;
   }

   static {
      float k = 1.0F / (float)Math.sqrt(3.0);
      float[] cube = new float[24];

      for (int i = 0; i < 8; i++) {
         cube[i * 3] = ((i & 1) == 0 ? -1 : 1) * k;
         cube[i * 3 + 1] = ((i & 2) == 0 ? -1 : 1) * k;
         cube[i * 3 + 2] = ((i & 4) == 0 ? -1 : 1) * k;
      }

      VERTS[0] = cube;
      EDGES[0] = new int[]{0, 1, 2, 3, 4, 5, 6, 7, 0, 2, 1, 3, 4, 6, 5, 7, 0, 4, 1, 5, 2, 6, 3, 7};
      FACES[0] = new int[]{0, 2, 6, 0, 6, 4, 1, 5, 7, 1, 7, 3, 0, 4, 5, 0, 5, 1, 2, 3, 7, 2, 7, 6, 0, 1, 3, 0, 3, 2, 4, 6, 7, 4, 7, 5};
      VERTS[1] = new float[]{1.0F, 0.0F, 0.0F, -1.0F, 0.0F, 0.0F, 0.0F, 1.0F, 0.0F, 0.0F, -1.0F, 0.0F, 0.0F, 0.0F, 1.0F, 0.0F, 0.0F, -1.0F};
      EDGES[1] = new int[]{0, 2, 0, 3, 0, 4, 0, 5, 1, 2, 1, 3, 1, 4, 1, 5, 2, 4, 4, 3, 3, 5, 5, 2};
      FACES[1] = new int[]{0, 2, 4, 0, 4, 3, 0, 3, 5, 0, 5, 2, 1, 4, 2, 1, 3, 4, 1, 5, 3, 1, 2, 5};
      float p = (1.0F + (float)Math.sqrt(5.0)) / 2.0F;
      float n = 1.0F / (float)Math.sqrt(1.0F + p * p);
      float[] ico = new float[]{
         -1.0F,
         p,
         0.0F,
         1.0F,
         p,
         0.0F,
         -1.0F,
         -p,
         0.0F,
         1.0F,
         -p,
         0.0F,
         0.0F,
         -1.0F,
         p,
         0.0F,
         1.0F,
         p,
         0.0F,
         -1.0F,
         -p,
         0.0F,
         1.0F,
         -p,
         p,
         0.0F,
         -1.0F,
         p,
         0.0F,
         1.0F,
         -p,
         0.0F,
         -1.0F,
         -p,
         0.0F,
         1.0F
      };
      int i = 0;

      while (i < ico.length) {
         int n2 = i++;
         ico[n2] *= n;
      }

      VERTS[2] = ico;
      FACES[2] = new int[]{
         0,
         11,
         5,
         0,
         5,
         1,
         0,
         1,
         7,
         0,
         7,
         10,
         0,
         10,
         11,
         1,
         5,
         9,
         5,
         11,
         4,
         11,
         10,
         2,
         10,
         7,
         6,
         7,
         1,
         8,
         3,
         9,
         4,
         3,
         4,
         2,
         3,
         2,
         6,
         3,
         6,
         8,
         3,
         8,
         9,
         4,
         9,
         5,
         2,
         4,
         11,
         6,
         2,
         10,
         8,
         6,
         7,
         9,
         8,
         1
      };
      EDGES[2] = edgesOf(FACES[2]);
   }

   public static enum Shape {
      CUBE,
      OCTAHEDRON,
      ICOSAHEDRON;
   }
}
