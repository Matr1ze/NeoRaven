package xyz.daikirai.render.furiah.module.ambience;

import xyz.daikirai.render.furiah.render.ColorUtil;
import xyz.daikirai.render.furiah.render.Easing;
import xyz.daikirai.render.furiah.render.RenderState;
import xyz.daikirai.render.furiah.render.Textures;
import xyz.daikirai.render.furiah.render.Vfx;
import xyz.daikirai.render.furiah.setting.BooleanSetting;
import xyz.daikirai.render.furiah.setting.SliderSetting;

final class GroundMistStyle extends AmbientStyleBase {
   final SliderSetting softness;
   final BooleanSetting skipWater;
   private final float[] flow = new float[3];

   GroundMistStyle(FuriahAmbienceModule module) {
      super(module, AmbientStyle.GROUND_MIST, spec());
      this.softness = this.setting(new SliderSetting("mist.softness", "Softness", "Softer mist is bigger and fainter.", 0.0, 100.0, 55.0, 5.0).unit("%"));
      this.skipWater = this.setting(new BooleanSetting("mist.skipWater", "Skip Water", "No mist over water.", true));
   }

   private static AmbientStyleBase.Spec spec() {
      AmbientStyleBase.Spec s = new AmbientStyleBase.Spec();
      s.baseDensity = 0.02F;
      s.opacity = 30.0F;
      s.glow = false;
      s.followWind = 60.0F;
      s.heightMin = 0.5F;
      s.heightMax = 3.0F;
      s.heightLimit = 10.0F;
      s.cap = 300;
      s.light = true;
      s.defaultColor = -2299660;
      s.densityHelp = "How thick the mist is.";
      return s;
   }

   @Override
   protected boolean spawn(AmbientEnv env, int i, float r, boolean fresh) {
      if (!this.randomPlace(env, i, r, 0.0F)) {
         return false;
      } else {
         AmbientPool p = this.pool;
         double g = p.ground[i];
         if (!Double.isNaN(g) && !(g < 2.0)) {
            if (this.skipWater.get() && env.liquidAt(p.x[i], g - 0.5, p.z[i])) {
               return false;
            } else {
               p.d[i] = this.rng.nextInt(4);
               p.a[i] = this.rng.next() * 6.283F;
               p.b[i] = this.rng.signed() * 0.08F;
               p.c[i] = this.rng.next() * 6.283F;
               p.e[i] = (float)(p.y[i] - g);
               p.size[i] = this.rng.range(0.75F, 1.3F);
               return true;
            }
         } else {
            return false;
         }
      }
   }

   @Override
   protected boolean step(AmbientEnv env, int i, float r, float dt) {
      AmbientPool p = this.pool;
      float sp = this.speed.getFloat();
      this.windAt(env, i, this.followWind.getFloat() / 100.0F, this.flow);
      double nx = p.x[i] + this.flow[0] * 0.55F * sp * dt;
      double nz = p.z[i] + this.flow[2] * 0.55F * sp * dt;
      double ny = p.y[i];
      double g = env.outdoorsOnly ? env.groundAt(nx, nz) : p.ground[i];
      if (!Double.isNaN(g) && !(g < 2.0) && (!this.skipWater.get() || !env.outdoorsOnly || (env.tickCount + i) % 10L != 0L || !env.liquidAt(nx, g - 0.5, nz))) {
         ny += (g + p.e[i] - ny) * Math.min(1.0, dt * 1.5);
         p.ground[i] = g;
         p.x[i] = nx;
         p.y[i] = ny;
         p.z[i] = nz;
         p.a[i] = p.a[i] + p.b[i] * sp * dt;
         return true;
      } else {
         return false;
      }
   }

   @Override
   void draw(AmbientEnv env) {
      float r = this.radius(env);
      float soft = this.softness.getFloat() / 100.0F;
      float s0 = (3.2F + 2.6F * soft) * this.size.getFloat();
      float cell = 0.25F;
      int[] order = this.backToFront(env);
      RenderState.translucent(false);

      for (int k = 0; k < this.pool.count; k++) {
         int i = order[k];
         float s = s0 * this.pool.size[i];
         if (this.place(env, i, s * 0.6F)) {
            float near = Easing.smoothstep(1.2F, 4.8F, this.dist);
            float breathe = 0.85F + 0.15F * (float)Math.sin(env.time * 0.35 + this.pool.c[i]);
            float a = this.fade(env, i, r) * near * breathe * (1.0F - 0.35F * soft);
            if (!(a <= 0.003F) && !(this.fogK >= 1.0F)) {
               float t = Easing.clamp01((this.pool.e[i] - this.lowest()) / Math.max(0.5F, this.highest() - this.lowest()));
               int c = this.fogColor(env, this.lit(env, this.color.getColor(t)));
               float u0 = (int)this.pool.d[i] * cell;
               float angle = this.pool.a[i] + this.pool.b[i] * this.speed.getFloat() * 0.05F * (env.partialTicks - 1.0F);
               float scale = s * (0.95F + 0.05F * breathe);
               Vfx.sprite(Textures.MIST_ATLAS, this.rx, this.ry, this.rz, scale * 0.5, scale * 0.38, angle, u0, 0.0F, u0 + cell, 1.0F, ColorUtil.mulAlpha(c, a));
            }
         }
      }

      Vfx.flush();
   }
}
