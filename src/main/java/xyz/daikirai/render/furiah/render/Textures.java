package xyz.daikirai.render.furiah.render;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D.Float;
import java.awt.geom.Path2D.Double;
import java.awt.image.BufferedImage;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Arrays;
import java.util.Random;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.TextureUtil;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GLContext;

public final class Textures {
   private static final org.apache.logging.log4j.Logger LOG = org.apache.logging.log4j.LogManager.getLogger("NeoRaven/JumpCircles");
   public static int WHITE;
   public static int GLOW;
   public static int SPARK;
   public static int FLARE;
   public static int LINE_GLOW;
   public static int LINE_CORE;
   public static int WAVE;
   public static int GROUND_GLOW;
   public static int HEART;
   public static int STAR;
   public static int RUNES;
   public static int STREAKS;
   public static int RAY;
   public static int STREAK;
   public static int SMOKE;
   public static int BROKEN_HEART;
   public static int SPARKLE;
   public static int SKULL;
   public static int FEATHER;
   public static int PETAL;
   public static int LEAF;
   public static int SNOWFLAKE;
   public static int RADIAL_FILL;
   public static int HEX_FILL;
   public static int BUBBLE;
   public static int SNOW_ATLAS;
   public static final int SNOW_SOFT_CELL = 0;
   public static final int SNOW_CRYSTAL_CELL = 1;
   public static final int SNOW_BLUR_CELL = 2;
   public static final int SNOW_CELLS = 3;
   public static int FOLIAGE_ATLAS;
   public static final int FOLIAGE_PETAL = 0;
   public static final int FOLIAGE_LEAF = 1;
   public static final int FOLIAGE_MAPLE = 2;
   public static final int FOLIAGE_CELLS = 3;
   public static int BOKEH_CIRCLE;
   public static int BOKEH_CIRCLE_SOFT;
   public static int BOKEH_HEX;
   public static int BOKEH_HEX_SOFT;
   public static int MIST_ATLAS;
   public static final int MIST_CELLS = 4;
   public static int KILL_X;
   public static int KILL_CROWN;
   public static int STONE;
   public static int VIGNETTE;
   public static final int RUNE_COUNT = 8;
   public static final int NEON_COUNT = 5;
   public static final int[] NEON_BODY = new int[5];
   public static final int[] NEON_CORE = new int[5];
   public static final int[] NEON_GLOW = new int[5];
   public static final float NEON_SYMBOL_FRACTION = 0.64F;
   private static final int PROFILE = 256;
   private static boolean loaded;
   private static float maxAnisotropy = -1.0F;
   public static int SHOE_PRINT;
   public static int BARE_PRINT;
   public static int PAW_PRINT;
   public static int NOTE;
   public static int FLAME;
   public static final int FLAME_FRAMES = 4;
   private static boolean trailsLoaded;
   private static int killStep;
   private static boolean ambienceLoaded;

   private Textures() {
   }

   public static void ensureLoaded() {
      if (!loaded) {
         loaded = true;
         long start = System.nanoTime();
         WHITE = upload(filled(4, 4), 4, 4, false, false);
         GLOW = upload(radial(256, new Textures.Profile() {
            @Override
            public float at(float r) {
               float s = 1.0F - Easing.smoothstep(0.0F, 1.0F, r);
               return 0.75F * s * s + 0.25F * (float)Math.exp(-r * r / 0.02F);
            }
         }), 256, 256, true, false);
         SPARK = upload(radial(256, new Textures.Profile() {
            @Override
            public float at(float r) {
               float s = 1.0F - Easing.smoothstep(0.0F, 1.0F, r);
               return (float)Math.exp(-r * r / 0.012F) + 0.35F * s * s * s;
            }
         }), 256, 256, true, false);
         FLARE = upload(flare(256), 256, 256, true, false);
         LINE_GLOW = upload(lineProfile(new Textures.Profile() {
            @Override
            public float at(float d) {
               float s = 1.0F - Easing.smoothstep(0.0F, 1.0F, d);
               return 0.8F * s * s + 0.2F * (float)Math.exp(-d * d / 0.02F);
            }
         }), 4, 256, true, false);
         LINE_CORE = upload(lineProfile(new Textures.Profile() {
            @Override
            public float at(float d) {
               return 1.0F - Easing.smoothstep(0.55F, 1.0F, d);
            }
         }), 4, 256, true, false);
         WAVE = upload(waveProfile(), 4, 256, true, false);
         GROUND_GLOW = upload(radial(256, new Textures.Profile() {
            @Override
            public float at(float r) {
               float fill = 0.3F * (1.0F - Easing.smoothstep(0.0F, 0.92F, r));
               float d = (r - 0.8F) / 0.075F;
               float rim = 0.9F * (float)Math.exp(-d * d);
               return (fill + rim) * (1.0F - Easing.smoothstep(0.9F, 1.0F, r));
            }
         }), 256, 256, true, false);
         HEART = upload(icon(heartShape(), 256), 256, 256, true, false);
         STAR = upload(icon(starShape(0.5F, 0.52F, 0.44F, 0.2F), 256), 256, 256, true, false);
         RUNES = upload(runes(128), 1024, 128, true, false);
         STREAKS = upload(streaks(256), 256, 256, true, true);

         for (int i = 0; i < 5; i++) {
            generateNeon(i);
         }

         generateEffectTextures();
         LOG.info("Generated effect textures in {} ms", new Object[]{(System.nanoTime() - start) / 1000000L});
      }
   }

   public static void ensureTrailsLoaded() {
      ensureLoaded();
      if (!trailsLoaded) {
         trailsLoaded = true;
         SHOE_PRINT = upload(icon(shoeShape(), 128), 128, 128, true, false);
         BARE_PRINT = upload(icon(bareFootShape(), 128), 128, 128, true, false);
         PAW_PRINT = upload(icon(pawShape(), 128), 128, 128, true, false);
         NOTE = upload(icon(noteShape(), 128), 128, 128, true, false);
         FLAME = upload(flames(64, 128, 4), 256, 128, true, false);
      }
   }

   private static int[] flames(int fw, int fh, int frames) {
      int w = fw * frames;
      int[] px = new int[w * fh];

      for (int f = 0; f < frames; f++) {
         float scroll = (float)f / frames;

         for (int y = 0; y < fh; y++) {
            float v = 1.0F - (y + 0.5F) / fh;

            for (int x = 0; x < fw; x++) {
               float u = (x + 0.5F) / fw;
               float n = 0.6F * valueNoise(u * 3.0F, (v - scroll) * 4.0F, 4, 41) + 0.4F * valueNoise(u * 6.0F, (v - scroll) * 8.0F, 8, 53);
               float sway = (n - 0.5F) * 0.34F * v;
               float hw = 0.4F * (float)Math.pow(1.0F - v, 0.7) * Easing.smoothstep(0.0F, 0.24F, v + 0.05F);
               float d = Math.abs(u - 0.5F + sway);
               float body = 1.0F - Easing.smoothstep(hw * 0.5F, hw + 0.025F, d);
               float tip = 1.0F - Easing.smoothstep(0.72F, 1.0F, v + (n - 0.5F) * 0.3F);
               px[y * w + f * fw + x] = white(Easing.clamp01(body * tip * (0.7F + 0.3F * n)));
            }
         }
      }

      return px;
   }

   private static Shape shoeShape() {
      Area a = new Area(new Float(0.22F, 0.0F, 0.56F, 0.56F));
      a.add(new Area(new java.awt.geom.RoundRectangle2D.Float(0.3F, 0.3F, 0.4F, 0.34F, 0.18F, 0.18F)));
      a.add(new Area(new Float(0.28F, 0.6F, 0.44F, 0.4F)));
      return a;
   }

   private static Shape bareFootShape() {
      Area a = new Area(new Float(0.27F, 0.22F, 0.46F, 0.5F));
      a.add(new Area(new Float(0.31F, 0.58F, 0.36F, 0.42F)));

      for (float[] t : new float[][]{{0.25F, 0.08F, 0.17F}, {0.42F, 0.02F, 0.13F}, {0.55F, 0.03F, 0.11F}, {0.66F, 0.08F, 0.1F}, {0.74F, 0.16F, 0.09F}}) {
         a.add(new Area(new Float(t[0], t[1], t[2], t[2] * 1.15F)));
      }

      return a;
   }

