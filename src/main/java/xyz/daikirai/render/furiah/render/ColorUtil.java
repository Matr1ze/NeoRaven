package xyz.daikirai.render.furiah.render;

import java.util.Locale;

public final class ColorUtil {
   private ColorUtil() {
   }

   public static int alpha(int c) {
      return c >>> 24;
   }

   public static int red(int c) {
      return c >> 16 & 0xFF;
   }

   public static int green(int c) {
      return c >> 8 & 0xFF;
   }

   public static int blue(int c) {
      return c & 0xFF;
   }

   public static int argb(int a, int r, int g, int b) {
      return clamp255(a) << 24 | clamp255(r) << 16 | clamp255(g) << 8 | clamp255(b);
   }

   public static int argb(float a, float r, float g, float b) {
      return argb(Math.round(a * 255.0F), Math.round(r * 255.0F), Math.round(g * 255.0F), Math.round(b * 255.0F));
   }

   public static int withAlpha(int c, int alpha) {
      return clamp255(alpha) << 24 | c & 16777215;
   }

   public static int withAlpha(int c, float alpha) {
      return withAlpha(c, Math.round(alpha * 255.0F));
   }

   public static int mulAlpha(int c, float factor) {
      if (factor >= 1.0F) {
         return c;
      } else {
         return factor <= 0.0F ? c & 16777215 : withAlpha(c, (int)(alpha(c) * factor + 0.5F));
      }
   }

   public static int lerp(int c1, int c2, float t) {
      if (t <= 0.0F) {
         return c1;
      } else if (t >= 1.0F) {
         return c2;
      } else {
         int a = (int)(alpha(c1) + (alpha(c2) - alpha(c1)) * t + 0.5F);
         int r = (int)(red(c1) + (red(c2) - red(c1)) * t + 0.5F);
         int g = (int)(green(c1) + (green(c2) - green(c1)) * t + 0.5F);
         int b = (int)(blue(c1) + (blue(c2) - blue(c1)) * t + 0.5F);
         return argb(a, r, g, b);
      }
   }

   public static int lerpOklab(int c1, int c2, float t) {
      if (t <= 0.0F) {
         return c1;
      } else if (t >= 1.0F) {
         return c2;
      } else {
         double[] a = toOklab(c1);
         double[] b = toOklab(c2);
         double l = a[0] + (b[0] - a[0]) * t;
         double ca = a[1] + (b[1] - a[1]) * t;
         double cb = a[2] + (b[2] - a[2]) * t;
         int alpha = (int)(alpha(c1) + (alpha(c2) - alpha(c1)) * t + 0.5F);
         return fromOklab(l, ca, cb, alpha);
      }
   }

   private static double[] toOklab(int c) {
      double r = toLinear(red(c) / 255.0);
      double g = toLinear(green(c) / 255.0);
      double b = toLinear(blue(c) / 255.0);
      double l = Math.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b);
      double m = Math.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b);
      double s = Math.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b);
      return new double[]{
         0.2104542553 * l + 0.793617785 * m - 0.0040720468 * s,
         1.9779984951 * l - 2.428592205 * m + 0.4505937099 * s,
         0.0259040371 * l + 0.7827717662 * m - 0.808675766 * s
      };
   }

   private static int fromOklab(double L, double A, double B, int alpha) {
      double l = L + 0.3963377774 * A + 0.2158037573 * B;
      double m = L - 0.1055613458 * A - 0.0638541728 * B;
      double s = L - 0.0894841775 * A - 1.291485548 * B;
      l = l * l * l;
      m = m * m * m;
      s = s * s * s;
      double r = 4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s;
      double g = -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s;
      double b = -0.0041960863 * l - 0.7034186147 * m + 1.707614701 * s;
      return argb(alpha, (int)Math.round(toSrgb(r) * 255.0), (int)Math.round(toSrgb(g) * 255.0), (int)Math.round(toSrgb(b) * 255.0));
   }

   private static double toLinear(double v) {
      return v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
   }

   private static double toSrgb(double v) {
      double var2;
      return (var2 = Math.max(0.0, Math.min(1.0, v))) <= 0.0031308 ? 12.92 * var2 : 1.055 * Math.pow(var2, 0.4166666666666667) - 0.055;
   }

   public static int hsv(float h, float s, float v) {
      h -= (float)Math.floor(h);
      s = Math.max(0.0F, Math.min(1.0F, s));
      v = Math.max(0.0F, Math.min(1.0F, v));
      float h6 = h * 6.0F;
      int sector = (int)h6;
      float f = h6 - sector;
      float p = v * (1.0F - s);
      float q = v * (1.0F - s * f);
      float t = v * (1.0F - s * (1.0F - f));
      float b;
      float g;
      float r;
      switch (sector) {
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

      return argb(1.0F, r, g, b);
   }

   public static float[] toHsv(int c, float[] out) {
      float r = red(c) / 255.0F;
      float b;
      float g;
      float max = Math.max(r, Math.max(g = green(c) / 255.0F, b = blue(c) / 255.0F));
      float d = max - Math.min(r, Math.min(g, b));
      float h = d <= 0.0F ? 0.0F : (max == r ? (g - b) / d / 6.0F : (max == g ? ((b - r) / d + 2.0F) / 6.0F : ((r - g) / d + 4.0F) / 6.0F));
      if (h < 0.0F) {
         h++;
      }

      out[0] = h;
      out[1] = max <= 0.0F ? 0.0F : d / max;
      out[2] = max;
      return out;
   }

   public static int core(int c) {
      int r = red(c);
      int g = green(c);
      int b = blue(c);
      return argb(alpha(c), r + (int)((255 - r) * 0.62F), g + (int)((255 - g) * 0.62F), b + (int)((255 - b) * 0.62F));
   }

   public static int brighten(int c, float amount) {
      return lerp(c, c | 16777215, amount);
   }

   public static int shade(int c, float factor) {
      return argb(alpha(c), (int)(red(c) * factor), (int)(green(c) * factor), (int)(blue(c) * factor));
   }

   public static int multiply(int a, int b) {
      return argb(alpha(a) * alpha(b) / 255, red(a) * red(b) / 255, green(a) * green(b) / 255, blue(a) * blue(b) / 255);
   }

   public static float luminance(int c) {
      return (0.2126F * red(c) + 0.7152F * green(c) + 0.0722F * blue(c)) / 255.0F;
   }

   public static String toHex(int c) {
      return String.format(Locale.ROOT, "#%06X", c & 16777215);
   }

   public static String toHexArgb(int c) {
      return String.format(Locale.ROOT, "#%08X", c);
   }

   public static int parseHex(String text, int fallback) {
      if (text == null) {
         return fallback;
      } else {
         String s = text.trim();
         if (s.startsWith("#")) {
            s = s.substring(1);
         } else if (s.startsWith("0x") || s.startsWith("0X")) {
            s = s.substring(2);
         }

         try {
            if (s.length() == 3) {
               int r = Integer.parseInt(s.substring(0, 1), 16);
               int g = Integer.parseInt(s.substring(1, 2), 16);
               int b = Integer.parseInt(s.substring(2, 3), 16);
               return argb(255, r * 17, g * 17, b * 17);
            }

            if (s.length() == 6) {
               return 0xFF000000 | Integer.parseInt(s, 16);
            }

            if (s.length() == 8) {
               return (int)Long.parseLong(s, 16);
            }
         } catch (NumberFormatException var6) {
         }

         return fallback;
      }
   }

   private static int clamp255(int v) {
      return v < 0 ? 0 : (v > 255 ? 255 : v);
   }
}
