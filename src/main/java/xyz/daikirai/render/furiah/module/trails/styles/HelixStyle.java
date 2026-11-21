package xyz.daikirai.render.furiah.module.trails.styles;

import java.util.List;
import xyz.daikirai.render.furiah.fx.trail.TrailAnchor;
import xyz.daikirai.render.furiah.fx.trail.TrailShape;
import xyz.daikirai.render.furiah.fx.trail.TrailStrip;
import xyz.daikirai.render.furiah.module.trails.PlayerTrail;
import xyz.daikirai.render.furiah.module.trails.TrailFrame;
import xyz.daikirai.render.furiah.module.trails.TrailStyle;
import xyz.daikirai.render.furiah.module.trails.TrailStyleBase;
import xyz.daikirai.render.furiah.module.trails.FuriahTrails;
import xyz.daikirai.render.furiah.render.ColorUtil;
import xyz.daikirai.render.furiah.render.RenderState;
import xyz.daikirai.render.furiah.render.Textures;
import xyz.daikirai.render.furiah.render.Vfx;
import xyz.daikirai.render.furiah.setting.BooleanSetting;
import xyz.daikirai.render.furiah.setting.SliderSetting;

public final class HelixStyle extends TrailStyleBase {
   public final SliderSetting strands = this.setting(
      new SliderSetting("helix.strands", "Strands", "How many orbs circle you, each drawing a strand.", 1.0, 4.0, 2.0, 1.0)
   );
   public final SliderSetting radius = this.setting(
      new SliderSetting("helix.radius", "Radius", "How far from the path the strands turn.", 0.3, 1.2, 0.6, 0.05).unit(" blocks")
   );
   public final SliderSetting spin = this.setting(
      new SliderSetting("helix.spin", "Spin Speed", "How fast the orbs circle (turns a second).", 0.2, 3.0, 1.0, 0.1).unit("/s")
   );
   public final SliderSetting orbSize = this.setting(
      new SliderSetting("helix.orbSize", "Orb Size", "How big the orbs are.", 0.05, 0.4, 0.14, 0.01).unit(" blocks")
   );
   public final BooleanSetting rungs = this.setting(
      new BooleanSetting("helix.rungs", "Rungs", "Thin lines join the strands, like the rungs of a DNA ladder.", false)
   );
   private static final double[] BX = new double[1024];
   private static final double[] BY = new double[1024];
   private static final double[] BZ = new double[1024];
   private static final float[] BW = new float[1024];
   private static final float[] PHASE = new float[1024];
   private static final double[] SX = new double[1024];
   private static final double[] SY = new double[1024];
   private static final double[] SZ = new double[1024];
   private static final double[] UX = new double[1024];
   private static final double[] UY = new double[1024];
   private static final double[] UZ = new double[1024];

   public HelixStyle(FuriahTrails module) {
      super(module, TrailStyle.HELIX, spec().size(0.02, 0.3, 0.07, 0.01).taper(40.0).anchor(TrailAnchor.WAIST, false));
   }

   @Override
   protected void addLookTargets(List<SliderSetting> out) {
      out.add(this.radius);
      out.add(this.orbSize);
   }

   @Override
   public void render(PlayerTrail p, TrailFrame f) {
      TrailShape s = p.shape;
      int count = this.strands.getInt();
      double r = this.radius.getFloat() * Math.sqrt(p.widthScale);
      double turnsPerSecond = this.spin.getFloat();
      float glowAmount = this.glow.getFloat() / 100.0F;

      for (int k = 0; k < s.runs; k++) {
         int n = this.fill(p, f, k, this.size.getFloat(), -1.0F, 0.0F);
         if (n >= 2) {
            frames(s, k, n, f.now, turnsPerSecond);

            for (int st = 0; st < count; st++) {
               double offset = (Math.PI * 2) * st / count;
               place(n, r, offset);
               if (st > 0) {
                  this.recolor(p, f, k, n, st * 0.22F);
               }

               TrailStrip.sides(0, n, 0, f.cam.posX, f.cam.posY, f.cam.posZ, 0.0F, null, 0);
               RenderState.translucent(false);
               TrailStrip.draw(Textures.LINE_GLOW, 0, n, 1.3F, 0.0, 0.5F);
               RenderState.additive();
               TrailStrip.draw(Textures.LINE_GLOW, 0, n, 1.6F + 2.6F * glowAmount, 0.0, 0.5F * glowAmount);

               for (int j = 0; j < n; j++) {
                  int c = Vfx.C[j];
                  Vfx.C[j] = c & 0xFF000000 | ColorUtil.core(c) & 16777215;
               }

               TrailStrip.draw(Textures.LINE_CORE, 0, n, 0.45F, 0.0, 0.85F);
            }

            if (this.rungs.get() && count >= 2) {
               this.drawRungs(p, f, s, k, n, r, count);
            }
         }
      }

      if (!p.ghost && p.style.head > 0.004F) {
         this.orbs(p, f, r, count, turnsPerSecond);
      }
   }

