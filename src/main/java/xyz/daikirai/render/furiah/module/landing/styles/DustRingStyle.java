package xyz.daikirai.render.furiah.module.landing.styles;

import xyz.daikirai.render.furiah.fx.FxFrame;
import xyz.daikirai.render.furiah.fx.Ground;
import xyz.daikirai.render.furiah.fx.Rng;
import xyz.daikirai.render.furiah.module.landing.LandingFx;
import xyz.daikirai.render.furiah.module.landing.LandingStyle;
import xyz.daikirai.render.furiah.module.landing.LandingStyleBase;
import xyz.daikirai.render.furiah.render.ColorUtil;
import xyz.daikirai.render.furiah.render.Easing;
import xyz.daikirai.render.furiah.render.RenderState;
import xyz.daikirai.render.furiah.render.Textures;
import xyz.daikirai.render.furiah.setting.BooleanSetting;
import xyz.daikirai.render.furiah.setting.ThemeTint;
import xyz.daikirai.render.furiah.setting.SliderSetting;

public final class DustRingStyle extends LandingStyleBase {
   public final BooleanSetting blockColor;
   public final SliderSetting puffs;
   private final Rng rng = new Rng();

   public DustRingStyle() {
      super(LandingStyle.DUST_RING, 1.0F, 2.2F, new ThemeTint());
      this.blockColor = this.setting(
         new BooleanSetting("dust.blockColor", "Use Block Color", "Tints the dust with the color of the block you landed on.", false)
      );
      this.puffs = this.setting(new SliderSetting("dust.puffs", "Puffs", "How many puffs roll out.", 8.0, 28.0, 16.0, 1.0));
   }

   private int puffColor(LandingFx fx, float t) {
      return this.blockColor.get() ? ColorUtil.lerp(fx.land.blockColor, -1, 0.28F) : this.color.getColor(t);
   }

   @Override
   public void start(LandingFx fx) {
      this.rng.seed(fx.seed);
      int n = fx.count(this.puffs.getFloat() * (fx.lod ? 0.5F : 1.0F));
      float spread = (float)Math.sqrt(fx.size);

      for (int i = 0; i < n; i++) {
         double ang = (Math.PI * 2) * (i + this.rng.next() * 0.8) / n;
         double cx = Math.cos(ang);
         double cz = Math.sin(ang);
         float out = this.rng.range(1.7F, 2.8F) * spread;
         float s = this.rng.range(0.4F, 0.55F) * spread;
         double x = fx.x() + cx * 0.3;
         double z = fx.z() + cz * 0.3;
         fx.particles
            .spawn(
               8,
               x,
               fx.y() + 0.14 + this.rng.next() * 0.12,
               z,
               (float)cx * out,
               this.rng.range(0.15F, 0.45F),
               (float)cz * out,
               fx.seconds(this.rng.range(0.75F, 1.0F)),
               s
            )
            .end(s * 2.5F)
            .drag(2.8F)
            .gravity(-0.25F)
            .spin(this.rng.next() * 6.0F, this.rng.signed() * 0.8F)
            .alpha(0.68F * fx.opacity)
            .fade(0.05F, 0.35F);
         if (this.blockColor.get()) {
            fx.particles.color(this.puffColor(fx, 0.0F));
         } else {
            fx.particles.color(this.color, this.rng.next());
         }
      }
   }

   @Override
   public void render(LandingFx fx, FxFrame f) {
      float p = fx.progress();
      float a = fx.alpha() * 0.22F * (1.0F - Easing.smoothstep(0.3F, 0.9F, p));
      if (!(a <= 0.003F)) {
         double r = 0.4 + 1.5 * Math.sqrt(fx.size) * Easing.outCubic(Easing.clamp01(p / 0.7F));
         RenderState.translucent(false);
         RenderState.decal(true);
         Ground.band(f, fx.ground, Textures.WAVE, fx.x(), fx.z(), Math.max(0.0, r - 0.5), r, 0.0, Math.PI * 2, 64, null, this.puffColor(fx, 0.5F), a, 0.0F);
         RenderState.decal(false);
         RenderState.additive();
      }
   }
}
