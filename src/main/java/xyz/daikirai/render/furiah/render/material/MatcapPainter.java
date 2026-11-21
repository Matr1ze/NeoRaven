package xyz.daikirai.render.furiah.render.material;

final class MatcapPainter {
   static final int SIZE = 512;
   private static final float KX;
   private static final float KY;
   private static final float KZ;
   private static final float FX;
   private static final float FY;
   private static final float FZ;
   private static final float TOON_SHADOW = 0.42F;
   private static final float TOON_MID = 0.74F;
   private static final float TOON_LIT = 0.97F;
   private static final float TOON_EDGE1 = 0.05F;
   private static final float TOON_EDGE2 = 0.55F;
   private static final ThreadLocal<float[]> ENV = new ThreadLocal<float[]>() {
      protected float[] initialValue() {
         return new float[3];
      }
   };

   private MatcapPainter() {
   }

   static MatcapPainter.Look look(Material m) {
      MatcapPainter.Look k = new MatcapPainter.Look();
      switch (m) {
         case GOLD:
            return k.base(1.0F, 0.78F, 0.32F).metal(0.22F, 0.9F).rim(0.22F, 1.0F, 0.92F, 0.7F);
         case ROSE_GOLD:
            return k.base(0.98F, 0.7F, 0.6F).metal(0.24F, 0.9F).rim(0.22F, 1.0F, 0.88F, 0.84F);
         case SILVER:
            return k.base(0.95F, 0.95F, 0.94F).metal(0.16F, 1.0F).rim(0.2F, 1.0F, 1.0F, 1.0F);
         case CHROME:
            return k.base(0.93F, 0.95F, 0.97F).metal(0.012F, 1.25F).rim(0.18F, 0.95F, 0.98F, 1.0F);
         case BRASS:
            return k.base(0.88F, 0.7F, 0.36F).metal(0.42F, 0.7F).rim(0.2F, 1.0F, 0.92F, 0.7F);
         case BRONZE:
            return k.base(0.8F, 0.5F, 0.3F).metal(0.36F, 0.75F).rim(0.2F, 1.0F, 0.86F, 0.7F);
         case IRON:
            return k.base(0.66F, 0.67F, 0.69F).metal(0.62F, 0.5F).rim(0.22F, 0.92F, 0.95F, 1.0F);
         case OBSIDIAN:
            k.base(0.13F, 0.12F, 0.15F).dielectric(0.04F, 1.15F, 1.25F).rim(0.32F, 0.8F, 0.82F, 0.93F).sheen(0.22F, 0.64F, 0.5F, 0.8F);
            k.ambient = 0.05F;
            return k;
         case PEARL:
            k.base(0.96F, 0.95F, 0.93F).dielectric(0.45F, 0.35F, 0.55F).rim(0.18F, 1.0F, 1.0F, 1.0F);
            k.iridescence = 0.55F;
            return k;
         case JADE:
            k.base(0.38F, 0.72F, 0.54F).dielectric(0.32F, 0.6F, 0.55F).rim(0.16F, 0.86F, 1.0F, 0.9F);
            k.subsurface = 0.75F;
            return k;
         default:
            return look(Finish.SATIN);
      }
   }

   static MatcapPainter.Look look(Finish f) {
      MatcapPainter.Look k = new MatcapPainter.Look();
      switch (f) {
         case METALLIC:
            k.metal(0.38F, 0.95F).rim(0.3F, 1.0F, 1.0F, 1.0F);
            k.whiteSpec = true;
            return k;
         case MATTE:
            k.dielectric(1.0F, 0.0F, 0.04F).rim(0.08F, 1.0F, 1.0F, 1.0F);
            k.ambient = 0.2F;
            return k;
         case GLASS:
            k.dielectric(0.03F, 1.1F, 1.35F).rim(0.5F, 1.0F, 1.0F, 1.0F);
            k.ambient = 0.08F;
            return k;
         case PEARLESCENT:
            k.dielectric(0.4F, 0.45F, 0.5F).rim(0.2F, 1.0F, 1.0F, 1.0F);
            k.iridescence = 0.6F;
            return k;
         case TOON:
            k.toon = true;
            k.rim = 0.0F;
            return k;
         default:
            return k.dielectric(0.5F, 0.35F, 0.35F).rim(0.18F, 1.0F, 1.0F, 1.0F);
      }
   }

