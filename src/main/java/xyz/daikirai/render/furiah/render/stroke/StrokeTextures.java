package xyz.daikirai.render.furiah.render.stroke;

import java.util.Random;
import xyz.daikirai.render.furiah.fx.kit.KitTextures;
import xyz.daikirai.render.furiah.render.Textures;

public final class StrokeTextures {
   private static final org.apache.logging.log4j.Logger LOG = org.apache.logging.log4j.LogManager.getLogger("NeoRaven/JumpCircles");
   public static int BRUSH;
   public static int BRUSH_SOFT;
   public static int PENCIL;
   public static int MARKER;
   public static int CHALK;
   public static int SOFT;
   private static boolean loaded;
   private static final KitTextures.Painted PAINTED = new KitTextures.Painted("stroke textures", StrokeTextures::paint);

   private StrokeTextures() {
   }

   public static void warmUp() {
      PAINTED.warm();
   }

   public static void ensureLoaded() {
      Textures.ensureLoaded();
      if (!loaded) {
         loaded = true;
         BRUSH = PAINTED.texture(0);
         BRUSH_SOFT = PAINTED.texture(1);
         PENCIL = PAINTED.texture(2);
         MARKER = PAINTED.texture(3);
         CHALK = PAINTED.texture(4);
         SOFT = PAINTED.texture(5);
      }
   }

   private static KitTextures.Image[] paint() {
      long start = System.nanoTime();
      float[] brush = brush(1024, 128);
      float[] soft = blur(downsample(brush, 1024, 128), 512, 64, 5);
      KitTextures.Image[] images = new KitTextures.Image[]{
         KitTextures.Image.argb(white(brush), 1024, 128, true, false),
         KitTextures.Image.argb(white(soft), 512, 64, true, false),
         KitTextures.Image.argb(white(pencil(256, 64)), 256, 64, true, true),
         KitTextures.Image.argb(white(marker(256, 64)), 256, 64, true, true),
         KitTextures.Image.argb(white(chalk(256, 64)), 256, 64, true, true),
         KitTextures.Image.argb(white(soft(64, 64)), 64, 64, true, false)
      };
      LOG.info("Generated stroke textures in {} ms", new Object[]{(System.nanoTime() - start) / 1000000L});
      return images;
   }

   private static float[] brush(int w, int h) {
      float[] a = new float[w * h];
      int bristles = 110;
      Random r = new Random(6221235L);
      float[] pos = new float[bristles];
      float[] dry = new float[bristles];
      float[] freq = new float[bristles];
      float[] phase = new float[bristles];
      float[] strength = new float[bristles];

      for (int i = 0; i < bristles; i++) {
         pos[i] = 0.06F + 0.88F * (i + r.nextFloat()) / bristles;
         dry[i] = 0.68F + 0.4F * (float)Math.sqrt(r.nextFloat()) - 0.3F * Math.abs(pos[i] - 0.5F);
         freq[i] = 9.0F + 26.0F * r.nextFloat();
         phase[i] = r.nextFloat() * 100.0F;
         strength[i] = 0.55F + 0.45F * r.nextFloat();
      }

      for (int y = 0; y < h; y++) {
         float v = (y + 0.5F) / h;
         float side = Math.abs(v - 0.5F) * 2.0F;
         float body = (1.0F - smooth(0.82F, 0.97F, side)) * (0.82F + 0.18F * smooth(0.55F, 0.85F, side));

         for (int x = 0; x < w; x++) {
            float u = (x + 0.5F) / w;
            a[y * w + x] = (1.0F - smooth(0.35F, 0.85F, u)) * body * 0.92F;
         }
      }

      for (int var23 = 0; var23 < bristles; var23++) {
         int y0 = Math.max(0, (int)Math.floor(pos[var23] * h - 3.0F));
         int y1 = Math.min(h - 1, (int)Math.ceil(pos[var23] * h + 3.0F));

         for (int y = y0; y <= y1; y++) {
            float d = ((y + 0.5F) / h - pos[var23]) * h;
            float line = (float)Math.exp(-d * d / 1.1F) * strength[var23];
            if (!(line < 0.01F)) {
               for (int x = 0; x < w; x++) {
                  float u = (x + 0.5F) / w;
                  float left = (dry[var23] - u) / 0.18F;
                  float ink = smooth(-0.15F, 0.2F, left);
                  if (!(ink <= 0.0F)) {
                     float keep = 1.0F;
                     if (left < 1.0F) {
                        float dash = valueNoise1(u * freq[var23] + phase[var23]);
                        float l = Math.max(0.0F, left);
                        keep = smooth(0.62F - 0.5F * l, 0.8F - 0.4F * l, dash);
                     }

                     int n = y * w + x;
                     a[n] += line * ink * keep;
                  }
               }
            }
         }
      }

      for (int var24 = 0; var24 < a.length; var24++) {
         a[var24] = clamp01(a[var24]);
      }

      return a;
   }