   private static Shape pawShape() {
      java.awt.geom.Path2D.Float pad = new java.awt.geom.Path2D.Float();
      pad.moveTo(0.5F, 0.42F);
      pad.curveTo(0.72F, 0.42F, 0.86F, 0.62F, 0.82F, 0.8F);
      pad.curveTo(0.78F, 0.98F, 0.6F, 0.92F, 0.5F, 0.9F);
      pad.curveTo(0.4F, 0.92F, 0.22F, 0.98F, 0.18F, 0.8F);
      pad.curveTo(0.14F, 0.62F, 0.28F, 0.42F, 0.5F, 0.42F);
      pad.closePath();
      Area a = new Area(pad);

      for (float[] b : new float[][]{{0.08F, 0.32F}, {0.28F, 0.1F}, {0.52F, 0.1F}, {0.72F, 0.32F}}) {
         a.add(new Area(new Float(b[0], b[1], 0.2F, 0.24F)));
      }

      return a;
   }

   private static Shape noteShape() {
      Area a = new Area(new Float(0.12F, 0.66F, 0.42F, 0.3F));
      a.transform(AffineTransform.getRotateInstance(-0.35, 0.33, 0.81));
      a.add(new Area(new java.awt.geom.Rectangle2D.Float(0.45F, 0.06F, 0.09F, 0.78F)));
      java.awt.geom.Path2D.Float flag = new java.awt.geom.Path2D.Float();
      flag.moveTo(0.54F, 0.06F);
      flag.curveTo(0.66F, 0.18F, 0.9F, 0.24F, 0.84F, 0.5F);
      flag.curveTo(0.8F, 0.36F, 0.66F, 0.3F, 0.54F, 0.28F);
      flag.closePath();
      a.add(new Area(flag));
      return a;
   }

   public static void ensureKillLoaded() {
      while (!warmKill()) {
      }
   }

   public static boolean warmKill() {
      ensureLoaded();
      switch (killStep) {
         case 0:
            KILL_X = upload(icon(crossShape(), 256), 256, 256, true, false);
            break;
         case 1:
            KILL_CROWN = upload(icon(crownShape(), 256), 256, 256, true, false);
            break;
         case 2:
            STONE = upload(stone(128), 128, 128, true, true);
            break;
         case 3:
            VIGNETTE = upload(vignette(256), 256, 256, false, false);
            break;
         default:
            return true;
      }

      return ++killStep >= 4;
   }

   private static int[] vignette(int size) {
      int[] px = new int[size * size];
      float half = size / 2.0F;

      for (int y = 0; y < size; y++) {
         for (int x = 0; x < size; x++) {
            float dx = Math.abs((x + 0.5F - half) / half);
            float dy = Math.abs((y + 0.5F - half) / half);
            float r = (float)Math.pow(Math.pow(dx, 4.0) + Math.pow(dy, 4.0), 0.25);
            float a = Easing.smoothstep(0.55F, 1.02F, r);
            px[y * size + x] = white(a * a);
         }
      }

      return px;
   }

   private static Shape crossShape() {
      java.awt.geom.RoundRectangle2D.Float bar = new java.awt.geom.RoundRectangle2D.Float(0.06F, 0.4F, 0.88F, 0.2F, 0.2F, 0.2F);
      Area a = new Area(AffineTransform.getRotateInstance(Math.PI / 4, 0.5, 0.5).createTransformedShape(bar));
      a.add(new Area(AffineTransform.getRotateInstance(-Math.PI / 4, 0.5, 0.5).createTransformedShape(bar)));
      return a;
   }

   private static Shape crownShape() {
      java.awt.geom.Path2D.Float p = new java.awt.geom.Path2D.Float();
      p.moveTo(0.1F, 0.78F);
      p.lineTo(0.1F, 0.3F);
      p.lineTo(0.3F, 0.52F);
      p.lineTo(0.5F, 0.2F);
      p.lineTo(0.7F, 0.52F);
      p.lineTo(0.9F, 0.3F);
      p.lineTo(0.9F, 0.78F);
      p.closePath();
      Area a = new Area(p);
      a.add(new Area(new java.awt.geom.RoundRectangle2D.Float(0.08F, 0.76F, 0.84F, 0.12F, 0.06F, 0.06F)));

      for (float[] c : new float[][]{{0.1F, 0.26F}, {0.5F, 0.15F}, {0.9F, 0.26F}}) {
         a.add(new Area(new Float(c[0] - 0.065F, c[1] - 0.065F, 0.13F, 0.13F)));
      }

      return a;
   }

   private static int[] stone(int size) {
      int[] px = new int[size * size];
      Random rnd = new Random(4711L);
      float[] grid = new float[256];

      for (int i = 0; i < grid.length; i++) {
         grid[i] = rnd.nextFloat();
      }

      for (int y = 0; y < size; y++) {
         for (int x = 0; x < size; x++) {
            float v = 0.0F;
            float amp = 0.5F;
            float total = 0.0F;

            for (int o = 0; o < 3; o++) {
               int cells = 4 << o;
               float fx = (float)x / size * cells;
               float fy = (float)y / size * cells;
               int ix = (int)fx;
               int iy = (int)fy;
               float tx = fx - ix;
               float ty = fy - iy;
               tx = tx * tx * (3.0F - 2.0F * tx);
               ty = ty * ty * (3.0F - 2.0F * ty);
               float a = grid[(iy % cells * 16 + ix % cells) % grid.length];
               float b = grid[(iy % cells * 16 + (ix + 1) % cells) % grid.length];
               float c = grid[((iy + 1) % cells * 16 + ix % cells) % grid.length];
               float d = grid[((iy + 1) % cells * 16 + (ix + 1) % cells) % grid.length];
               v += amp * (a + (b - a) * tx + (c - a) * ty + (a - b - c + d) * tx * ty);
               total += amp;
               amp *= 0.5F;
            }

            float speck = rnd.nextFloat();
            float var22;
            float g = 0.58F + ((var22 = v / total) - 0.5F) * 0.35F + (speck > 0.97F ? 0.12F : (speck < 0.03F ? -0.12F : 0.0F));
            int gi = Math.max(0, Math.min(255, Math.round(g * 255.0F)));
            px[y * size + x] = 0xFF000000 | gi << 16 | gi << 8 | gi;
         }
      }

      return px;
   }

   public static void ensureAmbienceLoaded() {
      ensureLoaded();
      if (!ambienceLoaded) {
         ambienceLoaded = true;
         long start = System.nanoTime();
         int[] soft = radial(256, new Textures.Profile() {
            @Override
            public float at(float r) {
               return 0.95F * (1.0F - Easing.smoothstep(0.35F, 0.62F, r)) + 0.05F * (1.0F - Easing.smoothstep(0.0F, 0.5F, r));
            }
         });
         int[] blur = radial(256, new Textures.Profile() {
            @Override
            public float at(float r) {
               return 0.8F * (float)Math.exp(-r * r / 0.22F) * (1.0F - Easing.smoothstep(0.85F, 1.0F, r));
            }
         });
         SNOW_ATLAS = upload(row(256, soft, snowCrystal(256), blur), 768, 256, true, false);
         FOLIAGE_ATLAS = upload(row(256, petal(256), leaf(256), maple(256)), 768, 256, true, false);
         BOKEH_CIRCLE = upload(bokeh(256, false, false), 256, 256, true, false);
         BOKEH_CIRCLE_SOFT = upload(bokeh(256, false, true), 256, 256, true, false);
         BOKEH_HEX = upload(bokeh(256, true, false), 256, 256, true, false);
         BOKEH_HEX_SOFT = upload(bokeh(256, true, true), 256, 256, true, false);
         MIST_ATLAS = upload(row(256, mist(256, 101), mist(256, 138), mist(256, 175), mist(256, 212)), 1024, 256, true, false);
         LOG.info("Generated ambience textures in {} ms", new Object[]{(System.nanoTime() - start) / 1000000L});
      }
   }

   private static int[] row(int size, int[]... cells) {
      int w = size * cells.length;
      int[] px = new int[w * size];

      for (int c = 0; c < cells.length; c++) {
         for (int y = 0; y < size; y++) {
            System.arraycopy(cells[c], y * size, px, y * w + c * size, size);
         }
      }

      return px;
   }

