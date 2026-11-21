package xyz.daikirai.render.furiah.module.ambience;

import xyz.daikirai.render.furiah.fx.Rng;
import xyz.daikirai.render.furiah.render.Camera;
import xyz.daikirai.render.furiah.render.Easing;
import xyz.daikirai.render.furiah.render.ViewFrustum;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.util.BlockPos.MutableBlockPos;
import net.minecraft.world.World;

final class AmbientEnv {
   private static final int CACHE = 200;
   World world;
   double camX;
   double camY;
   double camZ;
   float radius;
   boolean outdoorsOnly;
   boolean dayCycle;
   float daylight;
   float light = 1.0F;
   float globalAlpha;
   boolean paused;
   Wind wind = new Wind();
   final Rng rng = new Rng();
   boolean preview;
   double flatGround = Double.NaN;
   int previewCap = 200;
   float previewFar = 64.0F;
   double time;
   long tickCount;
   float partialTicks;
   double viewX;
   double viewY;
   double viewZ;
   double eyeX;
   double eyeY;
   double eyeZ;
   Camera cam;
   ViewFrustum frustum;
   float fogStart = 1.0E9F;
   float fogEnd = 2.0E9F;
   int fogColor = -4138753;
   float frameDt;
   boolean skyPass;
   private final int[] groundY = new int[40000];
   private final int[] groundStamp = new int[40000];
   private int cacheX0;
   private int cacheZ0;
   private int stamp = 1;
   private final MutableBlockPos probe = new MutableBlockPos();

   AmbientEnv() {
      this.rng.seed((int)System.nanoTime());
   }

   void tickCache() {
      int cx = (int)Math.floor(this.camX);
      int cz = (int)Math.floor(this.camZ);
      int half = 100;
      if (Math.abs(cx - (this.cacheX0 + half)) > 24 || Math.abs(cz - (this.cacheZ0 + half)) > 24) {
         this.cacheX0 = cx - half;
         this.cacheZ0 = cz - half;
         this.stamp++;
      } else if (this.tickCount % 20L == 0L) {
         this.stamp++;
      }
   }

   void forgetGround() {
      this.stamp++;
   }

   double groundAt(double x, double z) {
      if (this.preview) {
         return this.flatGround;
      } else {
         int bx = (int)Math.floor(x);
         int bz = (int)Math.floor(z);
         int ix = bx - this.cacheX0;
         int iz = bz - this.cacheZ0;
         if (ix >= 0 && iz >= 0 && ix < 200 && iz < 200) {
            int k = ix * 200 + iz;
            if (this.groundStamp[k] != this.stamp) {
               this.groundStamp[k] = this.stamp;
               this.groundY[k] = this.lookUpGround(bx, bz);
            }

            return this.groundY[k] == Integer.MIN_VALUE ? Double.NaN : this.groundY[k];
         } else {
            int y = this.lookUpGround(bx, bz);
            return y == Integer.MIN_VALUE ? Double.NaN : y;
         }
      }
   }

   private int lookUpGround(int bx, int bz) {
      if (this.world == null) {
         return Integer.MIN_VALUE;
      } else {
         this.probe.set(bx, 64, bz);
         return !this.world.isBlockLoaded(this.probe) ? Integer.MIN_VALUE : this.world.getPrecipitationHeight(this.probe).getY();
      }
   }

   boolean solidAt(double x, double y, double z) {
      if (this.preview) {
         return y < this.flatGround;
      } else if (this.world != null && !(y < 0.0) && !(y >= 256.0)) {
         this.probe.set((int)Math.floor(x), (int)Math.floor(y), (int)Math.floor(z));
         Block block = this.world.getBlockState(this.probe).getBlock();
         return block.getMaterial().blocksMovement();
      } else {
         return false;
      }
   }

   boolean liquidAt(double x, double y, double z) {
      if (!this.preview && this.world != null && !(y < 0.0) && !(y >= 256.0)) {
         this.probe.set((int)Math.floor(x), (int)Math.floor(y), (int)Math.floor(z));
         return this.world.getBlockState(this.probe).getBlock().getMaterial().isLiquid();
      } else {
         return false;
      }
   }

   double floorBelow(double x, double y, double z, int depth) {
      if (this.preview) {
         return y >= this.flatGround && y - this.flatGround <= depth ? this.flatGround : Double.NaN;
      } else if (this.world == null) {
         return Double.NaN;
      } else {
         int bx = (int)Math.floor(x);
         int bz = (int)Math.floor(z);

         int top;
         for (int by = top = (int)Math.floor(y); by >= Math.max(0, top - depth); by--) {
            this.probe.set(bx, by, bz);
            Material m = this.world.getBlockState(this.probe).getBlock().getMaterial();
            if (m.blocksMovement() || m.isLiquid()) {
               return by == top ? Double.NaN : by + 1;
            }
         }

         return Double.NaN;
      }
   }

   double headroom(double x, double y, double z, int max) {
      if (this.preview) {
         return max;
      } else if (this.world == null) {
         return 0.0;
      } else {
         int bx = (int)Math.floor(x);
         int bz = (int)Math.floor(z);

         int y0;
         for (int by = y0 = (int)Math.floor(y); by < y0 + max && by < 256; by++) {
            this.probe.set(bx, by, bz);
            if (this.world.getBlockState(this.probe).getBlock().getMaterial().blocksMovement()) {
               return by - y;
            }
         }

         return max;
      }
   }

   float edgeFade(double dx, double dz, float r) {
      double d = Math.sqrt(dx * dx + dz * dz);
      return 1.0F - Easing.smoothstep(r * 0.8F, r, (float)d);
   }

   float fog(double dist) {
      return dist <= this.fogStart ? 0.0F : Easing.clamp01((float)((dist - this.fogStart) / Math.max(0.001, (double)(this.fogEnd - this.fogStart))));
   }

   static float nearFade(double dist) {
      return Easing.smoothstep(0.55F, 1.35F, (float)dist);
   }
}