   private static float[] pencil(int w, int h) {
      float[] a = new float[w * h];

      for (int y = 0; y < h; y++) {
         float v = (y + 0.5F) / h;
         float side = Math.abs(v - 0.5F) * 2.0F;
         float band = 1.0F - smooth(0.55F, 1.0F, side);

         for (int x = 0; x < w; x++) {
            float u = (x + 0.5F) / w;
            float grain = tiled(u * 32.0F, v * 10.0F, 32, 10, 3) * 0.6F + tiled(u * 96.0F, v * 26.0F, 96, 26, 5) * 0.4F;
            float tooth = smooth(0.18F, 0.42F, tiled(u * 64.0F, v * 18.0F, 64, 18, 9));
            a[y * w + x] = clamp01(band * (0.35F + 0.65F * grain) * (0.45F + 0.55F * tooth) * 1.15F);
         }
      }

      return a;
   }

   private static float[] marker(int w, int h) {
      float[] a = new float[w * h];

      for (int y = 0; y < h; y++) {
         float v = (y + 0.5F) / h;
         float side = Math.abs(v - 0.5F) * 2.0F;
         float band = 1.0F - smooth(0.86F, 0.99F, side);
         float pooled = 0.58F + 0.2F * smooth(0.6F, 0.9F, side);

         for (int x = 0; x < w; x++) {
            float u = (x + 0.5F) / w;
            float streak = tiled(u * 2.0F, v * 40.0F, 2, 40, 13);
            a[y * w + x] = clamp01(band * (pooled + 0.06F * (streak - 0.5F)));
         }
      }

      return a;
   }

   private static float[] chalk(int w, int h) {
      float[] a = new float[w * h];

      for (int y = 0; y < h; y++) {
         float v = (y + 0.5F) / h;
         float side = Math.abs(v - 0.5F) * 2.0F;

         for (int x = 0; x < w; x++) {
            float u = (x + 0.5F) / w;
            float edge = 0.62F + 0.22F * tiled(u * 12.0F, v > 0.5F ? 3.7F : 1.3F, 12, 8, 17);
            float body = 1.0F - smooth(edge - 0.08F, edge + 0.05F, side);
            float grit = tiled(u * 80.0F, v * 22.0F, 80, 22, 21);
            float holes = smooth(0.2F, 0.36F, grit);
            float dust = side > edge ? smooth(0.86F, 0.93F, tiled(u * 120.0F, v * 30.0F, 120, 30, 23)) * 0.5F * (1.0F - side) : 0.0F;
            a[y * w + x] = clamp01(body * (0.55F + 0.45F * grit) * holes + dust);
         }
      }

      return a;
   }

   private static float[] soft(int w, int h) {
      float[] a = new float[w * h];

      for (int y = 0; y < h; y++) {
         float v = Math.abs((y + 0.5F) / h - 0.5F) * 2.0F;
         float s = 1.0F - smooth(0.0F, 1.0F, v);

         for (int x = 0; x < w; x++) {
            a[y * w + x] = s * s;
         }
      }

      return a;
   }