   private static int[] snowCrystal(int size) {
      return painted(size, 0.025F, 0.35F, new Textures.Painter() {
         @Override
         public void paint(Graphics2D g, float s) {
            g.scale(s, s);
            g.setColor(Color.WHITE);
            BasicStroke arm = new BasicStroke(0.018F, 1, 1);
            BasicStroke twig = new BasicStroke(0.012F, 1, 1);

            for (int i = 0; i < 6; i++) {
               double a = i * Math.PI / 3.0 - (Math.PI / 2);
               float cx = (float)Math.cos(a);
               float cy = (float)Math.sin(a);
               g.setStroke(arm);
               g.draw(new java.awt.geom.Line2D.Float(0.5F + cx * 0.06F, 0.5F + cy * 0.06F, 0.5F + cx * 0.44F, 0.5F + cy * 0.44F));
               g.setStroke(twig);

               for (float[] b : new float[][]{{0.16F, 0.1F}, {0.26F, 0.12F}, {0.35F, 0.075F}}) {
                  float bx = 0.5F + cx * b[0];
                  float by = 0.5F + cy * b[0];

                  for (int side = -1; side <= 1; side += 2) {
                     double t = a + side * Math.PI / 3.0;
                     float ex = bx + (float)Math.cos(t) * b[1];
                     float ey = by + (float)Math.sin(t) * b[1];
                     g.draw(new java.awt.geom.Line2D.Float(bx, by, ex, ey));
                     if (b[1] >= 0.1F) {
                        float mx = (bx + ex) / 2.0F;
                        float my = (by + ey) / 2.0F;
                        g.draw(new java.awt.geom.Line2D.Float(mx, my, mx + (float)Math.cos(a) * 0.035F, my + (float)Math.sin(a) * 0.035F));
                     }
                  }
               }

               java.awt.geom.Path2D.Float tip = new java.awt.geom.Path2D.Float();
               float tx = 0.5F + cx * 0.44F;
               float ty = 0.5F + cy * 0.44F;
               float px = -cy * 0.018F;
               float py = cx * 0.018F;
               tip.moveTo(tx + cx * 0.03F, ty + cy * 0.03F);
               tip.lineTo(tx + px, ty + py);
               tip.lineTo(tx - cx * 0.03F, ty - cy * 0.03F);
               tip.lineTo(tx - px, ty - py);
               tip.closePath();
               g.fill(tip);
            }

            java.awt.geom.Path2D.Float hex = new java.awt.geom.Path2D.Float();

            for (int i = 0; i < 6; i++) {
               double a = i * Math.PI / 3.0 - (Math.PI / 2) + (Math.PI / 6);
               float px = 0.5F + (float)Math.cos(a) * 0.07F;
               float py = 0.5F + (float)Math.sin(a) * 0.07F;
               if (i == 0) {
                  hex.moveTo(px, py);
               } else {
                  hex.lineTo(px, py);
               }
            }

            hex.closePath();
            g.setStroke(new BasicStroke(0.014F, 1, 1));
            g.draw(hex);
            g.setColor(new Color(1.0F, 1.0F, 1.0F, 0.45F));
            g.fill(hex);
         }
      });
   }

   private static int[] bokeh(int size, boolean hex, boolean soft) {
      int[] px = new int[size * size];
      float half = size / 2.0F;

      for (int y = 0; y < size; y++) {
         for (int x = 0; x < size; x++) {
            float dx = (x + 0.5F - half) / half;
            float dy = (y + 0.5F - half) / half;
            float d = hex ? hexNorm(dx, dy) * 0.94F : (float)Math.sqrt(dx * dx + dy * dy);
            float a;
            if (soft) {
               a = (float)Math.exp(-d * d / 0.3F) * (1.0F - Easing.smoothstep(0.8F, 1.0F, d));
            } else {
               float body = 0.62F + 0.16F * d * d;
               float rim = 0.3F * (float)Math.exp(-((d - 0.88F) / 0.05F) * ((d - 0.88F) / 0.05F));
               a = (body + rim) * (1.0F - Easing.smoothstep(0.9F, 0.95F, d));
            }

            px[y * size + x] = white(a);
         }
      }

      return px;
   }

   private static int[] mist(int size, int seed) {
      int[] px = new int[size * size];
      float half = size / 2.0F;

      for (int y = 0; y < size; y++) {
         for (int x = 0; x < size; x++) {
            float dx = (x + 0.5F - half) / half;
            float dy = (y + 0.5F - half) / half;
            float r = (float)Math.sqrt(dx * dx + dy * dy);
            float u = (x + 0.5F) / size;
            float v = (y + 0.5F) / size;
            float n = 0.5F * valueNoise(u * 3.0F, v * 3.0F, 3, seed)
               + 0.3F * valueNoise(u * 6.0F, v * 6.0F, 6, seed + 13)
               + 0.2F * valueNoise(u * 12.0F, v * 12.0F, 12, seed + 29);
            float edge = r + (n - 0.5F) * 0.55F;
            float a = (1.0F - Easing.smoothstep(0.05F, 0.95F, edge)) * (0.45F + 0.55F * n);
            a *= a * 0.9F + 0.1F;
            int shade = Math.round((0.8F + 0.2F * Easing.clamp01(n * 1.3F)) * 255.0F);
            int alpha = Math.round(Easing.clamp01(a) * 255.0F);
            px[y * size + x] = alpha << 24 | shade << 16 | shade << 8 | shade;
         }
      }

      return px;
   }

