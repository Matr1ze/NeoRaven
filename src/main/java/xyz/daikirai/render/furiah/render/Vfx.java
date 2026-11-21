package xyz.daikirai.render.furiah.render;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;

public final class Vfx {
   public static final int MAX_POINTS = 1024;
   public static final double[] X = new double[1024];
   public static final double[] Y = new double[1024];
   public static final double[] Z = new double[1024];
   public static final float[] W = new float[1024];
   public static final int[] C = new int[1024];
   private static final double TWO_PI = Math.PI * 2;
   private static final int INTS_PER_VERTEX = 6;
   private static final int MAX_VERTICES = 4096;
   private static final int[] DATA = new int[24576];
   private static final int[][] CHUNKS = new int[Integer.numberOfTrailingZeros(1024) + 1][];
   private static Camera cam = new Camera();
   private static boolean drawing;
   private static int vertexCount;
   private static int boundTexture = -1;

   private Vfx() {
   }

   public static void setCamera(Camera camera) {
      cam = camera;
   }

   public static Camera camera() {
      return cam;
   }

   public static void resetTextureBinding() {
      boundTexture = -1;
   }

   public static void begin(int texture) {
      if (drawing) {
         if (texture == boundTexture) {
            return;
         }

         flush();
      }

      if (texture != boundTexture) {
         GlStateManager.bindTexture(texture);
         boundTexture = texture;
      }

      drawing = true;
   }

   public static void flush() {
      if (drawing) {
         drawing = false;
         submit();
      }
   }

   private static void submit() {
      if (vertexCount != 0) {
         Tessellator tessellator = Tessellator.getInstance();
         WorldRenderer wr = tessellator.getWorldRenderer();
         wr.begin(7, DefaultVertexFormats.POSITION_TEX_COLOR);
         int offset = 0;
         int remaining = vertexCount;

         for (int k = CHUNKS.length - 1; k >= 0 && remaining > 0; k--) {
            int n = 4 << k;
            if (remaining >= n) {
               int[] chunk = CHUNKS[k];
               if (chunk == null) {
                  CHUNKS[k] = new int[n * 6];
                  chunk = CHUNKS[k];
               }

               System.arraycopy(DATA, offset * 6, chunk, 0, n * 6);
               wr.addVertexData(chunk);
               offset += n;
               remaining -= n;
            }
         }

         tessellator.draw();
         vertexCount = 0;
      }
   }

   private static void v(double x, double y, double z, double u, double v, int c) {
      c = xyz.daikirai.render.furiah.setting.ThemeTint.recolor(c);
      if (vertexCount == 4096) {
         submit();
      }

      int i = vertexCount * 6;
      DATA[i] = Float.floatToRawIntBits((float)x);
      DATA[i + 1] = Float.floatToRawIntBits((float)y);
      DATA[i + 2] = Float.floatToRawIntBits((float)z);
      DATA[i + 3] = Float.floatToRawIntBits((float)u);
      DATA[i + 4] = Float.floatToRawIntBits((float)v);
      DATA[i + 5] = c & -16711936 | c >> 16 & 0xFF | (c & 0xFF) << 16;
      vertexCount++;
   }

   public static void sprite(int texture, double x, double y, double z, double size, float rotation, int color) {
      sprite(texture, x, y, z, size * 0.5, size * 0.5, rotation, 0.0F, 0.0F, 1.0F, 1.0F, color);
   }

   public static void sprite(
      int texture, double x, double y, double z, double halfW, double halfH, float rotation, float u0, float v0, float u1, float v1, int color
   ) {
      if (color >>> 24 != 0 && !(halfW <= 0.0) && !(halfH <= 0.0)) {
         begin(texture);
         double cos = Math.cos(rotation);
         double sin = Math.sin(rotation);
         double ax = (cam.rightX * cos + cam.upX * sin) * halfW;
         double ay = (cam.rightY * cos + cam.upY * sin) * halfW;
         double az = (cam.rightZ * cos + cam.upZ * sin) * halfW;
         double bx = (-cam.rightX * sin + cam.upX * cos) * halfH;
         double by = (-cam.rightY * sin + cam.upY * cos) * halfH;
         double bz = (-cam.rightZ * sin + cam.upZ * cos) * halfH;
         v(x - ax - bx, y - ay - by, z - az - bz, u0, v1, color);
         v(x + ax - bx, y + ay - by, z + az - bz, u1, v1, color);
         v(x + ax + bx, y + ay + by, z + az + bz, u1, v0, color);
         v(x - ax + bx, y - ay + by, z - az + bz, u0, v0, color);
      }
   }

