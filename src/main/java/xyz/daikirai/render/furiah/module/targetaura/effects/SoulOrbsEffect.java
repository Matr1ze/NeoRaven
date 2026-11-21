package xyz.daikirai.render.furiah.module.targetaura.effects;

import xyz.daikirai.render.furiah.module.targetaura.AuraEffect;
import xyz.daikirai.render.furiah.module.targetaura.EffectContext;
import xyz.daikirai.render.furiah.module.targetaura.EffectType;
import xyz.daikirai.render.furiah.render.Easing;
import xyz.daikirai.render.furiah.render.Textures;
import xyz.daikirai.render.furiah.render.Vfx;
import xyz.daikirai.render.furiah.setting.ThemeTint;
import xyz.daikirai.render.furiah.setting.SliderSetting;

public final class SoulOrbsEffect extends AuraEffect {
   private static final int SAMPLES = 24;
   public final SliderSetting orbs = this.setting(new SliderSetting("orbs.count", "Orbs", "Number of orbs.", 3.0, 6.0, 4.0, 1.0));
   public final SliderSetting trailLength = this.setting(
      new SliderSetting("orbs.trail", "Trail Length", "Length of the trails, in seconds of travel.", 0.0, 0.8, 0.35, 0.05).unit("s")
   );

   public SoulOrbsEffect() {
      super(EffectType.SOUL_ORBS, "orbs", new ThemeTint());
   }

   private static void orb(EffectContext c, int i, int n, double tau, double radiusScale, float spd, float sz, int idx) {
      double phase = i * (Math.PI * 2) / n;
      double angle = tau * (Math.PI * 2) * 0.42 * spd + phase;
      double r = (0.72 + 0.05 * Math.sin(1.7 * tau + i * 1.3)) * radiusScale;
      Vfx.X[idx] = c.x + Math.cos(angle) * r;
      Vfx.Y[idx] = c.y + c.height * 0.6 + 0.14 * sz * Math.sin(tau * (Math.PI * 2) * 0.5 * spd + phase * 1.5);
      Vfx.Z[idx] = c.z + Math.sin(angle) * r;
   }

   @Override
   public void render(EffectContext c) {
      float sz = this.size(c);
      float inten = this.intensity(c);
      float spd = this.speed();
      int n = this.orbs.getInt();
      double radiusScale = sz * Math.max(0.0F, Easing.outBack(Easing.clamp01(c.intro / 0.8F), 1.2F));
      double tail = Math.min(this.trailLength.get(), c.time);
      int samples = tail > 0.01 ? 24 : 1;

      for (int i = 0; i < n; i++) {
         for (int k = 0; k < samples; k++) {
            orb(c, i, n, c.time - tail * k / Math.max(1, samples - 1), radiusScale, spd, sz, i * 24 + k);
         }
      }

      if (samples > 1) {
         for (int var17 = 0; var17 < n; var17++) {
            int col = this.color(c, n > 1 ? (float)var17 / (n - 1) : 0.0F);

            for (int k = 0; k < samples; k++) {
               float remain = 1.0F - (float)k / (samples - 1);
               Vfx.W[var17 * 24 + k] = 0.15F * sz * (float)Math.pow(remain, 0.8);
               Vfx.C[var17 * 24 + k] = alpha(col, 0.5F * inten * (float)Math.pow(remain, 1.6));
            }

            Vfx.ribbon(Textures.LINE_GLOW, var17 * 24, samples, false);
         }
      }

      for (int pass = 0; pass < 3; pass++) {
         for (int i2 = 0; i2 < n; i2++) {
            int idx = i2 * 24;
            int col = this.color(c, n > 1 ? (float)i2 / (n - 1) : 0.0F);
            float pulse = 0.85F + 0.15F * (float)Math.sin(c.time * 4.0 + i2 * 1.7);
            if (pass == 0) {
               Vfx.sprite(Textures.GLOW, Vfx.X[idx], Vfx.Y[idx], Vfx.Z[idx], 0.95F * sz * pulse, 0.0F, alpha(col, 0.5F * inten));
            } else if (pass == 1) {
               Vfx.sprite(Textures.GLOW, Vfx.X[idx], Vfx.Y[idx], Vfx.Z[idx], 0.38F * sz, 0.0F, core(col, 0.55F * inten));
            } else {
               Vfx.sprite(Textures.SPARK, Vfx.X[idx], Vfx.Y[idx], Vfx.Z[idx], 0.26F * sz, 0.0F, core(col, inten));
            }
         }
      }
   }
}
