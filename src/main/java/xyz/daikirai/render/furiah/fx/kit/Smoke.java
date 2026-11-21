package xyz.daikirai.render.furiah.fx.kit;

import java.util.Random;
import xyz.daikirai.render.furiah.fx.look.KeepVisible;
import xyz.daikirai.render.furiah.render.Camera;
import xyz.daikirai.render.furiah.render.ColorUtil;
import xyz.daikirai.render.furiah.render.Easing;
import xyz.daikirai.render.furiah.render.Textures;
import xyz.daikirai.render.furiah.render.Vfx;
import xyz.daikirai.render.furiah.setting.Displayable;

public final class Smoke {
   public static final int CELLS = 4;
   private static final KitTextures.Painted SHEET = new KitTextures.Painted("smoke sheet", Smoke::paintSheet);
   private static final KitTextures.Painted WISPS = new KitTextures.Painted("smoke wisps", Smoke::paintWisps);

   private Smoke() {
   }

   static void warmUp() {
      SHEET.warm();
      WISPS.warm();
   }

   public static int alphaOf(int color, float a) {
      return Math.round(255.0F * Easing.clamp01(a) * ((color >>> 24) / 255.0F)) << 24 | color & 16777215;
   }

   public static float readable(KeepVisible keep, Camera cam, double bodyX, double bodyZ, double x, double y, double z, double half, float a, double stack) {
      double side = (x - bodyX) * cam.rightX + (z - bodyZ) * cam.rightZ;
      double shift = Math.min(Math.abs(side), half) * Math.signum(side);
      float f = keep.darkFront(x - cam.rightX * shift, y, z - cam.rightZ * shift);
      if (f <= 0.0F) {
         return a;
      } else {
         float layer = 1.0F - (float)Math.pow(1.0F - keep.darkCap(), 1.0 / stack);
         return a > layer ? a + (layer - a) * f : a;
      }
   }

   public static void mist(double cx, double y, double cz, double half, double turn, int cell, int col) {
      if (col >>> 24 != 0) {
         int tex = sheet();
         double ca = Math.cos(turn) * half;
         double sa = Math.sin(turn) * half;
         float u0 = cell / 4.0F;
         float u1 = (cell + 1) / 4.0F;
         Vfx.quadUV(
            tex,
            cx - ca + sa,
            y,
            cz - sa - ca,
            u0,
            0.0,
            col,
            cx + ca + sa,
            y,
            cz + sa - ca,
            u1,
            0.0,
            col,
            cx + ca - sa,
            y,
            cz + sa + ca,
            u1,
            1.0,
            col,
            cx - ca - sa,
            y,
            cz - sa + ca,
            u0,
            1.0,
            col
         );
      }
   }

   public static void crack(double x0, double y, double z0, double x1, double z1, double w, int col) {
      double dx = x1 - x0;
      double dz = z1 - z0;
      double l = Math.sqrt(dx * dx + dz * dz);
      if (!(l < 1.0E-6) && col >>> 24 != 0) {
         double nx = -dz / l * w * 0.5;
         double nz = dx / l * w * 0.5;
         Vfx.quadUV(
            Textures.LINE_GLOW,
            x0 - nx,
            y,
            z0 - nz,
            0.0,
            0.0,
            col,
            x1 - nx,
            y,
            z1 - nz,
            1.0,
            0.0,
            col,
            x1 + nx,
            y,
            z1 + nz,
            1.0,
            1.0,
            col,
            x0 + nx,
            y,
            z0 + nz,
            0.0,
            1.0,
            col
         );
      }
   }

   public static void ember(double x, double y, double z, float glow, float spark, int color, float a) {
      if (!(a <= 0.004F)) {
         Vfx.sprite(Textures.GLOW, x, y, z, glow, 0.0F, ColorUtil.mulAlpha(color, 0.35F * a));
         Vfx.sprite(Textures.SPARK, x, y, z, spark, 0.0F, ColorUtil.mulAlpha(ColorUtil.core(color), 0.8F * a));
      }
   }

   public static void curl(double x, double y, double z, double t, double scale, double[] out) {
      double e = 0.08;
      double k = 1.0 / (2.0 * e);
      double w = t * 0.35;
      double var29;
      double var30;
      double var31;
      double dAy = (noise(x, (var30 = y * scale) + e, (var31 = z * scale) + w) - noise(var29 = x * scale, var30 - e, var31 + w)) * k;
      double dAz = (noise(var29, var30, var31 + e + w) - noise(var29, var30, var31 - e + w)) * k;
      double dBx = (noise(var29 + e + 31.4, var30, var31 - w) - noise(var29 - e + 31.4, var30, var31 - w)) * k;
      double dBz = (noise(var29 + 31.4, var30, var31 + e - w) - noise(var29 + 31.4, var30, var31 - e - w)) * k;
      double dCx = (noise(var29 + e - w, var30 + 47.2, var31) - noise(var29 - e - w, var30 + 47.2, var31)) * k;
      double dCy = (noise(var29 - w, var30 + e + 47.2, var31) - noise(var29 - w, var30 - e + 47.2, var31)) * k;
      out[0] = (dCy - dBz) * 0.5;
      out[1] = (dAz - dCx) * 0.5;
      out[2] = (dBx - dAy) * 0.5;
   }

