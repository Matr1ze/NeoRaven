package xyz.daikirai.render.furiah.module.targetaura.effects;

import xyz.daikirai.render.furiah.module.targetaura.AuraEffect;
import xyz.daikirai.render.furiah.module.targetaura.EffectContext;
import xyz.daikirai.render.furiah.module.targetaura.EffectType;
import xyz.daikirai.render.furiah.render.Camera;
import xyz.daikirai.render.furiah.render.ColorUtil;
import xyz.daikirai.render.furiah.render.Easing;
import xyz.daikirai.render.furiah.render.Textures;
import xyz.daikirai.render.furiah.render.Vfx;
import xyz.daikirai.render.furiah.setting.BooleanSetting;
import xyz.daikirai.render.furiah.setting.ThemeTint;

public final class LockOnEffect extends AuraEffect {
   private static final int DASHES = 20;
   private static final int DASH_POINTS = 5;
   public final BooleanSetting ring = this.setting(new BooleanSetting("lockon.ring", "Dashed Ring", "Rotating dashed ring behind the brackets.", true));

   public LockOnEffect() {
      super(EffectType.LOCK_ON, "lockon", new ThemeTint());
   }

   @Override
   public void render(EffectContext c) {
      float sz = this.size(c);
      float inten = this.intensity(c);
      float spd = this.speed();
      Camera cam = c.cam;
      float snapT = Easing.clamp01(c.time / 0.32);
      float snap = 1.0F + 1.3F * (1.0F - Easing.outExpo(snapT));
      float appear = Easing.outCubic(Easing.clamp01(c.time / 0.18));
      float breathe = 1.0F + 0.025F * (float)Math.sin(c.time * 3.5 * spd);
      float s = snap * breathe;
      float a = inten * appear;
      double top = c.y + c.height + 0.2 * sz;
      double bottom = c.y + 0.06;
      double cx = c.x;
      double cy = (top + bottom) * 0.5;
      double cz = c.z;
      double hw = (c.width * 0.5 + 0.28) * sz * s;
      double hh = (top - bottom) * 0.5 * s;
      double hhDown = Math.min(hh, (cy - c.groundY - 0.05) / Math.max(0.25, cam.upY));
      double arm = Math.min(hw, Math.min(hh, hhDown)) * 0.42;
      int col = this.color(c, 0.0F);
      if (this.ring.get()) {
         this.drawRing(c, cam, sz, inten, spd, hw / s);
      }

      for (int pass = 0; pass < 2; pass++) {
         int tex = pass == 0 ? Textures.LINE_GLOW : Textures.LINE_CORE;
         double width = (pass == 0 ? 0.11 : 0.028) * sz;

         for (int corner = 0; corner < 4; corner++) {
            double sx = corner != 0 && corner != 3 ? 1.0 : -1.0;
            double sy = corner < 2 ? -1.0 : 1.0;
            double vy = sy < 0.0 ? hhDown : hh;
            int cc = this.color(c, corner / 3.0F);
            int lineColor = pass == 0 ? alpha(cc, 0.35F * a) : alpha(ColorUtil.lerp(cc, ColorUtil.core(cc), 0.35F), 0.95F * a);
            double kx = cx + cam.rightX * sx * hw + cam.upX * sy * vy;
            double ky = cy + cam.rightY * sx * hw + cam.upY * sy * vy;
            double kz = cz + cam.rightZ * sx * hw + cam.upZ * sy * vy;
            double ex = width * 0.5;
            Vfx.line(
               tex,
               kx + cam.rightX * sx * ex,
               ky + cam.rightY * sx * ex,
               kz + cam.rightZ * sx * ex,
               kx - cam.rightX * sx * arm,
               ky - cam.rightY * sx * arm,
               kz - cam.rightZ * sx * arm,
               width,
               lineColor,
               lineColor
            );
            Vfx.line(
               tex,
               kx + cam.upX * sy * ex,
               ky + cam.upY * sy * ex,
               kz + cam.upZ * sy * ex,
               kx - cam.upX * sy * arm,
               ky - cam.upY * sy * arm,
               kz - cam.upZ * sy * arm,
               width,
               lineColor,
               lineColor
            );
         }
      }

      for (int corner = 0; corner < 4; corner++) {
         double sx = corner != 0 && corner != 3 ? 1.0 : -1.0;
         double sy = corner < 2 ? -1.0 : 1.0;
         double vy = sy < 0.0 ? hhDown : hh;
         Vfx.sprite(
            Textures.SPARK,
            cx + cam.rightX * sx * hw + cam.upX * sy * vy,
            cy + cam.rightY * sx * hw + cam.upY * sy * vy,
            cz + cam.rightZ * sx * hw + cam.upZ * sy * vy,
            0.09F * sz,
            0.0F,
            core(col, 0.8F * a)
         );
      }
   }

   private void drawRing(EffectContext c, Camera cam, float sz, float inten, float spd, double halfWidth) {
      float grow = Easing.outCubic(Easing.clamp01(c.time / 0.5));
      float a = inten * grow;
      if (!(a <= 0.003F)) {
         double radius = halfWidth * 1.12 * (0.6 + 0.4 * grow);
         double cx = c.x + cam.forwardX * 0.08;
         double cy = c.y + c.height * 0.56 + cam.forwardY * 0.08;
         double cz = c.z + cam.forwardZ * 0.08;
         double rotation = c.time * 0.7 * spd + (1.0F - grow) * 1.5;
         double sector = Math.PI / 10;
         double dash = sector * 0.62;
         int n = 0;

         for (int d = 0; d < 20; d++) {
            double start = rotation + d * sector;

            for (int k = 0; k < 5; k++) {
               double ang = start + dash * k / 4.0;
               double cos = Math.cos(ang) * radius;
               double sin = Math.sin(ang) * radius;
               Vfx.X[n] = cx + cam.rightX * cos + cam.upX * sin;
               Vfx.Y[n] = cy + cam.rightY * cos + cam.upY * sin;
               Vfx.Z[n] = cz + cam.rightZ * cos + cam.upZ * sin;
               Vfx.W[n] = 0.022F * sz;
               Vfx.C[n] = alpha(this.color(c, loop(d / 20.0F)), 0.6F * a);
               n++;
            }
         }

         for (int var38 = 0; var38 < 20; var38++) {
            Vfx.ribbon(Textures.LINE_CORE, var38 * 5, 5, false);
         }

         int tick = core(this.color(c, 0.5F), 0.8F * a);

         for (int t = 0; t < 4; t++) {
            double ang = t * Math.PI / 2.0;
            double dx = Math.cos(ang);
            double dy = Math.sin(ang);
            double r1 = radius * 0.78;
            double r2 = radius * 0.93;
            Vfx.line(
               Textures.LINE_CORE,
               cx + (cam.rightX * dx + cam.upX * dy) * r1,
               cy + (cam.rightY * dx + cam.upY * dy) * r1,
               cz + (cam.rightZ * dx + cam.upZ * dy) * r1,
               cx + (cam.rightX * dx + cam.upX * dy) * r2,
               cy + (cam.rightY * dx + cam.upY * dy) * r2,
               cz + (cam.rightZ * dx + cam.upZ * dy) * r2,
               0.022 * sz,
               tick,
               tick
            );
         }
      }
   }
}
