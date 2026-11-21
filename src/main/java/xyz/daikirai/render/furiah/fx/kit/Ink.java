package xyz.daikirai.render.furiah.fx.kit;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.GeneralPath;
import java.awt.geom.RoundRectangle2D.Double;
import java.awt.image.BufferedImage;
import java.util.Random;
import xyz.daikirai.render.furiah.render.Vfx;
import xyz.daikirai.render.furiah.setting.Displayable;

public final class Ink {
   public static final int BLOT = 0;
   public static final int RAYS = 2;
   public static final int DROPLETS = 4;
   public static final int SHAPE_CELLS = 6;
   public static final int DROP_ROUND = 0;
   public static final int DROP_TAIL = 1;
   public static final int DROP_CELLS = 2;
   private static final KitTextures.Painted SEALS = new KitTextures.Painted("ink seals", Ink::paintSeals);
   private static final KitTextures.Painted SPLATS = new KitTextures.Painted("ink splatters", Ink::paintSplats);
   private static final KitTextures.Painted SHAPES = new KitTextures.Painted("ink shapes", Ink::paintShapes);
   private static final KitTextures.Painted DROPS = new KitTextures.Painted("ink drops", Ink::paintDrops);

   static void warmUp() {
      SEALS.warm();
      SPLATS.warm();
      SHAPES.warm();
      DROPS.warm();
   }

   private Ink() {
   }

   public static void quad(
      int tex,
      int cell,
      int cells,
      double cx,
      double cy,
      double cz,
      double ax,
      double ay,
      double az,
      double bx,
      double by,
      double bz,
      double w,
      double h,
      float turn,
      int color
   ) {
      double ct = Math.cos(turn);
      double st = Math.sin(turn);
      double ux = (ax * ct + bx * st) * w * 0.5;
      double uy = (ay * ct + by * st) * w * 0.5;
      double uz = (az * ct + bz * st) * w * 0.5;
      double vx = (bx * ct - ax * st) * h * 0.5;
      double vy = (by * ct - ay * st) * h * 0.5;
      double vz = (bz * ct - az * st) * h * 0.5;
      float u0 = (float)cell / cells;
      float u1 = (float)(cell + 1) / cells;
      Vfx.quadUV(
         tex,
         cx - ux - vx,
         cy - uy - vy,
         cz - uz - vz,
         u0,
         1.0,
         color,
         cx + ux - vx,
         cy + uy - vy,
         cz + uz - vz,
         u1,
         1.0,
         color,
         cx + ux + vx,
         cy + uy + vy,
         cz + uz + vz,
         u1,
         0.0,
         color,
         cx - ux + vx,
         cy - uy + vy,
         cz - uz + vz,
         u0,
         0.0,
         color
      );
   }

   public static int seals() {
      return SEALS.texture();
   }

