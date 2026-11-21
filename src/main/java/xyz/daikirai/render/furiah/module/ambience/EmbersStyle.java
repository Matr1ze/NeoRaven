package xyz.daikirai.render.furiah.module.ambience;

import xyz.daikirai.render.furiah.render.ColorUtil;
import xyz.daikirai.render.furiah.render.Easing;
import xyz.daikirai.render.furiah.render.RenderState;
import xyz.daikirai.render.furiah.render.Textures;
import xyz.daikirai.render.furiah.render.Vfx;
import xyz.daikirai.render.furiah.setting.SliderSetting;
import net.minecraft.util.MathHelper;

final class EmbersStyle extends AmbientStyleBase {
   final SliderSetting rise;
   final SliderSetting flicker;
   final SliderSetting lifetime;
   private final float[] flow = new float[3];

   EmbersStyle(FuriahAmbienceModule module) {
      super(module, AmbientStyle.EMBERS, spec());
      this.rise = this.setting(new SliderSetting("embers.rise", "Rise Speed", "How fast they rise.", 0.1, 3.0, 1.0, 0.05).unit("×"));
      this.flicker = this.setting(new SliderSetting("embers.flicker", "Flicker", "How much they flicker.", 0.0, 100.0, 60.0, 5.0).unit("%"));
      this.lifetime = this.setting(new SliderSetting("embers.lifetime", "Lifetime", "How long an ember glows.", 1.0, 12.0, 4.5, 0.5).unit(" s"));
   }

   private static AmbientStyleBase.Spec spec() {
      AmbientStyleBase.Spec s = new AmbientStyleBase.Spec();
      s.baseDensity = 0.009F;
      s.nearBias = 1.6F;
      s.opacity = 100.0F;
      s.followWind = 50.0F;
      s.heightMin = 0.0F;
      s.heightMax = 9.0F;
      s.heightLimit = 40.0F;
      s.cap = 800;
      s.defaultColor = -34253;
      s.densityHelp = "How many embers there are.";
      return s;
   }

   @Override
   protected boolean spawn(AmbientEnv env, int i, float r, boolean fresh) {
      if (!this.randomPlace(env, i, r, 0.0F)) {
         return false;
      } else {
         AmbientPool p = this.pool;
         p.a[i] = this.rng.next() * 6.283F;
         p.b[i] = this.rng.next() * 6.283F;
         p.vx[i] = MathHelper.cos(p.b[i]);
         p.vz[i] = MathHelper.sin(p.b[i]);
         p.c[i] = this.rng.next() * 50.0F;
         p.size[i] = this.rng.range(0.6F, 1.4F);
         p.life[i] = this.lifetime.getFloat() * this.rng.range(0.7F, 1.3F);
         p.d[i] = this.rng.range(0.7F, 1.3F);
         float f = p.age[i] = fresh ? this.rng.next() * p.life[i] : 0.0F;
         double y;
         if (!fresh
            && !Double.isNaN(p.ground[i])
            && !env.solidAt(p.x[i], y = p.ground[i] + this.lowest() + this.rng.next() * Math.max(0.5F, (this.highest() - this.lowest()) * 0.3F), p.z[i])) {
            p.teleport(i, p.x[i], y, p.z[i]);
         }

         return true;
      }
   }

   @Override
   protected boolean step(AmbientEnv env, int i, float r, float dt) {
      AmbientPool p = this.pool;
      if (p.age[i] >= p.life[i]) {
         return false;
      } else {
         float sp = this.speed.getFloat();
         this.windAt(env, i, this.followWind.getFloat() / 100.0F, this.flow);
         float zig = 0.6F * MathHelper.sin(p.age[i] * 3.1F * sp + p.a[i]);
         double nx = p.x[i] + (this.flow[0] + p.vx[i] * zig) * sp * dt;
         double nz = p.z[i] + (this.flow[2] + p.vz[i] * zig) * sp * dt;
         double ny = p.y[i] + (0.75F * this.rise.getFloat() * p.d[i] + this.flow[1] * 0.3F) * sp * dt;
         if (env.solidAt(nx, ny, nz)) {
            return false;
         } else {
            p.x[i] = nx;
            p.y[i] = ny;
            p.z[i] = nz;
            return true;
         }
      }
   }

   private int emberColor(int i) {
      float t = Easing.clamp01(this.pool.age[i] / this.pool.life[i]);
      int c = this.color.getColor(t);
      return t < 0.12F ? ColorUtil.lerp(ColorUtil.core(c), c, t / 0.12F) : ColorUtil.shade(c, 1.0F - 0.45F * Easing.smoothstep(0.5F, 1.0F, t));
   }

   private float alpha(AmbientEnv env, int i, float r) {
      AmbientPool p = this.pool;
      float t = Easing.clamp01(p.age[i] / p.life[i]);
      float life = Easing.smoothstep(0.0F, 0.08F, t) * (1.0F - Easing.smoothstep(0.7F, 1.0F, t));
      float fl = this.flicker.getFloat() / 100.0F;
      double ph = env.time * 11.0 + p.c[i];
      float f = 1.0F - fl * 0.55F * (float)(0.5 + 0.5 * Math.sin(ph) * Math.sin(ph * 1.73 + 1.1));
      return this.fogged(this.fade(env, i, r)) * life * f;
   }

   @Override
   void draw(AmbientEnv env) {
      float r = this.radius(env);
      float s0 = 0.05F * this.size.getFloat();
      float glowK = this.glowK();
      RenderState.additive();
      if (glowK > 0.0F) {
         for (int i = 0; i < this.pool.count; i++) {
            float a;
            if (this.place(env, i, s0 * 6.0F) && (a = this.alpha(env, i, r)) > 0.004F) {
               Vfx.sprite(
                  Textures.GLOW, this.rx, this.ry, this.rz, s0 * this.pool.size[i] * 8.0F, 0.0F, ColorUtil.mulAlpha(this.emberColor(i), a * 0.55F * glowK)
               );
            }
         }

         Vfx.flush();
      }

      for (int ix = 0; ix < this.pool.count; ix++) {
         float a;
         if (this.place(env, ix, s0 * 2.0F) && (a = this.alpha(env, ix, r)) > 0.004F) {
            Vfx.sprite(Textures.SPARK, this.rx, this.ry, this.rz, s0 * this.pool.size[ix] * 2.0F, 0.0F, ColorUtil.mulAlpha(this.emberColor(ix), a));
         }
      }

      Vfx.flush();
   }
}
