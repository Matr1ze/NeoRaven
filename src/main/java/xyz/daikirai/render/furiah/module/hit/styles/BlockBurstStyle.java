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

public final class BlockBurstStyle extends HitStyleBase {
   public final SliderSetting count;
   public final SliderSetting cubeSize;
   public final BooleanSetting bounce;
   private final Rng rng = new Rng();

   public BlockBurstStyle() {
      super(HitStyle.BLOCK_BURST, 0.7F, new ThemeTint());
      this.count = this.setting(new SliderSetting("blockburst.count", "Cubes", "How many cubes burst out.", 8.0, 32.0, 16.0, 1.0));
      this.cubeSize = this.setting(new SliderSetting("blockburst.cubeSize", "Cube Size", "Size of the cubes.", 0.5, 2.0, 1.0, 0.05).unit("x"));
      this.bounce = this.setting(new BooleanSetting("blockburst.bounce", "Bounce", "Cubes bounce once on the ground.", true));
   }

   @Override
   public void start(HitFx fx) {
      Basis b = fx.basis;
      this.rng.seed(fx.seed);
      int n = fx.count(this.count.getFloat() * (fx.lod ? 0.5F : 1.0F), true);
      float big = fx.crit ? 1.35F : 1.0F;

      for (int i = 0; i < n; i++) {
         double theta = Math.toRadians(this.rng.range(18.0F, 70.0F));
         double cosT = Math.cos(theta);
         double sinT = Math.sin(theta);
         double phi = this.rng.next() * Math.PI * 2.0;
         double dx = b.fx * cosT + (b.rx * Math.cos(phi) + b.ux * Math.sin(phi)) * sinT;
         double dy = b.fy * cosT + (b.ry * Math.cos(phi) + b.uy * Math.sin(phi)) * sinT;
         double dz = b.fz * cosT + (b.rz * Math.cos(phi) + b.uz * Math.sin(phi)) * sinT;
         float speed = this.rng.range(3.0F, 6.0F) * (float)Math.sqrt(fx.size) * (fx.crit ? 1.2F : 1.0F);
         float s = this.rng.range(0.11F, 0.17F) * fx.size * this.cubeSize.getFloat() * big;
         double ox = fx.ix() - b.fx * 0.12;
         double oy = fx.iy() - b.fy * 0.12;
         double oz = fx.iz() - b.fz * 0.12;
         fx.particles
            .spawn(10, ox, oy, oz, (float)dx * speed, (float)dy * speed + 1.2F, (float)dz * speed, fx.seconds(this.rng.range(0.55F, 0.75F)), s)
            .end(s * 0.25F)
            .gravity(8.5F)
            .drag(2.3F)
            .axis(this.rng.signed(), this.rng.signed(), this.rng.signed())
            .spin(this.rng.next() * 6.0F, this.rng.range(5.0F, 11.0F) * (this.rng.next() < 0.5F ? -1 : 1))
            .color(this.color, this.rng.next())
            .alpha(fx.opacity)
            .fade(0.02F, 0.55F)
            .follow(fx.anchor, 0.5F)
            .ground(fx.hit.groundY, this.bounce.get() ? 1 : 0, 0.45F);
      }

      if (!fx.lod) {
         for (int var28 = 0; var28 < n / 2; var28++) {
            double phi = this.rng.next() * Math.PI * 2.0;
            double spread = this.rng.next() * 0.9;
            double dx = b.fx + (b.rx * Math.cos(phi) + b.ux * Math.sin(phi)) * spread;
            double dy = b.fy + (b.ry * Math.cos(phi) + b.uy * Math.sin(phi)) * spread;
            double dz = b.fz + (b.rz * Math.cos(phi) + b.uz * Math.sin(phi)) * spread;
            float speed = this.rng.range(2.0F, 5.0F);
            fx.particles
               .spawn(
                  1,
                  fx.ix(),
                  fx.iy(),
                  fx.iz(),
                  (float)dx * speed,
                  (float)dy * speed,
                  (float)dz * speed,
                  fx.seconds(this.rng.range(0.25F, 0.45F)),
                  this.rng.range(0.03F, 0.05F) * fx.size
               )
               .gravity(4.0F)
               .drag(3.0F)
               .color(this.color, this.rng.next())
               .alpha(fx.opacity)
               .follow(fx.anchor, 0.5F);
         }
      }
   }

   @Override
   public void render(HitFx fx, FxFrame f) {
      float p = fx.progress();
      float flash = 1.0F - Easing.smoothstep(0.0F, 0.22F, p);
      if (!(flash <= 0.003F)) {
         float a = fx.alpha() * flash;
         double x = f.rx(fx.ix() - fx.basis.fx * 0.1);
         double y = f.ry(fx.iy() - fx.basis.fy * 0.1);
         double z = f.rz(fx.iz() - fx.basis.fz * 0.1);
         int c = this.color.getColor(0.5F);
         RenderState.translucent(false);
         Vfx.sprite(Textures.SPARK, x, y, z, 0.5 * fx.size * (1.0F + 0.5F * (1.0F - flash)), 0.0F, alpha(c, 0.8F * a));
         RenderState.additive();
         Vfx.sprite(Textures.GLOW, x, y, z, 1.1 * fx.size * (fx.crit ? 1.4F : 1.0F), 0.0F, alpha(c, 0.45F * a));
         Vfx.sprite(Textures.SPARK, x, y, z, 0.3 * fx.size * (1.0F + 0.5F * (1.0F - flash)), 0.0F, alpha(Neon.tint(c), a));
      }
   }
}
