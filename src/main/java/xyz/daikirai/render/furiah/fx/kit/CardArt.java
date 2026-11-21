package xyz.daikirai.render.furiah.fx.kit;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Area;
import java.awt.geom.GeneralPath;
import java.awt.geom.RoundRectangle2D.Double;
import java.awt.image.BufferedImage;
import java.nio.Buffer;
import java.nio.IntBuffer;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.TextureUtil;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

public final class CardArt {
   private static final org.apache.logging.log4j.Logger LOG = org.apache.logging.log4j.LogManager.getLogger("NeoRaven/JumpCircles");
   public static final int W = 1024;
   public static final int H = 1536;
   public static final int CELL = 512;
   static final int ATLAS_W = 1536;
   static final int ATLAS_H = 1024;
   public static final int SUN = 0;
   public static final int MOON = 1;
   public static final int STAR = 2;
   public static final int EYE = 3;
   public static final int TOWER = 4;
   public static final int WHEEL = 5;
   public static final int CELESTIAL = 0;
   public static final int FLORAL = 1;
   public static final int MINIMAL = 2;
   private static final int DESIGNS = 3;
   private static final int SYMBOLS_SLOT = 6;
   private static final int PORTRAIT_SLOT = 7;
   private static final int SLOTS = 10;
   private static final double PICTURE_TOP = 205.0;
   private static final double PICTURE_BOTTOM = 1225.0;
   private static final double TITLE_TOP = 1236.0;
   private static final double TITLE_BOTTOM = 1372.0;
   public static final double PICTURE_Y = 0.051757812F;
   public static final double PICTURE_H = 0.99609375;
   public static final double PICTURE_W = 0.6640625;
   public static final double TITLE_Y = -0.5234375;
   public static final double TITLE_W = 0.5859375;
   public static final double TITLE_H = 0.1328125;
   private static final int[] TEXTURES = new int[10];
   private static final boolean[] REQUESTED = new boolean[10];
   private static final int[][][] PAINTED = new int[10][][];
   private static final ExecutorService PAINTER = Executors.newSingleThreadExecutor(new ThreadFactory() {
      @Override
      public Thread newThread(Runnable r) {
         Thread t = new Thread(r, "Furiah's Visuals card art");
         t.setDaemon(true);
         t.setPriority(1);
         return t;
      }
   });
   private static boolean uploadedThisFrame;
   private static long frameMark;

   private CardArt() {
   }

   public static int face(int d) {
      return get(d * 2);
   }

   public static int back(int d) {
      return get(d * 2 + 1);
   }

   public static int symbols() {
      return get(6);
   }

   public static int portraitFace(int d) {
      return get(7 + d);
   }

   public static void cell(int symbol, float[] out) {
      int cx = symbol % 3;
      int cy = symbol / 3;
      out[0] = cx / 3.0F;
      out[1] = cy / 2.0F;
      out[2] = (cx + 1) / 3.0F;
      out[3] = (cy + 1) / 2.0F;
   }

   private static int get(int slot) {
      if (TEXTURES[slot] != 0) {
         return TEXTURES[slot];
      } else if (!REQUESTED[slot]) {
         request(slot);
         return 0;
      } else {
         long now = System.nanoTime();
         if (now - frameMark > 4000000L) {
            frameMark = now;
            uploadedThisFrame = false;
         }

         if (uploadedThisFrame) {
            return 0;
         } else {
            int[][][] var4 = PAINTED;
            synchronized (PAINTED) {
               int[][] levels = PAINTED[slot];
               PAINTED[slot] = (int[][])null;
               if (levels != null) {
                  TEXTURES[slot] = upload(levels, slot == 6 ? 1536 : 1024, slot == 6 ? 1024 : 1536);
                  uploadedThisFrame = true;
                  return TEXTURES[slot];
               } else {
                  return 0;
               }
            }
         }
      }
   }

