package xyz.daikirai.render.furiah.fx;

import xyz.daikirai.render.furiah.render.Camera;
import xyz.daikirai.render.furiah.render.Easing;
import net.minecraft.world.World;

public final class FxFrame {
   private static long frameCounter;
   public Camera cam;
   public double viewX;
   public double viewY;
   public double viewZ;
   public double eyeX;
   public double eyeY;
   public double eyeZ;
   public float partialTicks;
   public float dt;
   public long now;
   public long frameId;
   public boolean preview;
   public World world;
   public boolean firstPerson;

   public static long nextFrame() {
      return ++frameCounter;
   }

   public static long currentFrame() {
      return frameCounter;
   }

   public double rx(double worldX) {
      return worldX - this.viewX;
   }

   public double ry(double worldY) {
      return worldY - this.viewY;
   }

   public double rz(double worldZ) {
      return worldZ - this.viewZ;
   }

   public double distanceSqToEye(double x, double y, double z) {
      double ddx = x - this.eyeX;
      double ddy = y - this.eyeY;
      double ddz = z - this.eyeZ;
      return ddx * ddx + ddy * ddy + ddz * ddz;
   }

   public float nearCameraFade(double x, double y, double z, float inner, float outer) {
      if (!this.firstPerson) {
         return 1.0F;
      } else {
         float d = (float)Math.sqrt(this.distanceSqToEye(x, y, z));
         return Easing.smoothstep(inner, outer, d);
      }
   }
}
