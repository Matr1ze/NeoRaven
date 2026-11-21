package xyz.daikirai.render.furiah.module.hit.styles;

import xyz.daikirai.render.furiah.fx.Basis;
import xyz.daikirai.render.furiah.fx.FxFrame;
import xyz.daikirai.render.furiah.fx.Rng;
import xyz.daikirai.render.furiah.fx.WarpShell;
import xyz.daikirai.render.furiah.module.hit.HitFx;
import xyz.daikirai.render.furiah.module.hit.HitStyle;
import xyz.daikirai.render.furiah.module.hit.HitStyleBase;
import xyz.daikirai.render.furiah.render.Camera;
import xyz.daikirai.render.furiah.render.Easing;
import xyz.daikirai.render.furiah.render.Textures;
import xyz.daikirai.render.furiah.render.Vfx;
import xyz.daikirai.render.furiah.setting.ThemeTint;
import xyz.daikirai.render.furiah.setting.SliderSetting;

public final class WarpBubbleStyle extends HitStyleBase {
   private static final float POP = 0.8F;
   public final SliderSetting wobble;
   public final SliderSetting refraction;
   private final Rng rng = new Rng();

   public WarpBubbleStyle() {
      super(HitStyle.WARP_BUBBLE, 0.6F, new ThemeTint());
      this.wobble = this.setting(new SliderSetting("warpbubble.wobble", "Wobble", "How much it squashes and stretches.", 0.0, 2.0, 1.0, 0.05).unit("x"));
      this.refraction = this.setting(
         new SliderSetting("warpbubble.refraction", "Refraction", "How strongly it bends what is behind it.", 0.0, 2.0, 1.0, 0.05).unit("x")
      );
   }

   private static double radius(HitFx fx) {
      return 0.62 * fx.size * (fx.crit ? 1.2 : 1.0);
   }

   @Override
   public void start(HitFx fx) {
      if (!fx.lod) {
         Basis b = fx.basis;
         this.rng.seed(fx.seed);
         double r = radius(fx);
         float delay = fx.life * 0.8F;
         int n = fx.count(14.0F, true);

         for (int i = 0; i < n; i++) {
            double phi = (Math.PI * 2) * (i + this.rng.next() * 0.6) / n;
            double ox = b.rx * Math.cos(phi) + b.ux * Math.sin(phi);
            double oy = b.ry * Math.cos(phi) + b.uy * Math.sin(phi);
            double oz = b.rz * Math.cos(phi) + b.uz * Math.sin(phi);
            double x = centerX(fx) + ox * r;
            double y = centerY(fx) + oy * r;
            double z = centerZ(fx) + oz * r;
            float out = this.rng.range(1.2F, 2.4F);
            float away = this.rng.range(1.2F, 2.6F);
            fx.particles
               .spawn(
                  6,
                  x,
                  y,
                  z,
                  (float)(ox * out + b.fx * away),
                  (float)(oy * out + b.fy * away) + 0.8F,
                  (float)(oz * out + b.fz * away),
                  fx.seconds(this.rng.range(0.35F, 0.55F)),
                  this.rng.range(0.07F, 0.11F) * fx.size
               )
               .end(0.03F * fx.size)
               .gravity(7.0F)
               .drag(1.5F)
               .color(this.color, this.rng.next())
               .alpha(fx.opacity)
               .fade(0.01F, 0.5F)
               .delay(delay)
               .follow(fx.anchor, 0.5F)
               .ground(fx.hit.groundY, 0, 0.3F);
         }

         for (int var25 = 0; var25 < n / 2; var25++) {
            double phi = this.rng.next() * Math.PI * 2.0;
            double lift = this.rng.signed() * 0.5;
            double ox = b.rx * Math.cos(phi) + b.ux * Math.sin(phi);
            double oy = b.ry * Math.cos(phi) + b.uy * Math.sin(phi) + lift;
            double oz = b.rz * Math.cos(phi) + b.uz * Math.sin(phi);
            fx.particles
               .spawn(
                  5,
                  centerX(fx) + ox * r * 0.8,
                  centerY(fx) + oy * r * 0.8,
                  centerZ(fx) + oz * r * 0.8,
                  (float)ox * 1.2F + (float)b.fx,
                  (float)oy * 1.2F + 0.3F,
                  (float)oz * 1.2F + (float)b.fz,
                  fx.seconds(this.rng.range(0.25F, 0.4F)),
                  this.rng.range(0.12F, 0.2F) * fx.size
               )
               .end(0.02F)
               .drag(3.0F)
               .spin(this.rng.next() * 3.0F, this.rng.signed() * 4.0F)
               .color(this.color, this.rng.next())
               .alpha(fx.opacity)
               .delay(delay)
               .follow(fx.anchor, 0.5F);
         }
      }
   }