   private static void request(final int slot) {
      REQUESTED[slot] = true;
      PAINTER.execute(new Runnable() {
         @Override
         public void run() {
            try {
               long start = System.nanoTime();
               int w = 1024;
               int h = 1536;
               int[] px;
               if (slot == 6) {
                  px = CardArt.symbolAtlas();
                  w = 1536;
                  h = 1024;
               } else {
                  px = slot >= 7 ? CardArt.faceFrame(slot - 7, true) : ((slot & 1) == 0 ? CardArt.faceFrame(slot / 2, false) : CardArt.cardBack(slot / 2));
               }

               int[][] levels = CardArt.mipmaps(px, w, h);
               int[][][] nArray = CardArt.PAINTED;
               synchronized (nArray) {
                  CardArt.PAINTED[slot] = levels;
               }

               LOG.debug("Card art {} painted in {} ms", new Object[]{slot, (System.nanoTime() - start) / 1000000L});
            } catch (RuntimeException var11) {
               LOG.warn("Painting the card art failed; the cards show without it", var11);
            }
         }
      });
   }

   public static void paintAllForTest() {
      for (int s = 0; s < 10; s++) {
         if (TEXTURES[s] == 0) {
            REQUESTED[s] = true;
            int[] px = s == 6 ? symbolAtlas() : (s >= 7 ? faceFrame(s - 7, true) : ((s & 1) == 0 ? faceFrame(s / 2, false) : cardBack(s / 2)));
            int w = s == 6 ? 1536 : 1024;
            int h = s == 6 ? 1024 : 1536;
            TEXTURES[s] = upload(mipmaps(px, w, h), w, h);
         }
      }
   }

   public static String stateForTest() {
      int n = 0;

      for (int t : TEXTURES) {
         if (t != 0) {
            n++;
         }
      }

      return n + " of " + 10;
   }

   private static Graphics2D pen(BufferedImage img) {
      Graphics2D g = img.createGraphics();
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
      g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
      g.setColor(Color.WHITE);
      return g;
   }

   private static void width(Graphics2D g, double w) {
      g.setStroke(new BasicStroke((float)w, 1, 1));
   }

   private static int[] pixels(BufferedImage img) {
      int w = img.getWidth();
      int h = img.getHeight();
      int[] px = img.getRGB(0, 0, w, h, null, 0, w);

      for (int i = 0; i < px.length; i++) {
         px[i] = px[i] & 0xFF000000 | 16777215;
      }

      return px;
   }

   static int[] faceFrame(int design, boolean portrait) {
      BufferedImage img = new BufferedImage(1024, 1536, 2);
      Graphics2D g = pen(img);
      double sx = 512.0;
      double sy = 600.0;
      if (design == 0) {
         width(g, 7.0);
         g.draw(new Double(64.0, 64.0, 896.0, 1408.0, 96.0, 96.0));
         width(g, 2.5);
         g.draw(new Double(90.0, 90.0, 844.0, 1356.0, 70.0, 70.0));

         for (double[] c : new double[][]{{150.0, 150.0}, {874.0, 150.0}, {150.0, 1386.0}, {874.0, 1386.0}}) {
            width(g, 4.0);
            star(g, c[0], c[1], 4, 40.0, 10.0, 0.0);
            width(g, 2.5);
            g.draw(new java.awt.geom.Ellipse2D.Double(c[0] - 16.0, c[1] - 16.0, 32.0, 32.0));
         }

         for (double x = 230.0; x <= 794.0; x += 56.0) {
            dot(g, x, 120.0, 4.0);
            dot(g, x, 1416.0, 4.0);
         }

         if (!portrait) {
            width(g, 3.0);
            g.draw(new java.awt.geom.Ellipse2D.Double(sx - 330.0, sy - 330.0, 660.0, 660.0));
            width(g, 1.8);
            g.draw(new java.awt.geom.Ellipse2D.Double(sx - 350.0, sy - 350.0, 700.0, 700.0));

            for (int i = 0; i < 48; i++) {
               double a = i * Math.PI * 2.0 / 48.0;
               double in = i % 4 == 0 ? 300.0 : 316.0;
               g.draw(new java.awt.geom.Line2D.Double(sx + Math.cos(a) * in, sy + Math.sin(a) * in, sx + Math.cos(a) * 330.0, sy + Math.sin(a) * 330.0));
            }
         }

         width(g, 4.0);
         crescent(g, sx, 205.0, 44.0, 0.55);
         width(g, 3.0);
         star(g, sx - 120.0, 215.0, 4, 16.0, 5.0, 0.0);
         star(g, sx + 120.0, 215.0, 4, 16.0, 5.0, 0.0);
         plate(g, 1, portrait);
      } else if (design == 1) {
         width(g, 6.0);
         g.draw(new Double(64.0, 64.0, 896.0, 1408.0, 120.0, 120.0));
         width(g, 3.0);
         vine(g, 120.0, 260.0, 120.0, 1276.0, 1);
         vine(g, 904.0, 260.0, 904.0, 1276.0, -1);

         for (double[] c : new double[][]{{150.0, 150.0}, {874.0, 150.0}, {150.0, 1386.0}, {874.0, 1386.0}}) {
            rosette(g, c[0], c[1], 44.0, 6);
         }

         if (!portrait) {
            width(g, 3.0);

            for (int i = 0; i < 22; i++) {
               double a = i * Math.PI * 2.0 / 22.0;
               leaf(g, sx + Math.cos(a) * 330.0, sy + Math.sin(a) * 330.0, a + (Math.PI / 2), 46.0, 18.0);
            }

            width(g, 2.0);
            g.draw(new java.awt.geom.Ellipse2D.Double(sx - 300.0, sy - 300.0, 600.0, 600.0));
         }

         plate(g, 2, portrait);
      } else {
         width(g, 4.0);
         g.draw(new Double(72.0, 72.0, 880.0, 1392.0, 64.0, 64.0));
         width(g, 3.0);

         for (double[] c : new double[][]{{120.0, 120.0, 1.0, 1.0}, {904.0, 120.0, -1.0, 1.0}, {120.0, 1416.0, 1.0, -1.0}, {904.0, 1416.0, -1.0, -1.0}}) {
            g.draw(new java.awt.geom.Line2D.Double(c[0], c[1], c[0] + 60.0 * c[2], c[1]));
            g.draw(new java.awt.geom.Line2D.Double(c[0], c[1], c[0], c[1] + 60.0 * c[3]));
         }

         if (!portrait) {
            width(g, 2.5);
            g.draw(new java.awt.geom.Ellipse2D.Double(sx - 320.0, sy - 320.0, 640.0, 640.0));
         }

         plate(g, 0, portrait);
      }

      g.dispose();
      return pixels(img);
   }

