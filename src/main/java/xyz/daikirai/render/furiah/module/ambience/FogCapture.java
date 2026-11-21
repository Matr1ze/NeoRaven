package xyz.daikirai.render.furiah.module.ambience;

import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraft.client.renderer.GlStateManager;

public final class FogCapture {
   private static float red = 0.75F;
   private static float green = 0.85F;
   private static float blue = 1.0F;
   private static float start = -1.0F;
   private static float end = -1.0F;
   private static boolean linear;
   private static final float[] SE = new float[2];

   private FogCapture() {
   }

   public static void color(float[] out) {
      out[0] = red;
      out[1] = green;
      out[2] = blue;
   }

   public static void colors(EntityViewRenderEvent.FogColors event) {
      red = event.red;
      green = event.green;
      blue = event.blue;
      linear = false;
   }

   public static void fog(EntityViewRenderEvent.RenderFogEvent event, float factor) {
      if (event.fogMode == 0) {
         float[] se = vanilla(event);
         start = se[0] * factor;
         end = se[1] * factor;
         linear = true;
      }
   }

   private static float[] vanilla(EntityViewRenderEvent.RenderFogEvent event) {
      float f = event.farPlaneDistance;
      if (event.fogMode == -1) {
         SE[0] = 0.0F;
         SE[1] = f;
         return SE;
      } else {
         SE[0] = f * 0.75F;
         SE[1] = f;
         if (event.entity != null
            && event.entity.worldObj != null
            && event.entity.worldObj.provider.doesXZShowFog((int)event.entity.posX, (int)event.entity.posZ)) {
            SE[0] = f * 0.05F;
            SE[1] = Math.min(f, 192.0F) * 0.5F;
         }

         return SE;
      }
   }

   public static float fade(double distance) {
      if (linear && !(end <= 0.0F)) {
         float e = Math.max(start + 1.0F, end);
         return (float)Math.max(0.0, Math.min(1.0, (e - distance) / (e - start)));
      } else {
         return 1.0F;
      }
   }

   static void scaleFog(EntityViewRenderEvent.RenderFogEvent event, float factor) {
      float[] se = vanilla(event);
      GlStateManager.setFogStart(se[0] * factor);
      GlStateManager.setFogEnd(se[1] * factor);
   }

   static void apply(AmbientEnv env, float fallbackEnd) {
      env.fogColor = 0xFF000000 | clamp(red) << 16 | clamp(green) << 8 | clamp(blue);
      if (linear && end > 0.0F) {
         env.fogStart = start;
         env.fogEnd = Math.max(start + 1.0F, end);
      } else {
         env.fogStart = fallbackEnd * 0.75F;
         env.fogEnd = fallbackEnd;
      }
   }

   private static int clamp(float c) {
      return Math.max(0, Math.min(255, Math.round(c * 255.0F)));
   }
}