   public static void ribbon(int texture, int n, boolean closed) {
      ribbon(texture, 0, n, closed);
   }

   public static void ribbon(int texture, int offset, int n, boolean closed) {
      if (n >= 2) {
         begin(texture);
         double firstLx = 0.0;
         double firstLy = 0.0;
         double firstLz = 0.0;
         double firstRx = 0.0;
         double firstRy = 0.0;
         double firstRz = 0.0;
         double prevLx = 0.0;
         double prevLy = 0.0;
         double prevLz = 0.0;
         double prevRx = 0.0;
         double prevRy = 0.0;
         double prevRz = 0.0;
         double lastSx = cam.upX;
         double lastSy = cam.upY;
         double lastSz = cam.upZ;
         int end = offset + n - 1;

         for (int i = offset; i <= end; i++) {
            int ip = closed ? (i == offset ? end : i - 1) : Math.max(offset, i - 1);
            int in = closed ? (i == end ? offset : i + 1) : Math.min(end, i + 1);
            double tx = X[in] - X[ip];
            double ty = Y[in] - Y[ip];
            double tz = Z[in] - Z[ip];
            double vx = cam.posX - X[i];
            double vy = cam.posY - Y[i];
            double vz = cam.posZ - Z[i];
            double sx = ty * vz - tz * vy;
            double sy = tz * vx - tx * vz;
            double sz = tx * vy - ty * vx;
            double len = Math.sqrt(sx * sx + sy * sy + sz * sz);
            if (len < 1.0E-9) {
               sx = lastSx;
               sy = lastSy;
               sz = lastSz;
            } else {
               sx /= len;
               sy /= len;
               sz /= len;
               if (i > offset && sx * lastSx + sy * lastSy + sz * lastSz < 0.0) {
                  sx = -sx;
                  sy = -sy;
                  sz = -sz;
               }
            }

            lastSx = sx;
            lastSy = sy;
            lastSz = sz;
            double hw = W[i] * 0.5;
            double lx = X[i] - sx * hw;
            double ly = Y[i] - sy * hw;
            double lz = Z[i] - sz * hw;
            double rx = X[i] + sx * hw;
            double ry = Y[i] + sy * hw;
            double rz = Z[i] + sz * hw;
            if (i == offset) {
               firstLx = lx;
               firstLy = ly;
               firstLz = lz;
               firstRx = rx;
               firstRy = ry;
               firstRz = rz;
            } else if ((C[i - 1] | C[i]) >>> 24 != 0) {
               double u0 = (double)(i - 1 - offset) / n;
               double u1 = (double)(i - offset) / n;
               v(prevLx, prevLy, prevLz, u0, 0.0, C[i - 1]);
               v(prevRx, prevRy, prevRz, u0, 1.0, C[i - 1]);
               v(rx, ry, rz, u1, 1.0, C[i]);
               v(lx, ly, lz, u1, 0.0, C[i]);
            }

            prevLx = lx;
            prevLy = ly;
            prevLz = lz;
            prevRx = rx;
            prevRy = ry;
            prevRz = rz;
         }

         if (closed && (C[end] | C[offset]) >>> 24 != 0) {
            v(prevLx, prevLy, prevLz, (double)(n - 1) / n, 0.0, C[end]);
            v(prevRx, prevRy, prevRz, (double)(n - 1) / n, 1.0, C[end]);
            v(firstRx, firstRy, firstRz, 1.0, 1.0, C[offset]);
            v(firstLx, firstLy, firstLz, 1.0, 0.0, C[offset]);
         }
      }
   }

