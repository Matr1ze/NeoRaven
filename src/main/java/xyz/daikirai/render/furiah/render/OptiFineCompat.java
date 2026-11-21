package xyz.daikirai.render.furiah.render;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.world.World;

public final class OptiFineCompat {
   private static final long CACHE_MS = 1000L;
   private static boolean looked;
   private static Field shaderPackLoaded;
   private static Method hasSkyLayers;
   private static long shadersCheckedAt;
   private static boolean shadersOn;
   private static long skyCheckedAt;
   private static boolean customSkyOn;
   private static Boolean shadersForTest;

   private OptiFineCompat() {
   }

   public static void shadersForTest(Boolean on) {
      shadersForTest = on;
   }

   private static void lookUp() {
      if (!looked) {
         looked = true;

         for (String name : new String[]{"net.optifine.shaders.Shaders", "shadersmod.client.Shaders"}) {
            try {
               Field f = Class.forName(name).getDeclaredField("shaderPackLoaded");
               f.setAccessible(true);
               shaderPackLoaded = f;
               break;
            } catch (Throwable var6) {
            }
         }

         for (String name : new String[]{"net.optifine.CustomSky", "CustomSky"}) {
            try {
               Method m = Class.forName(name).getDeclaredMethod("hasSkyLayers", World.class);
               m.setAccessible(true);
               hasSkyLayers = m;
               break;
            } catch (Throwable var5) {
            }
         }
      }
   }

   public static boolean shadersActive() {
      if (shadersForTest != null) {
         return shadersForTest;
      } else {
         long now = System.currentTimeMillis();
         if (now - shadersCheckedAt < 1000L) {
            return shadersOn;
         } else {
            shadersCheckedAt = now;
            lookUp();

            try {
               shadersOn = shaderPackLoaded != null && shaderPackLoaded.getBoolean(null);
            } catch (Throwable var3) {
               shadersOn = false;
            }

            return shadersOn;
         }
      }
   }

   public static boolean customSkyActive(World world) {
      long now = System.currentTimeMillis();
      if (now - skyCheckedAt < 1000L) {
         return customSkyOn;
      } else {
         skyCheckedAt = now;
         lookUp();

         try {
            customSkyOn = hasSkyLayers != null && world != null && Boolean.TRUE.equals(hasSkyLayers.invoke(null, world));
         } catch (Throwable var4) {
            customSkyOn = false;
         }

         return customSkyOn;
      }
   }
}
