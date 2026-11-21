package xyz.daikirai.render.furiah.module.landing.styles;

import xyz.daikirai.render.furiah.fx.FxFrame;
import xyz.daikirai.render.furiah.fx.Neon;
import xyz.daikirai.render.furiah.fx.Rng;
import xyz.daikirai.render.furiah.fx.WarpShell;
import xyz.daikirai.render.furiah.module.landing.LandingFx;
import xyz.daikirai.render.furiah.module.landing.LandingStyle;
import xyz.daikirai.render.furiah.module.landing.LandingStyleBase;
import xyz.daikirai.render.furiah.render.Easing;
import xyz.daikirai.render.furiah.render.RenderState;
import xyz.daikirai.render.furiah.render.Textures;
import xyz.daikirai.render.furiah.render.Vfx;
import xyz.daikirai.render.furiah.setting.ThemeTint;
import xyz.daikirai.render.furiah.setting.SliderSetting;

public final class WarpDomeStyle extends LandingStyleBase {
   private static final float POP = 0.78F;
   public final SliderSetting wobble;
   public final SliderSetting refraction;
   private final Rng rng = new Rng();

   public WarpDomeStyle() {
      super(LandingStyle.WARP_DOME, 0.7F, 1.6F, new ThemeTint());
      this.wobble = this.setting(new SliderSetting("warpdome.wobble", "Wobble", "How much it squashes and stretches.", 0.0, 2.0, 1.0, 0.05).unit("x"));
      this.refraction = this.setting(
         new SliderSetting("warpdome.refraction", "Refraction", "How strongly it bends what is behind it.", 0.0, 2.0, 1.0, 0.05).unit("x")
      );
   }

   private static double radius(LandingFx fx) {
      return 1.05 * fx.size;
   }

   @Override
   public void start(LandingFx fx) {
      if (!fx.lod) {
         this.rng.seed(fx.seed);
         double r = radius(fx);
         int n = fx.count(18.0F);
         float delay = fx.life * 0.78F;

         for (int i = 0; i < n; i++) {
            double ang = (Math.PI * 2) * (i + this.rng.next() * 0.7) / n;
            double cx = Math.cos(ang);
            double cz = Math.sin(ang);
            float out = this.rng.range(1.6F, 3.2F);
            double y0 = fx.y() + 0.1 + this.rng.next() * r * 0.5;
            fx.particles
               .spawn(
                  6,
                  fx.x() + cx * r * 0.95,
                  y0,
                  fx.z() + cz * r * 0.95,
                  (float)cx * out,
                  this.rng.range(0.6F, 2.0F),
                  (float)cz * out,
                  fx.seconds(this.rng.range(0.4F, 0.6F)),
                  this.rng.range(0.07F, 0.11F) * (float)Math.sqrt(fx.size)
               )
               .end(0.03F)
               .gravity(8.0F)
               .drag(1.2F)
               .color(this.color, this.rng.next())
               .alpha(fx.opacity)
               .fade(0.01F, 0.55F)
               .delay(delay)
               .ground(fx.y() + 0.03, 0, 0.3F);
         }
      }
   }

   @Override
   public void render(LandingFx fx, FxFrame f) {
      float p = fx.progress();
      float a = fx.alpha();
      double r = radius(fx);
      double t = fx.styleTime();
      if (p < 0.84F) {
         float inflate = Math.max(0.0F, Easing.outBack(Easing.clamp01(p / 0.3F), 2.4F));
         double jelly = 0.2 * this.wobble.get() * Math.sin(t * 30.0) * Math.exp(-t * 5.0) * Easing.smoothstep(0.12F, 0.32F, p);
         float popT = Easing.clamp01((p - 0.78F) / 0.06F);
         double swell = 1.0 + 0.1 * popT;
         float shellA = a * (1.0F - popT);
         WarpShell.draw(
            f,
            fx.x(),
            fx.y() + 0.01,
            fx.z(),
            r * inflate * swell,
            0.0,
            1.0,
            0.0,
            1.0 + jelly,
            1.0 - jelly * 0.5,
            0.0,
            1.0,
            0.0,
            0.0,
            1.0,
            true,
            this.refraction.getFloat(),
            this.color.getColor(0.5F),
            1.1F,
            shellA,
            !fx.lod
         );
         RenderState.decal(true);
         Neon.ring(f, fx.ground, fx.x(), fx.z(), r * inflate * swell, 0.06 * Math.sqrt(fx.size), 64, this.color, 0, 0.7F * shellA, 0.0F);
         RenderState.decal(false);
      }

      float flash;
      if ((flash = Easing.smoothstep(0.76F, 0.79999995F, p) * (1.0F - Easing.smoothstep(0.79999995F, 1.0F, p))) > 0.003F) {
         double y = fx.y() + r * 0.45;
         Vfx.sprite(Textures.GLOW, f.rx(fx.x()), f.ry(y), f.rz(fx.z()), r * 3.0, 0.0F, alpha(this.color.getColor(0.5F), 0.4F * flash * a));
         Vfx.sprite(Textures.FLARE, f.rx(fx.x()), f.ry(y), f.rz(fx.z()), r * 1.6, 0.4F, alpha(Neon.tint(this.color.getColor(0.5F)), 0.55F * flash * a));
      }
   }
}
