package xyz.daikirai.render.furiah.module.landing.styles;

import xyz.daikirai.render.furiah.fx.FxFrame;
import xyz.daikirai.render.furiah.fx.Ground;
import xyz.daikirai.render.furiah.fx.Neon;
import xyz.daikirai.render.furiah.fx.Rng;
import xyz.daikirai.render.furiah.module.landing.LandingFx;
import xyz.daikirai.render.furiah.module.landing.LandingStyle;
import xyz.daikirai.render.furiah.module.landing.LandingStyleBase;
import xyz.daikirai.render.furiah.render.Distortion;
import xyz.daikirai.render.furiah.render.Easing;
import xyz.daikirai.render.furiah.render.RenderState;
import xyz.daikirai.render.furiah.render.Textures;
import xyz.daikirai.render.furiah.setting.ThemeTint;
import xyz.daikirai.render.furiah.setting.SliderSetting;

public final class LiquidRippleStyle extends LandingStyleBase {
   private static final int SEGMENTS = 72;
   public final SliderSetting ripples;
   public final SliderSetting refraction;
   private final Rng rng = new Rng();

   public LiquidRippleStyle() {
      super(LandingStyle.LIQUID_RIPPLE, 1.2F, 2.8F, new ThemeTint());
      this.ripples = this.setting(new SliderSetting("ripple.ripples", "Ripples", "How many ripples spread out.", 3.0, 4.0, 3.0, 1.0));
      this.refraction = this.setting(
         new SliderSetting("ripple.refraction", "Refraction", "How much the ground warps under the crests.", 0.0, 2.0, 1.0, 0.05).unit("x")
      );
   }

   @Override
   public void start(LandingFx fx) {
      if (!fx.lod) {
         this.rng.seed(fx.seed);
         int n = fx.count(8.0F);

         for (int i = 0; i < n; i++) {
            double ang = this.rng.next() * (Math.PI * 2);
            float out = this.rng.range(0.3F, 0.9F);
            fx.particles
               .spawn(
                  6,
                  fx.x() + Math.cos(ang) * 0.1,
                  fx.y() + 0.05,
                  fx.z() + Math.sin(ang) * 0.1,
                  (float)Math.cos(ang) * out,
                  this.rng.range(2.4F, 4.0F),
                  (float)Math.sin(ang) * out,
                  fx.seconds(this.rng.range(0.45F, 0.65F)),
                  this.rng.range(0.06F, 0.09F)
               )
               .gravity(10.0F)
               .color(this.color, this.rng.next())
               .alpha(fx.opacity)
               .fade(0.02F, 0.6F)
               .delay(fx.life * this.rng.range(0.0F, 0.2F))
               .ground(fx.y() + 0.03, 0, 0.3F);
         }
      }
   }

   @Override
   public void render(LandingFx fx, FxFrame f) {
      float p = fx.progress();
      int n = this.ripples.getInt();
      double maxR = 2.4 * Math.sqrt(fx.size);

      for (int k = 0; k < n; k++) {
         float q = (p - k * 0.12F) / 0.78F;
         if (!(q <= 0.0F) && !(q >= 1.0F)) {
            double r = 0.15 + maxR * (1.0 - Math.pow(1.0 - q, 1.7));
            float amp = (float)Math.pow(1.0F - q, 1.4) * Easing.smoothstep(0.0F, 0.06F, q) * (1.0F - 0.15F * k);
            float a = fx.alpha() * amp;
            if (!(a <= 0.003F)) {
               if (!fx.lod) {
                  this.warp(fx, f, r, 0.28 * Math.sqrt(fx.size), a);
               }

               RenderState.decal(true);
               RenderState.translucent(false);
               Ground.band(
                  f,
                  fx.ground,
                  Textures.WAVE,
                  fx.x(),
                  fx.z(),
                  Math.max(0.0, r - 0.34 * Math.sqrt(fx.size)),
                  r,
                  0.0,
                  Math.PI * 2,
                  72,
                  this.color,
                  0,
                  0.45F * a,
                  k * 0.2F
               );
               RenderState.additive();
               Ground.band(
                  f,
                  fx.ground,
                  Textures.WAVE,
                  fx.x(),
                  fx.z(),
                  Math.max(0.0, r - 0.34 * Math.sqrt(fx.size)),
                  r,
                  0.0,
                  Math.PI * 2,
                  72,
                  this.color,
                  0,
                  0.3F * a,
                  k * 0.2F
               );
               Neon.ring(f, fx.ground, fx.x(), fx.z(), r - 0.02, 0.05 * Math.sqrt(fx.size), 72, this.color, 0, 0.8F * a, k * 0.2F);
               RenderState.decal(false);
            }
         }
      }
   }

   private void warp(LandingFx fx, FxFrame f, double r, double width, float a) {
      float strength = this.refraction.getFloat();
      if (!(strength <= 0.01F)) {
         Distortion.begin(f.frameId, 0.12F * strength, this.color.getColor(0.5F), 0.25F, Math.min(1.0F, a * 1.2F), true);
         double rIn = Math.max(0.0, r - width);
         double rOut = r + width * 0.3;

         for (int i = 0; i < 72; i++) {
            double a0 = (Math.PI * 2) * i / 72.0;
            double a1 = (Math.PI * 2) * (i + 1) / 72.0;
            vertex(fx, f, rIn, a0, -0.55);
            vertex(fx, f, rOut, a0, 0.7);
            vertex(fx, f, rOut, a1, 0.7);
            vertex(fx, f, rIn, a1, -0.55);
         }

         Distortion.end();
         RenderState.additive();
      }
   }

   private static void vertex(LandingFx fx, FxFrame f, double r, double ang, double tilt) {
      double c = Math.cos(ang);
      double s = Math.sin(ang);
      double x = fx.x() + c * r;
      double z = fx.z() + s * r;
      double y = Ground.y(fx.ground, x, z) + 0.004;
      double nx = c * tilt;
      double ny = 1.0;
      double nz = s * tilt;
      double l = Math.sqrt(nx * nx + ny * ny + nz * nz);
      Distortion.vertex(f.rx(x), f.ry(y), f.rz(z), nx / l, ny / l, nz / l);
   }
}
