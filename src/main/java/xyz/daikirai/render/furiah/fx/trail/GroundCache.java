package xyz.daikirai.render.furiah.fx.trail;

import java.util.Arrays;
import xyz.daikirai.render.furiah.fx.Terrain;
import net.minecraft.world.World;

public final class GroundCache {
   private static final int SIZE = 1024;
   private static final int MASK = 1023;
   private static final int PROBES = 8;
   private static final long FORGET_NANOS = 1000000000L;
   private final long[] keys = new long[1024];
   private final double[] tops = new double[1024];
   private final boolean[] used = new boolean[1024];
   private World world;
   private long since;

   public double surface(World w, double x, double y, double z, double up, double down) {
      if (w == null) {
         return y - down <= 0.0 && 0.0 <= y + up ? 0.0 : Double.NaN;
      } else {
         long now = System.nanoTime();
         if (w != this.world || now - this.since > 1000000000L) {
            Arrays.fill(this.used, false);
            this.world = w;
            this.since = now;
         }

         int bx = (int)Math.floor(x);
         int by = (int)Math.floor(y);
         int bz = (int)Math.floor(z);
         long key = (bx & 67108863L) << 38 | (bz & 67108863L) << 12 | by & 4095L;
         int h = (int)(key * -7046029254386353131L >>> 54) & 1023;

         for (int i = 0; i < 8; i++) {
            int slot = h + i & 1023;
            if (!this.used[slot]) {
               double top = Terrain.surfaceAt(w, bx + 0.5, bz + 0.5, y, up, down);
               this.used[slot] = true;
               this.keys[slot] = key;
               this.tops[slot] = top;
               return top;
            }

            if (this.keys[slot] == key) {
               return this.tops[slot];
            }
         }

         return Terrain.surfaceAt(w, bx + 0.5, bz + 0.5, y, up, down);
      }
   }
}
