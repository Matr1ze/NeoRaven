package xyz.daikirai.render.furiah.module.landing.styles;

import xyz.daikirai.render.furiah.fx.FxFrame;
import xyz.daikirai.render.furiah.fx.Ground;
import xyz.daikirai.render.furiah.fx.Neon;
import xyz.daikirai.render.furiah.module.landing.LandingFx;
import xyz.daikirai.render.furiah.module.landing.LandingStyle;
import xyz.daikirai.render.furiah.module.landing.LandingStyleBase;
import xyz.daikirai.render.furiah.render.Easing;
import xyz.daikirai.render.furiah.render.RenderState;
import xyz.daikirai.render.furiah.render.Textures;
import xyz.daikirai.render.furiah.render.Vfx;
import xyz.daikirai.render.furiah.setting.ThemeTint;
import xyz.daikirai.render.furiah.setting.SliderSetting;

public final class SparkleBurstStyle extends LandingStyleBase {
   public final SliderSetting sparkles = this.setting(new SliderSetting("sparkle.count", "Sparkles", "How many sparkles shoot out.", 4.0, 16.0, 8.0, 1.0));

   public SparkleBurstStyle() {
      super(
         LandingStyle.SPARKLE_BURST, 0.9F, 2.2F, new ThemeTint()
      );
   }

   @Override
   public void render(LandingFx fx, FxFrame f) {
      float p = fx.progress();
      float a = fx.alpha();
      double cy = fx.y() + 0.2;
      float flash = 1.0F - Easing.smoothstep(0.0F, 0.35F, p);
      if (flash > 0.003F) {
         int c = this.color.getColor(0.5F);
         double x = f.rx(fx.x());
         double y = f.ry(cy);
         double z = f.rz(fx.z());
         RenderState.translucent(false);
         Vfx.sprite(Textures.SPARKLE, x, y, z, 0.9 * fx.size * (1.1F - 0.3F * flash), 0.0F, alpha(c, 0.8F * flash * a));
         RenderState.additive();
         Vfx.sprite(Textures.GLOW, x, y, z, 1.4 * fx.size, 0.0F, alpha(c, 0.5F * flash * a));
         Vfx.sprite(
            Textures.STREAK, x, y, z, 1.6 * fx.size * (1.3F - 0.3F * flash), 0.09 * fx.size, 0.0F, 0.0F, 0.0F, 1.0F, 1.0F, alpha(Neon.tint(c), flash * a)
         );
         Vfx.sprite(Textures.SPARKLE, x, y, z, 0.55 * fx.size * (1.1F - 0.3F * flash), 0.0F, alpha(-1, flash * a));
      }

      int n = Math.max(1, Math.round(this.sparkles.getFloat() * fx.density));
      float travel = Easing.outCubic(Easing.clamp01(p / 0.75F));
      float fade = a * Easing.smoothstep(0.0F, 0.04F, p) * (1.0F - Easing.smoothstep(0.55F, 1.0F, p));
      if (!(fade <= 0.003F)) {
         double reach = 1.9 * Math.sqrt(fx.size);
         RenderState.decal(true);
         int offset = 0;

         for (int i = 0; i < n; i++) {
            double ang = (Math.PI * 2) * (i + 0.4 * fx.srand(i)) / n;
            double d = reach * (0.75 + 0.35 * fx.rand(i + 30)) * travel;
            int c = this.color.getColor(fx.rand(i + 60));
            int count = Ground.linePoints(
               f,
               fx.ground,
               offset,
               fx.x() + Math.cos(ang) * d * 0.3,
               fx.z() + Math.sin(ang) * d * 0.3,
               fx.x() + Math.cos(ang) * d,
               fx.z() + Math.sin(ang) * d,
               0.06 * Math.sqrt(fx.size),
               c & 16777215,
               alpha(c, 0.55F * fade),
               4
            );
            Neon.addFlat(offset, count);
            offset += count;
         }

         Neon.flush();
         RenderState.decal(false);

         for (int pass = 0; pass < 3; pass++) {
            if (pass == 0) {
               RenderState.translucent(false);
            } else if (pass == 1) {
               RenderState.additive();
            }

            for (int i = 0; i < n; i++) {
               double ang = (Math.PI * 2) * (i + 0.4 * fx.srand(i)) / n;
               double d = reach * (0.75 + 0.35 * fx.rand(i + 30)) * travel;
               double x = fx.x() + Math.cos(ang) * d;
               double z = fx.z() + Math.sin(ang) * d;
               double y = Ground.y(fx.ground, x, z) + 0.2 + 0.14 * Math.sin(Math.PI * travel);
               float twinkle = 0.65F + 0.35F * (float)Math.sin(fx.age * 26.0F + i * 2.1F);
               int c = this.color.getColor(fx.rand(i + 60));
               float s = (float)(0.55 * Math.sqrt(fx.size) * twinkle);
               float spin = (float)(fx.age * 1.5 + i);
               if (pass == 0) {
                  Vfx.sprite(Textures.SPARKLE, f.rx(x), f.ry(y), f.rz(z), s, spin, alpha(c, 0.9F * fade));
               } else if (pass == 1) {
                  Vfx.sprite(Textures.GLOW, f.rx(x), f.ry(y), f.rz(z), s * 2.0F, 0.0F, alpha(c, 0.35F * fade));
               } else {
                  Vfx.sprite(Textures.SPARKLE, f.rx(x), f.ry(y), f.rz(z), s * 0.55F, spin, alpha(Neon.tint(c), fade * twinkle));
               }
            }
         }
      }
   }
}
