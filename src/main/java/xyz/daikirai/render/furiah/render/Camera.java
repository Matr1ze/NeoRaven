package xyz.daikirai.render.furiah.render;

import java.nio.Buffer;
import java.nio.FloatBuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

public final class Camera {
   private static final FloatBuffer MATRIX = BufferUtils.createFloatBuffer(16);
   public double rightX;
   public double rightY;
   public double rightZ;
   public double upX;
   public double upY;
   public double upZ;
   public double forwardX;
   public double forwardY;
   public double forwardZ;
   public double posX;
   public double posY;
   public double posZ;
   public double projectionScaleY;

   public void captureModelView() {
      ((Buffer)MATRIX).clear();
      GL11.glGetFloat(2982, MATRIX);
      this.read(MATRIX);
      ((Buffer)MATRIX).clear();
      GL11.glGetFloat(2983, MATRIX);
      this.projectionScaleY = MATRIX.get(5);
   }

   private void read(FloatBuffer m) {
      float m0 = m.get(0);
      float m1 = m.get(1);
      float m2 = m.get(2);
      float m4 = m.get(4);
      float m5 = m.get(5);
      float m6 = m.get(6);
      float m8 = m.get(8);
      float m9 = m.get(9);
      float m10 = m.get(10);
      float m12 = m.get(12);
      float m13 = m.get(13);
      float m14 = m.get(14);
      double sx = Math.sqrt(m0 * m0 + m4 * m4 + m8 * m8);
      double sy = Math.sqrt(m1 * m1 + m5 * m5 + m9 * m9);
      double sz = Math.sqrt(m2 * m2 + m6 * m6 + m10 * m10);
      if (!(sx < 1.0E-9) && !(sy < 1.0E-9) && !(sz < 1.0E-9)) {
         this.rightX = m0 / sx;
         this.rightY = m4 / sx;
         this.rightZ = m8 / sx;
         this.upX = m1 / sy;
         this.upY = m5 / sy;
         this.upZ = m9 / sy;
         this.forwardX = -m2 / sz;
         this.forwardY = -m6 / sz;
         this.forwardZ = -m10 / sz;
         double s2 = sx * sx;
         this.posX = -(m0 * m12 + m1 * m13 + m2 * m14) / s2;
         this.posY = -(m4 * m12 + m5 * m13 + m6 * m14) / s2;
         this.posZ = -(m8 * m12 + m9 * m13 + m10 * m14) / s2;
      }
   }

   public double distanceSq(double x, double y, double z) {
      double dx = x - this.posX;
      double dy = y - this.posY;
      double dz = z - this.posZ;
      return dx * dx + dy * dy + dz * dz;
   }
}
