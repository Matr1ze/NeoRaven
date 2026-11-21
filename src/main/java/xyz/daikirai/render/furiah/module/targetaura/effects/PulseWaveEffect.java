package xyz.daikirai.render.furiah.module.targetaura.effects;

import xyz.daikirai.render.furiah.module.targetaura.AuraEffect;
import xyz.daikirai.render.furiah.module.targetaura.EffectContext;
import xyz.daikirai.render.furiah.module.targetaura.EffectType;
import xyz.daikirai.render.furiah.render.Easing;
import xyz.daikirai.render.furiah.render.RenderState;
import xyz.daikirai.render.furiah.render.Textures;
import xyz.daikirai.render.furiah.render.Vfx;
import xyz.daikirai.render.furiah.setting.ThemeTint;
import xyz.daikirai.render.furiah.setting.SliderSetting;

public final class PulseWaveEffect extends AuraEffect {
   public final SliderSetting interval = this.setting(new SliderSetting("pulse.interval", "Interval", "Time between waves.", 0.3, 3.0, 1.0, 0.05).unit("s"));
   public final SliderSetting thickness = this.setting(new SliderSetting("pulse.thickness", "Thickness", "Width of each wave.", 0.5, 2.0, 1.0, 0.05).unit("x"));

   public PulseWaveEffect() {
      super(EffectType.PULSE_WAVE, "pulse", new ThemeTint());
   }

   @Override
   public void render(EffectContext c) {
      float sz = this.size(c);
      float a = this.intensity(c) * c.groundAlpha;
      if (!(a <= 0.003F)) {
         float spd = this.speed();
         float thick = this.thickness.getFloat();
         double every = this.interval.get();
         double duration = 1.35 / spd;
         double maxRadius = 2.3 * sz;
         double x = c.x;
         double y = c.groundY + 0.015;
         double z = c.z;
         RenderState.decal(true);

         int newest;
         double age;
         for (int k = newest = (int)Math.floor(c.time / every); k >= 0 && !((age = c.time - k * every) >= duration); k--) {
            float p = (float)(age / duration);
            double radius = Math.max(0.05, maxRadius * Easing.outCubic(p));
            double width = (0.14 + 0.45 * p) * thick * sz;
            float fade = (float)Math.pow(1.0F - p, 1.3);
            int col = this.color(c, p);
            Vfx.flatBand(Textures.WAVE, x, y, z, Math.max(0.0, radius - width), radius, 0.0, Math.PI * 2, 128, alpha(col, 0.85F * fade * a));
         }

         for (int var32 = newest; var32 >= 0 && !((age = c.time - var32 * every) >= duration); var32--) {
            float p = (float)(age / duration);
            double radius = Math.max(0.05, maxRadius * Easing.outCubic(p));
            float fade = (float)Math.pow(1.0F - p, 1.3);
            Vfx.flatRing(Textures.LINE_CORE, x, y, z, radius, 0.032 * sz * thick, 128, core(this.color(c, p), 0.85F * fade * a));
         }

         float flash = (float)Math.exp(-(c.time - newest * every) * 5.0);
         Vfx.flatSprite(Textures.GLOW, x, y, z, 0.6 * sz * (1.0 + 0.35 * flash), 0.0, alpha(this.color(c, 0.0F), (0.25F + 0.5F * flash) * a));
         Vfx.flatRing(Textures.LINE_GLOW, x, y, z, 0.42 * sz, 0.16 * sz, 96, alpha(this.color(c, 0.0F), (0.2F + 0.3F * flash) * a));
         Vfx.flatRing(Textures.LINE_CORE, x, y, z, 0.42 * sz, 0.025 * sz, 96, core(this.color(c, 0.0F), (0.35F + 0.45F * flash) * a));
         RenderState.decal(false);
      }
   }
}
