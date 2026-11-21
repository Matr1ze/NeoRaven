package xyz.daikirai.render.furiah.fx;

import xyz.daikirai.render.furiah.core.GameClock;

public final class Breeze {
   public static final float GENTLE = 0.55F;

   private Breeze() {
   }

   public static float[] at(double x, double y, double z, boolean preview, float[] out) {
      if (preview || !xyz.daikirai.render.furiah.module.ambience.FuriahAmbienceModule.sampleWind(x, y, z, 1, out)) {
         gentle(GameClock.seconds(), out);
      }
      return out;
   }

   public static void gentle(float[] out) {
      gentle(GameClock.seconds(), out);
   }

   private static void gentle(double t, float[] out) {
      double heading = 0.6 + 0.5 * Math.sin(t * 0.019) + 0.25 * Math.sin(t * 0.053 + 1.3);
      double gust = 1.0 + 0.25 * Math.sin(t * 0.41) * Math.sin(t * 0.17 + 0.7);
      out[0] = (float)(Math.cos(heading) * 0.55F * gust);
      out[1] = 0.0F;
      out[2] = (float)(Math.sin(heading) * 0.55F * gust);
   }
}
