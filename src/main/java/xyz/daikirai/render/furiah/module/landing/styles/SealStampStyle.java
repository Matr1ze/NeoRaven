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

public final class SealStampStyle extends LandingStyleBase {
   private static final int SEGMENTS = 96;
   private static final int TICKS = 36;
   public final SliderSetting spin = this.setting(new SliderSetting("seal.spin", "Spin", "How fast the seal turns.", 0.0, 2.0, 1.0, 0.05).unit("x"));

   public SealStampStyle() {
      super(LandingStyle.SEAL_STAMP, 1.2F, 2.6F, new ThemeTint());
   }

   @Override
   public void render(LandingFx fx, FxFrame f) {
      float p = fx.progress();
      float a = fx.alpha();
      float slam = Easing.outCubic(Easing.clamp01(p / 0.1F));
      double scale = (1.4 - 0.4 * slam) * fx.size;
      float appear = Easing.smoothstep(0.0F, 0.05F, p);
      double rot = fx.styleTime() * 0.9 * this.spin.get() + Math.toRadians(fx.land.yaw);
      double r = 1.15 * scale;
      double w = 0.075 * Math.sqrt(fx.size);
      RenderState.decal(true);
      float flash = Easing.smoothstep(0.06F, 0.1F, p) * (1.0F - Easing.smoothstep(0.1F, 0.35F, p));
      if (flash > 0.003F) {
         RenderState.translucent(false);
         Ground.disc(f, fx.ground, Textures.GLOW, fx.x(), fx.z(), r * 1.4, 0.0, alpha(this.color.getColor(0.5F), 0.35F * flash * a), 3, 24);
         RenderState.additive();
         Ground.disc(f, fx.ground, Textures.GLOW, fx.x(), fx.z(), r * 1.7, 0.0, alpha(this.color.getColor(0.5F), 0.45F * flash * a), 3, 24);
         double shock = r * (1.0 + 0.8 * Easing.outCubic(Easing.clamp01((p - 0.1F) / 0.25F)));
         Ground.ring(f, fx.ground, Textures.WAVE, fx.x(), fx.z(), shock, 0.35 * fx.size, 96, this.color, 0, 0.6F * flash * a, 0.0F);
      }

      float inner;
      if ((inner = a * appear * (1.0F - Easing.smoothstep(0.62F, 0.95F, p))) > 0.003F) {
         RenderState.translucent(false);
         Ground.disc(f, fx.ground, Textures.RADIAL_FILL, fx.x(), fx.z(), r * 0.84, rot, alpha(this.color.getColor(0.5F), 0.22F * inner), 3, 32);
         RenderState.additive();
      }

      int offset = 0;
      double[] radii = new double[]{r, r * 0.8, r * 0.52};
      float[] detachAt = new float[]{0.46F, 0.56F, 0.66F};

      for (int k = 0; k < 3; k++) {
         float d = Easing.clamp01((p - detachAt[k]) / 0.3F);
         double rr = radii[k] * (1.0 + 0.9 * Easing.outCubic(d));
         float ra = a * appear * (1.0F - d);
         if (!(ra <= 0.003F)) {
            float tShift = (float)(rot / (Math.PI * 2)) * (k % 2 == 0 ? 1 : -1);
            int count = Ground.ringPoints(f, fx.ground, offset, fx.x(), fx.z(), rr, w * (k == 0 ? 1.0 : 0.8), 96, this.color, 0, ra, tShift);
            Neon.addFlat(offset, count);
            offset += count;
         }
      }

      if (inner > 0.003F) {
         for (int i = 0; i < 36; i++) {
            double ang = rot + (Math.PI * 2) * i / 36.0;
            double len = (i % 3 == 0 ? 0.15 : 0.08) * scale;
            double r0 = r * 0.84;
            double r1 = r0 + len;
            int c = alpha(this.color.getColor(loop(i / 36.0F)), inner);
            int count = Ground.linePoints(
               f,
               fx.ground,
               offset,
               fx.x() + Math.cos(ang) * r0,
               fx.z() + Math.sin(ang) * r0,
               fx.x() + Math.cos(ang) * r1,
               fx.z() + Math.sin(ang) * r1,
               w * 0.6,
               c,
               c,
               1
            );
            Neon.addFlat(offset, count);
            offset += count;
         }

         double sr = r * 0.52;

         for (int tri = 0; tri < 2; tri++) {
            for (int kx = 0; kx < 3; kx++) {
               double a0 = -rot * 0.6 + tri * Math.PI / 3.0 + kx * (Math.PI * 2) / 3.0;
               double a1 = a0 + (Math.PI * 2.0 / 3.0);
               int c0 = alpha(this.color.getColor(loop(kx / 3.0F + tri * 0.5F)), inner);
               int c1 = alpha(this.color.getColor(loop((kx + 1) / 3.0F + tri * 0.5F)), inner);
               int count = Ground.linePoints(
                  f,
                  fx.ground,
                  offset,
                  fx.x() + Math.cos(a0) * sr,
                  fx.z() + Math.sin(a0) * sr,
                  fx.x() + Math.cos(a1) * sr,
                  fx.z() + Math.sin(a1) * sr,
                  w * 0.7,
                  c0,
                  c1,
                  6
               );
               Neon.addFlat(offset, count);
               offset += count;
            }
         }

         int count2 = Ground.ringPoints(f, fx.ground, offset, fx.x(), fx.z(), r * 0.2, w * 0.6, 40, this.color, 0, inner, 0.0F);
         Neon.addFlat(offset, count2);
      }

      Neon.flush();
      RenderState.decal(false);
      if (flash > 0.003F) {
         Vfx.sprite(
            Textures.FLARE, f.rx(fx.x()), f.ry(fx.y() + 0.25), f.rz(fx.z()), 1.4 * fx.size, 0.2F, alpha(Neon.tint(this.color.getColor(0.5F)), flash * a)
         );
      }
   }
}