   public static void flatRibbon(int texture, int offset, int n) {
      if (n >= 2) {
         begin(texture);
         double prevLx = 0.0;
         double prevLz = 0.0;
         double prevRx = 0.0;
         double prevRz = 0.0;
         double prevY = 0.0;
         double lastSx = 1.0;
         double lastSz = 0.0;
         int end = offset + n - 1;

         for (int i = offset; i <= end; i++) {
            int ip = Math.max(offset, i - 1);
            int in = Math.min(end, i + 1);
            double tx = X[in] - X[ip];
            double tz;
            double len = Math.sqrt(tx * tx + (tz = Z[in] - Z[ip]) * tz);
            double sz;
            double sx;
            if (len < 1.0E-9) {
               sx = lastSx;
               sz = lastSz;
            } else {
               sx = -tz / len;
               sz = tx / len;
            }

            lastSx = sx;
            lastSz = sz;
            double hw = W[i] * 0.5;
            double lx = X[i] - sx * hw;
            double lz = Z[i] - sz * hw;
            double rx = X[i] + sx * hw;
            double rz = Z[i] + sz * hw;
            if (i > offset && (C[i - 1] | C[i]) >>> 24 != 0) {
               double u0 = (double)(i - 1 - offset) / n;
               double u1 = (double)(i - offset) / n;
               v(prevLx, prevY, prevLz, u0, 0.0, C[i - 1]);
               v(prevRx, prevY, prevRz, u0, 1.0, C[i - 1]);
               v(rx, Y[i], rz, u1, 1.0, C[i]);
               v(lx, Y[i], lz, u1, 0.0, C[i]);
            }

            prevLx = lx;
            prevLz = lz;
            prevRx = rx;
            prevRz = rz;
            prevY = Y[i];
         }
      }
   }

   public static void quadUV(
      int texture,
      double x1,
      double y1,
      double z1,
      double u1,
      double v1,
      int c1,
      double x2,
      double y2,
      double z2,
      double u2,
      double v2,
      int c2,
      double x3,
      double y3,
      double z3,
      double u3,
      double v3,
      int c3,
      double x4,
      double y4,
      double z4,
      double u4,
      double v4,
      int c4
   ) {
      if ((c1 | c2 | c3 | c4) >>> 24 != 0) {
         begin(texture);
         v(x1, y1, z1, u1, v1, c1);
         v(x2, y2, z2, u2, v2, c2);
         v(x3, y3, z3, u3, v3, c3);
         v(x4, y4, z4, u4, v4, c4);
      }
   }

   public static void line(int texture, double x1, double y1, double z1, double x2, double y2, double z2, double width, int c1, int c2) {
      if ((c1 | c2) >>> 24 != 0) {
         begin(texture);
         double tx = x2 - x1;
         double ty = y2 - y1;
         double tz = z2 - z1;
         double mx = (x1 + x2) * 0.5;
         double my = (y1 + y2) * 0.5;
         double mz = (z1 + z2) * 0.5;
         double vx = cam.posX - mx;
         double vy = cam.posY - my;
         double vz = cam.posZ - mz;
         double sx = ty * vz - tz * vy;
         double sy = tz * vx - tx * vz;
         double sz = tx * vy - ty * vx;
         double len = Math.sqrt(sx * sx + sy * sy + sz * sz);
         if (!(len < 1.0E-9)) {
            double k = width * 0.5 / len;
            double var45;
            double var46;
            double var47;
            v(x1 - (var45 = sx * k), y1 - (var46 = sy * k), z1 - (var47 = sz * k), 0.0, 0.0, c1);
            v(x1 + var45, y1 + var46, z1 + var47, 0.0, 1.0, c1);
            v(x2 + var45, y2 + var46, z2 + var47, 1.0, 1.0, c2);
            v(x2 - var45, y2 - var46, z2 - var47, 1.0, 0.0, c2);
         }
      }
   }

   public static void flatSprite(int texture, double cx, double y, double cz, double half, double rotation, int color) {
      double c = Math.cos(rotation) * half;
      double s = Math.sin(rotation) * half;
      flatQuad(texture, cx, y, cz, c, s, -s, c, 0.0F, 0.0F, 1.0F, 1.0F, color);
   }