   static int[] paint(MatcapPainter.Look k, int size, boolean tintable) {
      int[] px = new int[size * size];
      float[] out = new float[6];

      for (int y = 0; y < size; y++) {
         float ny = (y + 0.5F) / size * 2.0F - 1.0F;

         for (int x = 0; x < size; x++) {
            float nx = (x + 0.5F) / size * 2.0F - 1.0F;
            float sx = nx;
            float sy = ny;
            float d2 = nx * nx + ny * ny;
            if (d2 > 0.996F) {
               float s = (float)(0.998 / Math.sqrt(d2));
               d2 = (sx = nx * s) * sx + (sy = ny * s) * sy;
            }

            float nz = (float)Math.sqrt(Math.max(0.0F, 1.0F - d2));
            if (k.toon) {
               toon(sx, sy, nz, 2.0F / size, out);
            } else {
               shade(k, sx, sy, nz, out);
            }

            float r = out[0] + out[3];
            float g = out[1] + out[4];
            float b = out[2] + out[5];
            int a = 255;
            if (tintable) {
               float tinted = lum(out[0], out[1], out[2]);
               float white = lum(out[3], out[4], out[5]);
               a = Math.round(255.0F * clamp01(tinted / Math.max(1.0E-4F, tinted + white)));
            }

            px[y * size + x] = a << 24 | encode(r) << 16 | encode(g) << 8 | encode(b);
         }
      }

      return px;
   }

   private static void shade(MatcapPainter.Look k, float nx, float ny, float nz, float[] out) {
      float rx = 2.0F * nz * nx;
      float ry = 2.0F * nz * ny;
      float rz = 2.0F * nz * nz - 1.0F;
      float graze = 1.0F - nz;
      float g2 = graze * graze;
      float g5 = g2 * g2 * graze;
      float f0 = 0.04F + 0.96F * k.metal;
      float fresnel = f0 + (1.0F - f0) * g5;
      float[] e = ENV.get();
      env(rx, ry, rz, k.rough, e);
      float er = e[0];
      float eg = e[1];
      float eb = e[2];
      float wrap = k.subsurface * 0.6F;
      float key = Math.max(0.0F, (nx * KX + ny * KY + nz * KZ + wrap) / (1.0F + wrap));
      float fill = Math.max(0.0F, (nx * FX + ny * FY + nz * FZ + wrap) / (1.0F + wrap));
      float diffuse = k.ambient + 0.84F * key + 0.2F * fill;
      float rdl = Math.max(0.0F, rx * KX + ry * KY + rz * KZ);
      float shininess = 8.0F + 700.0F * cube(1.0F - k.rough);
      float spec = (float)Math.pow(rdl, shininess) * k.spec * (1.0F + 1.6F * (1.0F - k.rough) * (1.0F - k.rough));
      float wr;
      float wg;
      float wb;
      float tb;
      float tg;
      float tr;
      if (k.metal >= 0.5F) {
         float refl = k.env * (0.12F + 0.88F * fresnel);
         tr = k.br * (er * refl + 0.04F * diffuse);
         tg = k.bg * (eg * refl + 0.04F * diffuse);
         tb = k.bb * (eb * refl + 0.04F * diffuse);
         if (k.whiteSpec) {
            wb = spec;
            wg = spec;
            wr = spec;
         } else {
            tr += k.br * spec;
            tg += k.bg * spec;
            tb += k.bb * spec;
            wg = wb = spec * 0.22F;
            wr = wb;
         }
      } else {
         float keep = 1.0F - fresnel * Math.min(1.0F, k.env) * 0.6F;
         tr = k.br * diffuse * keep;
         tg = k.bg * diffuse * keep;
         tb = k.bb * diffuse * keep;
         float refl = fresnel * k.env;
         wr = er * refl + spec;
         wg = eg * refl + spec;
         wb = eb * refl + spec;
         if (k.subsurface > 0.0F) {
            float core = k.subsurface * 0.32F * nz * nz;
            tr += k.br * core * 1.6F;
            tg += k.bg * core * 1.6F;
            tb += k.bb * core * 1.6F;
         }
      }

      float rim = k.rim * g2 * graze;
      wr += k.rimR * rim;
      wg += k.rimG * rim;
      wb += k.rimB * rim;
      if (k.iridescence > 0.0F) {
         float hue = 0.58F + graze * 1.05F + ny * 0.12F;
         float a = k.iridescence * (float)Math.pow(graze, 1.6) * 0.75F;
         wr += a * rainbow(hue);
         wg += a * rainbow(hue - 0.33333334F);
         wb += a * rainbow(hue - 0.6666667F);
      }

      if (k.sheen > 0.0F) {
         float a = k.sheen * g2;
         wr += k.sheenR * a;
         wg += k.sheenG * a;
         wb += k.sheenB * a;
      }

      out[0] = tr;
      out[1] = tg;
      out[2] = tb;
      out[3] = wr;
      out[4] = wg;
      out[5] = wb;
   }

   private static void toon(float nx, float ny, float nz, float pixel, float[] out) {
      float key = nx * KX + ny * KY + nz * KZ;
      float aa = pixel * 1.5F;
      float b1 = smooth(0.05F - aa, 0.05F + aa, key);
      float b2 = smooth(0.55F - aa, 0.55F + aa, key);
      float tone = 0.42F + 0.32000002F * b1 + 0.23000002F * b2;
      float rx = 2.0F * nz * nx;
      float ry = 2.0F * nz * ny;
      float rz = 2.0F * nz * nz - 1.0F;
      float rdl = rx * KX + ry * KY + rz * KZ;
      float hi = smooth(0.965F - aa * 2.0F, 0.965F + aa * 2.0F, rdl);
      float lit = decode(tone) * (1.0F - hi);
      float white = decode(0.985F) * hi;
      out[1] = out[2] = lit;
      out[0] = out[2];
      out[4] = out[5] = white;
      out[3] = out[5];
   }

