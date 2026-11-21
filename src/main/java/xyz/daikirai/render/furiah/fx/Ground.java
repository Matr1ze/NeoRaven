package xyz.daikirai.render.furiah.fx;

import xyz.daikirai.render.furiah.render.ColorUtil;
import xyz.daikirai.render.furiah.render.Vfx;
import xyz.daikirai.render.furiah.setting.ThemeTint;

public final class Ground {
   public static final double LIFT = 0.012;
   private static final double[] IX = new double[1024];
   private static final double[] IY = new double[1024];
   private static final double[] IZ = new double[1024];
   private static final double[] OX = new double[1024];
   private static final double[] OY = new double[1024];
   private static final double[] OZ = new double[1024];
   private static final float[] IA = new float[1024];
   private static final float[] OA = new float[1024];
   private static boolean lastValid;
   public static float lift;
   private static final int GRID = 576;
   private static final double[] GX = new double[576];
   private static final double[] GY = new double[576];
   private static final double[] GZ = new double[576];
   private static final float[] GU = new float[576];
   private static final float[] GV = new float[576];
   private static final int[] GA = new int[576];

   private Ground() {
   }

   private static double height(Terrain.HeightField hf, double x, double z) {
      double h = hf.at(x, z);
      if (Double.isNaN(h)) {
         lastValid = false;
         return hf.baseY() + 0.012;
      } else {
         lastValid = true;
         return h + 0.012;
      }
   }

   public static float loop(float p) {
      p -= (float)Math.floor(p);
      return 1.0F - Math.abs(2.0F * p - 1.0F);
   }

   private static int colorAt(ThemeTint color, int fallback, float t) {
      int c = color != null ? color.getColor(t) : fallback;
      return lift > 0.0F ? ColorUtil.lerp(c, c | 16777215, lift) : c;
   }

   public static void band(
      FxFrame f,
      Terrain.HeightField hf,
      int tex,
      double cx,
      double cz,
      double rIn,
      double rOut,
      double a0,
      double span,
      int segments,
      ThemeTint color,
      int staticColor,
      float alpha,
      float tShift
   ) {
      if (!(alpha <= 0.003F) && !(rOut <= 0.0)) {
         segments = Math.max(1, Math.min(segments, 1023));
         boolean full = span >= 6.283184307179586;

         for (int i = 0; i <= segments; i++) {
            double a = a0 + span * i / segments;
            double cos = Math.cos(a);
            double sin = Math.sin(a);
            double ix = cx + cos * rIn;
            double iz = cz + sin * rIn;
            double ox = cx + cos * rOut;
            double oz = cz + sin * rOut;
            IX[i] = ix - f.viewX;
            IZ[i] = iz - f.viewZ;
            IY[i] = height(hf, ix, iz) - f.viewY;
            IA[i] = lastValid ? 1.0F : 0.0F;
            OX[i] = ox - f.viewX;
            OZ[i] = oz - f.viewZ;
            OY[i] = height(hf, ox, oz) - f.viewY;
            OA[i] = lastValid ? 1.0F : 0.0F;
         }

         for (int var37 = 0; var37 < segments; var37++) {
            float t0 = (float)var37 / segments;
            float t1 = (float)(var37 + 1) / segments;
            float p0 = full ? loop(t0 + tShift) : t0;
            float p1 = full ? loop(t1 + tShift) : t1;
            int c0 = ColorUtil.mulAlpha(colorAt(color, staticColor, p0), alpha);
            int c1 = ColorUtil.mulAlpha(colorAt(color, staticColor, p1), alpha);
            Vfx.quadUV(
               tex,
               IX[var37],
               IY[var37],
               IZ[var37],
               t0,
               0.0,
               ColorUtil.mulAlpha(c0, IA[var37]),
               OX[var37],
               OY[var37],
               OZ[var37],
               t0,
               1.0,
               ColorUtil.mulAlpha(c0, OA[var37]),
               OX[var37 + 1],
               OY[var37 + 1],
               OZ[var37 + 1],
               t1,
               1.0,
               ColorUtil.mulAlpha(c1, OA[var37 + 1]),
               IX[var37 + 1],
               IY[var37 + 1],
               IZ[var37 + 1],
               t1,
               0.0,
               ColorUtil.mulAlpha(c1, IA[var37 + 1])
            );
         }
      }
   }