   private static void frames(TrailShape s, int k, int n, double now, double turnsPerSecond) {
      int from = s.runStart[k];
      double lx = 1.0;
      double ly = 0.0;
      double lz = 0.0;

      for (int j = 0; j < n; j++) {
         BX[j] = Vfx.X[j];
         BY[j] = Vfx.Y[j];
         BZ[j] = Vfx.Z[j];
         BW[j] = Vfx.W[j];
         int a = Math.max(0, j - 1);
         int b = Math.min(n - 1, j + 1);
         double tx = Vfx.X[b] - Vfx.X[a];
         double tz;
         double ty;
         double tl = Math.sqrt(tx * tx + (ty = Vfx.Y[b] - Vfx.Y[a]) * ty + (tz = Vfx.Z[b] - Vfx.Z[a]) * tz);
         if (tl < 1.0E-6) {
            tx = 0.0;
            ty = 0.0;
            tz = 1.0;
         } else {
            tx /= tl;
            ty /= tl;
            tz /= tl;
         }

         double sx = -tz;
         double sy = 0.0;
         double sl = Math.sqrt(sx * sx + tx * tx);
         double sz;
         if (sl < 1.0E-4) {
            sx = lx;
            sy = ly;
            sz = lz;
         } else {
            sx /= sl;
            sz = tx / sl;
            if (j > 0 && sx * lx + sz * lz < 0.0) {
               sx = -sx;
               sz = -sz;
            }
         }

         lx = sx;
         ly = sy;
         lz = sz;
         SX[j] = sx;
         SY[j] = sy;
         SZ[j] = sz;
         UX[j] = sy * tz - sz * ty;
         UY[j] = sz * tx - sx * tz;
         UZ[j] = sx * ty - sy * tx;
         double laid = now - s.age[from + j];
         PHASE[j] = (float)(laid * turnsPerSecond % 1.0 * Math.PI * 2.0);
      }
   }

   private static void place(int n, double r, double offset) {
      for (int j = 0; j < n; j++) {
         double a = PHASE[j] + offset;
         double c = Math.cos(a) * r;
         double sn = Math.sin(a) * r;
         Vfx.X[j] = BX[j] + SX[j] * c + UX[j] * sn;
         Vfx.Y[j] = BY[j] + SY[j] * c + UY[j] * sn;
         Vfx.Z[j] = BZ[j] + SZ[j] * c + UZ[j] * sn;
         Vfx.W[j] = BW[j];
      }
   }

   private void drawRungs(PlayerTrail p, TrailFrame f, TrailShape s, int k, int n, double r, int count) {
      int from = s.runStart[k];
      float last = -1.0F;

      for (int j = 0; j < n; j++) {
         float along = s.along[from + j];
         if (!(last >= 0.0F) || !(last - along < 0.35F)) {
            last = along;
            int a = Math.round(255.0F * POINT_ALPHA[j]);
            if (a > 2) {
               for (int st = 0; st < count; st++) {
                  double a0 = PHASE[j] + (Math.PI * 2) * st / count;
                  double a1 = PHASE[j] + (Math.PI * 2) * ((st + 1) % count) / count;
                  double x0 = BX[j] + SX[j] * Math.cos(a0) * r + UX[j] * Math.sin(a0) * r;
                  double y0 = BY[j] + SY[j] * Math.cos(a0) * r + UY[j] * Math.sin(a0) * r;
                  double z0 = BZ[j] + SZ[j] * Math.cos(a0) * r + UZ[j] * Math.sin(a0) * r;
                  double x1 = BX[j] + SX[j] * Math.cos(a1) * r + UX[j] * Math.sin(a1) * r;
                  double y1 = BY[j] + SY[j] * Math.cos(a1) * r + UY[j] * Math.sin(a1) * r;
                  double z1 = BZ[j] + SZ[j] * Math.cos(a1) * r + UZ[j] * Math.sin(a1) * r;
                  int c = ColorUtil.withAlpha(
                     this.colorAt(Math.min(1.0F, along / Math.max(0.001F, s.length)), 0.0F, s.speed[from + j], f.colorTime, 0.0F), a / 255.0F * 0.6F
                  );
                  Vfx.line(Textures.LINE_GLOW, x0, y0, z0, x1, y1, z1, BW[j] * 0.7, c, c);
                  if (count == 2) {
                     break;
                  }
               }
            }
         }
      }
   }

   private void orbs(PlayerTrail p, TrailFrame f, double r, int count, double turnsPerSecond) {
      TrailShape s = p.shape;
      if (s.runs != 0) {
         int k = s.runs - 1;
         int last = s.runEnd[k] - 1;
         int prev = Math.max(s.runStart[k], last - 2);
         double tx = s.x[last] - s.x[prev];
         double tz;
         double tl = Math.sqrt(tx * tx + (tz = s.z[last] - s.z[prev]) * tz);
         if (tl < 1.0E-4) {
            double yaw = Math.toRadians(p.pose.bodyYaw);
            tx = -Math.sin(yaw);
            tz = Math.cos(yaw);
         } else {
            tx /= tl;
            tz /= tl;
         }

         double sx = -tz;
         double sz = tx;
         double phase = f.now * turnsPerSecond % 1.0 * Math.PI * 2.0;
         float a = this.opacity.getFloat() / 100.0F * p.alpha * p.style.head;
         float o = this.orbSize.getFloat() * (float)Math.sqrt(p.widthScale);

         for (int st = 0; st < count; st++) {
            double ang = phase + (Math.PI * 2) * st / count;
            double c = Math.cos(ang) * r;
            double wx = p.headX + sx * c;
            double wz;
            double wy;
            float fa = a * f.nearFade(wx, wy = p.headY + Math.sin(ang) * r, wz = p.headZ + sz * c) * f.fog(wx, wy, wz);
            if (!(fa <= 0.004F)) {
               int col = this.colorAt(0.0F, 0.0F, p.speed, f.colorTime, st * 0.22F);
               double x = wx - f.viewX;
               double y = wy - f.viewY;
               double z = wz - f.viewZ;
               Vfx.sprite(Textures.GLOW, x, y, z, o * 3.2F, 0.0F, ColorUtil.mulAlpha(col, fa * 0.55F));
               Vfx.sprite(Textures.SPARK, x, y, z, o * 1.6F, 0.0F, ColorUtil.mulAlpha(ColorUtil.core(col), fa));
            }
         }
      }
   }
}