   private static double centerX(HitFx fx) {
      return fx.cx() + (fx.ix() - fx.cx()) * 0.25;
   }

   private static double centerY(HitFx fx) {
      double torso = fx.anchor.feetY() + fx.hit.height * 0.6;
      return torso + (fx.iy() - torso) * 0.25;
   }

   private static double centerZ(HitFx fx) {
      return fx.cz() + (fx.iz() - fx.cz()) * 0.25;
   }

   @Override
   public void render(HitFx fx, FxFrame f) {
      float p = fx.progress();
      float a = fx.alpha();
      Basis b = fx.basis;
      double cx = centerX(fx);
      double cy = centerY(fx);
      double cz = centerZ(fx);
      double r = radius(fx);
      double t = fx.styleTime();
      if (p < 0.86F) {
         float inflate = Math.max(0.0F, Easing.outBack(Easing.clamp01(p / 0.28F), 2.6F));
         float w = this.wobble.getFloat();
         double jelly = 0.2 * w * Math.sin(t * 34.0) * Math.exp(-t * 5.5) * Easing.smoothstep(0.1F, 0.3F, p);
         double along = 1.0 + jelly;
         double across = 1.0 - jelly * 0.5;
         float popT = Easing.clamp01((p - 0.8F) / 0.06F);
         double swell = 1.0 + 0.12 * popT;
         float shellA = a * (1.0F - popT);
         double dx = -b.fx;
         double dy = -b.fy;
         double dz = -b.fz;
         double dent = 0.32 * Math.exp(-t * 16.0) * Easing.smoothstep(0.0F, 0.05F, p);
         WarpShell.draw(
            f,
            cx,
            cy,
            cz,
            r * inflate * swell,
            b.fx,
            b.fy,
            b.fz,
            along,
            across,
            dx,
            dy,
            dz,
            dent,
            7.0,
            false,
            this.refraction.getFloat(),
            this.color.getColor(0.5F),
            1.1F,
            shellA,
            !fx.lod
         );
         if (inflate > 0.2F && shellA > 0.01F) {
            Camera cam = Vfx.camera();
            double vx = cam.posX - f.rx(cx);
            double vz;
            double vy;
            double vl = Math.sqrt(vx * vx + (vy = cam.posY - f.ry(cy)) * vy + (vz = cam.posZ - f.rz(cz)) * vz);
            if (vl > 1.0E-6) {
               double k = r * inflate * 0.62 / vl;
               double hx = f.rx(cx) + vx * k - cam.rightX * r * 0.3 + cam.upX * r * 0.35;
               double hy = f.ry(cy) + vy * k - cam.rightY * r * 0.3 + cam.upY * r * 0.35;
               double hz = f.rz(cz) + vz * k - cam.rightZ * r * 0.3 + cam.upZ * r * 0.35;
               Vfx.sprite(Textures.GLOW, hx, hy, hz, r * 0.35, 0.0F, alpha(-1, 0.35F * shellA));
               Vfx.sprite(Textures.SPARK, hx, hy, hz, r * 0.12, 0.0F, alpha(-1, 0.6F * shellA));
            }
         }
      }

      float flash;
      if ((flash = Easing.smoothstep(0.78000003F, 0.82F, p) * (1.0F - Easing.smoothstep(0.82F, 1.0F, p))) > 0.003F) {
         Vfx.sprite(Textures.GLOW, f.rx(cx), f.ry(cy), f.rz(cz), r * 3.2, 0.0F, alpha(this.color.getColor(0.5F), 0.45F * flash * a));
         Vfx.sprite(Textures.FLARE, f.rx(cx), f.ry(cy), f.rz(cz), r * 1.6, 0.4F, core(this.color.getColor(0.5F), 0.6F * flash * a));
      }
   }
}
