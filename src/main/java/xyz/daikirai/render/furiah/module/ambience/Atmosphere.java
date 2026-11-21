package xyz.daikirai.render.furiah.module.ambience;

import xyz.daikirai.render.furiah.render.ColorUtil;
import xyz.daikirai.render.furiah.render.Easing;
import net.minecraft.util.Vec3;

final class Atmosphere {
   static final int FOG = 0;
   static final int CLOUDS = 1;
   static final int SKY = 2;
   private final float[] r = new float[3];
   private final float[] g = new float[3];
   private final float[] b = new float[3];
   private float strength;
   private float fog = 1.0F;
   private boolean started;
   private final float[] tmp = new float[3];

   void update(float dt, int fogColor, int cloudColor, int skyColor, float amount, float fogFactor) {
      float k = 1.0F - (float)Math.exp(-dt * 3.2F);
      boolean snap = !this.started || this.strength < 0.002F && amount > 0.0F;
      this.started = true;
      this.glide(0, fogColor, k, snap);
      this.glide(1, cloudColor, k, snap);
      this.glide(2, skyColor, k, snap);
      this.strength = this.strength + (amount - this.strength) * k;
      this.fog = this.fog + (fogFactor - this.fog) * k;
      if (Math.abs(this.strength - amount) < 1.0E-4F) {
         this.strength = amount;
      }

      if (Math.abs(this.fog - fogFactor) < 1.0E-4F) {
         this.fog = fogFactor;
      }
   }

   private void glide(int slot, int color, float k, boolean snap) {
      float tr = ColorUtil.red(color) / 255.0F;
      float tg = ColorUtil.green(color) / 255.0F;
      float tb = ColorUtil.blue(color) / 255.0F;
      if (snap) {
         this.r[slot] = tr;
         this.g[slot] = tg;
         this.b[slot] = tb;
      } else {
         this.r[slot] = this.r[slot] + (tr - this.r[slot]) * k;
         this.g[slot] = this.g[slot] + (tg - this.g[slot]) * k;
         this.b[slot] = this.b[slot] + (tb - this.b[slot]) * k;
      }
   }

   boolean active() {
      return this.strength > 0.002F;
   }

   float fogFactor() {
      return this.fog;
   }

   boolean fogChanged() {
      return Math.abs(this.fog - 1.0F) > 0.002F;
   }

   void reset() {
      this.strength = 0.0F;
      this.fog = 1.0F;
   }

   void tint(int slot, float cr, float cg, float cb, float[] out, float amount) {
      float lum = 0.2126F * cr + 0.7152F * cg + 0.0722F * cb;
      float tl = Math.max(0.05F, 0.2126F * this.r[slot] + 0.7152F * this.g[slot] + 0.0722F * this.b[slot]);
      float k = lum / tl;
      float tr = Math.min(1.0F, this.r[slot] * k);
      float tg = Math.min(1.0F, this.g[slot] * k);
      float tb = Math.min(1.0F, this.b[slot] * k);
      float s = Easing.clamp01(this.strength * amount);
      out[0] = cr + (tr - cr) * s;
      out[1] = cg + (tg - cg) * s;
      out[2] = cb + (tb - cb) * s;
   }

   Vec3 tint(int slot, Vec3 v, float amount) {
      if (v != null && this.active()) {
         this.tint(slot, (float)v.xCoord, (float)v.yCoord, (float)v.zCoord, this.tmp, amount);
         return new Vec3(this.tmp[0], this.tmp[1], this.tmp[2]);
      } else {
         return v;
      }
   }
}