   private static KitTextures.Image[] paintSeals() {
      int cell = 256;
      BufferedImage img = new BufferedImage(cell * 4, cell, 2);
      Graphics2D g = img.createGraphics();
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

      label85:
      for (int d = 0; d < 4; d++) {
         double x0 = d * cell;
         g.setComposite(AlphaComposite.Src);
         g.setColor(Color.WHITE);
         g.fill(new Double(x0 + 22.0, 22.0, cell - 44, cell - 44, 26.0, 26.0));
         g.setComposite(AlphaComposite.Clear);
         g.setStroke(new BasicStroke(9.0F, 1, 1));
         g.draw(new Double(x0 + 40.0, 40.0, cell - 80, cell - 80, 16.0, 16.0));
         double cx = x0 + cell / 2.0;
         double cy = cell / 2.0;
         int k;
         switch (d) {
            case 0:
               g.setStroke(new BasicStroke(11.0F, 1, 1));
               g.draw(new java.awt.geom.Ellipse2D.Double(cx - 46.0, cy - 66.0, 92.0, 92.0));
               g.fill(new java.awt.geom.Ellipse2D.Double(cx - 18.0, cy - 38.0, 36.0, 36.0));
               g.draw(new java.awt.geom.Line2D.Double(cx - 72.0, cy + 48.0, cx + 72.0, cy + 48.0));
               g.draw(new java.awt.geom.Line2D.Double(cx - 50.0, cy + 74.0, cx + 50.0, cy + 74.0));
               continue;
            case 1:
               g.setStroke(new BasicStroke(11.0F, 1, 1));
               int w = 0;

               while (true) {
                  if (w >= 3) {
                     continue label85;
                  }

                  double y = cy - 52.0 + w * 50;
                  java.awt.geom.Path2D.Double p = new java.awt.geom.Path2D.Double();
                  p.moveTo(cx - 74.0, y + 10.0);
                  p.curveTo(cx - 50.0, y - 24.0, cx - 22.0, y - 24.0, cx, y + 4.0);
                  p.curveTo(cx + 22.0, y + 30.0, cx + 50.0, y + 30.0, cx + 74.0, y - 4.0);
                  g.draw(p);
                  w++;
               }
            case 2:
               g.setStroke(new BasicStroke(10.0F, 1, 1));

               for (int b = 0; b < 3; b++) {
                  double y = cy - 64.0 + b * 40;
                  double half = 28 + b * 20;
                  GeneralPath p = new GeneralPath();
                  p.moveTo(cx, y);
                  p.lineTo(cx + half, y + 40.0);
                  p.lineTo(cx - half, y + 40.0);
                  p.closePath();
                  g.draw(p);
               }

               g.draw(new java.awt.geom.Line2D.Double(cx, cy + 56.0, cx, cy + 82.0));
               continue;
            default:
               g.setClip(new Double(x0 + 62.0, 62.0, cell - 124, cell - 124, 8.0, 8.0));
               g.setStroke(new BasicStroke(8.0F, 1, 1));
               k = -2;
         }

         while (k <= 2) {
            g.draw(new java.awt.geom.Line2D.Double(cx - 70.0 + k * 36, cy - 70.0, cx + 70.0 + k * 36, cy + 70.0));
            g.draw(new java.awt.geom.Line2D.Double(cx + 70.0 + k * 36, cy - 70.0, cx - 70.0 + k * 36, cy + 70.0));
            k++;
         }

         g.setClip(null);
      }

      g.dispose();
      int w = cell * 4;
      int h = cell;
      int[] px = img.getRGB(0, 0, w, cell, null, 0, w);
      Random r = new Random(24225L);
      float[] speck = new float[w * cell];

      for (int i = 0; i < speck.length; i++) {
         speck[i] = r.nextFloat();
      }

      for (int y = 0; y < h; y++) {
         for (int x = 0; x < w; x++) {
            int i = y * w + x;
            float a = (px[i] >>> 24) / 255.0F;
            if (a > 0.0F) {
               float n = speck[i];
               if (x > 0) {
                  n += speck[i - 1];
               }

               if (y > 0) {
                  n += speck[i - w];
               }

               if ((n = n / 3.0F) > 0.83F) {
                  a *= 0.25F;
               } else if (n > 0.76F) {
                  a *= 0.7F;
               }
            }

            px[i] = Math.round(a * 255.0F) << 24 | 16777215;
         }
      }

      return new KitTextures.Image[]{KitTextures.Image.argb(px, w, h, true, false)};
   }

   public static int splats() {
      return SPLATS.texture();
   }

   private static KitTextures.Image[] paintSplats() {
      int cell = 128;
      BufferedImage img = new BufferedImage(cell * 4, cell, 2);
      Graphics2D g = img.createGraphics();
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.setColor(Color.WHITE);
      Random r = new Random(6743L);

      for (int d = 0; d < 4; d++) {
         double cx = d * cell + cell / 2.0;
         double cy = cell / 2.0;
         java.awt.geom.Path2D.Double p = new java.awt.geom.Path2D.Double();
         int steps = 24;
         double base = 18.0 + r.nextDouble() * 8.0;

         for (int i = 0; i <= steps; i++) {
            double a = i * Math.PI * 2.0 / steps;
            double rr = base * (0.8 + 0.4 * r.nextDouble());
            double x = cx + Math.cos(a) * rr;
            double y = cy + Math.sin(a) * rr;
            if (i == 0) {
               p.moveTo(x, y);
            } else {
               p.lineTo(x, y);
            }
         }

         p.closePath();
         g.fill(p);

         for (int k = 0; k < 9; k++) {
            double a = r.nextDouble() * Math.PI * 2.0;
            double dist = base + 6.0 + r.nextDouble() * 34.0;
            double s = Math.max(2.0, 9.0 * (1.0 - (dist - base) / 42.0)) * (0.6 + 0.6 * r.nextDouble());
            g.fill(new java.awt.geom.Ellipse2D.Double(cx + Math.cos(a) * dist - s / 2.0, cy + Math.sin(a) * dist - s / 2.0, s, s));
         }
      }

      g.dispose();
      int w = cell * 4;
      int[] px = img.getRGB(0, 0, w, cell, null, 0, w);

      for (int ix = 0; ix < px.length; ix++) {
         px[ix] = px[ix] & 0xFF000000 | 16777215;
      }

      return new KitTextures.Image[]{KitTextures.Image.argb(px, w, cell, true, false)};
   }