   private static void plate(Graphics2D g, int style, boolean portrait) {
      double y = 1236.0;
      width(g, 3.0);
      g.draw(new java.awt.geom.Line2D.Double(180.0, y, 452.0, y));
      g.draw(new java.awt.geom.Line2D.Double(572.0, y, 844.0, y));
      if (style == 0) {
         dot(g, 512.0, y, 7.0);
      } else if (style == 1) {
         width(g, 3.5);
         diamond(g, 512.0, y, 34.0, 18.0);
         dot(g, 512.0, y, 5.0);
      } else {
         rosette(g, 512.0, y, 28.0, 5);
      }

      width(g, 2.0);
      if (!portrait) {
         g.draw(new java.awt.geom.Line2D.Double(240.0, y + 46.0, 784.0, y + 46.0));
      } else {
         g.draw(new java.awt.geom.Line2D.Double(250.0, 1372.0, 774.0, 1372.0));
         width(g, 2.5);
         diamond(g, 236.0, 1372.0, 12.0, 8.0);
         diamond(g, 788.0, 1372.0, 12.0, 8.0);
      }
   }

   static int[] cardBack(int design) {
      BufferedImage img = new BufferedImage(1024, 1536, 2);
      Graphics2D g = pen(img);
      double cx = 512.0;
      double cy = 768.0;
      width(g, 7.0);
      g.draw(new Double(64.0, 64.0, 896.0, 1408.0, 96.0, 96.0));
      width(g, 2.5);
      g.draw(new Double(92.0, 92.0, 840.0, 1352.0, 70.0, 70.0));
      if (design == 0) {
         Random r = new Random(500231L);

         for (int i = 0; i < 140; i++) {
            double x = 130.0 + r.nextDouble() * 764.0;
            double dx = x - cx;
            double y;
            double dy;
            if (!(dx * dx + (dy = (y = 130.0 + r.nextDouble() * 1276.0) - cy) * dy < 90000.0)) {
               double s = r.nextDouble();
               if (s < 0.75) {
                  dot(g, x, y, 2.0 + s * 4.0);
               } else {
                  width(g, 2.5);
                  star(g, x, y, 4, 10.0 + s * 14.0, 3.0, r.nextDouble());
               }
            }
         }

         width(g, 5.0);
         star(g, cx, cy, 8, 200.0, 70.0, 0.0);
         width(g, 3.0);
         g.draw(new java.awt.geom.Ellipse2D.Double(cx - 240.0, cy - 240.0, 480.0, 480.0));
         g.draw(new java.awt.geom.Ellipse2D.Double(cx - 260.0, cy - 260.0, 520.0, 520.0));
         width(g, 4.0);

         for (int var19 = 0; var19 < 4; var19++) {
            double a = var19 * Math.PI / 2.0 + (Math.PI / 4);
            crescent(g, cx + Math.cos(a) * 330.0, cy + Math.sin(a) * 330.0, 30.0, 0.55);
         }
      } else if (design == 1) {
         width(g, 3.5);
         petals(g, cx, cy, 12, 90.0, 250.0, 60.0);
         petals(g, cx, cy, 8, 50.0, 150.0, 44.0);
         petals(g, cx, cy, 6, 18.0, 80.0, 30.0);
         g.draw(new java.awt.geom.Ellipse2D.Double(cx - 280.0, cy - 280.0, 560.0, 560.0));
         dot(g, cx, cy, 12.0);

         for (double[] c : new double[][]{
            {170.0, 170.0, Math.PI / 4}, {854.0, 170.0, Math.PI * 3.0 / 4.0}, {170.0, 1366.0, -Math.PI / 4}, {854.0, 1366.0, -Math.PI * 3.0 / 4.0}
         }) {
            for (int k = -1; k <= 1; k++) {
               leaf(g, c[0], c[1], c[2] + k * 0.5, 70.0, 24.0);
            }
         }

         width(g, 3.0);
         vine(g, cx - 300.0, 200.0, cx + 300.0, 200.0, 1);
         vine(g, cx - 300.0, 1336.0, cx + 300.0, 1336.0, -1);
      } else {
         width(g, 2.0);

         for (double y = 170.0; y <= 1366.0; y += 80.0) {
            for (double x = 170 + (int)((y - 170.0) / 80.0) % 2 * 40; x <= 854.0; x += 80.0) {
               double dx = x - cx;
               double dy = y - cy;
               if (!(dx * dx + dy * dy < 48400.0)) {
                  diamond(g, x, y, 12.0, 12.0);
               }
            }
         }

         width(g, 4.0);
         g.draw(new java.awt.geom.Ellipse2D.Double(cx - 170.0, cy - 170.0, 340.0, 340.0));
         width(g, 3.0);
         diamond(g, cx, cy, 70.0, 70.0);
         dot(g, cx, cy, 10.0);
      }

      g.dispose();
      return pixels(img);
   }

