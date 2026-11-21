package xyz.daikirai.render.furiah.fx;

public final class Noise3 {
   private static final float[] GRAD = new float[]{
      1.0F,
      1.0F,
      0.0F,
      -1.0F,
      1.0F,
      0.0F,
      1.0F,
      -1.0F,
      0.0F,
      -1.0F,
      -1.0F,
      0.0F,
      1.0F,
      0.0F,
      1.0F,
      -1.0F,
      0.0F,
      1.0F,
      1.0F,
      0.0F,
      -1.0F,
      -1.0F,
      0.0F,
      -1.0F,
      0.0F,
      1.0F,
      1.0F,
      0.0F,
      -1.0F,
      1.0F,
      0.0F,
      1.0F,
      -1.0F,
      0.0F,
      -1.0F,
      -1.0F
   };
   private static final float[] D = new float[9];

   private Noise3() {
   }

   private static int grad(int x, int y, int z, int seed) {
      int h = Rng.mix(x * 668265261 ^ y * 374761393 ^ z * -1640531535 ^ seed * -2048144789);
      return (h >>> 8) % 12 * 3;
   }

   public static float noise(float x, float y, float z, int seed, float[] d, int o) {
      int ix = floor(x);
      int iy = floor(y);
      int iz = floor(z);
      float fx = x - ix;
      float fy = y - iy;
      float fz = z - iz;
      float ux = fx * fx * fx * (fx * (fx * 6.0F - 15.0F) + 10.0F);
      float uy = fy * fy * fy * (fy * (fy * 6.0F - 15.0F) + 10.0F);
      float uz = fz * fz * fz * (fz * (fz * 6.0F - 15.0F) + 10.0F);
      float dux = 30.0F * fx * fx * (fx * (fx - 2.0F) + 1.0F);
      float duy = 30.0F * fy * fy * (fy * (fy - 2.0F) + 1.0F);
      float duz = 30.0F * fz * fz * (fz * (fz - 2.0F) + 1.0F);
      int a = grad(ix, iy, iz, seed);
      int b = grad(ix + 1, iy, iz, seed);
      int c = grad(ix, iy + 1, iz, seed);
      int dd = grad(ix + 1, iy + 1, iz, seed);
      int e = grad(ix, iy, iz + 1, seed);
      int f = grad(ix + 1, iy, iz + 1, seed);
      int g = grad(ix, iy + 1, iz + 1, seed);
      int h = grad(ix + 1, iy + 1, iz + 1, seed);
      float va = GRAD[a] * fx + GRAD[a + 1] * fy + GRAD[a + 2] * fz;
      float vb = GRAD[b] * (fx - 1.0F) + GRAD[b + 1] * fy + GRAD[b + 2] * fz;
      float vc = GRAD[c] * fx + GRAD[c + 1] * (fy - 1.0F) + GRAD[c + 2] * fz;
      float vd = GRAD[dd] * (fx - 1.0F) + GRAD[dd + 1] * (fy - 1.0F) + GRAD[dd + 2] * fz;
      float ve = GRAD[e] * fx + GRAD[e + 1] * fy + GRAD[e + 2] * (fz - 1.0F);
      float vf = GRAD[f] * (fx - 1.0F) + GRAD[f + 1] * fy + GRAD[f + 2] * (fz - 1.0F);
      float vg = GRAD[g] * fx + GRAD[g + 1] * (fy - 1.0F) + GRAD[g + 2] * (fz - 1.0F);
      float vh = GRAD[h] * (fx - 1.0F) + GRAD[h + 1] * (fy - 1.0F) + GRAD[h + 2] * (fz - 1.0F);
      float k1 = vb - va;
      float k2 = vc - va;
      float k3 = ve - va;
      float k4 = va - vb - vc + vd;
      float k5 = va - vc - ve + vg;
      float k6 = va - vb - ve + vf;
      float k7 = -va + vb + vc - vd + ve - vf - vg + vh;
      float value = va + ux * k1 + uy * k2 + uz * k3 + ux * uy * k4 + uy * uz * k5 + uz * ux * k6 + ux * uy * uz * k7;
      if (d != null) {
         for (int axis = 0; axis < 3; axis++) {
            float ga = GRAD[a + axis];
            float gb = GRAD[b + axis];
            float gc = GRAD[c + axis];
            float gd = GRAD[dd + axis];
            float ge = GRAD[e + axis];
            float gf = GRAD[f + axis];
            float gg = GRAD[g + axis];
            float gh = GRAD[h + axis];
            d[o + axis] = ga
               + ux * (gb - ga)
               + uy * (gc - ga)
               + uz * (ge - ga)
               + ux * uy * (ga - gb - gc + gd)
               + uy * uz * (ga - gc - ge + gg)
               + uz * ux * (ga - gb - ge + gf)
               + ux * uy * uz * (-ga + gb + gc - gd + ge - gf - gg + gh);
         }

         d[o] += dux * (k1 + uy * k4 + uz * k6 + uy * uz * k7);
         int n2 = o + 1;
         d[n2] += duy * (k2 + uz * k5 + ux * k4 + uz * ux * k7);
         int n3 = o + 2;
         d[n3] += duz * (k3 + ux * k6 + uy * k5 + ux * uy * k7);
      }

      return value;
   }

   public static void curl(float x, float y, float z, int seed, float[] out) {
      noise(x, y, z, seed, D, 0);
      noise(x + 31.7F, y - 12.9F, z + 7.3F, seed + 101, D, 3);
      noise(x - 19.1F, y + 45.2F, z - 23.8F, seed + 211, D, 6);
      out[0] = D[7] - D[5];
      out[1] = D[2] - D[6];
      out[2] = D[3] - D[1];
   }

   private static int floor(float v) {
      int i = (int)v;
      return v < i ? i - 1 : i;
   }
}
