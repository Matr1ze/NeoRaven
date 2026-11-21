package xyz.daikirai.render.furiah.module.landing.styles;

import xyz.daikirai.render.furiah.fx.FxFrame;
import xyz.daikirai.render.furiah.fx.Ground;
import xyz.daikirai.render.furiah.fx.Neon;
import xyz.daikirai.render.furiah.fx.Rng;
import xyz.daikirai.render.furiah.module.landing.LandingFx;
import xyz.daikirai.render.furiah.module.landing.LandingStyle;
import xyz.daikirai.render.furiah.module.landing.LandingStyleBase;
import xyz.daikirai.render.furiah.render.Easing;
import xyz.daikirai.render.furiah.render.RenderState;
import xyz.daikirai.render.furiah.render.Textures;
import xyz.daikirai.render.furiah.render.Vfx;
import xyz.daikirai.render.furiah.setting.BooleanSetting;
import xyz.daikirai.render.furiah.setting.ThemeTint;
import xyz.daikirai.render.furiah.setting.SliderSetting;

public final class LightPillarStyle extends LandingStyleBase {
   private static final int POINTS = 14;
   public final SliderSetting height;
   public final SliderSetting width;
   public final BooleanSetting sparks;
   private final Rng rng = new Rng();

   public LightPillarStyle() {
      super(LandingStyle.LIGHT_PILLAR, 0.8F, 1.2F, new ThemeTint());
      this.height = this.setting(new SliderSetting("pillar.height", "Height", "How tall the beam is.", 3.0, 6.0, 4.5, 0.1).unit(" blocks"));
      this.width = this.setting(new SliderSetting("pillar.width", "Width", "How wide the beam is.", 0.5, 2.0, 1.0, 0.05).unit("x"));
      this.sparks = this.setting(new BooleanSetting("pillar.sparks", "Sparks", "Sparks rising along the beam.", true));
   }

   @Override
   public void start(LandingFx fx) {
      if (this.sparks.get() && !fx.lod) {
         this.rng.seed(fx.seed);
         int n = fx.count(12.0F);

         for (int i = 0; i < n; i++) {
            double ang = this.rng.next() * (Math.PI * 2);
            double rad = this.rng.range(0.05F, 0.3F) * fx.size;
            fx.particles
               .spawn(
                  1,
                  fx.x() + Math.cos(ang) * rad,
                  fx.y() + this.rng.range(0.1F, 1.2F),
                  fx.z() + Math.sin(ang) * rad,
                  0.0F,
                  this.rng.range(2.2F, 5.0F),
                  0.0F,
                  fx.seconds(this.rng.range(0.3F, 0.5F)),
                  this.rng.range(0.04F, 0.07F)
               )
               .drag(1.2F)
               .color(this.color, this.rng.next())
               .alpha(fx.opacity)
               .hot(0.5F)
               .delay(fx.life * this.rng.range(0.02F, 0.5F));
         }
      }
   }

   @Override
   public void render(LandingFx fx, FxFrame f) {
      float p = fx.progress();
      float a = fx.alpha() * (1.0F - Easing.smoothstep(0.8F, 1.0F, p));
      if (!(a <= 0.003F)) {
         double h = this.height.get() * Math.sqrt(fx.size) * Easing.outCubic(Easing.clamp01(p / 0.14F));
         float thin = 1.0F - 0.92F * Easing.smoothstep(0.5F, 0.95F, p);
         double w = 0.55 * this.width.get() * Math.sqrt(fx.size) * thin * Easing.smoothstep(0.0F, 0.06F, p);
         double x = fx.x();
         double y = fx.y();
         double z = fx.z();
         RenderState.decal(true);
         float base = a * (1.0F - Easing.smoothstep(0.35F, 0.9F, p));
         RenderState.translucent(false);
         Ground.disc(f, fx.ground, Textures.GLOW, x, z, 0.9 * fx.size, 0.0, alpha(this.color.getColor(0.0F), 0.35F * base), 3, 20);
         RenderState.additive();
         Ground.disc(f, fx.ground, Textures.GLOW, x, z, 1.1 * fx.size, 0.0, alpha(this.color.getColor(0.0F), 0.4F * base), 3, 20);
         double ringR = (0.35 + 0.5 * Easing.outCubic(Easing.clamp01(p / 0.4F))) * fx.size;
         Neon.ring(f, fx.ground, x, z, ringR, 0.07 * fx.size, 64, this.color, 0, base, 0.0F);
         RenderState.decal(false);
         if (!(h <= 0.02) && !(w <= 0.002)) {
            for (int i = 0; i < 14; i++) {
               float s = i / 13.0F;
               double py = y + h * s;
               Vfx.X[i] = f.rx(x);
               Vfx.Y[i] = f.ry(py);
               Vfx.Z[i] = f.rz(z);
               float taper = 1.0F - 0.75F * (float)Math.pow(s, 1.4);
               float near = f.nearCameraFade(x, py, z, 0.45F, 1.3F);
               float top = 1.0F - Easing.smoothstep(0.75F, 1.0F, s);
               Vfx.W[i] = (float)(w * 1.1 * taper);
               Vfx.C[i] = alpha(this.color.getColor(s), a * top * near);
            }

            Neon.add(0, 14, false);
            Neon.flush(1.2F, 0.8F, 1.0F);
         }
      }
   }
}