   private static double noise(double x, double y, double z) {
      int ix = (int)Math.floor(x);
      int iy = (int)Math.floor(y);
      int iz = (int)Math.floor(z);
      double fx = x - ix;
      double fy = y - iy;
      double fz = z - iz;
      fx = fx * fx * (3.0 - 2.0 * fx);
      fy = fy * fy * (3.0 - 2.0 * fy);
      fz = fz * fz * (3.0 - 2.0 * fz);
      double a = lerp(hash(ix, iy, iz), hash(ix + 1, iy, iz), fx);
      double b = lerp(hash(ix, iy + 1, iz), hash(ix + 1, iy + 1, iz), fx);
      double c = lerp(hash(ix, iy, iz + 1), hash(ix + 1, iy, iz + 1), fx);
      double d = lerp(hash(ix, iy + 1, iz + 1), hash(ix + 1, iy + 1, iz + 1), fx);
      return lerp(lerp(a, b, fy), lerp(c, d, fy), fz);
   }

   private static double lerp(double a, double b, double t) {
      return a + (b - a) * t;
   }

   private static double hash(int x, int y, int z) {
      int h = x * 374761393 + y * 668265263 + z * 1274126177;
      h = (h ^ h >>> 13) * 1103515245;
      return ((h ^ h >>> 16) & 65535) / 65535.0;
   }

   public static int wisps() {
      return WISPS.texture();
   }

   private static KitTextures.Image[] paintWisps() {
      int w = 256;
      int h = 64;
      int[] px = new int[w * h];
      Random r = new Random(14871L);
      int[] gu = new int[]{4, 8, 16};
      int[] gv = new int[]{2, 3, 5};
      float[][] grids = new float[3][];

      for (int o = 0; o < 3; o++) {
         grids[o] = new float[gu[o] * (gv[o] + 1)];

         for (int i = 0; i < grids[o].length; i++) {
            grids[o][i] = r.nextFloat();
         }
      }

      for (int y = 0; y < h; y++) {
         float v = (y + 0.5F) / h;
         float across = Math.abs(v * 2.0F - 1.0F);
         float core = 1.0F - Easing.smoothstep(0.0F, 0.85F, across);

         for (int x = 0; x < w; x++) {
            float n = 0.0F;
            float amp = 0.55F;
            float sum = 0.0F;

            for (int o = 0; o < 3; o++) {
               float fx = (float)(x * gu[o]) / w;
               float fy = v * gv[o];
               int x0 = (int)fx;
               int y0 = Math.min(gv[o] - 1, (int)fy);
               float tx = fx - x0;
               float ty = fy - y0;
               tx = tx * tx * (3.0F - 2.0F * tx);
               ty = ty * ty * (3.0F - 2.0F * ty);
               int x1 = (x0 + 1) % gu[o];
               float[] g = grids[o];
               float a = g[y0 * gu[o] + x0];
               float b = g[y0 * gu[o] + x1];
               float c = g[(y0 + 1) * gu[o] + x0];
               float d = g[(y0 + 1) * gu[o] + x1];
               n += amp * ((a + (b - a) * tx) * (1.0F - ty) + (c + (d - c) * tx) * ty);
               sum += amp;
               amp *= 0.5F;
            }

            float var30;
            float a = Easing.clamp01((core - 0.2F) * 1.25F + ((var30 = n / sum) - 0.5F) * 1.2F);
            int shade = Math.round((0.8F + 0.2F * var30) * 255.0F);
            px[y * w + x] = Math.round(a * 255.0F) << 24 | shade << 16 | shade << 8 | shade;
         }
      }

      return new KitTextures.Image[]{KitTextures.Image.argb(px, w, h, true, true)};
   }

   public static int sheet() {
      return SHEET.texture();
   }

