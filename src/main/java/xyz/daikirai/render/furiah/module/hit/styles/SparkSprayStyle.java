package xyz.daikirai.render.furiah.module.hit.styles;

import xyz.daikirai.render.furiah.fx.Basis;
import xyz.daikirai.render.furiah.fx.FxFrame;
import xyz.daikirai.render.furiah.fx.Neon;
import xyz.daikirai.render.furiah.fx.Rng;
import xyz.daikirai.render.furiah.module.hit.HitFx;
import xyz.daikirai.render.furiah.module.hit.HitStyle;
import xyz.daikirai.render.furiah.module.hit.HitStyleBase;
import xyz.daikirai.render.furiah.render.Easing;
import xyz.daikirai.render.furiah.render.RenderState;
import xyz.daikirai.render.furiah.render.Textures;
import xyz.daikirai.render.furiah.render.Vfx;
import xyz.daikirai.render.furiah.setting.BooleanSetting;
import xyz.daikirai.render.furiah.setting.ThemeTint;
import xyz.daikirai.render.furiah.setting.SliderSetting;

public final class SparkSprayStyle extends HitStyleBase {
   public final SliderSetting count;
   public final BooleanSetting bounce;
   private final Rng rng = new Rng();

   public SparkSprayStyle() {
      super(HitStyle.SPARK_SPRAY, 0.5F, new ThemeTint());
      this.count = this.setting(new SliderSetting("sparks.count", "Sparks", "How many sparks fly.", 10.0, 60.0, 30.0, 1.0));
      this.bounce = this.setting(new BooleanSetting("sparks.bounce", "Bounce", "Sparks bounce once off the ground.", true));
   }

   @Override
   public void start(HitFx fx) {
      Basis b = fx.basis;
      this.rng.seed(fx.seed);
      int n = fx.count(this.count.getFloat() * (fx.lod ? 0.4F : 1.0F), true);
      float hot = fx.crit ? 1.0F : 0.3F;

      for (int i = 0; i < n; i++) {
         double theta = Math.toRadians(50.0) * Math.sqrt(0.15 + 0.85 * this.rng.next());
         double cosT = Math.cos(theta);
         double sinT = Math.sin(theta);
         double phi = this.rng.next() * Math.PI * 2.0;
         double dx = b.fx * cosT + (b.rx * Math.cos(phi) + b.ux * Math.sin(phi)) * sinT;
         double dy = b.fy * cosT + (b.ry * Math.cos(phi) + b.uy * Math.sin(phi)) * sinT;
         double dz = b.fz * cosT + (b.rz * Math.cos(phi) + b.uz * Math.sin(phi)) * sinT;
         float speed = this.rng.range(4.5F, 10.5F) * (float)Math.sqrt(fx.size);
         fx.particles
            .spawn(
               2,
               fx.ix(),
               fx.iy(),
               fx.iz(),
               (float)dx * speed,
               (float)dy * speed + 1.0F,
               (float)dz * speed,
               fx.seconds(this.rng.range(0.3F, 0.5F)),
               this.rng.range(0.016F, 0.028F) * fx.size
            )
            .gravity(15.0F)
            .drag(1.4F)
            .color(this.color, this.rng.next())
            .hot(hot)
            .alpha(fx.opacity)
            .fade(0.0F, 0.6F)
            .follow(fx.anchor, 0.5F)
            .ground(fx.hit.groundY, this.bounce.get() ? 1 : 0, 0.35F);
      }
   }

   @Override
   public void render(HitFx fx, FxFrame f) {
      float p = fx.progress();
      float flash = (float)Math.pow(1.0F - Easing.smoothstep(0.0F, 0.4F, p), 2.0);
      if (!(flash <= 0.003F)) {
         float a = fx.alpha() * flash;
         double x = f.rx(fx.ix() - fx.basis.fx * 0.1);
         double y = f.ry(fx.iy() - fx.basis.fy * 0.1);
         double z = f.rz(fx.iz() - fx.basis.fz * 0.1);
         int c = this.color.getColor(0.5F);
         float s = fx.size * (fx.crit ? 1.45F : 1.0F);
         RenderState.translucent(false);
         Vfx.sprite(Textures.FLARE, x, y, z, 0.8 * s, 0.15F + p * 0.8F, alpha(c, 0.8F * a));
         RenderState.additive();
         Vfx.sprite(Textures.GLOW, x, y, z, 0.9 * s, 0.0F, alpha(c, 0.45F * a));
         Vfx.sprite(Textures.FLARE, x, y, z, 0.55 * s, 0.15F + p * 0.8F, alpha(Neon.tint(c), a));
         if (fx.crit) {
            Vfx.sprite(Textures.SPARK, x, y, z, 0.3 * s, 0.0F, alpha(-1, a));
         }
      }
   }
}
