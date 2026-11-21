package xyz.daikirai.render.furiah.render.look;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import xyz.daikirai.render.furiah.fx.FxFrame;
import xyz.daikirai.render.furiah.render.PlayerModel;
import xyz.daikirai.render.furiah.render.SkinSampler;
import xyz.daikirai.render.furiah.render.Textures;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.util.ResourceLocation;

public final class LookTextures {
   private static final org.apache.logging.log4j.Logger LOG = org.apache.logging.log4j.LogManager.getLogger("NeoRaven/JumpCircles");
   private static int S = 8;
   private static final int MAX_KEPT = 24;
   public static final int SKIN_WIDE = 0;
   public static final int SKIN_SLIM = 1;
   public static final int ARMOR_1 = 2;
   public static final int ARMOR_2 = 3;
   public static final int CAPE = 4;
   public static final int P_KIND = 0;
   public static final int P_INK_LEVELS = 1;
   public static final int P_GRAIN = 2;
   public static final int P_PALETTE = 3;
   public static final int P_CELL = 4;
   public static final int P_GLASS1 = 5;
   public static final int P_GLASS2 = 6;
   public static final int P_GLASS3 = 7;
   public static final int P_LEAD = 8;
   public static final int P_OPACITY = 9;
   public static final int P_EMBERS = 10;
   public static final int P_COUNT = 11;
   public static final int FROM_SKIN = 0;
   public static final int CATHEDRAL = 1;
   public static final int TINTED = 2;
   public static final int[] CATHEDRAL_SET = new int[]{-4184513, -13935399, -14705042, -868018};
   private static final Map<LookTextures.Key, LookTextures.Entry> CACHE = new LinkedHashMap<>(32, 0.75F, true);
   private static final List<Integer> TO_DELETE = new ArrayList<>();
   private static final int[] PROBE = new int[11];
   private static ExecutorService worker;
   private static boolean uploadedThisFrame;
   private static int hatchTexture;
   private static long frameSeen = -1L;
   private static final int INK_R = 28;
   private static final int INK_G = 28;
   private static final int INK_B = 31;
   private static final int PAPER_R = 236;
   private static final int PAPER_G = 229;
   private static final int PAPER_B = 212;

   static int scaleFor(int w, int h) {
      return Math.max(2, Math.min(8, 512 / Math.max(w, h)));
   }

   private LookTextures() {
   }

   public static int get(ResourceLocation source, BodyLook look, int[] params) {
      if (source != null && look.derived()) {
         long frame = FxFrame.currentFrame();
         if (frame != frameSeen) {
            frameSeen = frame;
            frame();
         }

         System.arraycopy(params, 0, PROBE, 0, 11);
         LookTextures.Key probe = new LookTextures.Key(source, look, PROBE);
         LookTextures.Entry e = CACHE.get(probe);
         if (e == null && (e = start(new LookTextures.Key(source, look, (int[])PROBE.clone()))) == null) {
            return -1;
         } else {
            if (e.texture < 0 && e.levels != null && !uploadedThisFrame) {
               uploadedThisFrame = true;
               e.texture = Textures.uploadChain(e.levels, e.width, e.height, false);
               e.levels = (int[][])null;
            }

            return e.texture;
         }
      } else {
         return -1;
      }
   }

   public static int hatch() {
      if (hatchTexture == 0) {
         int size = 128;
         int[] px = new int[size * size];

         for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
               float d = (x + y) % 16.0F;
               float dist = Math.min(d, 16.0F - d);
               float line = clamp01(1.6F - dist) * (0.75F + 0.25F * hash(x, y, 79));
               px[y * size + x] = Math.round(255.0F * line) << 24 | 16777215;
            }
         }