   public static void flatQuad(
      int texture, double cx, double y, double cz, double ax, double az, double bx, double bz, float u0, float v0, float u1, float v1, int color
   ) {
      if (color >>> 24 != 0) {
         begin(texture);
         v(cx - ax - bx, y, cz - az - bz, u0, v0, color);
         v(cx - ax + bx, y, cz - az + bz, u0, v1, color);
         v(cx + ax + bx, y, cz + az + bz, u1, v1, color);
         v(cx + ax - bx, y, cz + az - bz, u1, v0, color);
      }
   }

   public static void flatBand(int texture, double cx, double y, double cz, double rIn, double rOut, double a0, double span, int segments) {
      if (!(rOut <= 0.0) && segments >= 1) {
         begin(texture);
         double step = span / segments;
         double prevCos = Math.cos(a0);
         double prevSin = Math.sin(a0);

         for (int i = 1; i <= segments; i++) {
            double a = a0 + step * i;
            double cos = Math.cos(a);
            double sin = Math.sin(a);
            int c0 = C[i - 1];
            int c1 = C[i];
            if ((c0 | c1) >>> 24 != 0) {
               v(cx + prevCos * rIn, y, cz + prevSin * rIn, 0.0, 0.0, c0);
               v(cx + prevCos * rOut, y, cz + prevSin * rOut, 0.0, 1.0, c0);
               v(cx + cos * rOut, y, cz + sin * rOut, 1.0, 1.0, c1);
               v(cx + cos * rIn, y, cz + sin * rIn, 1.0, 0.0, c1);
            }

            prevCos = cos;
            prevSin = sin;
         }
      }
   }

   public static void flatBand(int texture, double cx, double y, double cz, double rIn, double rOut, double a0, double span, int segments, int color) {
      if (color >>> 24 != 0) {
         int n = Math.min(segments, 1023);

         for (int i = 0; i <= n; i++) {
            C[i] = color;
         }

         flatBand(texture, cx, y, cz, rIn, rOut, a0, span, n);
      }
   }

   public static void flatRing(int texture, double cx, double y, double cz, double radius, double width, int segments, int color) {
      flatBand(texture, cx, y, cz, Math.max(0.0, radius - width * 0.5), radius + width * 0.5, 0.0, Math.PI * 2, segments, color);
   }

   public static void flatLine(int texture, double x1, double z1, double x2, double z2, double y, double width, int c1, int c2) {
      if ((c1 | c2) >>> 24 != 0) {
         double dx = x2 - x1;
         double dz = z2 - z1;
         double len = Math.sqrt(dx * dx + dz * dz);
         if (!(len < 1.0E-9)) {
            begin(texture);
            double nx = -dz / len * width * 0.5;
            double nz = dx / len * width * 0.5;
            v(x1 - nx, y, z1 - nz, 0.0, 0.0, c1);
            v(x1 + nx, y, z1 + nz, 0.0, 1.0, c1);
            v(x2 + nx, y, z2 + nz, 1.0, 1.0, c2);
            v(x2 - nx, y, z2 - nz, 1.0, 0.0, c2);
         }
      }
   }

   public static void triangle(double x1, double y1, double z1, double x2, double y2, double z2, double x3, double y3, double z3, int c1, int c2, int c3) {
      if ((c1 | c2 | c3) >>> 24 != 0) {
         begin(Textures.WHITE);
         v(x1, y1, z1, 0.5, 0.5, c1);
         v(x2, y2, z2, 0.5, 0.5, c2);
         v(x3, y3, z3, 0.5, 0.5, c3);
         v(x3, y3, z3, 0.5, 0.5, c3);
      }
   }

   public static void quad(
      double x1,
      double y1,
      double z1,
      double x2,
      double y2,
      double z2,
      double x3,
      double y3,
      double z3,
      double x4,
      double y4,
      double z4,
      int c1,
      int c2,
      int c3,
      int c4
   ) {
      begin(Textures.WHITE);
      v(x1, y1, z1, 0.5, 0.5, c1);
      v(x2, y2, z2, 0.5, 0.5, c2);
      v(x3, y3, z3, 0.5, 0.5, c3);
      v(x4, y4, z4, 0.5, 0.5, c4);
   }
}