   static int[] symbolAtlas() {
      BufferedImage img = new BufferedImage(1536, 1024, 2);
      Graphics2D g = pen(img);

      for (int s = 0; s < 6; s++) {
         double cx = s % 3 * 512 + 256.0;
         double cy = s / 3 * 512 + 256.0;
         switch (s) {
            case 0:
               width(g, 12.0);
               g.draw(new java.awt.geom.Ellipse2D.Double(cx - 110.0, cy - 110.0, 220.0, 220.0));
               width(g, 5.0);
               g.draw(new java.awt.geom.Ellipse2D.Double(cx - 72.0, cy - 72.0, 144.0, 144.0));
               int i = 0;

               for (; i < 16; i++) {
                  double a = i * Math.PI * 2.0 / 16.0;
                  double ca = Math.cos(a);
                  double sa = Math.sin(a);
                  if (i % 2 == 0) {
                     width(g, 10.0);
                     g.draw(new java.awt.geom.Line2D.Double(cx + ca * 138.0, cy + sa * 138.0, cx + ca * 222.0, cy + sa * 222.0));
                  } else {
                     width(g, 7.0);
                     java.awt.geom.Path2D.Double eyePath = new java.awt.geom.Path2D.Double();
                     double px = -sa;
                     eyePath.moveTo(cx + ca * 138.0, cy + sa * 138.0);
                     eyePath.curveTo(
                        cx + ca * 160.0 + px * 14.0,
                        cy + sa * 160.0 + ca * 14.0,
                        cx + ca * 175.0 - px * 14.0,
                        cy + sa * 175.0 - ca * 14.0,
                        cx + ca * 196.0,
                        cy + sa * 196.0
                     );
                     g.draw(eyePath);
                  }
               }

               dot(g, cx, cy, 14.0);
               break;
            case 1:
               width(g, 12.0);
               crescent(g, cx - 20.0, cy, 150.0, 0.62);
               width(g, 6.0);
               star(g, cx + 120.0, cy - 120.0, 4, 34.0, 9.0, 0.0);
               star(g, cx + 150.0, cy + 40.0, 4, 22.0, 6.0, 0.0);
               star(g, cx + 70.0, cy + 150.0, 4, 16.0, 5.0, 0.0);
               break;
            case 2:
               width(g, 11.0);
               star(g, cx, cy, 8, 200.0, 80.0, 0.0);
               width(g, 6.0);
               g.draw(new java.awt.geom.Ellipse2D.Double(cx - 56.0, cy - 56.0, 112.0, 112.0));
               dot(g, cx, cy, 16.0);
               width(g, 5.0);
               star(g, cx - 175.0, cy - 175.0, 4, 26.0, 6.0, 0.0);
               star(g, cx + 175.0, cy + 175.0, 4, 26.0, 6.0, 0.0);
               break;
            case 3:
               width(g, 11.0);
               java.awt.geom.Path2D.Double p = new java.awt.geom.Path2D.Double();
               p.moveTo(cx - 200.0, cy + 20.0);
               p.quadTo(cx, cy - 170.0, cx + 200.0, cy + 20.0);
               p.quadTo(cx, cy + 190.0, cx - 200.0, cy + 20.0);
               p.closePath();
               g.draw(p);
               width(g, 8.0);
               g.draw(new java.awt.geom.Ellipse2D.Double(cx - 66.0, cy - 46.0, 132.0, 132.0));
               dot(g, cx, cy + 20.0, 30.0);
               width(g, 7.0);

               for (int starIndex = 0; starIndex < 9; starIndex++) {
                  double a = Math.PI + Math.PI * (starIndex + 1) / 10.0;
                  g.draw(
                     new java.awt.geom.Line2D.Double(
                        cx + Math.cos(a) * 140.0, cy - 30.0 + Math.sin(a) * 110.0, cx + Math.cos(a) * 205.0, cy - 30.0 + Math.sin(a) * 170.0
                     )
                  );
               }
               break;
            case 4:
               width(g, 10.0);
               double l = cx - 72.0;
               double r = cx + 72.0;
               double top = cy - 120.0;
               double bottom = cy + 190.0;
               java.awt.geom.Path2D.Double crownPath = new java.awt.geom.Path2D.Double();
               crownPath.moveTo(l - 12.0, bottom);
               crownPath.lineTo(l, top);
               double step = (r - l) / 5.0;
               crownPath.lineTo(l, top - 40.0);

               for (int toothIndex = 0; toothIndex < 5; toothIndex++) {
                  double x0 = l + toothIndex * step;
                  crownPath.lineTo(x0 + step, toothIndex % 2 == 0 ? top - 40.0 : top);
                  if (toothIndex < 4) {
                     crownPath.lineTo(x0 + step, toothIndex % 2 == 0 ? top : top - 40.0);
                  }
               }

               crownPath.lineTo(r, top);
               crownPath.lineTo(r + 12.0, bottom);
               g.draw(crownPath);
               g.draw(new java.awt.geom.Line2D.Double(l - 40.0, bottom, r + 40.0, bottom));
               width(g, 7.0);
               g.draw(new java.awt.geom.Arc2D.Double(cx - 28.0, bottom - 100.0, 56.0, 70.0, 0.0, 180.0, 0));
               g.draw(new java.awt.geom.Line2D.Double(cx - 28.0, bottom - 65.0, cx - 28.0, bottom));
               g.draw(new java.awt.geom.Line2D.Double(cx + 28.0, bottom - 65.0, cx + 28.0, bottom));
               g.draw(new java.awt.geom.Line2D.Double(cx - 30.0, cy - 50.0, cx - 30.0, cy - 10.0));
               g.draw(new java.awt.geom.Line2D.Double(cx + 30.0, cy - 50.0, cx + 30.0, cy - 10.0));
               width(g, 9.0);
               java.awt.geom.Path2D.Double bolt = new java.awt.geom.Path2D.Double();
               bolt.moveTo(cx - 210.0, cy - 220.0);
               bolt.lineTo(cx - 120.0, cy - 150.0);
               bolt.lineTo(cx - 150.0, cy - 140.0);
               bolt.lineTo(cx - 60.0, cy - 170.0);
               g.draw(bolt);
               break;
            default:
               width(g, 12.0);
               g.draw(new java.awt.geom.Ellipse2D.Double(cx - 195.0, cy - 195.0, 390.0, 390.0));
               width(g, 5.0);
               g.draw(new java.awt.geom.Ellipse2D.Double(cx - 152.0, cy - 152.0, 304.0, 304.0));
               width(g, 9.0);
               g.draw(new java.awt.geom.Ellipse2D.Double(cx - 36.0, cy - 36.0, 72.0, 72.0));

               for (int rayIndex = 0; rayIndex < 8; rayIndex++) {
                  double a = rayIndex * Math.PI / 4.0;
                  width(g, 8.0);
                  g.draw(new java.awt.geom.Line2D.Double(cx + Math.cos(a) * 36.0, cy + Math.sin(a) * 36.0, cx + Math.cos(a) * 152.0, cy + Math.sin(a) * 152.0));
                  width(g, 5.0);
                  double b = a + (Math.PI / 8);
                  diamond(g, cx + Math.cos(b) * 174.0, cy + Math.sin(b) * 174.0, 12.0, 12.0);
               }
         }
      }

      g.dispose();
      return pixels(img);
   }

