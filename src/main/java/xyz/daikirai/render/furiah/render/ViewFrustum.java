package xyz.daikirai.render.furiah.render;

import java.nio.Buffer;
import java.nio.FloatBuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

public final class ViewFrustum {
   private static final FloatBuffer BUF = BufferUtils.createFloatBuffer(16);
   private final float[] p = new float[16];
   private final float[] m = new float[16];
   private final float[] c = new float[16];
   private final double[] planes = new double[16];
   private boolean valid;

   public void capture() {
      ((Buffer)BUF).clear();
      GL11.glGetFloat(2983, BUF);
      BUF.get(this.p);
      ((Buffer)BUF).clear();
      GL11.glGetFloat(2982, BUF);
      BUF.get(this.m);

      for (int col = 0; col < 4; col++) {
         for (int row = 0; row < 4; row++) {
            float s = 0.0F;

            for (int k = 0; k < 4; k++) {
               s += this.p[k * 4 + row] * this.m[col * 4 + k];
            }

            this.c[col * 4 + row] = s;
         }
      }

      this.plane(0, 3, 0, 1.0F);
      this.plane(1, 3, 0, -1.0F);
      this.plane(2, 3, 1, 1.0F);
      this.plane(3, 3, 1, -1.0F);
      this.valid = true;
   }

   private void plane(int index, int w, int axis, float sign) {
      double a = this.c[w] + sign * this.c[axis];
      double b = this.c[4 + w] + sign * this.c[4 + axis];
      double cc = this.c[8 + w] + sign * this.c[8 + axis];
      double d = this.c[12 + w] + sign * this.c[12 + axis];
      double len = Math.sqrt(a * a + b * b + cc * cc);
      if (len < 1.0E-12) {
         len = 1.0;
      }

      int o = index * 4;
      this.planes[o] = a / len;
      this.planes[o + 1] = b / len;
      this.planes[o + 2] = cc / len;
      this.planes[o + 3] = d / len;
   }

   public boolean sphere(double x, double y, double z, double radius) {
      if (!this.valid) {
         return true;
      } else {
         for (int i = 0; i < 16; i += 4) {
            if (this.planes[i] * x + this.planes[i + 1] * y + this.planes[i + 2] * z + this.planes[i + 3] < -radius) {
               return false;
            }
         }

         return true;
      }
   }
}