   public static void ring(
      FxFrame f,
      Terrain.HeightField hf,
      int tex,
      double cx,
      double cz,
      double radius,
      double width,
      int segments,
      ThemeTint color,
      int staticColor,
      float alpha,
      float tShift
   ) {
      band(f, hf, tex, cx, cz, Math.max(0.0, radius - width * 0.5), radius + width * 0.5, 0.0, Math.PI * 2, segments, color, staticColor, alpha, tShift);
   }

   public static void disc(FxFrame f, Terrain.HeightField hf, int tex, double cx, double cz, double half, double rotation, int color, int rings, int segments) {
      if (color >>> 24 != 0 && !(half <= 0.0)) {
         if (hf.isFlat()) {
            double c = Math.cos(rotation) * half;
            double s = Math.sin(rotation) * half;
            Vfx.flatQuad(tex, cx - f.viewX, hf.baseY() + 0.012 - f.viewY, cz - f.viewZ, c, s, -s, c, 0.0F, 0.0F, 1.0F, 1.0F, color);
         } else {
            rings = Math.min(rings, 8);
            segments = Math.min(segments, 64);
            double reach = half * 1.4142;
            double rc = Math.cos(rotation);
            double rs = Math.sin(rotation);

            for (int r = 0; r <= rings; r++) {
               double rad = reach * r / rings;

               for (int s = 0; s < segments; s++) {
                  double a = (Math.PI * 2) * s / segments;
                  double lx = Math.cos(a) * rad;
                  double lz = Math.sin(a) * rad;
                  double wx = cx + lx * rc - lz * rs;
                  double wz = cz + lx * rs + lz * rc;
                  int k = r * segments + s;
                  GX[k] = wx - f.viewX;
                  GZ[k] = wz - f.viewZ;
                  GY[k] = height(hf, wx, wz) - f.viewY;
                  GA[k] = lastValid ? color : color & 16777215;
                  GU[k] = (float)(0.5 + lx / (2.0 * half));
                  GV[k] = (float)(0.5 + lz / (2.0 * half));
               }
            }

            for (int var37 = 0; var37 < rings; var37++) {
               for (int s = 0; s < segments; s++) {
                  int s1 = (s + 1) % segments;
                  int a = var37 * segments + s;
                  int b = (var37 + 1) * segments + s;
                  int c = (var37 + 1) * segments + s1;
                  int d = var37 * segments + s1;
                  Vfx.quadUV(
                     tex,
                     GX[a],
                     GY[a],
                     GZ[a],
                     GU[a],
                     GV[a],
                     GA[a],
                     GX[b],
                     GY[b],
                     GZ[b],
                     GU[b],
                     GV[b],
                     GA[b],
                     GX[c],
                     GY[c],
                     GZ[c],
                     GU[c],
                     GV[c],
                     GA[c],
                     GX[d],
                     GY[d],
                     GZ[d],
                     GU[d],
                     GV[d],
                     GA[d]
                  );
               }
            }
         }
      }
   }

   public static void decal(
      FxFrame f,
      Terrain.HeightField hf,
      int tex,
      float u0,
      float v0,
      float u1,
      float v1,
      double cx,
      double cz,
      double half,
      double rotation,
      int color,
      int cells
   ) {
      if (color >>> 24 != 0 && !(half <= 0.0)) {
         double rc = Math.cos(rotation);
         double rs = Math.sin(rotation);
         if (hf.isFlat()) {
            double c = rc * half;
            double s = rs * half;
            Vfx.flatQuad(tex, cx - f.viewX, hf.baseY() + 0.012 - f.viewY, cz - f.viewZ, c, s, -s, c, u0, v0, u1, v1, color);
         } else {
            cells = Math.max(1, Math.min(cells, 16));
            int n = cells + 1;

            for (int j = 0; j < n; j++) {
               for (int i = 0; i < n; i++) {
                  double lx = ((double)i / cells * 2.0 - 1.0) * half;
                  double lz = ((double)j / cells * 2.0 - 1.0) * half;
                  double wx = cx + lx * rc - lz * rs;
                  double wz = cz + lx * rs + lz * rc;
                  int k = j * n + i;
                  GX[k] = wx - f.viewX;
                  GZ[k] = wz - f.viewZ;
                  GY[k] = height(hf, wx, wz) - f.viewY;
                  GA[k] = lastValid ? color : color & 16777215;
                  GU[k] = u0 + (u1 - u0) * i / cells;
                  GV[k] = v0 + (v1 - v0) * j / cells;
               }
            }

            for (int var35 = 0; var35 < cells; var35++) {
               for (int i = 0; i < cells; i++) {
                  int a = var35 * n + i;
                  int b = a + 1;
                  int c = a + n + 1;
                  int d = a + n;
                  Vfx.quadUV(
                     tex,
                     GX[a],
                     GY[a],
                     GZ[a],
                     GU[a],
                     GV[a],
                     GA[a],
                     GX[b],
                     GY[b],
                     GZ[b],
                     GU[b],
                     GV[b],
                     GA[b],
                     GX[c],
                     GY[c],
                     GZ[c],
                     GU[c],
                     GV[c],
                     GA[c],
                     GX[d],
                     GY[d],
                     GZ[d],
                     GU[d],
                     GV[d],
                     GA[d]
                  );
               }
            }
         }
      }
   }