   private static void dot(Graphics2D g, double x, double y, double r) {
      g.fill(new java.awt.geom.Ellipse2D.Double(x - r, y - r, r * 2.0, r * 2.0));
   }

   private static void star(Graphics2D g, double cx, double cy, int points, double outer, double inner, double turn) {
      GeneralPath p = new GeneralPath();

      for (int i = 0; i < points * 2; i++) {
         double a = (-Math.PI / 2) + (i + turn * 2.0) * Math.PI / points;
         double r = (i & 1) == 0 ? outer : inner;
         double x = cx + Math.cos(a) * r;
         double y = cy + Math.sin(a) * r;
         if (i == 0) {
            p.moveTo(x, y);
         } else {
            p.lineTo(x, y);
         }
      }

      p.closePath();
      g.draw(p);
   }

   private static void crescent(Graphics2D g, double cx, double cy, double r, double bite) {
      Area moon = new Area(new java.awt.geom.Ellipse2D.Double(cx - r, cy - r, r * 2.0, r * 2.0));
      double off = r * bite;
      moon.subtract(new Area(new java.awt.geom.Ellipse2D.Double(cx - r + off, cy - r - off * 0.4, r * 2.0, r * 2.0)));
      g.draw(moon);
   }

   private static void diamond(Graphics2D g, double cx, double cy, double rx, double ry) {
      GeneralPath p = new GeneralPath();
      p.moveTo(cx, cy - ry);
      p.lineTo(cx + rx, cy);
      p.lineTo(cx, cy + ry);
      p.lineTo(cx - rx, cy);
      p.closePath();
      g.draw(p);
   }