   private static float decode(float screen) {
      double t = Math.pow(Math.min(0.995, Math.max(0.0, (double)screen)), 2.2);
      return (float)(-Math.log(1.0 - t) / 1.3);
   }

   private static void env(float rx, float ry, float rz, float rough, float[] o) {
      float up = smooth(0.0F, 0.8F, ry);
      float skyR = lerp(1.0F, 0.36F, up);
      float skyG = lerp(1.04F, 0.52F, up);
      float skyB = lerp(1.1F, 0.92F, up);
      float down = smooth(0.0F, -0.7F, ry);
      float gR = lerp(0.34F, 0.05F, down);
      float gG = lerp(0.3F, 0.045F, down);
      float gB = lerp(0.26F, 0.042F, down);
      float w = 0.012F + 0.75F * rough;
      float t = smooth(-w, w, ry);
      float r = lerp(gR, skyR, t);
      float g = lerp(gG, skyG, t);
      float b = lerp(gB, skyB, t);
      float bw = 0.03F + 0.4F * rough;
      float band = (float)Math.exp(-(ry / bw) * (ry / bw)) * 0.45F / (1.0F + 8.0F * rough);
      float sx = rx + 0.45F;
      float sy = ry - 0.52F;
      float spread = 1.0F + 9.0F * rough;
      float window = (float)Math.exp(-(sx * sx / (0.022F * spread) + sy * sy / (0.04F * spread))) * 1.15F / (1.0F + 2.5F * rough);
      float behind = 0.88F + 0.12F * (1.0F - Math.max(0.0F, rz));
      o[0] = (r + band + window) * behind;
      o[1] = (g + band + window) * behind;
      o[2] = (b + band * 1.05F + window) * behind;
   }

   private static float rainbow(float h) {
      h -= (float)Math.floor(h);
      return 0.5F + 0.5F * (float)Math.cos(h * Math.PI * 2.0);
   }

   private static int encode(float c) {
      float t = 1.0F - (float)Math.exp(-1.3F * Math.max(0.0F, c));
      return Math.round(255.0F * (float)Math.pow(t, 0.45454545454545453));
   }

   private static float lum(float r, float g, float b) {
      return 0.2126F * r + 0.7152F * g + 0.0722F * b;
   }

   private static float cube(float v) {
      return v * v * v;
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

   private static float[] unit(float x, float y, float z) {
      float l = (float)Math.sqrt(x * x + y * y + z * z);
      return new float[]{x / l, y / l, z / l};
   }

   static {
      float[] k = unit(0.38F, 0.78F, 0.5F);
      KX = k[0];
      KY = k[1];
      KZ = k[2];
      float[] f = unit(-0.75F, 0.08F, 0.66F);
      FX = f[0];
      FY = f[1];
      FZ = f[2];
   }

   static final class Look {
      float br = 1.0F;
      float bg = 1.0F;
      float bb = 1.0F;
      float metal;
      float rough = 0.3F;
      float spec = 0.6F;
      float env = 1.0F;
      float rim = 0.25F;
      float rimR = 1.0F;
      float rimG = 1.0F;
      float rimB = 1.0F;
      float ambient = 0.16F;
      float iridescence;
      float subsurface;
      float sheen;
      float sheenR;
      float sheenG;
      float sheenB;
      boolean whiteSpec;
      boolean toon;

      MatcapPainter.Look base(float r, float g, float b) {
         this.br = linear(r);
         this.bg = linear(g);
         this.bb = linear(b);
         return this;
      }

      MatcapPainter.Look metal(float rough, float spec) {
         this.metal = 1.0F;
         this.rough = rough;
         this.spec = spec;
         return this;
      }

      MatcapPainter.Look dielectric(float rough, float spec, float env) {
         this.metal = 0.0F;
         this.rough = rough;
         this.spec = spec;
         this.env = env;
         return this;
      }

      MatcapPainter.Look rim(float amount, float r, float g, float b) {
         this.rim = amount;
         this.rimR = linear(r);
         this.rimG = linear(g);
         this.rimB = linear(b);
         return this;
      }

      MatcapPainter.Look sheen(float amount, float r, float g, float b) {
         this.sheen = amount;
         this.sheenR = linear(r);
         this.sheenG = linear(g);
         this.sheenB = linear(b);
         return this;
      }

      private static float linear(float srgb) {
         return (float)Math.pow(Math.max(0.0F, srgb), 2.2);
      }
   }
}
