package xyz.daikirai.render.furiah.fx;

import xyz.daikirai.render.furiah.render.Distortion;

public final class WarpShell {
   private WarpShell() {
   }

   public static void draw(
      FxFrame f,
      double cx,
      double cy,
      double cz,
      double radius,
      double axX,
      double axY,
      double axZ,
      double along,
      double across,
      double dentX,
      double dentY,
      double dentZ,
      double dent,
      double dentWidth,
      boolean dome,
      float refraction,
      int tint,
      float rim,
      float alpha,
      boolean allowRefract
   ) {
      if (!(alpha <= 0.003F) && !(radius <= 0.01)) {
         int around = 32;
         int rings = dome ? 9 : 18;
         double latMin = dome ? 0.0 : -Math.PI / 2;
         double latMax = Math.PI / 2;
         Distortion.begin(f.frameId, (float)(0.16 * radius * refraction), tint, rim, alpha, allowRefract);

         for (int j = 0; j < rings; j++) {
            double lat0 = latMin + (latMax - latMin) * j / rings;
            double lat1 = latMin + (latMax - latMin) * (j + 1) / rings;

            for (int i = 0; i < around; i++) {
               double lon0 = (Math.PI * 2) * i / around;
               double lon1 = (Math.PI * 2) * (i + 1) / around;
               vertex(f, cx, cy, cz, radius, axX, axY, axZ, along, across, dentX, dentY, dentZ, dent, dentWidth, lat0, lon0);
               vertex(f, cx, cy, cz, radius, axX, axY, axZ, along, across, dentX, dentY, dentZ, dent, dentWidth, lat0, lon1);
               vertex(f, cx, cy, cz, radius, axX, axY, axZ, along, across, dentX, dentY, dentZ, dent, dentWidth, lat1, lon1);
               vertex(f, cx, cy, cz, radius, axX, axY, axZ, along, across, dentX, dentY, dentZ, dent, dentWidth, lat1, lon0);
            }
         }

         Distortion.end();
      }
   }

   private static void vertex(
      FxFrame f,
      double cx,
      double cy,
      double cz,
      double radius,
      double axX,
      double axY,
      double axZ,
      double along,
      double across,
      double dentX,
      double dentY,
      double dentZ,
      double dent,
      double dentWidth,
      double lat,
      double lon
   ) {
      double cl = Math.cos(lat);
      double dx = cl * Math.cos(lon);
      double dy = Math.sin(lat);
      double dz = cl * Math.sin(lon);
      double a = dx * axX + dy * axY + dz * axZ;
      double px = dx - axX * a;
      double py = dy - axY * a;
      double pz = dz - axZ * a;
      double r = radius;
      if (dent > 0.0) {
         double facing = dx * dentX + dy * dentY + dz * dentZ;
         r = radius * (1.0 - dent * Math.exp(-(1.0 - facing) * dentWidth));
      }

      double x = cx + (axX * a * along + px * across) * r;
      double y = cy + (axY * a * along + py * across) * r;
      double z = cz + (axZ * a * along + pz * across) * r;
      double nx = axX * a / along + px / across;
      double ny = axY * a / along + py / across;
      double nz = axZ * a / along + pz / across;
      double nl = Math.sqrt(nx * nx + ny * ny + nz * nz);
      if (nl < 1.0E-9) {
         nx = dx;
         ny = dy;
         nz = dz;
         nl = 1.0;
      }

      Distortion.vertex(x - f.viewX, y - f.viewY, z - f.viewZ, nx / nl, ny / nl, nz / nl);
   }
}
