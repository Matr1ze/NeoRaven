package xyz.daikirai.render.furiah.fx.kit;

import xyz.daikirai.render.furiah.render.ColorUtil;
import xyz.daikirai.render.furiah.render.stroke.Strokes;
import xyz.daikirai.render.furiah.setting.Displayable;

public final class Sketch {
   private static final double TWO_PI = Math.PI * 2;

   private Sketch() {
   }

   public static int star(Sketch.Plane p, double u, double v, double r) {
      int per = 6;
      int n = 5 * per + 1;

      for (int k = 0; k < 5; k++) {
         double a0 = Math.toRadians(90 + 144 * k);
         double a1 = Math.toRadians(90 + 144 * (k + 1));

         for (int i = 0; i < per; i++) {
            double t = (double)i / per;
            p.at(k * per + i, u + (Math.cos(a0) + (Math.cos(a1) - Math.cos(a0)) * t) * r, v + (Math.sin(a0) + (Math.sin(a1) - Math.sin(a0)) * t) * r);
         }
      }

      p.at(n - 1, u + Math.cos(Math.toRadians(90.0)) * r, v + Math.sin(Math.toRadians(90.0)) * r);
      return n;
   }

   public static int heart(Sketch.Plane p, double u, double v, double r) {
      int n = 40;

      for (int i = 0; i < n; i++) {
         double a = (Math.PI * 2) * i / (n - 1.0);
         double x = 16.0 * Math.pow(Math.sin(a), 3.0);
         double y = 13.0 * Math.cos(a) - 5.0 * Math.cos(2.0 * a) - 2.0 * Math.cos(3.0 * a) - Math.cos(4.0 * a);
         p.at(i, u + x / 16.0 * r, v + y / 16.0 * r);
      }

      return n;
   }

   public static int swirl(Sketch.Plane p, double u, double v, double r, double turns, int dir, double start) {
      int n = Math.max(12, (int)Math.round(turns * 22.0));

      for (int i = 0; i < n; i++) {
         double t = i / (n - 1.0);
         double a = start + dir * (Math.PI * 2) * turns * t;
         double rr = r * (0.12 + 0.88 * t);
         p.at(i, u + Math.cos(a) * rr, v + Math.sin(a) * rr);
      }

      return n;
   }

   public static int loops(Sketch.Plane p, double u, double v, double w, double r, int count) {
      int n = Math.max(16, count * 18);
      double a = w / ((Math.PI * 2) * count);
      double b = a * 1.9;

      for (int i = 0; i < n; i++) {
         double th = (Math.PI * 2) * count * i / (n - 1.0);
         double x = a * th - b * Math.sin(th) - w * 0.5;
         double y = -b * Math.cos(th);
         p.at(i, u + x, v + y * r / (2.0 * b));
      }

      return n;
   }

   public static int bangBar(Sketch.Plane p, double u, double v, double h) {
      int per = 8;
      int n = per * 2;
      double w = h * 0.07;

      for (int i = 0; i < per; i++) {
         double t = i / (per - 1.0);
         p.at(i, u - w * (1.0 - t) * 0.5, v + h * (1.0 - t) + h * 0.22);
      }

      for (int var15 = 0; var15 < per; var15++) {
         double t = var15 / (per - 1.0);
         p.at(per + var15, u + w * t * 0.5 + w * 0.3 * (1.0 - t), v + h * 0.22 + h * t);
      }

      return n;
   }

   public static int bangDot(Sketch.Plane p, double u, double v, double h) {
      int n = 12;
      double r = h * 0.06;

      for (int i = 0; i < n; i++) {
         double a = 7.225663103256523 * i / (n - 1.0);
         p.at(i, u + Math.cos(a) * r, v + Math.sin(a) * r);
      }

      return n;
   }

   public static int smear(Sketch.Plane p, double front, double half, double swing) {
      int m = 11;

      for (int i = 0; i < m; i++) {
         double v = (1.0 - 2.0 * i / (m - 1.0)) * half;
         p.at(i, front + ((i & 1) == 0 ? -swing : swing), v);
      }

      return m;
   }

   public static int smearColor(int lines) {
      return ColorUtil.lerp(lines | 0xFF000000, -855310, 0.65F);
   }

   public static final class Plane {
      public double ox;
      public double oy;
      public double oz;
      public double rx;
      public double ry;
      public double rz;
      public double ux;
      public double uy;
      public double uz;

      public void at(int i, double u, double v) {
         Strokes.X[i] = this.ox + this.rx * u + this.ux * v;
         Strokes.Y[i] = this.oy + this.ry * u + this.uy * v;
         Strokes.Z[i] = this.oz + this.rz * u + this.uz * v;
      }

      public void normal(double[] out) {
         out[0] = this.ry * this.uz - this.rz * this.uy;
         out[1] = this.rz * this.ux - this.rx * this.uz;
         out[2] = this.rx * this.uy - this.ry * this.ux;
      }
   }

   public static enum Tool implements Displayable {
      PENCIL("Pencil", "Graphite on paper", Strokes.Tool.PENCIL, -12961222, 0.032),
      MARKER("Marker", "A see-through marker", Strokes.Tool.MARKER, -13668371, 0.055),
      CHALK("Chalk", "Dusty chalk", Strokes.Tool.CHALK, -723728, 0.065);

      private final String displayName;
      private final String description;
      private final Strokes.Tool stroke;
      private final int color;
      private final double width;

      private Tool(String displayName, String description, Strokes.Tool stroke, int color, double width) {
         this.displayName = displayName;
         this.description = description;
         this.stroke = stroke;
         this.color = color;
         this.width = width;
      }

      public Strokes.Tool stroke() {
         return this.stroke;
      }

      public int color() {
         return this.color;
      }

      public double width() {
         return this.width;
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
