package xyz.daikirai.render.furiah.module.ambience;

import xyz.daikirai.render.furiah.fx.Noise3;
import xyz.daikirai.render.furiah.fx.Rng;
import xyz.daikirai.render.furiah.render.Procedural;

final class Wind {
   static final float MAX_SPEED = 9.0F;
   float dirX = 1.0F;
   float dirZ = 0.0F;
   float speed;
   float gust = 1.0F;
   boolean gustStarted;
   double time;
   private float angle;
   private float gustT = -1.0F;
   private float gustLength;
   private float gustPeak;
   private float gustWanted;
   private float turbulence;
   private final Rng rng = new Rng();
   private final float[] curl = new float[3];

   Wind() {
      this.rng.seed(24301);
   }

   void tick(float dt, float fixedDegrees, float strength, float gustFrequency, float gustStrength, float turb) {
      this.time += dt;
      if (Float.isNaN(fixedDegrees)) {
         this.angle = (float)(this.time * 0.012 + 1.6 * Procedural.fbm((float)(this.time * 0.004), 911));
      } else {
         float target = (float)Math.toRadians(fixedDegrees - 90.0);
         float diff = (float)Math.atan2(Math.sin(target - this.angle), Math.cos(target - this.angle));
         this.angle = this.angle + diff * Math.min(1.0F, dt * 1.5F);
      }

      this.dirX = (float)Math.cos(this.angle);
      this.dirZ = (float)Math.sin(this.angle);
      this.gustStarted = false;
      if (this.gustWanted > 0.0F) {
         this.gustT = 0.0F;
         this.gustLength = this.rng.range(2.5F, 5.0F);
         this.gustPeak = this.gustWanted * this.rng.range(0.9F, 1.1F);
         this.gustStarted = true;
         this.gustWanted = 0.0F;
      } else if (this.gustT < 0.0F && gustFrequency > 0.0F && gustStrength > 0.0F) {
         float perSecond = gustFrequency * 0.12F;
         if (this.rng.next() < perSecond * dt) {
            this.gustT = 0.0F;
            this.gustLength = this.rng.range(2.5F, 5.0F);
            this.gustPeak = gustStrength * this.rng.range(0.8F, 1.2F);
            this.gustStarted = true;
         }
      }

      float g = 0.0F;
      if (this.gustT >= 0.0F) {
         this.gustT += dt;
         float up = Math.min(1.0F, this.gustT / 0.9F);
         float down = Math.min(1.0F, Math.max(0.0F, (this.gustLength - this.gustT) / 1.4F));
         g = up * up * (3.0F - 2.0F * up) * down;
         if (this.gustT >= this.gustLength) {
            this.gustT = -1.0F;
         }
      }

      this.gust = 1.0F + 1.6F * this.gustPeak * g;
      this.speed = strength * 9.0F * this.gust;
      this.turbulence = turb;
   }

   void gustNow(float strength) {
      this.gustWanted = Math.max(0.5F, strength);
   }

   float gustAmount() {
      return this.gust <= 1.0F ? 0.0F : Math.min(1.0F, (this.gust - 1.0F) / 1.6F);
   }

   void velocity(double x, double y, double z, float follow, float[] out) {
      float tx = 0.0F;
      float ty = 0.0F;
      float tz = 0.0F;
      if (this.turbulence > 0.0F) {
         Noise3.curl((float)(x * 0.075), (float)(y * 0.11), (float)(z * 0.075 + this.time * 0.09), 4242, this.curl);
         float amp = this.turbulence * (0.8F + 0.25F * this.speed);
         tx = this.curl[0] * amp;
         ty = this.curl[1] * amp * 0.45F;
         tz = this.curl[2] * amp;
      }

      out[0] = (this.dirX * this.speed + tx) * follow;
      out[1] = ty * follow;
      out[2] = (this.dirZ * this.speed + tz) * follow;
   }

   void swirl(double x, double y, double z, float[] out) {
      if (this.turbulence <= 0.0F) {
         out[2] = 0.0F;
         out[1] = 0.0F;
         out[0] = 0.0F;
      } else {
         Noise3.curl((float)(x * 0.075), (float)(y * 0.11), (float)(z * 0.075 + this.time * 0.09), 4242, this.curl);
         float amp = this.turbulence * (0.8F + 0.25F * this.speed);
         out[0] = this.curl[0] * amp;
         out[1] = this.curl[1] * amp * 0.45F;
         out[2] = this.curl[2] * amp;
      }
   }

   float compassDegrees() {
      float deg = (float)Math.toDegrees(Math.atan2(this.dirZ, this.dirX)) + 90.0F;
      return (deg % 360.0F + 360.0F) % 360.0F;
   }
}