   public static void line(FxFrame f, Terrain.HeightField hf, int tex, double x1, double z1, double x2, double z2, double width, int c1, int c2, int parts) {
      if ((c1 | c2) >>> 24 != 0) {
         parts = Math.max(1, Math.min(parts, 1023));

         for (int i = 0; i <= parts; i++) {
            float t = (float)i / parts;
            double x = x1 + (x2 - x1) * t;
            double z = z1 + (z2 - z1) * t;
            Vfx.X[i] = x - f.viewX;
            Vfx.Z[i] = z - f.viewZ;
            Vfx.Y[i] = height(hf, x, z) - f.viewY;
            Vfx.W[i] = (float)width;
            Vfx.C[i] = ColorUtil.mulAlpha(ColorUtil.lerp(c1, c2, t), lastValid ? 1.0F : 0.0F);
         }

         Vfx.flatRibbon(tex, 0, parts + 1);
      }
   }

   public static void ribbon(FxFrame f, Terrain.HeightField hf, int tex, int offset, int n) {
      for (int i = offset; i < offset + n; i++) {
         double x = Vfx.X[i];
         double z = Vfx.Z[i];
         Vfx.Y[i] = height(hf, x, z) - f.viewY;
         if (!lastValid) {
            Vfx.C[i] = Vfx.C[i] & 16777215;
         }

         Vfx.X[i] = x - f.viewX;
         Vfx.Z[i] = z - f.viewZ;
      }

      Vfx.flatRibbon(tex, offset, n);
   }

   public static void toRender(FxFrame f, Terrain.HeightField hf, int offset, int n) {
      for (int i = offset; i < offset + n; i++) {
         double x = Vfx.X[i];
         double z = Vfx.Z[i];
         Vfx.Y[i] = height(hf, x, z) - f.viewY;
         if (!lastValid) {
            Vfx.C[i] = Vfx.C[i] & 16777215;
         }

         Vfx.X[i] = x - f.viewX;
         Vfx.Z[i] = z - f.viewZ;
      }
   }

   public static int ringPoints(
      FxFrame f,
      Terrain.HeightField hf,
      int offset,
      double cx,
      double cz,
      double radius,
      double width,
      int segments,
      ThemeTint color,
      int staticColor,
      float alpha,
      float tShift
   ) {
      segments = Math.max(3, Math.min(segments, 1024 - offset - 2));

      for (int i = 0; i <= segments; i++) {
         double a = (Math.PI * 2) * i / segments;
         int k = offset + i;
         Vfx.X[k] = cx + Math.cos(a) * radius;
         Vfx.Z[k] = cz + Math.sin(a) * radius;
         Vfx.W[k] = (float)width;
         Vfx.C[k] = ColorUtil.mulAlpha(colorAt(color, staticColor, loop((float)i / segments + tShift)), alpha);
      }

      toRender(f, hf, offset, segments + 1);
      return segments + 1;
   }

   public static int linePoints(
      FxFrame f, Terrain.HeightField hf, int offset, double x1, double z1, double x2, double z2, double width, int c1, int c2, int parts
   ) {
      parts = Math.max(1, Math.min(parts, 1024 - offset - 2));

      for (int i = 0; i <= parts; i++) {
         float t = (float)i / parts;
         int k = offset + i;
         Vfx.X[k] = x1 + (x2 - x1) * t;
         Vfx.Z[k] = z1 + (z2 - z1) * t;
         Vfx.W[k] = (float)width;
         Vfx.C[k] = ColorUtil.lerp(c1, c2, t);
      }

      toRender(f, hf, offset, parts + 1);
      return parts + 1;
   }

   public static double y(Terrain.HeightField hf, double x, double z) {
      return height(hf, x, z);
   }

   public static boolean lastValid() {
      return lastValid;
   }
}
