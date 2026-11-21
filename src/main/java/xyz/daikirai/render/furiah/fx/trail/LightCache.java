package xyz.daikirai.render.furiah.fx.trail;

import java.util.Arrays;
import xyz.daikirai.render.furiah.fx.look.LightBoost;
import net.minecraft.util.BlockPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;

public final class LightCache {
   private static final int SIZE = 512;
   private static final int MASK = 511;
   private static final int PROBES = 8;
   private static final long FORGET_NANOS = 500000000L;
   private static final float[] CURVE = new float[16];
   private final long[] keys = new long[512];
   private final float[] values = new float[512];
   private final boolean[] used = new boolean[512];
   private World world;
   private long since;
   private int skyDarkening;

   public void frame(World w, float partialTicks) {
      int d = w == null ? 0 : w.calculateSkylightSubtracted(partialTicks);
      if (d != this.skyDarkening) {
         this.skyDarkening = d;
         Arrays.fill(this.used, false);
      }
   }

   public float at(World w, double x, double y, double z) {
      return LightBoost.lift(this.raw(w, x, y, z));
   }

   private float raw(World w, double x, double y, double z) {
      if (w == null) {
         return 1.0F;
      } else {
         long now = System.nanoTime();
         if (w != this.world || now - this.since > 500000000L) {
            Arrays.fill(this.used, false);
            this.world = w;
            this.since = now;
         }

         int bx = (int)Math.floor(x);
         int by = (int)Math.floor(y);
         int bz = (int)Math.floor(z);
         long key = (bx & 67108863L) << 38 | (bz & 67108863L) << 12 | by & 4095L;
         int h = (int)(key * -7046029254386353131L >>> 55) & 511;

         for (int i = 0; i < 8; i++) {
            int slot = h + i & 511;
            if (!this.used[slot]) {
               float v = look(w, bx, by, bz, this.skyDarkening);
               this.used[slot] = true;
               this.keys[slot] = key;
               this.values[slot] = v;
               return v;
            }

            if (this.keys[slot] == key) {
               return this.values[slot];
            }
         }

         return look(w, bx, by, bz, this.skyDarkening);
      }
   }

   private static float look(World w, int x, int y, int z, int skyDarkening) {
      if (y >= 0 && y <= 255) {
         BlockPos pos = new BlockPos(x, y, z);
         int sky = w.getLightFor(EnumSkyBlock.SKY, pos) - skyDarkening;
         int block = w.getLightFor(EnumSkyBlock.BLOCK, pos);
         int level = Math.max(0, Math.min(15, Math.max(sky, block)));
         return 0.12F + 0.88F * CURVE[level];
      } else {
         return 1.0F;
      }
   }

   static {
      for (int i = 0; i < 16; i++) {
         float f = 1.0F - i / 15.0F;
         CURVE[i] = (1.0F - f) / (f * 3.0F + 1.0F) * 0.95F + 0.05F;
      }
   }
}