   private static int[] maple(int size) {
      return painted(size, 0.02F, 0.3F, new Textures.Painter() {
         @Override
         public void paint(Graphics2D g, float s) {
            g.scale(s, s);
            Area leaf = new Area();
            double[] angles = new double[]{-90.0, -40.0, -140.0, 10.0, 190.0};
            double[] lengths = new double[]{0.42, 0.34, 0.34, 0.24, 0.24};

            for (int i = 0; i < angles.length; i++) {
               double a = Math.toRadians(angles[i]);
               double l = lengths[i];
               double cx = 0.5;
               double cy = 0.56;
               double tx = cx + Math.cos(a) * l;
               double ty = cy + Math.sin(a) * l;
               double nx = -Math.sin(a);
               double ny = Math.cos(a);
               Double lobe = new Double();
               lobe.moveTo(cx, cy);
               lobe.lineTo(cx + Math.cos(a) * l * 0.45 + nx * l * 0.28, cy + Math.sin(a) * l * 0.45 + ny * l * 0.28);
               lobe.lineTo(cx + Math.cos(a) * l * 0.62 + nx * l * 0.16, cy + Math.sin(a) * l * 0.62 + ny * l * 0.16);
               lobe.lineTo(tx, ty);
               lobe.lineTo(cx + Math.cos(a) * l * 0.62 - nx * l * 0.16, cy + Math.sin(a) * l * 0.62 - ny * l * 0.16);
               lobe.lineTo(cx + Math.cos(a) * l * 0.45 - nx * l * 0.28, cy + Math.sin(a) * l * 0.45 - ny * l * 0.28);
               lobe.closePath();
               leaf.add(new Area(lobe));
            }

            g.setColor(new Color(1.0F, 1.0F, 1.0F, 0.9F));
            g.fill(leaf);
            g.setClip(leaf);
            g.setComposite(AlphaComposite.getInstance(8, 0.4F));
            g.setStroke(new BasicStroke(0.012F, 1, 1));

            for (int var25 = 0; var25 < angles.length; var25++) {
               double a = Math.toRadians(angles[var25]);
               g.draw(new java.awt.geom.Line2D.Double(0.5, 0.56, 0.5 + Math.cos(a) * lengths[var25] * 0.9, 0.56 + Math.sin(a) * lengths[var25] * 0.9));
            }

            g.setComposite(AlphaComposite.SrcOver);
            g.setClip(null);
            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(0.02F, 1, 1));
            g.draw(new java.awt.geom.Line2D.Float(0.5F, 0.56F, 0.52F, 0.95F));
         }
      });
   }

   private static void generateEffectTextures() {
      RAY = upload(ray(64, 256), 64, 256, true, false);
      STREAK = upload(streak(256, 64), 256, 64, true, false);
      SMOKE = upload(smoke(128), 128, 128, true, false);
      BROKEN_HEART = upload(icon(brokenHeartShape(), 256), 256, 256, true, false);
      SPARKLE = upload(icon(sparkleShape(), 256), 256, 256, true, false);
      SKULL = upload(icon(skullShape(), 256), 256, 256, true, false);
      FEATHER = upload(feather(256), 256, 256, true, false);
      PETAL = upload(petal(256), 256, 256, true, false);
      LEAF = upload(leaf(256), 256, 256, true, false);
      SNOWFLAKE = upload(snowflake(256), 256, 256, true, false);
      RADIAL_FILL = upload(radial(256, new Textures.Profile() {
         @Override
         public float at(float r) {
            return (float)Math.pow(r, 1.8) * (1.0F - Easing.smoothstep(0.88F, 1.0F, r));
         }
      }), 256, 256, true, false);
      HEX_FILL = upload(hexFill(256), 256, 256, true, false);
      BUBBLE = upload(bubble(128), 128, 128, true, false);
   }

   private static int[] ray(int w, int h) {
      int[] px = new int[w * h];

      for (int y = 0; y < h; y++) {
         float v = (y + 0.5F) / h;
         float along = (float)Math.pow(1.0F - v, 1.7) * Easing.smoothstep(0.0F, 0.05F, v);

         for (int x = 0; x < w; x++) {
            float d = Math.abs((x + 0.5F) / w * 2.0F - 1.0F);
            float across = 1.0F - Easing.smoothstep(0.0F, 1.0F, d);
            px[y * w + x] = white(along * across * across);
         }
      }

      return px;
   }

   private static int[] streak(int w, int h) {
      int[] px = new int[w * h];

      for (int y = 0; y < h; y++) {
         float dy = (y + 0.5F) / h * 2.0F - 1.0F;
         float across = (float)(Math.exp(-dy * dy / 0.006) + 0.25 * Math.exp(-dy * dy / 0.08));

         for (int x = 0; x < w; x++) {
            float dx = Math.abs((x + 0.5F) / w * 2.0F - 1.0F);
            float along = (float)Math.pow(1.0F - dx, 2.6);
            px[y * w + x] = white(across * along * (1.0F - Easing.smoothstep(0.7F, 1.0F, Math.abs(dy))));
         }
      }

      return px;
   }

   private static float valueNoise(float x, float y, int cells, int seed) {
      int x0 = (int)Math.floor(x);
      int y0 = (int)Math.floor(y);
      float fx = x - x0;
      float fy = y - y0;
      float sx = fx * fx * (3.0F - 2.0F * fx);
      float sy = fy * fy * (3.0F - 2.0F * fy);
      float a = lattice(x0, y0, cells, seed);
      float b = lattice(x0 + 1, y0, cells, seed);
      float c = lattice(x0, y0 + 1, cells, seed);
      float d = lattice(x0 + 1, y0 + 1, cells, seed);
      return a + (b - a) * sx + (c + (d - c) * sx - (a + (b - a) * sx)) * sy;
   }

   private static float lattice(int x, int y, int cells, int seed) {
      int h = Math.floorMod(x, cells) * 374761393 + Math.floorMod(y, cells) * 668265263 + seed * 144665;
      h = (h ^ h >>> 13) * 1274126177;
      return ((h ^ h >>> 16) & 65535) / 65535.0F;
   }

   private static int[] smoke(int size) {
      int[] px = new int[size * size];
      float half = size / 2.0F;

      for (int y = 0; y < size; y++) {
         for (int x = 0; x < size; x++) {
            float dx = (x + 0.5F - half) / half;
            float dy = (y + 0.5F - half) / half;
            float r = (float)Math.sqrt(dx * dx + dy * dy);
            float u = (x + 0.5F) / size;
            float v = (y + 0.5F) / size;
            float n = 0.5F * valueNoise(u * 4.0F, v * 4.0F, 4, 11)
               + 0.3F * valueNoise(u * 8.0F, v * 8.0F, 8, 23)
               + 0.2F * valueNoise(u * 16.0F, v * 16.0F, 16, 37);
            float edge = r + (n - 0.5F) * 0.45F;
            float a = (1.0F - Easing.smoothstep(0.2F, 0.95F, edge)) * (0.55F + 0.45F * n);
            int shade = Math.round((0.72F + 0.28F * Easing.clamp01(n * 1.2F)) * 255.0F);
            int alpha = Math.round(Easing.clamp01(a) * 255.0F);
            px[y * size + x] = alpha << 24 | shade << 16 | shade << 8 | shade;
         }
      }

      return px;
   }

   private static Shape brokenHeartShape() {
      java.awt.geom.Path2D.Float zig = new java.awt.geom.Path2D.Float();
      zig.moveTo(0.5F, -0.1F);
      zig.lineTo(0.5F, 0.18F);
      zig.lineTo(0.43F, 0.34F);
      zig.lineTo(0.56F, 0.5F);
      zig.lineTo(0.45F, 0.66F);
      zig.lineTo(0.52F, 1.1F);
      java.awt.geom.Path2D.Float leftRegion = new java.awt.geom.Path2D.Float(zig);
      leftRegion.lineTo(-0.2F, 1.1F);
      leftRegion.lineTo(-0.2F, -0.1F);
      leftRegion.closePath();
      Area heart = new Area(heartShape());
      Area left = new Area(heart);
      left.intersect(new Area(leftRegion));
      Area right = new Area(heart);
      right.subtract(new Area(leftRegion));
      AffineTransform tl = new AffineTransform();
      tl.translate(-0.05F, 0.015F);
      tl.rotate(-0.12, 0.5, 0.8);
      AffineTransform tr = new AffineTransform();
      tr.translate(0.05F, 0.015F);
      tr.rotate(0.12, 0.5, 0.8);
      Area out = left.createTransformedArea(tl);
      out.add(right.createTransformedArea(tr));
      AffineTransform fit = new AffineTransform();
      fit.translate(0.06, 0.03);
      fit.scale(0.88, 0.88);
      return out.createTransformedArea(fit);
   }

   private static Shape sparkleShape() {
      java.awt.geom.Path2D.Float p = new java.awt.geom.Path2D.Float();
      p.moveTo(0.5F, 0.0F);
      p.quadTo(0.53F, 0.47F, 1.0F, 0.5F);
      p.quadTo(0.53F, 0.53F, 0.5F, 1.0F);
      p.quadTo(0.47F, 0.53F, 0.0F, 0.5F);
      p.quadTo(0.47F, 0.47F, 0.5F, 0.0F);
      p.closePath();
      return p;
   }

   private static Shape skullShape() {
      Area skull = new Area(new Float(0.12F, 0.04F, 0.76F, 0.7F));
      skull.add(new Area(new java.awt.geom.RoundRectangle2D.Float(0.27F, 0.5F, 0.46F, 0.42F, 0.12F, 0.12F)));
      skull.subtract(new Area(new Float(0.24F, 0.33F, 0.2F, 0.22F)));
      skull.subtract(new Area(new Float(0.56F, 0.33F, 0.2F, 0.22F)));
      java.awt.geom.Path2D.Float nose = new java.awt.geom.Path2D.Float();
      nose.moveTo(0.5F, 0.57F);
      nose.lineTo(0.44F, 0.69F);
      nose.lineTo(0.56F, 0.69F);
      nose.closePath();
      skull.subtract(new Area(nose));

      for (float tx : new float[]{0.39F, 0.47F, 0.55F}) {
         skull.subtract(new Area(new java.awt.geom.Rectangle2D.Float(tx, 0.79F, 0.035F, 0.14F)));
      }

      return skull;
   }

   private static int[] painted(int size, float glowRadius, float glowStrength, Textures.Painter painter) {
      BufferedImage img = new BufferedImage(size, size, 2);
      Graphics2D g = graphics(img);
      painter.paint(g, size);
      g.dispose();
      float[] shape = alphaOf(img);
      float[] glow = glowRadius > 0.0F ? blur(shape, size, size, Math.round(size * glowRadius)) : null;
      int[] px = new int[size * size];

      for (int i = 0; i < px.length; i++) {
         px[i] = white(glow == null ? shape[i] : Math.max(shape[i], glow[i] * glowStrength));
      }

      return px;
   }

   private static int[] feather(int size) {
      return painted(size, 0.02F, 0.35F, new Textures.Painter() {
         @Override
         public void paint(Graphics2D g, float s) {
            g.scale(s, s);
            java.awt.geom.Path2D.Float vane = new java.awt.geom.Path2D.Float();
            vane.moveTo(0.52F, 0.06F);
            vane.curveTo(0.72F, 0.2F, 0.74F, 0.52F, 0.56F, 0.84F);
            vane.lineTo(0.5F, 0.84F);
            vane.curveTo(0.28F, 0.6F, 0.3F, 0.26F, 0.52F, 0.06F);
            vane.closePath();
            Area area = new Area(vane);

            for (float[] cut : new float[][]{{0.66F, 0.36F, 0.56F, 0.44F}, {0.36F, 0.5F, 0.47F, 0.56F}, {0.65F, 0.6F, 0.55F, 0.66F}}) {
               java.awt.geom.Path2D.Float slit = new java.awt.geom.Path2D.Float();
               slit.moveTo(cut[0], cut[1] - 0.012F);
               slit.lineTo(cut[2], cut[3]);
               slit.lineTo(cut[0], cut[1] + 0.012F);
               slit.closePath();
               area.subtract(new Area(slit));
            }

            g.setColor(new Color(1.0F, 1.0F, 1.0F, 0.78F));
            g.fill(area);
            g.setClip(area);
            g.setColor(new Color(1.0F, 1.0F, 1.0F, 0.28F));
            g.setStroke(new BasicStroke(0.006F));

            for (int i = 0; i < 26; i++) {
               float y0 = 0.1F + i * 0.03F;
               g.draw(new java.awt.geom.Line2D.Float(0.52F, y0, 0.78F, y0 - 0.1F));
               g.draw(new java.awt.geom.Line2D.Float(0.52F, y0, 0.26F, y0 - 0.1F));
            }

            g.setClip(null);
            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(0.018F, 1, 1));
            java.awt.geom.Path2D.Float shaft = new java.awt.geom.Path2D.Float();
            shaft.moveTo(0.52F, 0.08F);
            shaft.curveTo(0.56F, 0.4F, 0.54F, 0.7F, 0.5F, 0.96F);
            g.draw(shaft);
         }
      });
   }

   private static int[] petal(int size) {
      return painted(size, 0.03F, 0.3F, new Textures.Painter() {
         @Override
         public void paint(Graphics2D g, float s) {
            g.scale(s, s);
            java.awt.geom.Path2D.Float p = new java.awt.geom.Path2D.Float();
            p.moveTo(0.5F, 0.94F);
            p.curveTo(0.2F, 0.78F, 0.1F, 0.4F, 0.26F, 0.14F);
            p.curveTo(0.34F, 0.04F, 0.46F, 0.08F, 0.5F, 0.18F);
            p.curveTo(0.54F, 0.08F, 0.66F, 0.04F, 0.74F, 0.14F);
            p.curveTo(0.9F, 0.4F, 0.8F, 0.78F, 0.5F, 0.94F);
            p.closePath();
            g.setColor(new Color(1.0F, 1.0F, 1.0F, 0.86F));
            g.fill(p);
            g.setClip(p);
            g.setColor(new Color(1.0F, 1.0F, 1.0F, 0.35F));
            g.setStroke(new BasicStroke(0.012F, 1, 1));
            g.draw(new java.awt.geom.Line2D.Float(0.5F, 0.9F, 0.5F, 0.3F));
            g.draw(new java.awt.geom.Line2D.Float(0.5F, 0.8F, 0.36F, 0.36F));
            g.draw(new java.awt.geom.Line2D.Float(0.5F, 0.8F, 0.64F, 0.36F));
            g.setClip(null);
         }
      });
   }

   private static int[] leaf(int size) {
      return painted(size, 0.02F, 0.3F, new Textures.Painter() {
         @Override
         public void paint(Graphics2D g, float s) {
            g.scale(s, s);
            java.awt.geom.Path2D.Float p = new java.awt.geom.Path2D.Float();
            p.moveTo(0.5F, 0.82F);
            p.curveTo(0.16F, 0.68F, 0.18F, 0.28F, 0.5F, 0.04F);
            p.curveTo(0.82F, 0.28F, 0.84F, 0.68F, 0.5F, 0.82F);
            p.closePath();
            Area area = new Area(p);
            g.setColor(new Color(1.0F, 1.0F, 1.0F, 0.9F));
            g.fill(area);
            g.setClip(area);
            g.setComposite(AlphaComposite.getInstance(8, 0.45F));
            g.setStroke(new BasicStroke(0.014F, 1, 1));
            g.draw(new java.awt.geom.Line2D.Float(0.5F, 0.8F, 0.5F, 0.1F));

            for (int i = 0; i < 5; i++) {
               float y0 = 0.7F - i * 0.12F;
               g.draw(new java.awt.geom.Line2D.Float(0.5F, y0, 0.3F, y0 - 0.12F));
               g.draw(new java.awt.geom.Line2D.Float(0.5F, y0, 0.7F, y0 - 0.12F));
            }

            g.setComposite(AlphaComposite.SrcOver);
            g.setClip(null);
            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(0.02F, 1, 1));
            g.draw(new java.awt.geom.Line2D.Float(0.5F, 0.8F, 0.52F, 0.96F));
         }
      });
   }

   private static int[] snowflake(int size) {
      return painted(size, 0.035F, 0.45F, new Textures.Painter() {
         @Override
         public void paint(Graphics2D g, float s) {
            g.scale(s, s);
            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(0.034F, 1, 1));

            for (int i = 0; i < 6; i++) {
               double a = i * Math.PI / 3.0;
               float cx = (float)Math.cos(a);
               float cy = (float)Math.sin(a);
               g.draw(new java.awt.geom.Line2D.Float(0.5F, 0.5F, 0.5F + cx * 0.4F, 0.5F + cy * 0.4F));

               for (float d : new float[]{0.2F, 0.3F}) {
                  float bx = 0.5F + cx * d;
                  float by = 0.5F + cy * d;
                  float len = d == 0.2F ? 0.1F : 0.07F;

                  for (int side = -1; side <= 1; side += 2) {
                     double b = a + side * Math.PI / 3.0;
                     g.draw(new java.awt.geom.Line2D.Float(bx, by, bx + (float)Math.cos(b) * len, by + (float)Math.sin(b) * len));
                  }
               }
            }

            java.awt.geom.Path2D.Float hex = new java.awt.geom.Path2D.Float();

            for (int i = 0; i < 6; i++) {
               double a = i * Math.PI / 3.0 + (Math.PI / 6);
               float px = 0.5F + (float)Math.cos(a) * 0.07F;
               float py = 0.5F + (float)Math.sin(a) * 0.07F;
               if (i == 0) {
                  hex.moveTo(px, py);
               } else {
                  hex.lineTo(px, py);
               }
            }

            hex.closePath();
            g.fill(hex);
         }
      });
   }

   public static float hexNorm(float x, float y) {
      float ax = Math.abs(x);
      float ay = Math.abs(y);
      return Math.max(ay, 0.8660254F * ax + 0.5F * ay) / 0.8660254F;
   }

   private static int[] hexFill(int size) {
      int[] px = new int[size * size];
      float half = size / 2.0F;

      for (int y = 0; y < size; y++) {
         for (int x = 0; x < size; x++) {
            float dx = (x + 0.5F - half) / half;
            float dy = (y + 0.5F - half) / half;
            float d = hexNorm(dx, dy);
            if (!(d >= 1.0F)) {
               float fill = 0.15F + 0.55F * d * d;
               float rim = (float)Math.exp(-((d - 0.93F) / 0.035F) * ((d - 0.93F) / 0.035F));
               px[y * size + x] = white((fill + rim) * (1.0F - Easing.smoothstep(0.96F, 1.0F, d)));
            }
         }
      }

      return px;
   }

   private static int[] bubble(int size) {
      int[] px = new int[size * size];
      float half = size / 2.0F;

      for (int y = 0; y < size; y++) {
         for (int x = 0; x < size; x++) {
            float dx = (x + 0.5F - half) / half;
            float dy = (y + 0.5F - half) / half;
            float r = (float)Math.sqrt(dx * dx + dy * dy);
            if (!(r >= 1.0F)) {
               float rim = (float)Math.exp(-((r - 0.72F) / 0.12F) * ((r - 0.72F) / 0.12F));
               float body = 0.22F * (1.0F - Easing.smoothstep(0.6F, 0.85F, r));
               float hx = dx + 0.3F;
               float hy = dy + 0.3F;
               float highlight = (float)Math.exp(-(hx * hx + hy * hy) / 0.018F);
               float a = (rim * 0.9F + body + highlight) * (1.0F - Easing.smoothstep(0.86F, 1.0F, r));
               px[y * size + x] = white(a);
            }
         }
      }

      return px;
   }

   private static int[] filled(int w, int h) {
      int[] px = new int[w * h];
      Arrays.fill(px, -1);
      return px;
   }

   private static int[] radial(int size, Textures.Profile profile) {
      int[] px = new int[size * size];
      float half = size / 2.0F;

      for (int y = 0; y < size; y++) {
         for (int x = 0; x < size; x++) {
            float dx = (x + 0.5F - half) / half;
            float dy = (y + 0.5F - half) / half;
            float r = (float)Math.sqrt(dx * dx + dy * dy);
            float a = r >= 1.0F ? 0.0F : profile.at(r);
            px[y * size + x] = white(a);
         }
      }

      return px;
   }

   private static int[] lineProfile(Textures.Profile profile) {
      int w = 4;
      int h = 256;
      int[] px = new int[w * h];

      for (int y = 0; y < h; y++) {
         float d = Math.abs((y + 0.5F) / h * 2.0F - 1.0F);
         int c = white(profile.at(d));

         for (int x = 0; x < w; x++) {
            px[y * w + x] = c;
         }
      }

      return px;
   }

   private static int[] waveProfile() {
      int w = 4;
      int h = 256;
      int[] px = new int[w * h];

      for (int y = 0; y < h; y++) {
         float v = (y + 0.5F) / h;
         float body = (float)Math.pow(v, 2.4);
         float front = 1.0F - Easing.smoothstep(0.88F, 1.0F, v);
         float inner = Easing.smoothstep(0.0F, 0.08F, v);
         int c = white(body * front * inner * 1.15F);

         for (int x = 0; x < w; x++) {
            px[y * w + x] = c;
         }
      }

      return px;
   }

   private static int[] flare(int size) {
      int[] px = new int[size * size];
      float half = size / 2.0F;

      for (int y = 0; y < size; y++) {
         for (int x = 0; x < size; x++) {
            float dx = (x + 0.5F - half) / half;
            float dy = (y + 0.5F - half) / half;
            float r2 = dx * dx + dy * dy;
            if (!(r2 >= 1.0F)) {
               float window = (1.0F - r2) * (1.0F - r2);
               float h = (float)Math.exp(-dy * dy / 9.0E-4F) * (1.0F - Math.abs(dx));
               float v = (float)Math.exp(-dx * dx / 9.0E-4F) * (1.0F - Math.abs(dy));
               float d1 = (dx - dy) * 0.7071F;
               float d2 = (dx + dy) * 0.7071F;
               float diag = 0.35F * ((float)Math.exp(-d1 * d1 / 6.0E-4F) + (float)Math.exp(-d2 * d2 / 6.0E-4F)) * (1.0F - (float)Math.sqrt(r2));
               float centre = (float)Math.exp(-r2 / 0.012F);
               px[y * size + x] = white((h * h + v * v + diag + centre) * window);
            }
         }
      }

      return px;
   }

   private static int[] streaks(int size) {
      int[] px = new int[size * size];

      for (int x = 0; x < size; x++) {
         double u = (double)x / size * Math.PI * 2.0;
         double col = 0.5 + 0.22 * Math.sin(u * 3.0 + 0.7) + 0.16 * Math.sin(u * 7.0 + 2.1) + 0.12 * Math.sin(u * 13.0 + 4.4);
         col = Math.max(0.0, Math.min(1.0, col));
         col = col * col * 1.3;
         double phase = Math.sin(u * 5.0 + 1.3) * 2.0;

         for (int y = 0; y < size; y++) {
            double v = (double)y / size * Math.PI * 2.0;
            double along = 0.55 + 0.45 * Math.sin(v * 2.0 + phase) * Math.sin(v + phase * 0.5 + u);
            px[y * size + x] = white((float)(col * along));
         }
      }

      return px;
   }

   private static Shape heartShape() {
      java.awt.geom.Path2D.Float p = new java.awt.geom.Path2D.Float();
      int n = 200;

      for (int i = 0; i <= n; i++) {
         double t = (double)i / n * Math.PI * 2.0;
         double x = 16.0 * Math.pow(Math.sin(t), 3.0);
         double y = 13.0 * Math.cos(t) - 5.0 * Math.cos(2.0 * t) - 2.0 * Math.cos(3.0 * t) - Math.cos(4.0 * t);
         float px = (float)(0.5 + x / 36.0);
         float py = (float)(0.46 - y / 36.0);
         if (i == 0) {
            p.moveTo(px, py);
         } else {
            p.lineTo(px, py);
         }
      }

      p.closePath();
      return p;
   }

   private static Shape starShape(float cx, float cy, float outer, float inner) {
      java.awt.geom.Path2D.Float p = new java.awt.geom.Path2D.Float();

      for (int i = 0; i < 10; i++) {
         double a = (-Math.PI / 2) + i * Math.PI / 5.0;
         float r = i % 2 == 0 ? outer : inner;
         float x = cx + (float)Math.cos(a) * r;
         float y = cy + (float)Math.sin(a) * r;
         if (i == 0) {
            p.moveTo(x, y);
         } else {
            p.lineTo(x, y);
         }
      }

      p.closePath();
      return p;
   }

   private static int[] icon(Shape unitShape, int size) {
      BufferedImage img = new BufferedImage(size, size, 2);
      Graphics2D g = graphics(img);
      float inset = size * 0.16F;
      AffineTransform tx = new AffineTransform();
      tx.translate(inset, inset);
      tx.scale(size - inset * 2.0F, size - inset * 2.0F);
      Shape s = tx.createTransformedShape(unitShape);
      g.setColor(Color.WHITE);
      g.fill(s);
      g.setStroke(new BasicStroke(size * 0.02F, 1, 1));
      g.draw(s);
      g.dispose();
      float[] shape = alphaOf(img);
      float[] glow = blur(shape, size, size, Math.round(size * 0.05F));
      int[] px = new int[size * size];

      for (int i = 0; i < px.length; i++) {
         px[i] = white(Math.max(shape[i], glow[i] * 0.55F));
      }

      return px;
   }

   private static int[] runes(int cell) {
      float[][][] glyphs = new float[][][]{
         {{0.35F, 0.1F, 0.35F, 0.9F}, {0.35F, 0.28F, 0.7F, 0.1F}, {0.35F, 0.5F, 0.7F, 0.32F}},
         {{0.3F, 0.9F, 0.3F, 0.12F, 0.7F, 0.34F, 0.7F, 0.9F}},
         {{0.35F, 0.1F, 0.35F, 0.9F}, {0.35F, 0.3F, 0.68F, 0.5F, 0.35F, 0.7F}},
         {{0.35F, 0.1F, 0.35F, 0.9F}, {0.35F, 0.12F, 0.7F, 0.32F}, {0.35F, 0.38F, 0.7F, 0.58F}},
         {{0.35F, 0.9F, 0.35F, 0.1F, 0.68F, 0.3F, 0.35F, 0.5F, 0.7F, 0.9F}},
         {{0.68F, 0.18F, 0.32F, 0.5F, 0.68F, 0.82F}},
         {{0.25F, 0.15F, 0.75F, 0.85F}, {0.75F, 0.15F, 0.25F, 0.85F}},
         {{0.5F, 0.1F, 0.5F, 0.9F}, {0.5F, 0.45F, 0.24F, 0.14F}, {0.5F, 0.45F, 0.76F, 0.14F}}
      };
      int w = cell * 8;
      BufferedImage img = new BufferedImage(w, cell, 2);
      Graphics2D g = graphics(img);
      g.setColor(Color.WHITE);
      g.setStroke(new BasicStroke(cell * 0.075F, 1, 1));

      for (int i = 0; i < 8; i++) {
         float ox = i * cell;

         for (float[] line : glyphs[i]) {
            java.awt.geom.Path2D.Float p = new java.awt.geom.Path2D.Float();
            p.moveTo(ox + line[0] * cell, line[1] * cell);

            for (int k = 2; k < line.length; k += 2) {
               p.lineTo(ox + line[k] * cell, line[k + 1] * cell);
            }

            g.draw(p);
         }
      }

      g.dispose();
      float[] shape = alphaOf(img);
      float[] glow = blur(shape, w, cell, Math.round(cell * 0.06F));
      int[] px = new int[w * cell];

      for (int i = 0; i < px.length; i++) {
         px[i] = white(Math.max(shape[i], glow[i] * 0.6F));
      }

      return px;
   }

   private static void generateNeon(int index) {
      int size = 256;
      float box = size * 0.64F;
      float offset = (size - box) / 2.0F;
      AffineTransform tx = new AffineTransform();
      tx.translate(offset, offset);
      tx.scale(box, box);
      Shape[] strokes = neonShapes(index);
      float bodyWidth = box * 0.075F;
      int[] body = strokeImage(strokes, tx, size, bodyWidth, index == 3);
      int[] core = strokeImage(strokes, tx, size, bodyWidth * 0.38F, index == 3);
      int small = size / 2;
      float[] a = new float[small * small];

      for (int y = 0; y < small; y++) {
         for (int x = 0; x < small; x++) {
            float sum = 0.0F;

            for (int yy = 0; yy < 2; yy++) {
               for (int xx = 0; xx < 2; xx++) {
                  sum += (body[(y * 2 + yy) * size + x * 2 + xx] >>> 24) / 255.0F;
               }
            }

            a[y * small + x] = sum / 4.0F;
         }
      }

      float[] blurred = blur(a, small, small, 8);
      float max = 1.0E-4F;

      for (float v : blurred) {
         max = Math.max(max, v);
      }

      int[] glow = new int[small * small];

      for (int i = 0; i < glow.length; i++) {
         glow[i] = white(Math.min(1.0F, blurred[i] / max * 1.1F));
      }

      NEON_BODY[index] = upload(body, size, size, true, false);
      NEON_CORE[index] = upload(core, size, size, true, false);
      NEON_GLOW[index] = upload(glow, small, small, true, false);
   }

   private static int[] strokeImage(Shape[] shapes, AffineTransform tx, int size, float width, boolean fillLastAsDot) {
      BufferedImage img = new BufferedImage(size, size, 2);
      Graphics2D g = graphics(img);
      g.setColor(Color.WHITE);
      g.setStroke(new BasicStroke(width, 1, 1));

      for (int i = 0; i < shapes.length; i++) {
         Shape s = tx.createTransformedShape(shapes[i]);
         if (fillLastAsDot && i == shapes.length - 1) {
            g.fill(s);
         } else {
            g.draw(s);
         }
      }

      g.dispose();
      return img.getRGB(0, 0, size, size, null, 0, size);
   }

   private static Shape[] neonShapes(int index) {
      switch (index) {
         case 0:
            java.awt.geom.Path2D.Float s = new java.awt.geom.Path2D.Float();
            s.moveTo(0.73F, 0.27F);
            s.curveTo(0.67F, 0.16F, 0.58F, 0.13F, 0.49F, 0.13F);
            s.curveTo(0.35F, 0.13F, 0.27F, 0.21F, 0.27F, 0.31F);
            s.curveTo(0.27F, 0.43F, 0.38F, 0.47F, 0.5F, 0.5F);
            s.curveTo(0.63F, 0.53F, 0.74F, 0.58F, 0.74F, 0.7F);
            s.curveTo(0.74F, 0.81F, 0.65F, 0.88F, 0.51F, 0.88F);
            s.curveTo(0.41F, 0.88F, 0.31F, 0.84F, 0.26F, 0.73F);
            return new Shape[]{s, new java.awt.geom.Line2D.Float(0.5F, 0.02F, 0.5F, 0.98F)};
         case 1:
            return new Shape[]{heartShape()};
         case 2:
            java.awt.geom.Path2D.Float head = new java.awt.geom.Path2D.Float();
            head.moveTo(0.29F, 0.67F);
            head.curveTo(0.14F, 0.57F, 0.12F, 0.4F, 0.18F, 0.27F);
            head.curveTo(0.26F, 0.12F, 0.4F, 0.06F, 0.5F, 0.06F);
            head.curveTo(0.6F, 0.06F, 0.74F, 0.12F, 0.82F, 0.27F);
            head.curveTo(0.88F, 0.4F, 0.86F, 0.57F, 0.71F, 0.67F);
            head.lineTo(0.7F, 0.86F);
            head.lineTo(0.3F, 0.86F);
            head.closePath();
            java.awt.geom.Path2D.Float nose = new java.awt.geom.Path2D.Float();
            nose.moveTo(0.5F, 0.55F);
            nose.lineTo(0.455F, 0.635F);
            nose.lineTo(0.545F, 0.635F);
            nose.closePath();
            return new Shape[]{
               head,
               new Float(0.27F, 0.34F, 0.17F, 0.17F),
               new Float(0.56F, 0.34F, 0.17F, 0.17F),
               nose,
               new java.awt.geom.Line2D.Float(0.4F, 0.74F, 0.4F, 0.86F),
               new java.awt.geom.Line2D.Float(0.5F, 0.74F, 0.5F, 0.86F),
               new java.awt.geom.Line2D.Float(0.6F, 0.74F, 0.6F, 0.86F)
            };
         case 3:
            return new Shape[]{
               new Float(0.17F, 0.17F, 0.66F, 0.66F),
               new java.awt.geom.Line2D.Float(0.5F, 0.02F, 0.5F, 0.33F),
               new java.awt.geom.Line2D.Float(0.5F, 0.67F, 0.5F, 0.98F),
               new java.awt.geom.Line2D.Float(0.02F, 0.5F, 0.33F, 0.5F),
               new java.awt.geom.Line2D.Float(0.67F, 0.5F, 0.98F, 0.5F),
               new Float(0.47F, 0.47F, 0.06F, 0.06F)
            };
         default:
            return new Shape[]{starShape(0.5F, 0.53F, 0.47F, 0.2F)};
      }
   }

   private static Graphics2D graphics(BufferedImage img) {
      Graphics2D g = img.createGraphics();
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
      g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
      return g;
   }

   private static float[] alphaOf(BufferedImage img) {
      int w = img.getWidth();
      int h = img.getHeight();
      int[] px = img.getRGB(0, 0, w, h, null, 0, w);
      float[] a = new float[px.length];

      for (int i = 0; i < px.length; i++) {
         a[i] = (px[i] >>> 24) / 255.0F;
      }

      return a;
   }

   private static float[] blur(float[] src, int w, int h, int radius) {
      float[] a = (float[])src.clone();
      float[] tmp = new float[a.length];

      for (int pass = 0; pass < 3; pass++) {
         boxBlur(a, tmp, w, h, radius, true);
         boxBlur(tmp, a, w, h, radius, false);
      }

      return a;
   }

   private static void boxBlur(float[] in, float[] out, int w, int h, int r, boolean horizontal) {
      float norm = 1.0F / (2 * r + 1);
      int lines = horizontal ? h : w;
      int len = horizontal ? w : h;

      for (int line = 0; line < lines; line++) {
         float sum = 0.0F;

         for (int i = -r; i <= r; i++) {
            sum += sample(in, w, horizontal, line, i, len);
         }

         for (int var13 = 0; var13 < len; var13++) {
            int idx = horizontal ? line * w + var13 : var13 * w + line;
            out[idx] = sum * norm;
            sum += sample(in, w, horizontal, line, var13 + r + 1, len) - sample(in, w, horizontal, line, var13 - r, len);
         }
      }
   }

   private static float sample(float[] a, int w, boolean horizontal, int line, int i, int len) {
      if (i >= 0 && i < len) {
         return horizontal ? a[line * w + i] : a[i * w + line];
      } else {
         return 0.0F;
      }
   }

   private static int white(float alpha) {
      int a = Math.round(Math.max(0.0F, Math.min(1.0F, alpha)) * 255.0F);
      return a << 24 | 16777215;
   }

   public static int uploadImage(int[] argb, int w, int h, boolean mipmap, boolean repeat) {
      return upload(argb, w, h, mipmap, repeat);
   }

   public static int[][] mipChain(int[] argb, int w, int h) {
      int levels = 0;

      for (int m = Math.max(w, h); m > 1; levels++) {
         m >>= 1;
      }

      int[][] chain = new int[levels + 1][];
      chain[0] = argb;
      int lw = w;
      int lh = h;

      for (int l = 1; l <= levels; l++) {
         int nw = Math.max(1, lw / 2);
         int nh = Math.max(1, lh / 2);
         chain[l] = downsample(chain[l - 1], lw, lh, nw, nh);
         lw = nw;
         lh = nh;
      }

      return chain;
   }

   public static int uploadChain(int[][] chain, int w, int h, boolean repeat) {
      int id = TextureUtil.glGenTextures();
      GlStateManager.bindTexture(id);
      int levels = chain.length - 1;
      GL11.glTexParameteri(3553, 33085, levels);
      GL11.glTexParameteri(3553, 10241, levels > 0 ? 9987 : 9729);
      GL11.glTexParameteri(3553, 10240, 9729);
      int wrap = repeat ? 10497 : '脯';
      GL11.glTexParameteri(3553, 10242, wrap);
      GL11.glTexParameteri(3553, 10243, wrap);
      if (levels > 0 && anisotropy() > 1.0F) {
         GL11.glTexParameterf(3553, 34046, anisotropy());
      }

      int lw = w;
      int lh = h;

      for (int l = 0; l <= levels; l++) {
         IntBuffer buffer = BufferUtils.createIntBuffer(lw * lh);
         buffer.put(chain[l], 0, lw * lh);
         ((Buffer)buffer).flip();
         GL11.glTexImage2D(3553, l, 32856, lw, lh, 0, 32993, 33639, buffer);
         lw = Math.max(1, lw / 2);
         lh = Math.max(1, lh / 2);
      }

      Vfx.resetTextureBinding();
      return id;
   }

   private static int upload(int[] argb, int w, int h, boolean mipmap, boolean repeat) {
      int id = TextureUtil.glGenTextures();
      GlStateManager.bindTexture(id);
      int levels = 0;
      if (mipmap) {
         for (int m = Math.max(w, h); m > 1; levels++) {
            m >>= 1;
         }
      }

      GL11.glTexParameteri(3553, 33085, levels);
      GL11.glTexParameteri(3553, 10241, mipmap ? 9987 : 9729);
      GL11.glTexParameteri(3553, 10240, 9729);
      int wrap = repeat ? 10497 : '脯';
      GL11.glTexParameteri(3553, 10242, wrap);
      GL11.glTexParameteri(3553, 10243, wrap);
      if (mipmap && anisotropy() > 1.0F) {
         GL11.glTexParameterf(3553, 34046, anisotropy());
      }

      int[] level = argb;
      int lw = w;
      int lh = h;

      for (int l = 0; l <= levels; l++) {
         IntBuffer buffer = BufferUtils.createIntBuffer(lw * lh);
         buffer.put(level, 0, lw * lh);
         ((Buffer)buffer).flip();
         GL11.glTexImage2D(3553, l, 32856, lw, lh, 0, 32993, 33639, buffer);
         if (l < levels) {
            int nw = Math.max(1, lw / 2);
            int nh = Math.max(1, lh / 2);
            level = downsample(level, lw, lh, nw, nh);
            lw = nw;
            lh = nh;
         }
      }

      return id;
   }

   public static byte[][] laMipChain(byte[] la, int w, int h) {
      int levels = 0;

      for (int m = Math.max(w, h); m > 1; m >>= 1) {
         levels++;
      }

      byte[][] chain = new byte[levels + 1][];
      chain[0] = la;
      int lw = w;
      int lh = h;

      for (int l = 1; l <= levels; l++) {
         int nw = Math.max(1, lw / 2);
         int nh = Math.max(1, lh / 2);
         byte[] src = chain[l - 1];
         byte[] dst = new byte[nw * nh * 2];
         int fx = lw / nw;
         int fy = lh / nh;

         for (int y = 0; y < nh; y++) {
            for (int x = 0; x < nw; x++) {
               int a = 0;
               int lum = 0;
               int n = 0;

               for (int yy = 0; yy < fy; yy++) {
                  for (int xx = 0; xx < fx; xx++) {
                     int i = ((y * fy + yy) * lw + x * fx + xx) * 2;
                     int ca = src[i + 1] & 255;
                     a += ca;
                     lum += (src[i] & 255) * ca;
                     n++;
                  }
               }

               int o = (y * nw + x) * 2;
               dst[o] = (byte)(a > 0 ? lum / a : 255);
               dst[o + 1] = (byte)(a / n);
            }
         }

         chain[l] = dst;
         lw = nw;
         lh = nh;
      }

      return chain;
   }

   public static int uploadLAChain(byte[][] chain, int w, int h, boolean repeat) {
      int id = TextureUtil.glGenTextures();
      GlStateManager.bindTexture(id);
      int levels = chain.length - 1;
      GL11.glTexParameteri(3553, 33085, levels);
      GL11.glTexParameteri(3553, 10241, levels > 0 ? 9987 : 9729);
      GL11.glTexParameteri(3553, 10240, 9729);
      int wrap = repeat ? 10497 : '脯';
      GL11.glTexParameteri(3553, 10242, wrap);
      GL11.glTexParameteri(3553, 10243, wrap);
      if (levels > 0 && anisotropy() > 1.0F) {
         GL11.glTexParameterf(3553, 34046, anisotropy());
      }

      int alignment;
      if ((alignment = GL11.glGetInteger(3317)) > 2) {
         GL11.glPixelStorei(3317, 2);
      }

      try {
         int lw = w;
         int lh = h;

         for (int l = 0; l <= levels; l++) {
            ByteBuffer buffer = BufferUtils.createByteBuffer(lw * lh * 2);
            buffer.put(chain[l], 0, lw * lh * 2);
            ((Buffer)buffer).flip();
            GL11.glTexImage2D(3553, l, 32837, lw, lh, 0, 6410, 5121, buffer);
            lw = Math.max(1, lw / 2);
            lh = Math.max(1, lh / 2);
         }
      } finally {
         if (alignment > 2) {
            GL11.glPixelStorei(3317, alignment);
         }
      }

      Vfx.resetTextureBinding();
      return id;
   }

   private static int[] downsample(int[] src, int w, int h, int nw, int nh) {
      int[] dst = new int[nw * nh];
      int fx = w / nw;
      int fy = h / nh;

      for (int y = 0; y < nh; y++) {
         for (int x = 0; x < nw; x++) {
            int a = 0;
            int r = 0;
            int g = 0;
            int b = 0;
            int n = 0;

            for (int yy = 0; yy < fy; yy++) {
               for (int xx = 0; xx < fx; xx++) {
                  int c = src[(y * fy + yy) * w + x * fx + xx];
                  int ca = c >>> 24;
                  a += ca;
                  r += (c >> 16 & 0xFF) * ca;
                  g += (c >> 8 & 0xFF) * ca;
                  b += (c & 0xFF) * ca;
                  n++;
               }
            }

            int outA = a / n;
            dst[y * nw + x] = a > 0 ? outA << 24 | r / a << 16 | g / a << 8 | b / a : 16777215;
         }
      }

      return dst;
   }

   private static float anisotropy() {
      if (maxAnisotropy < 0.0F) {
         maxAnisotropy = 1.0F;

         try {
            if (GLContext.getCapabilities().GL_EXT_texture_filter_anisotropic) {
               maxAnisotropy = Math.min(8.0F, GL11.glGetFloat(34047));
            }
         } catch (RuntimeException var1) {
            maxAnisotropy = 1.0F;
         }
      }

      return maxAnisotropy;
   }

   public static boolean isLoaded() {
      return loaded;
   }

   private interface Painter {
      void paint(Graphics2D var1, float var2);
   }

   private interface Profile {
      float at(float var1);
   }
}
