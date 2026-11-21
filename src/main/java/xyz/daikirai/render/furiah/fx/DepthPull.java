package xyz.daikirai.render.furiah.fx;

import xyz.daikirai.render.furiah.render.Camera;
import xyz.daikirai.render.furiah.render.Vfx;

public final class DepthPull {
   private DepthPull() {
   }

   public static double distance(FxFrame f, double worldX, double worldY, double worldZ) {
      Camera cam = Vfx.camera();
      double dx = f.rx(worldX) - cam.posX;
      double dy = f.ry(worldY) - cam.posY;
      double dz = f.rz(worldZ) - cam.posZ;
      return Math.sqrt(dx * dx + dy * dy + dz * dz);
   }

   public static double factor(double rx, double ry, double rz, double limit) {
      Camera cam = Vfx.camera();
      double dx = rx - cam.posX;
      double dy = ry - cam.posY;
      double dz = rz - cam.posZ;
      double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
      double knee = limit - 0.3;
      return !(limit <= 0.0) && !(d <= knee) && !(d < 1.0E-6) ? (knee + 0.25 * (1.0 - Math.exp(-(d - knee) / 0.25))) / d : 1.0;
   }

   public static void apply(int offset, int n, double limit) {
      Camera cam = Vfx.camera();

      for (int i = offset; i < offset + n; i++) {
         double k = factor(Vfx.X[i], Vfx.Y[i], Vfx.Z[i], limit);
         if (k != 1.0) {
            Vfx.X[i] = cam.posX + (Vfx.X[i] - cam.posX) * k;
            Vfx.Y[i] = cam.posY + (Vfx.Y[i] - cam.posY) * k;
            Vfx.Z[i] = cam.posZ + (Vfx.Z[i] - cam.posZ) * k;
            Vfx.W[i] = Vfx.W[i] * (float)k;
         }
      }
   }
}