         hatchTexture = Textures.uploadImage(px, size, size, true, true);
      }

      return hatchTexture;
   }

   public static void frame() {
      uploadedThisFrame = false;

      for (int id : TO_DELETE) {
         TextureUtil.deleteTexture(id);
      }

      TO_DELETE.clear();
   }

   private static LookTextures.Entry start(final LookTextures.Key key) {
      SkinSampler.Image img = SkinSampler.image(key.source);
      final LookTextures.Entry e = new LookTextures.Entry();
      CACHE.put(key, e);
      trim();
      if (img == null) {
         e.failed = true;
         return null;
      } else {
         final int w = img.width;
         final int h = img.height;
         final int scale = scaleFor(w, h);
         e.width = w * scale;
         e.height = h * scale;
         final int[] src = img.pixels();
         worker().execute(new Runnable() {
            @Override
            public void run() {
               try {
                  int[] out = LookTextures.paint(key.look, key.params, src, w, h);
                  e.levels = Textures.mipChain(out, w * scale, h * scale);
               } catch (RuntimeException | OutOfMemoryError var2) {
                  e.failed = true;
                  LOG.warn("A body look's texture could not be made ({})", new Object[]{key.look, var2});
               }
            }
         });
         return e;
      }
   }

   private static void trim() {
      for (Iterator<Map.Entry<LookTextures.Key, LookTextures.Entry>> it = CACHE.entrySet().iterator(); CACHE.size() > 24 && it.hasNext(); it.remove()) {
         LookTextures.Entry old = it.next().getValue();
         if (old.texture >= 0) {
            TO_DELETE.add(old.texture);
         }
      }
   }

   private static ExecutorService worker() {
      if (worker == null) {
         worker = Executors.newSingleThreadExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
               Thread t = new Thread(r, "Furiah's Visuals body looks");
               t.setDaemon(true);
               t.setPriority(2);
               return t;
            }
         });
      }

      return worker;
   }

   public static int[] paintForTest(ResourceLocation source, BodyLook look, int[] params, int[] size) {
      SkinSampler.Image img = SkinSampler.image(source);
      if (img == null) {
         return null;
      } else {
         int[] out = paint(look, params, img.pixels(), img.width, img.height);
         size[0] = img.width * scaleFor(img.width, img.height);
         size[1] = img.height * scaleFor(img.width, img.height);
         return out;
      }
   }

   public static String stateForTest() {
      int made = 0;
      int waiting = 0;
      int failed = 0;

      for (LookTextures.Entry e : CACHE.values()) {
         if (e.failed) {
            failed++;
         } else if (e.texture >= 0) {
            made++;
         } else {
            waiting++;
         }
      }

      return made + " made, " + waiting + " being made, " + failed + " failed";
   }

   static int[] paint(BodyLook look, int[] p, int[] src, int w, int h) {
      S = scaleFor(w, h);
      LookTextures.Faces faces = new LookTextures.Faces(p[0], w, h);
      switch (look) {
         case INK:
            return ink(src, w, h, faces, Math.max(2, Math.min(5, p[1])), p[2] != 0);
         case SKETCH:
            return sketch(src, w, h, faces);
         case GLASS:
            return glass(src, w, h, faces, p);
         case PAPER:
            return paper(src, w, h, (p[2] & 1) != 0, (p[2] & 2) != 0);
         case SHADOW:
            return p[10] != 0 ? embers(src, w, h, faces) : shadow(src, w, h);
         case TOON:
            return toon(src, w, h);
         default:
            return upscale(src, w, h);
      }
   }

   private static int[] upscale(int[] src, int w, int h) {
      int ow = w * S;
      int oh = h * S;
      int[] out = new int[ow * oh];

      for (int y = 0; y < oh; y++) {
         for (int x = 0; x < ow; x++) {
            out[y * ow + x] = src[y / S * w + x / S];
         }
      }

      return out;
   }

   private static int[] ink(int[] src, int w, int h, LookTextures.Faces faces, int levels, boolean grain) {
      int ow = w * S;
      int oh = h * S;
      int[] out = new int[ow * oh];
      int[] level = new int[w * h];

      for (int i = 0; i < w * h; i++) {
         float l = clamp01((luma(src[i]) - 0.07F) / 0.86F);
         level[i] = Math.min(levels - 1, (int)(l * levels));
      }

      for (int y = 0; y < oh; y++) {
         int sy = y / S;

         for (int x = 0; x < ow; x++) {
            int sx = x / S;
            int c = src[sy * w + sx];
            int a = c >>> 24;
            if (a >= 26) {
               int lv = level[sy * w + sx];
               float t = (float)Math.pow((float)lv / (levels - 1), 0.9);
               float pool = (noise(x * 0.045F, y * 0.045F, 11) - 0.5F) * 0.07F;
               float k = clamp01(t + pool * (1.0F - t) * 2.0F);
               float r = lerp(28.0F, 236.0F, k);
               float g = lerp(28.0F, 229.0F, k);
               float b = lerp(31.0F, 212.0F, k);
               float edge = edgeToDarker(level, w, h, faces, sx, sy, x, y, lv);
               if (edge > 0.0F) {
                  float d = edge * (0.55F + 0.25F * noise(x * 0.3F, y * 0.3F, 5));
                  r = lerp(r, 28.0F, d);
                  g = lerp(g, 28.0F, d);
                  b = lerp(b, 31.0F, d);
               }

               if (grain) {
                  float n = (hash(x, y, 3) - 0.5F) * 10.0F + (noise(x * 0.5F, y * 0.12F, 9) - 0.5F) * 9.0F;
                  r += n;
                  g += n;
                  b += n * 0.9F;
               }

               out[y * ow + x] = argb(a, r, g, b);
            }
         }
      }

      return out;
   }

   private static float edgeToDarker(int[] level, int w, int h, LookTextures.Faces faces, int sx, int sy, int x, int y, int lv) {
      float fx = (x % S + 0.5F) / S;
      float fy = (y % S + 0.5F) / S;
      float best = 0.0F;
      float width = 1.7F / S;
      if (sx > 0 && faces.same(sx, sy, sx - 1, sy) && level[sy * w + sx - 1] < lv) {
         best = Math.max(best, 1.0F - fx / width);
      }

      if (sx + 1 < w && faces.same(sx, sy, sx + 1, sy) && level[sy * w + sx + 1] < lv) {
         best = Math.max(best, 1.0F - (1.0F - fx) / width);
      }

      if (sy > 0 && faces.same(sx, sy, sx, sy - 1) && level[(sy - 1) * w + sx] < lv) {
         best = Math.max(best, 1.0F - fy / width);
      }

      if (sy + 1 < h && faces.same(sx, sy, sx, sy + 1) && level[(sy + 1) * w + sx] < lv) {
         best = Math.max(best, 1.0F - (1.0F - fy) / width);
      }

      return clamp01(best);
   }

   private static int[] sketch(int[] src, int w, int h, LookTextures.Faces faces) {
      int ow = w * S;
      int oh = h * S;
      int[] out = new int[ow * oh];
      float[] l = new float[w * h];

      for (int i = 0; i < w * h; i++) {
         l[i] = luma(src[i]);
      }

      for (int y = 0; y < oh; y++) {
         int sy = y / S;

         for (int x = 0; x < ow; x++) {
            int sx = x / S;
            int c = src[sy * w + sx];
            int a = c >>> 24;
            if (a >= 26) {
               float dark = clamp01((0.78F - l[sy * w + sx]) / 0.7F);
               float lines = 0.0F;
               float jitter = (noise(x * 0.08F, y * 0.08F, 21) - 0.5F) * 1.6F;
               if (dark > 0.12F) {
                  float d = mod(x + y + jitter, 5.5F);
                  lines = Math.max(lines, smoothLine(d, 0.55F + 0.9F * dark));
               }

               if (dark > 0.55F) {
                  float d = mod(x - y + jitter + 300.0F, 5.5F);
                  lines = Math.max(lines, smoothLine(d, 0.4F + 0.8F * (dark - 0.55F) * 2.0F));
               }

               float contour = contour(l, w, h, faces, sx, sy, x, y);
               lines = Math.max(lines, contour);
               float tooth = (hash(x, y, 7) - 0.5F) * 0.25F;
               float k = clamp01(lines * (0.82F + tooth));
               float r = lerp(244.0F, 66.0F, k);
               float g = lerp(241.0F, 66.0F, k);
               float b = lerp(234.0F, 72.0F, k);
               out[y * ow + x] = argb(a, r, g, b);
            }
         }
      }

      return out;
   }

   private static float contour(float[] l, int w, int h, LookTextures.Faces faces, int sx, int sy, int x, int y) {
      float fx = (x % S + 0.5F) / S;
      float fy = (y % S + 0.5F) / S;
      float width = 1.3F / S;
      float best = 0.0F;
      float own = l[sy * w + sx];
      if (sx > 0 && faces.same(sx, sy, sx - 1, sy) && Math.abs(l[sy * w + sx - 1] - own) > 0.22F) {
         best = Math.max(best, 1.0F - fx / width);
      }

      if (sy > 0 && faces.same(sx, sy, sx, sy - 1) && Math.abs(l[(sy - 1) * w + sx] - own) > 0.22F) {
         best = Math.max(best, 1.0F - fy / width);
      }

      return clamp01(best) * 0.85F;
   }

   private static int[] glass(int[] src, int w, int h, LookTextures.Faces faces, int[] p) {
      int ow = w * S;
      int oh = h * S;
      int[] out = new int[ow * oh];
      float cell = 3.4F * Math.max(0.5F, Math.min(1.5F, p[4] / 100.0F));
      int palette = p[3];
      int lead = p[8] != 0 ? p[8] : -15066594;
      int glassAlpha = Math.round(255.0F * clamp01(p[9] <= 0 ? 0.75F : p[9] / 100.0F));
      float[] nearest = new float[4];

      for (int y = 0; y < oh; y++) {
         int sy = y / S;

         for (int x = 0; x < ow; x++) {
            int sx = x / S;
            int c = src[sy * w + sx];
            if (c >>> 24 >= 26) {
               float px = (x + 0.5F) / S;
               float py = (y + 0.5F) / S;
               voronoi(px / cell, py / cell, 31, nearest);
               float border = (nearest[1] - nearest[0]) * cell;
               float faceEdge = faces.edgeDistance(px, py);
               float leadW = 0.36F;
               if (!(border < leadW) && !(faceEdge < leadW * 0.8F)) {
                  float cx = nearest[2] * cell;
                  float cy = nearest[3] * cell;
                  int seedColor = faces.sampleInFace(src, w, h, sx, sy, cx, cy);
                  float lum;
                  int col = palette == 1
                     ? nearestHue(seedColor, CATHEDRAL_SET)
                     : (palette == 2 ? ((lum = luma(seedColor)) < 0.33F ? p[5] : (lum < 0.62F ? p[6] : p[7])) : glassy(seedColor));
                  float mid = clamp01(1.0F - nearest[0] * 1.4F);
                  float streak = (noise(px * 2.2F + py * 0.7F, py * 0.4F, 17) - 0.5F) * 0.12F;
                  float k = 0.82F + 0.24F * mid + streak;
                  out[y * ow + x] = argb(glassAlpha, (col >> 16 & 0xFF) * k, (col >> 8 & 0xFF) * k, (col & 0xFF) * k);
               } else {
                  float rough = (hash(x, y, 13) - 0.5F) * 12.0F;
                  out[y * ow + x] = argb(255, (lead >> 16 & 0xFF) + rough, (lead >> 8 & 0xFF) + rough, (lead & 0xFF) + rough);
               }
            }
         }
      }

      return out;
   }

   private static int glassy(int c) {
      float[] hsv = hsv(c);
      hsv[1] = Math.min(1.0F, hsv[1] * 1.25F + 0.12F);
      hsv[2] = 0.5F + 0.5F * hsv[2];
      return rgb(hsv[0], hsv[1], hsv[2]);
   }

   private static int nearestHue(int c, int[] set) {
      float[] hsv = hsv(c);
      if (hsv[1] < 0.18F) {
         return hsv[2] < 0.35F ? set[1] : (hsv[2] < 0.6F ? set[2] : set[3]);
      } else {
         int best = set[0];
         float bestD = 9.0F;

         for (int s : set) {
            float d = Math.abs(hsv[0] - hsv(s)[0]);
            if ((d = Math.min(d, 1.0F - d)) < bestD) {
               bestD = d;
               best = s;
            }
         }

         return best;
      }
   }

   private static int[] paper(int[] src, int w, int h, boolean fibers, boolean plain) {
      int ow = w * S;
      int oh = h * S;
      int[] out = new int[ow * oh];

      for (int y = 0; y < oh; y++) {
         for (int x = 0; x < ow; x++) {
            int c = src[y / S * w + x / S];
            int a = c >>> 24;
            if (a >= 26) {
               float r = c >> 16 & 0xFF;
               float g = c >> 8 & 0xFF;
               float b = c & 0xFF;
               float l = 0.299F * r + 0.587F * g + 0.114F * b;
               if (plain) {
                  g = b = 128.0F + 127.0F * (float)Math.pow(l / 255.0F, 0.8);
                  r = b;
               } else {
                  r = lerp(lerp(r, l, 0.25F), 238.0F, 0.16F);
                  g = lerp(lerp(g, l, 0.25F), 233.0F, 0.16F);
                  b = lerp(lerp(b, l, 0.25F), 222.0F, 0.16F);
               }

               if (fibers) {
                  float n = (noise(x * 0.9F, y * 0.11F, 41) - 0.5F) * 10.0F + (noise(x * 0.13F, y * 0.85F, 43) - 0.5F) * 8.0F + (hash(x, y, 47) - 0.5F) * 6.0F;
                  r += n;
                  g += n;
                  b += n;
               }

               out[y * ow + x] = argb(a, r, g, b);
            }
         }
      }

      return out;
   }

   private static int[] shadow(int[] src, int w, int h) {
      int ow = w * S;
      int oh = h * S;
      int[] out = new int[ow * oh];

      for (int y = 0; y < oh; y++) {
         for (int x = 0; x < ow; x++) {
            int c = src[y / S * w + x / S];
            int a = c >>> 24;
            if (a >= 26) {
               float l = luma(c) * 0.75F + (noise(x * 0.07F, y * 0.07F, 51) - 0.5F) * 0.18F;
               float k = clamp01(l);
               out[y * ow + x] = argb(a, lerp(16.0F, 54.0F, k), lerp(12.0F, 40.0F, k), lerp(17.0F, 44.0F, k));
            }
         }
      }

      return out;
   }

   private static int[] embers(int[] src, int w, int h, LookTextures.Faces faces) {
      int ow = w * S;
      int oh = h * S;
      int[] out = new int[ow * oh];
      float[] nearest = new float[4];
      float cell = 4.2F;

      for (int y = 0; y < oh; y++) {
         for (int x = 0; x < ow; x++) {
            int c = src[y / S * w + x / S];
            if (c >>> 24 >= 26) {
               float px = (x + 0.5F) / S;
               float py = (y + 0.5F) / S;
               voronoi(px / cell, py / cell, 61, nearest);
               float border = (nearest[1] - nearest[0]) * cell;
               float keep = noise(px * 0.45F, py * 0.45F, 67);
               if (!(keep < 0.42F) && !(border > 0.3F)) {
                  float k = (1.0F - border / 0.3F) * clamp01((keep - 0.42F) * 4.0F);
                  float hot = 0.6F + 0.4F * noise(px * 1.7F, py * 1.7F, 71);
                  int a = Math.round(255.0F * clamp01(k * hot));
                  out[y * ow + x] = argb(a, 255.0F, 77.0F + 80.0F * k * hot, 46.0F + 40.0F * k);
               }
            }
         }
      }

      return out;
   }

   private static int[] toon(int[] src, int w, int h) {
      int[] flat = new int[w * h];

      for (int i = 0; i < w * h; i++) {
         int c = src[i];
         float[] hsv = hsv(c);
         float v = hsv[2] < 0.3F ? 0.38F : (hsv[2] < 0.62F ? 0.68F : 0.95F);
         hsv[1] = Math.min(1.0F, hsv[1] * 1.18F);
         flat[i] = c & 0xFF000000 | rgb(hsv[0], hsv[1], v) & 16777215;
      }

      return upscale(flat, w, h);
   }

   private static void voronoi(float x, float y, int seed, float[] out) {
      int ix = (int)Math.floor(x);
      int iy = (int)Math.floor(y);
      float d1 = 9.0F;
      float d2 = 9.0F;
      float sx = 0.0F;
      float sy = 0.0F;

      for (int j = -1; j <= 1; j++) {
         for (int i = -1; i <= 1; i++) {
            int cx = ix + i;
            int cy = iy + j;
            float fx = cx + 0.15F + 0.7F * hash(cx, cy, seed);
            float dx = fx - x;
            float fy;
            float dy;
            float d = (float)Math.sqrt(dx * dx + (dy = (fy = cy + 0.15F + 0.7F * hash(cx, cy, seed + 1)) - y) * dy);
            if (d < d1) {
               d2 = d1;
               d1 = d;
               sx = fx;
               sy = fy;
            } else if (d < d2) {
               d2 = d;
            }
         }
      }

      out[0] = d1;
      out[1] = d2;
      out[2] = sx;
      out[3] = sy;
   }

   private static float smoothLine(float d, float width) {
      float dist = Math.min(d, 5.5F - d);
      return clamp01(width * 0.5F + 0.5F - dist);
   }

   private static float mod(float a, float m) {
      float r = a % m;
      return r < 0.0F ? r + m : r;
   }

   static float hash(int x, int y, int seed) {
      int h = x * 374761393 + y * 668265263 + seed * 1442695041;
      h = (h ^ h >>> 13) * 1274126177;
      h ^= h >>> 16;
      return (h & 16777215) / 1.6777216E7F;
   }

   static float noise(float x, float y, int seed) {
      int ix = (int)Math.floor(x);
      int iy = (int)Math.floor(y);
      float fx = x - ix;
      float fy = y - iy;
      fx = fx * fx * (3.0F - 2.0F * fx);
      fy = fy * fy * (3.0F - 2.0F * fy);
      float a = hash(ix, iy, seed);
      float b = hash(ix + 1, iy, seed);
      float c = hash(ix, iy + 1, seed);
      float d = hash(ix + 1, iy + 1, seed);
      return lerp(lerp(a, b, fx), lerp(c, d, fx), fy);
   }

   static float luma(int c) {
      return (0.299F * (c >> 16 & 0xFF) + 0.587F * (c >> 8 & 0xFF) + 0.114F * (c & 0xFF)) / 255.0F;
   }

   private static float[] hsv(int c) {
      float r = (c >> 16 & 0xFF) / 255.0F;
      float g = (c >> 8 & 0xFF) / 255.0F;
      float b = (c & 0xFF) / 255.0F;
      float max = Math.max(r, Math.max(g, b));
      float min = Math.min(r, Math.min(g, b));
      float d = max - min;
      float hue = 0.0F;
      if (d > 1.0E-5F && (hue = max == r ? (g - b) / d / 6.0F : (max == g ? ((b - r) / d + 2.0F) / 6.0F : ((r - g) / d + 4.0F) / 6.0F)) < 0.0F) {
         hue++;
      }

      return new float[]{hue, max <= 1.0E-5F ? 0.0F : d / max, max};
   }

   private static int rgb(float hue, float s, float v) {
      int i = (int)Math.floor(hue * 6.0F);
      float f = hue * 6.0F - i;
      float p = v * (1.0F - s);
      float q = v * (1.0F - f * s);
      float t = v * (1.0F - (1.0F - f) * s);
      float b;
      float g;
      float r;
      switch ((i % 6 + 6) % 6) {
         case 0:
            r = v;
            g = t;
            b = p;
            break;
         case 1:
            r = q;
            g = v;
            b = p;
            break;
         case 2:
            r = p;
            g = v;
            b = t;
            break;
         case 3:
            r = p;
            g = q;
            b = v;
            break;
         case 4:
            r = t;
            g = p;
            b = v;
            break;
         default:
            r = v;
            g = p;
            b = q;
      }

      return 0xFF000000 | Math.round(r * 255.0F) << 16 | Math.round(g * 255.0F) << 8 | Math.round(b * 255.0F);
   }

   private static int argb(int a, float r, float g, float b) {
      return Math.max(0, Math.min(255, a)) << 24 | clamp255(r) << 16 | clamp255(g) << 8 | clamp255(b);
   }

   private static int clamp255(float v) {
      return v <= 0.0F ? 0 : (v >= 255.0F ? 255 : Math.round(v));
   }

   private static float clamp01(float v) {
      return v < 0.0F ? 0.0F : (v > 1.0F ? 1.0F : v);
   }

   private static float lerp(float a, float b, float t) {
      return a + (b - a) * t;
   }

   private static final class Entry {
      volatile int[][] levels;
      volatile boolean failed;
      int width;
      int height;
      int texture = -1;

      private Entry() {
      }
   }

   static final class Faces {
      final int[] owner;
      final List<float[]> rects = new ArrayList<>();
      final int w;
      final int h;

      Faces(int kind, int w, int h) {
         this.w = w;
         this.h = h;
         this.owner = new int[w * h];
         Arrays.fill(this.owner, -1);
         float sx = w / 64.0F;
         float sy = h / (kind != 2 && kind != 3 && kind != 4 && h != 32 ? 64.0F : 32.0F);
         float[] uv = new float[4];
         ArrayList<PlayerModel.Box> boxes = new ArrayList<>();
         if (kind == 4) {
            boxes.add(PlayerModel.CAPE);
         } else if (kind != 2 && kind != 3) {
            for (int part = 0; part < 6; part++) {
               boxes.add(PlayerModel.base(part, kind == 1));
               boxes.add(PlayerModel.wear(part, kind == 1));
            }
         } else {
            for (int part = 0; part < 6; part++) {
               boxes.add(PlayerModel.armor(part, kind == 3));
            }

            boxes.add(PlayerModel.HELMET_OVERLAY);
         }

         for (PlayerModel.Box b : boxes) {
            for (int f = 0; f < 6; f++) {
               PlayerModel.faceUV(b, f, uv);
               float u1 = Math.min(uv[0], uv[2]) * sx;
               float u2 = Math.max(uv[0], uv[2]) * sx;
               float v1 = Math.min(uv[1], uv[3]) * sy;
               float v2 = Math.max(uv[1], uv[3]) * sy;
               if (!(u2 - u1 < 0.5F) && !(v2 - v1 < 0.5F)) {
                  int id = this.rects.size();
                  this.rects.add(new float[]{u1, v1, u2, v2});

                  for (int y = (int)Math.floor(v1); y < Math.ceil(v2) && y < h; y++) {
                     for (int x = (int)Math.floor(u1); x < Math.ceil(u2) && x < w; x++) {
                        if (x >= 0 && y >= 0 && this.owner[y * w + x] < 0) {
                           this.owner[y * w + x] = id;
                        }
                     }
                  }
               }
            }
         }
      }

      boolean same(int ax, int ay, int bx, int by) {
         return this.owner[ay * this.w + ax] == this.owner[by * this.w + bx];
      }

      float edgeDistance(float px, float py) {
         int x = Math.min(this.w - 1, Math.max(0, (int)px));
         int y = Math.min(this.h - 1, Math.max(0, (int)py));
         int id = this.owner[y * this.w + x];
         if (id < 0) {
            return 99.0F;
         } else {
            float[] r = this.rects.get(id);
            return Math.min(Math.min(px - r[0], r[2] - px), Math.min(py - r[1], r[3] - py));
         }
      }

      int sampleInFace(int[] src, int w, int h, int sx, int sy, float cx, float cy) {
         int id = this.owner[sy * w + sx];
         int x = (int)cx;
         int y = (int)cy;
         if (id >= 0) {
            float[] r = this.rects.get(id);
            x = (int)Math.max(r[0], Math.min(r[2] - 0.01F, cx));
            y = (int)Math.max(r[1], Math.min(r[3] - 0.01F, cy));
         }

         x = Math.max(0, Math.min(w - 1, x));
         int c = src[Math.max(0, Math.min(h - 1, y)) * w + x];
         return c >>> 24 < 128 ? src[sy * w + sx] : c;
      }
   }

   private static final class Key {
      final ResourceLocation source;
      final BodyLook look;
      final int[] params;
      final int hash;

      Key(ResourceLocation source, BodyLook look, int[] params) {
         this.source = source;
         this.look = look;
         this.params = params;
         this.hash = (source.hashCode() * 31 + look.ordinal()) * 31 + Arrays.hashCode(params);
      }

      @Override
      public boolean equals(Object o) {
         if (!(o instanceof LookTextures.Key)) {
            return false;
         } else {
            LookTextures.Key k = (LookTextures.Key)o;
            return k.hash == this.hash && k.look == this.look && k.source.equals(this.source) && Arrays.equals(k.params, this.params);
         }
      }

      @Override
      public int hashCode() {
         return this.hash;
      }
   }
}
