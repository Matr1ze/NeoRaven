package xyz.daikirai.render.furiah.module.ambience;

import xyz.daikirai.render.furiah.fx.Neon;
import xyz.daikirai.render.furiah.fx.Noise3;
import xyz.daikirai.render.furiah.render.ColorUtil;
import xyz.daikirai.render.furiah.render.RenderState;
import xyz.daikirai.render.furiah.render.Textures;
import xyz.daikirai.render.furiah.render.Vfx;
import xyz.daikirai.render.furiah.setting.SliderSetting;

final class FirefliesStyle extends AmbientStyleBase {
   final SliderSetting blinkRate;
   final SliderSetting wander;
   private final float[] curl = new float[3];
   private final float[] flow = new float[3];

   FirefliesStyle(FuriahAmbienceModule module) {
      super(module, AmbientStyle.FIREFLIES, spec());
      this.blinkRate = this.setting(new SliderSetting("fireflies.blink", "Blink Rate", "How often they light up.", 0.2, 3.0, 1.0, 0.05).unit("×"));
      this.wander = this.setting(new SliderSetting("fireflies.wander", "Wander Speed", "How fast they wander about.", 0.1, 3.0, 1.0, 0.05).unit("×"));
   }

   private static AmbientStyleBase.Spec spec() {
      AmbientStyleBase.Spec s = new AmbientStyleBase.Spec();
      s.baseDensity = 0.014F;
      s.nearBias = 1.5F;
      s.opacity = 100.0F;
      s.followWind = 15.0F;
      s.heightMin = 0.3F;
      s.heightMax = 3.0F;
      s.heightLimit = 20.0F;
      s.time = AmbientStyleBase.TimeRule.NIGHT;
      s.cap = 600;
      s.light = true;
      s.defaultColor = -2556054;
      s.densityHelp = "How many fireflies there are.";
      return s;
   }

   @Override
   protected boolean spawn(AmbientEnv env, int i, float r, boolean fresh) {
      if (!this.randomPlace(env, i, r, 0.0F)) {
         return false;
      } else {
         AmbientPool p = this.pool;
         p.a[i] = this.rng.next() * 10.0F;
         p.b[i] = this.rng.range(2.2F, 5.5F);
         p.c[i] = this.rng.range(0.35F, 0.6F);
         p.d[i] = this.rng.next() * 500.0F;
         p.size[i] = this.rng.range(0.7F, 1.3F);
         p.e[i] = Double.isNaN(p.ground[i]) ? 1.5F : (float)(p.y[i] - p.ground[i]);
         p.life[i] = 1.0E9F;
         return true;
      }
   }

   @Override
   protected boolean step(AmbientEnv env, int i, float r, float dt) {
      AmbientPool p = this.pool;
      float sp = this.speed.getFloat() * this.wander.getFloat();
      Noise3.curl((float)(p.x[i] * 0.22), (float)(p.y[i] * 0.3), (float)(p.z[i] * 0.22 + env.time * 0.08 + p.d[i]), 777, this.curl);
      this.windAt(env, i, this.followWind.getFloat() / 100.0F, this.flow);
      double nx = p.x[i] + (this.curl[0] * 0.9F * sp + this.flow[0] * 0.5F) * dt;
      double nz = p.z[i] + (this.curl[2] * 0.9F * sp + this.flow[2] * 0.5F) * dt;
      double ny = p.y[i] + this.curl[1] * 0.35F * sp * dt;
      double g = env.outdoorsOnly ? env.groundAt(nx, nz) : p.ground[i];
      if (!Double.isNaN(g)) {
         double want = g + p.e[i];
         ny += (want - ny) * Math.min(1.0, dt * 1.2);
         p.ground[i] = g;
      }

      if (env.solidAt(nx, ny, nz)) {
         return true;
      } else {
         p.x[i] = nx;
         p.y[i] = ny;
         p.z[i] = nz;
         return true;
      }
   }

   private float blink(AmbientEnv env, int i) {
      AmbientPool p = this.pool;
      double t = env.time * this.blinkRate.getFloat() + p.a[i];
      double period;
      double ph = t % (period = p.b[i]) / period;
      float on;
      float k;
      if (ph < (on = p.c[i])) {
         float x = (float)(ph / on);
         k = (float)Math.sin(x * Math.PI);
         k *= k;
      } else {
         k = 0.0F;
      }

      return 0.22F + 0.78F * k;
   }

   @Override
   void draw(AmbientEnv env) {
      float r = this.radius(env);
      float s0 = 0.075F * this.size.getFloat();
      float glowK = this.glowK();
      RenderState.additive();
      if (glowK > 0.0F) {
         for (int i = 0; i < this.pool.count; i++) {
            if (this.place(env, i, s0 * 8.0F)) {
               float b = this.blink(env, i);
               float a = this.fogged(this.fade(env, i, r)) * b;
               if (a > 0.004F) {
                  int c = this.color.getColor(this.hash(i, 5));
                  Vfx.sprite(
                     Textures.GLOW, this.rx, this.ry, this.rz, s0 * this.pool.size[i] * (9.0F + 5.0F * b), 0.0F, ColorUtil.mulAlpha(c, a * 0.5F * glowK)
                  );
               }
            }
         }

         Vfx.flush();
      }

      for (int ix = 0; ix < this.pool.count; ix++) {
         float a;
         if (this.place(env, ix, s0 * 2.0F) && (a = this.fogged(this.fade(env, ix, r)) * this.blink(env, ix)) > 0.004F) {
            int c = this.color.getColor(this.hash(ix, 5));
            Vfx.sprite(
               Textures.SPARK, this.rx, this.ry, this.rz, s0 * this.pool.size[ix] * 2.0F, 0.0F, ColorUtil.mulAlpha(Neon.tint(c), Math.min(1.0F, a * 1.2F))
            );
         }
      }

      Vfx.flush();
   }
}