   private static KitTextures.Image[] paintSheet() {
      int cell = 128;
      int w = cell * 4;
      int[] px = new int[w * cell];
      Random r = new Random(368846L);

      for (int v = 0; v < 4; v++) {
         float[][] grids = new float[3][];
         int[] sizes = new int[]{6, 12, 24};

         for (int o = 0; o < 3; o++) {
            grids[o] = new float[(sizes[o] + 1) * (sizes[o] + 1)];

            for (int i = 0; i < grids[o].length; i++) {
               grids[o][i] = r.nextFloat();
            }
         }

         for (int y = 0; y < cell; y++) {
            for (int x = 0; x < cell; x++) {
               float n = 0.0F;
               float amp = 0.55F;
               float sum = 0.0F;

               for (int o = 0; o < 3; o++) {
                  int g = sizes[o];
                  float fx = (float)(x * g) / cell;
                  float fy = (float)(y * g) / cell;
                  int x0 = (int)fx;
                  int y0 = (int)fy;
                  float tx = fx - x0;
                  float ty = fy - y0;
                  tx = tx * tx * (3.0F - 2.0F * tx);
                  ty = ty * ty * (3.0F - 2.0F * ty);
                  float[] gr = grids[o];
                  int row = g + 1;
                  float a = gr[y0 * row + x0];
                  float b = gr[y0 * row + x0 + 1];
                  float cc = gr[(y0 + 1) * row + x0];
                  float d = gr[(y0 + 1) * row + x0 + 1];
                  n += amp * ((a + (b - a) * tx) * (1.0F - ty) + (cc + (d - cc) * tx) * ty);
                  sum += amp;
                  amp *= 0.5F;
               }

               n /= sum;
               float dx = (x + 0.5F) / cell * 2.0F - 1.0F;
               float dy = (y + 0.5F) / cell * 2.0F - 1.0F;
               float rr = (float)Math.sqrt(dx * dx + dy * dy);
               float fall = Math.max(0.0F, 1.0F - rr);
               fall = fall * fall * (3.0F - 2.0F * fall);
               float a = Easing.clamp01((n - 0.26F) * 3.0F) * fall;
               int shade = Math.round((0.82F + 0.18F * n) * 255.0F);
               px[y * w + v * cell + x] = Math.round(a * 255.0F) << 24 | shade << 16 | shade << 8 | shade;
            }
         }
      }

      return new KitTextures.Image[]{KitTextures.Image.argb(px, w, cell, true, false)};
   }

   public static enum EdgeLight implements Displayable {
      AUTO("Auto", "When it is dark round the target (Night Visibility)"),
      ON("On", "Always"),
      OFF("Off", "Never");

      private final String displayName;
      private final String description;

      private EdgeLight(String displayName, String description) {
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

   public static final class Puffs {
      public final double[] x;
      public final double[] y;
      public final double[] z;
      public final float[] size;
      public final float[] alpha;
      public final float[] turn;
      public final int[] cell;
      private final double[] depth;
      private final int[] order;
      public int count;

      public Puffs(int capacity) {
         this.x = new double[capacity];
         this.y = new double[capacity];
         this.z = new double[capacity];
         this.size = new float[capacity];
         this.alpha = new float[capacity];
         this.turn = new float[capacity];
         this.cell = new int[capacity];
         this.depth = new double[capacity];
         this.order = new int[capacity];
      }

      public int capacity() {
         return this.x.length;
      }

      public void sort(Camera cam) {
         int k = 0;

         while (k < this.count) {
            this.depth[k] = (this.x[k] - cam.posX) * cam.forwardX + (this.y[k] - cam.posY) * cam.forwardY + (this.z[k] - cam.posZ) * cam.forwardZ;
            this.order[k] = k++;
         }

         for (int i = 1; i < this.count; i++) {
            int v = this.order[i];
            double d = this.depth[v];

            int j;
            for (j = i - 1; j >= 0 && this.depth[this.order[j]] < d; j--) {
               this.order[j + 1] = this.order[j];
            }

            this.order[j + 1] = v;
         }
      }

      public int order(int i) {
         return this.order[i];
      }

      public void draw(int color, float alpha, KeepVisible keep, Camera cam, double bodyX, double bodyZ, double stack) {
         int tex = Smoke.sheet();

         for (int i = 0; i < this.count; i++) {
            int k = this.order[i];
            float a = alpha * this.alpha[k];
            if (keep != null) {
               a = Smoke.readable(keep, cam, bodyX, bodyZ, this.x[k], this.y[k], this.z[k], this.size[k] * 0.5, a, stack);
            }

            if (!(a <= 0.004F)) {
               float u0 = this.cell[k] / 4.0F;
               float u1 = (this.cell[k] + 1) / 4.0F;
               double h = this.size[k] * 0.5;
               Vfx.sprite(tex, this.x[k], this.y[k], this.z[k], h, h, this.turn[k], u0, 0.0F, u1, 1.0F, Smoke.alphaOf(color, a));
            }
         }
      }
   }
}