   public static int shapes() {
      return SHAPES.texture();
   }

   private static KitTextures.Image[] paintShapes() {
      int cell = 256;
      BufferedImage img = new BufferedImage(cell * 6, cell, 2);
      Graphics2D g = img.createGraphics();
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
      g.setColor(Color.WHITE);
      Random r = new Random(6919L);

      for (int d = 0; d < 6; d++) {
         double cx = d * cell + cell / 2.0;
         double cy = cell / 2.0;
         if (d < 2) {
            blot(g, r, cx, cy, 52.0 + r.nextDouble() * 10.0, 5 + r.nextInt(3));
            droplets(g, r, cx, cy, 70.0, 118.0, 7, 9.0);
         } else if (d < 4) {
            blot(g, r, cx, cy, 30.0 + r.nextDouble() * 6.0, 0);
            rays(g, r, cx, cy, 30.0, 9 + r.nextInt(4));
         } else {
            droplets(g, r, cx, cy, 6.0, 118.0, 22, 15.0);
         }
      }

      g.dispose();
      int w = cell * 6;
      int[] px = img.getRGB(0, 0, w, cell, null, 0, w);

      for (int i = 0; i < px.length; i++) {
         px[i] = px[i] & 0xFF000000 | 16777215;
      }

      return new KitTextures.Image[]{KitTextures.Image.argb(px, w, cell, true, false)};
   }

   public static int drops() {
      return DROPS.texture();
   }

   private static KitTextures.Image[] paintDrops() {
      int cell = 128;
      BufferedImage img = new BufferedImage(cell * 2, cell, 2);
      Graphics2D g = img.createGraphics();
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
      g.setColor(Color.WHITE);
      double cy = cell / 2.0;
      java.awt.geom.Path2D.Double round = new java.awt.geom.Path2D.Double();

      for (int i = 0; i <= 48; i++) {
         double a = i * Math.PI * 2.0 / 48.0;
         double r = 44.0 * (1.0 + 0.04 * Math.sin(a) + 0.02 * Math.sin(a * 3.0 + 1.1));
         double x = cell / 2.0 + Math.cos(a) * r;
         double y = cy + Math.sin(a) * r;
         if (i == 0) {
            round.moveTo(x, y);
         } else {
            round.lineTo(x, y);
         }
      }

      round.closePath();
      g.fill(round);
      double hx = cell + cell * 0.7;
      double r = 24.0;
      double tail = cell + 6;
      GeneralPath p = new GeneralPath();
      p.moveTo(tail, cy);
      p.curveTo(tail + 34.0, cy - 3.0, hx - r * 1.3, cy - r, hx, cy - r);
      p.curveTo(hx + r * 0.56, cy - r, hx + r, cy - r * 0.56, hx + r, cy);
      p.curveTo(hx + r, cy + r * 0.56, hx + r * 0.56, cy + r, hx, cy + r);
      p.curveTo(hx - r * 1.3, cy + r, tail + 34.0, cy + 3.0, tail, cy);
      p.closePath();
      g.fill(p);
      g.dispose();
      int w = cell * 2;
      int[] px = img.getRGB(0, 0, w, cell, null, 0, w);

      for (int ix = 0; ix < px.length; ix++) {
         px[ix] = px[ix] & 0xFF000000 | 16777215;
      }

      return new KitTextures.Image[]{KitTextures.Image.argb(px, w, cell, true, false)};
   }