   private static int[] white(float[] alpha) {
      int[] px = new int[alpha.length];

      for (int i = 0; i < alpha.length; i++) {
         px[i] = Math.round(clamp01(alpha[i]) * 255.0F) << 24 | 16777215;
      }

      return px;
   }

   private static float[] downsample(float[] a, int w, int h) {
      int nw = w / 2;
      int nh = h / 2;
      float[] o = new float[nw * nh];

      for (int y = 0; y < nh; y++) {
         for (int x = 0; x < nw; x++) {
            o[y * nw + x] = (a[2 * y * w + 2 * x] + a[2 * y * w + 2 * x + 1] + a[(2 * y + 1) * w + 2 * x] + a[(2 * y + 1) * w + 2 * x + 1]) * 0.25F;
         }
      }

      return o;
   }

   private static float[] blur(float[] a, int w, int h, int radius) {
      float[] tmp = new float[w * h];

      for (int pass = 0; pass < 3; pass++) {
         box(a, tmp, w, h, radius, true);
         box(tmp, a, w, h, radius, false);
      }

      return a;
   }

   private static void box(float[] in, float[] out, int w, int h, int r, boolean horizontal) {
      int lines = horizontal ? h : w;
      int len = horizontal ? w : h;
      float norm = 1.0F / (2 * r + 1);

      for (int line = 0; line < lines; line++) {
         float sum = 0.0F;

         for (int i = -r; i <= r; i++) {
            sum += at(in, w, horizontal, line, i, len);
         }

         for (int var12 = 0; var12 < len; var12++) {
            out[horizontal ? line * w + var12 : var12 * w + line] = sum * norm;
            sum += at(in, w, horizontal, line, var12 + r + 1, len) - at(in, w, horizontal, line, var12 - r, len);
         }
      }
   }

   private static float at(float[] a, int w, boolean horizontal, int line, int i, int len) {
      if (i >= 0 && i < len) {
         return horizontal ? a[line * w + i] : a[i * w + line];
      } else {
         return 0.0F;
      }
   }

   private static float valueNoise1(float x) {
      int i = (int)Math.floor(x);
      float f = x - i;
      f = f * f * (3.0F - 2.0F * f);
      return lerp(hash(i, 0, 1), hash(i + 1, 0, 1), f);
   }

   private static float tiled(float x, float y, int px, int py, int seed) {
      int ix = (int)Math.floor(x);
      int iy = (int)Math.floor(y);
      float fx = x - ix;
      float fy = y - iy;
      fx = fx * fx * (3.0F - 2.0F * fx);
      fy = fy * fy * (3.0F - 2.0F * fy);
      int x0 = Math.floorMod(ix, px);
      int x1 = Math.floorMod(ix + 1, px);
      int y0 = Math.floorMod(iy, py);
      int y1 = Math.floorMod(iy + 1, py);
      return lerp(lerp(hash(x0, y0, seed), hash(x1, y0, seed), fx), lerp(hash(x0, y1, seed), hash(x1, y1, seed), fx), fy);
   }

   private static float hash(int x, int y, int seed) {
      int h = x * 668265261 ^ y * 374761393 ^ seed * -1640531535;
      h ^= h >>> 15;
      h *= -2048144789;
      h ^= h >>> 13;
      h *= -1028477387;
      h ^= h >>> 16;
      return (h >>> 8) / 1.6777216E7F;
   }

   private static float lerp(float a, float b, float t) {
      return a + (b - a) * t;
   }

   private static float smooth(float e0, float e1, float x) {
      float t = clamp01((x - e0) / (e1 - e0));
      return t * t * (3.0F - 2.0F * t);
   }

   private static float clamp01(float v) {
      return v < 0.0F ? 0.0F : (v > 1.0F ? 1.0F : v);
   }
}
