package xyz.daikirai.render.furiah.render;

import xyz.daikirai.render.furiah.fx.Rng;

public final class Procedural {
   private static final double[] SPAN_X = new double[1024];
   private static final double[] SPAN_Y = new double[1024];
   private static final double[] SPAN_Z = new double[1024];
   private static final double[] CX2 = new double[2];
   private static final double[] CY2 = new double[2];
   private static final double[] CZ2 = new double[2];

   private Procedural() {
   }

   private static float lattice(int i, int seed) {
      return Rng.hash(i, seed) * 2.0F - 1.0F;
   }

   public static float noise(float x, int seed) {
      int i = (int)Math.floor(x);
      float f = x - i;
      float u = f * f * (3.0F - 2.0F * f);
      float a = lattice(i, seed);
      float b = lattice(i + 1, seed);
      return a + (b - a) * u;
   }

   public static float fbm(float x, int seed) {
      return 0.6F * noise(x, seed) + 0.28F * noise(x * 2.07F + 3.1F, seed + 17) + 0.12F * noise(x * 4.3F + 7.7F, seed + 31);
   }

   public static int jagged(Rng rng, int offset, double[] cx, double[] cy, double[] cz, int controls, int depth, double jag, boolean flat) {
      int per = 1 << depth;
      int out = offset;

      for (int s = 0; s + 1 < controls; s++) {
         SPAN_X[0] = cx[s];
         SPAN_Y[0] = cy[s];
         SPAN_Z[0] = cz[s];
         SPAN_X[per] = cx[s + 1];
         SPAN_Y[per] = cy[s + 1];
         SPAN_Z[per] = cz[s + 1];
         double dx = cx[s + 1] - cx[s];
         double dy = cy[s + 1] - cy[s];
         double dz = cz[s + 1] - cz[s];
         double amp = Math.sqrt(dx * dx + dy * dy + dz * dz) * jag;

         for (int step = per; step > 1; step >>= 1) {
            int half;
            for (int i = half = step >> 1; i < per; i += step) {
               int a = i - half;
               int b = i + half;
               double sx = SPAN_X[b] - SPAN_X[a];
               double sy = SPAN_Y[b] - SPAN_Y[a];
               double sz = SPAN_Z[b] - SPAN_Z[a];
               double pz;
               double py;
               double px;
               if (flat) {
                  px = -sz;
                  py = 0.0;
                  pz = sx;
               } else {
                  double rx = rng.signed();
                  double ry = rng.signed();
                  double rz = rng.signed();
                  double len2 = sx * sx + sy * sy + sz * sz;
                  double k = len2 < 1.0E-12 ? 0.0 : (rx * sx + ry * sy + rz * sz) / len2;
                  px = rx - sx * k;
                  py = ry - sy * k;
                  pz = rz - sz * k;
               }

               double pl = Math.sqrt(px * px + py * py + pz * pz);
               double push = pl < 1.0E-12 ? 0.0 : amp * rng.signed() / pl;
               SPAN_X[i] = (SPAN_X[a] + SPAN_X[b]) * 0.5 + px * push;
               SPAN_Y[i] = (SPAN_Y[a] + SPAN_Y[b]) * 0.5 + py * push;
               SPAN_Z[i] = (SPAN_Z[a] + SPAN_Z[b]) * 0.5 + pz * push;
            }

            amp *= 0.5;
         }

         int first;
         for (int i = first = s == 0 ? 0 : 1; i <= per && out < 1024; i++) {
            Vfx.X[out] = SPAN_X[i];
            Vfx.Y[out] = SPAN_Y[i];
            Vfx.Z[out] = SPAN_Z[i];
            out++;
         }
      }

      return out - offset;
   }

   public static int bolt(Rng rng, int offset, double x0, double y0, double z0, double x1, double y1, double z1, int depth, double jag) {
      CX2[0] = x0;
      CY2[0] = y0;
      CZ2[0] = z0;
      CX2[1] = x1;
      CY2[1] = y1;
      CZ2[1] = z1;
      return jagged(rng, offset, CX2, CY2, CZ2, 2, depth, jag, false);
   }

   public static void tendril(int offset, int n, double x, double y, double z, double angle, double step, float wiggle, float freq, int seed) {
      double px = x;
      double pz = z;

      for (int i = 0; i < n && offset + i < 1024; i++) {
         Vfx.X[offset + i] = px;
         Vfx.Y[offset + i] = y;
         Vfx.Z[offset + i] = pz;
         double a = angle + wiggle * fbm((float)(i * step * freq), seed);
         px += Math.cos(a) * step;
         pz += Math.sin(a) * step;
      }
   }
}