   private static void leaf(Graphics2D g, double x, double y, double a, double len, double wid) {
      double ca = Math.cos(a);
      double sa = Math.sin(a);
      double nx = -sa;
      double tx = x + ca * len;
      double ty = y + sa * len;
      java.awt.geom.Path2D.Double p = new java.awt.geom.Path2D.Double();
      p.moveTo(x, y);
      p.quadTo(x + ca * len * 0.5 + nx * wid, y + sa * len * 0.5 + ca * wid, tx, ty);
      p.quadTo(x + ca * len * 0.5 - nx * wid, y + sa * len * 0.5 - ca * wid, x, y);
      g.draw(p);
      g.draw(new java.awt.geom.Line2D.Double(x, y, x + ca * len * 0.8, y + sa * len * 0.8));
   }

   private static void rosette(Graphics2D g, double cx, double cy, double r, int n) {
      width(g, 3.0);

      for (int i = 0; i < n; i++) {
         double a = (-Math.PI / 2) + i * Math.PI * 2.0 / n;
         leaf(g, cx, cy, a, r, r * 0.42);
      }

      dot(g, cx, cy, r * 0.16);
   }

   private static void petals(Graphics2D g, double cx, double cy, int n, double from, double to, double wid) {
      for (int i = 0; i < n; i++) {
         double a = i * Math.PI * 2.0 / n;
         leaf(g, cx + Math.cos(a) * from, cy + Math.sin(a) * from, a, to - from, wid);
      }
   }

