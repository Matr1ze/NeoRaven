package xyz.daikirai.render.furiah.module.hit.styles;

import xyz.daikirai.render.furiah.fx.Basis;
import xyz.daikirai.render.furiah.fx.DepthPull;
import xyz.daikirai.render.furiah.fx.FxFrame;
import xyz.daikirai.render.furiah.module.hit.HitFx;
import xyz.daikirai.render.furiah.module.hit.HitStyle;
import xyz.daikirai.render.furiah.module.hit.HitStyleBase;
import xyz.daikirai.render.furiah.render.Camera;
import xyz.daikirai.render.furiah.render.Easing;
import xyz.daikirai.render.furiah.render.PoseSnapshot;
import xyz.daikirai.render.furiah.render.Silhouette;
import xyz.daikirai.render.furiah.render.Textures;
import xyz.daikirai.render.furiah.render.Vfx;
import xyz.daikirai.render.furiah.setting.BooleanSetting;
import xyz.daikirai.render.furiah.setting.ThemeTint;
import xyz.daikirai.render.furiah.setting.SliderSetting;

public final class SoulKnockStyle extends HitStyleBase {
   public final SliderSetting distance;
   public final BooleanSetting motes;
   private final Silhouette.Look look = new Silhouette.Look();
   private final double[] point = new double[3];

   public SoulKnockStyle() {
      super(HitStyle.SOUL_KNOCK, 0.5F, new ThemeTint());
      this.distance = this.setting(new SliderSetting("soul.distance", "Distance", "How far the ghost is knocked.", 0.4, 1.5, 0.8, 0.05).unit(" blocks"));
      this.motes = this.setting(new BooleanSetting("soul.motes", "Motes", "The ghost dissolves into small motes.", true));
   }

   @Override
   public void render(HitFx fx, FxFrame f) {
      PoseSnapshot pose = fx.hit.pose;
      if (pose.valid) {
         float p = fx.progress();
         Basis b = fx.basis;
         float slide = Easing.outCubic(Easing.clamp01(p / 0.75F));
         double d = this.distance.get() * fx.size * (fx.crit ? 1.25 : 1.0) * slide;
         float a = fx.alpha() * Easing.smoothstep(0.0F, 0.05F, p) * (1.0F - Easing.smoothstep(0.82F, 1.0F, p));
         if (!(a <= 0.003F)) {
            this.look.reset();
            this.look.color = this.color;
            this.look.alpha = a;
            this.look.fill = 0.8F;
            this.look.rim = 0.9F;
            this.look.inflate = 0.3F;
            this.look.rimWidth = 0.8F;
            Camera cam = Vfx.camera();
            double ex = f.rx(fx.ix()) - cam.posX;
            double ey = f.ry(fx.iy()) - cam.posY;
            double ez = f.rz(fx.iz()) - cam.posZ;
            this.look.pullDistance = Math.max(0.3, Math.sqrt(ex * ex + ey * ey + ez * ez) - 0.12);
            this.look.stretchX = b.fx;
            this.look.stretchY = b.fy;
            this.look.stretchZ = b.fz;
            this.look.stretch = 1.0F + 0.2F * (float)Math.sin(Math.PI * Easing.clamp01(p / 0.7F));
            this.look.shiftX = b.fx * d;
            this.look.shiftY = b.fy * d;
            this.look.shiftZ = b.fz * d;
            this.look.dissolve = Easing.smoothstep(0.42F, 1.0F, p) * 1.15F - 0.05F;
            if (this.look.dissolve < 0.0F) {
               this.look.dissolve = -1.0F;
            }

            Silhouette.draw(pose, f.viewX, f.viewY, f.viewZ, this.look);
            double gx = f.rx(pose.x + this.look.shiftX);
            double gy = f.ry(pose.y + 1.1 + this.look.shiftY);
            double gz = f.rz(pose.z + this.look.shiftZ);
            double k = DepthPull.factor(gx, gy, gz, this.look.pullDistance);
            gx = cam.posX + (gx - cam.posX) * k;
            gy = cam.posY + (gy - cam.posY) * k;
            gz = cam.posZ + (gz - cam.posZ) * k;
            Vfx.sprite(Textures.GLOW, gx, gy, gz, 2.2 * fx.size * k, 0.0F, alpha(this.color.getColor(0.6F), 0.2F * a));
            if (this.motes.get() && !fx.lod && this.look.dissolve > 0.0F && f.dt > 0.0F) {
               int spawn = Math.min(6, Math.round(f.dt * 90.0F * fx.density));

               for (int i = 0; i < spawn; i++) {
                  float r1 = (float)Math.random();
                  float r3;
                  float r2;
                  float h = Silhouette.randomPointHeight(r1, r2 = (float)Math.random(), r3 = (float)Math.random());
                  if (!(Math.abs(h - this.look.dissolve) > 0.18F)) {
                     Silhouette.randomPoint(r1, r2, r3, this.point);
                     fx.particles
                        .spawn(
                           3,
                           this.point[0],
                           this.point[1],
                           this.point[2],
                           (float)(b.fx * 0.4 + (Math.random() - 0.5) * 0.3),
                           0.5F + (float)Math.random() * 0.5F,
                           (float)(b.fz * 0.4 + (Math.random() - 0.5) * 0.3),
                           fx.seconds(0.35F + (float)Math.random() * 0.25F),
                           0.05F * fx.size
                        )
                        .end(0.01F)
                        .drag(1.5F)
                        .color(this.color, h)
                        .alpha(fx.opacity)
                        .flags(2)
                        .sway(0.0F, (float)Math.random());
                  }
               }
            }
         }
      }
   }
}