   private static void blot(Graphics2D g, Random r, double cx, double cy, double base, int fingers) {
      java.awt.geom.Path2D.Double p = new java.awt.geom.Path2D.Double();
      int steps = 120;
      double[] fa = new double[fingers];
      double[] fl = new double[fingers];

      for (int f = 0; f < fingers; f++) {
         fa[f] = r.nextDouble() * Math.PI * 2.0;
         fl[f] = 0.25 + r.nextDouble() * 0.45;
      }

      double p1 = r.nextDouble() * 6.28;
      double p2 = r.nextDouble() * 6.28;
      double p3 = r.nextDouble() * 6.28;

      for (int i = 0; i <= steps; i++) {
         double a = i * Math.PI * 2.0 / steps;
         double rr = base * (1.0 + 0.1 * Math.sin(a * 3.0 + p1) + 0.07 * Math.sin(a * 5.0 + p2) + 0.04 * Math.sin(a * 11.0 + p3));

         for (int f = 0; f < fingers; f++) {
            double d = Math.abs(Math.atan2(Math.sin(a - fa[f]), Math.cos(a - fa[f])));
            rr += base * fl[f] * Math.exp(-(d / 0.11) * (d / 0.11));
         }

         double x = cx + Math.cos(a) * rr;
         double y = cy + Math.sin(a) * rr;
         if (i == 0) {
            p.moveTo(x, y);
         } else {
            p.lineTo(x, y);
         }
      }

      p.closePath();
      g.fill(p);
   }

   private static void rays(Graphics2D g, Random r, double cx, double cy, double from, int count) {
      for (int k = 0; k < count; k++) {
         double a = (k + 0.5 * r.nextDouble()) * Math.PI * 2.0 / count;
         double len = 40.0 + r.nextDouble() * 52.0;
         double w = 5.0 + r.nextDouble() * 5.0;
         double ca = Math.cos(a);
         double sa = Math.sin(a);
         double bx = cx + ca * (from - 4.0);
         double by = cy + sa * (from - 4.0);
         double tx = cx + ca * (from + len);
         double ty = cy + sa * (from + len);
         GeneralPath p = new GeneralPath();
         p.moveTo(bx - sa * w, by + ca * w);
         p.quadTo(bx + ca * len * 0.5 - sa * w * 0.4, by + sa * len * 0.5 + ca * w * 0.4, tx, ty);
         p.quadTo(bx + ca * len * 0.5 + sa * w * 0.4, by + sa * len * 0.5 - ca * w * 0.4, bx + sa * w, by - ca * w);
         p.closePath();
         g.fill(p);
         if (r.nextDouble() < 0.7) {
            double s = 4.0 + r.nextDouble() * 6.0;
            double d = from + len + 6.0 + r.nextDouble() * 10.0;
            g.fill(new java.awt.geom.Ellipse2D.Double(cx + ca * d - s / 2.0, cy + sa * d - s / 2.0, s, s));
         }
      }
   }

   private static void droplets(Graphics2D g, Random r, double cx, double cy, double inner, double outer, int count, double big) {
      for (int k = 0; k < count; k++) {
         double a = r.nextDouble() * Math.PI * 2.0;
         double dist = inner + r.nextDouble() * (outer - inner);
         double t = (dist - inner) / Math.max(1.0, outer - inner);
         double s = Math.max(2.5, big * (1.0 - 0.7 * t) * (0.55 + 0.6 * r.nextDouble()));
         double x = cx + Math.cos(a) * dist;
         double y = cy + Math.sin(a) * dist;
         g.fill(new java.awt.geom.Ellipse2D.Double(x - s / 2.0, y - s / 2.0, s, s));
         if (r.nextDouble() < 0.4) {
            double tl = s * (1.2 + r.nextDouble());
            GeneralPath p = new GeneralPath();
            double ca = Math.cos(a);
            double sa = Math.sin(a);
            p.moveTo(x - sa * s * 0.42, y + ca * s * 0.42);
            p.lineTo(x - ca * tl, y - sa * tl);
            p.lineTo(x + sa * s * 0.42, y - ca * s * 0.42);
            p.closePath();
            g.fill(p);
         }
      }
   }

   public static enum SealDesign implements Displayable {
      SUN("Sun", "A sun over a horizon"),
      WAVES("Waves", "Three rolling waves"),
      PINE("Pine", "A pine of stacked boughs"),
      LATTICE("Lattice", "A diamond lattice");

      private final String displayName;
      private final String description;

      private SealDesign(String displayName, String description) {
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
}