   private static void vine(Graphics2D g, double x0, double y0, double x1, double y1, int side) {
      double dx = x1 - x0;
      double dy = y1 - y0;
      double len = Math.sqrt(dx * dx + dy * dy);
      double ux = dx / len;
      double uy = dy / len;
      double nx = -uy * side;
      double ny = ux * side;
      int waves = (int)Math.max(2L, Math.round(len / 160.0));
      java.awt.geom.Path2D.Double p = new java.awt.geom.Path2D.Double();
      p.moveTo(x0, y0);
      int steps = waves * 16;

      for (int i = 1; i <= steps; i++) {
         double t = (double)i / steps;
         double w = Math.sin(t * Math.PI * 2.0 * waves) * 14.0;
         p.lineTo(x0 + dx * t + nx * w, y0 + dy * t + ny * w);
      }

      g.draw(p);

      for (int var40 = 0; var40 < waves * 2; var40++) {
         double t = (var40 + 0.5) / (waves * 2.0);
         double w = Math.sin(t * Math.PI * 2.0 * waves) * 14.0;
         double bx = x0 + dx * t + nx * w;
         double by = y0 + dy * t + ny * w;
         double s = (var40 & 1) == 0 ? 1.0 : -1.0;
         leaf(g, bx, by, Math.atan2(uy + ny * s * 1.4, ux + nx * s * 1.4), 34.0, 12.0);
      }
   }

   static int[][] mipmaps(int[] px, int w, int h) {
      int levels = 1;

      for (int m = Math.max(w, h); m > 1; m >>= 1) {
         levels++;
      }

      int[][] out = new int[levels][];
      out[0] = px;
      int lw = w;
      int lh = h;

      for (int l = 1; l < levels; l++) {
         int nw = Math.max(1, lw / 2);
         int nh = Math.max(1, lh / 2);
         int[] src = out[l - 1];
         int[] dst = new int[nw * nh];

         for (int y = 0; y < nh; y++) {
            for (int x = 0; x < nw; x++) {
               long a = 0L;
               long r = 0L;
               long gg = 0L;
               long b = 0L;
               int n = 0;

               for (int k = 0; k < 4; k++) {
                  int sx = Math.min(lw - 1, x * 2 + (k & 1));
                  int sy = Math.min(lh - 1, y * 2 + (k >> 1));
                  int c = src[sy * lw + sx];
                  int ca = c >>> 24;
                  a += ca;
                  r += (c >> 16 & 0xFF) * ca;
                  gg += (c >> 8 & 0xFF) * ca;
                  b += (c & 0xFF) * ca;
                  n++;
               }

               int aa = (int)((a + n / 2) / n);
               int rr = a == 0L ? 255 : (int)(r / a);
               int g2 = a == 0L ? 255 : (int)(gg / a);
               int bb = a == 0L ? 255 : (int)(b / a);
               dst[y * nw + x] = aa << 24 | rr << 16 | g2 << 8 | bb;
            }
         }

         out[l] = dst;
         lw = nw;
         lh = nh;
      }

      return out;
   }

   private static int upload(int[][] levels, int w, int h) {
      int id = TextureUtil.glGenTextures();
      GlStateManager.bindTexture(id);
      GL11.glTexParameteri(3553, 33085, levels.length - 1);
      GL11.glTexParameteri(3553, 10241, 9987);
      GL11.glTexParameteri(3553, 10240, 9729);
      GL11.glTexParameteri(3553, 10242, 33071);
      GL11.glTexParameteri(3553, 10243, 33071);
      int lw = w;
      int lh = h;

      for (int l = 0; l < levels.length; l++) {
         IntBuffer buffer = BufferUtils.createIntBuffer(lw * lh);
         buffer.put(levels[l], 0, lw * lh);
         ((Buffer)buffer).flip();
         GL11.glTexImage2D(3553, l, 32856, lw, lh, 0, 32993, 33639, buffer);
         lw = Math.max(1, lw / 2);
         lh = Math.max(1, lh / 2);
      }

      return id;
   }
}
