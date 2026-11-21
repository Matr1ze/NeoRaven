package xyz.daikirai.render.furiah.module.hit.styles;

import xyz.daikirai.render.furiah.fx.kit.Glitch;

import java.util.ArrayDeque;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import xyz.daikirai.render.furiah.fx.Basis;
import xyz.daikirai.render.furiah.fx.FxFrame;
import xyz.daikirai.render.furiah.fx.kit.Clockwork;
import xyz.daikirai.render.furiah.fx.pass.FxPass;
import xyz.daikirai.render.furiah.fx.physics.PhysicsClock;
import xyz.daikirai.render.furiah.module.hit.HitFx;
import xyz.daikirai.render.furiah.module.hit.HitStyle;
import xyz.daikirai.render.furiah.module.hit.HitStyleBase;
import xyz.daikirai.render.furiah.render.ColorUtil;
import xyz.daikirai.render.furiah.render.Easing;
import xyz.daikirai.render.furiah.render.RenderState;
import xyz.daikirai.render.furiah.render.Textures;
import xyz.daikirai.render.furiah.render.Vfx;
import xyz.daikirai.render.furiah.render.material.Finish;
import xyz.daikirai.render.furiah.render.material.Material;
import xyz.daikirai.render.furiah.render.material.MeshBatch;
import xyz.daikirai.render.furiah.setting.BooleanSetting;
import xyz.daikirai.render.furiah.setting.ThemeTint;
import xyz.daikirai.render.furiah.setting.Displayable;
import xyz.daikirai.render.furiah.setting.ModeSetting;
import xyz.daikirai.render.furiah.setting.SliderSetting;

public final class GearBurstStyle extends HitStyleBase {
   private static final float POP = 0.13333334F;
   private static final float SWEEP = 0.46666667F;
   private static final float BURST = 0.53333336F;
   private static final float FADE = 0.88F;
   private static final int MAX_PIECES = 40;
   private static final int MAX_SPRINGS = 6;
   private static final int POOL = 8;
   private static final double GRAVITY = 18.0;
   private static final double BOUNCE = 0.32;
   private static final double ROLL_FRICTION = 3.0;
   private static final double PHYS_SPEED = 1.8;
   private static final float RIM = 0.16F;
   private static final int ENGRAVING = -15068148;
   private static final int FLYING = 0;
   private static final int BOUNCED = 1;
   private static final int ROLLING = 2;
   private static final int RESTING = 3;
   public final ThemeTint tint;
   public final ModeSetting<Material> material;
   public final ModeSetting<Finish> finish;
   public final SliderSetting gears;
   public final ModeSetting<GearBurstStyle.Dial> dial;
   public final ModeSetting<GearBurstStyle.Sweep> sweep;
   public final SliderSetting size;
   public final BooleanSetting springs;
   private double dx;
   private double dy;
   private double dz;
   private double ux;
   private double uy;
   private double uz;
   private double rx;
   private double ry;
   private double rz;
   private double ox;
   private double oy;
   private double oz;
   private final double[] point = new double[3];
   private static final double[] AX = new double[3];
   private final ArrayDeque<GearBurstStyle.State> pool = new ArrayDeque<>();

   public GearBurstStyle() {
      super(
         HitStyle.GEAR_BURST,
         0.75F,
         new ThemeTint()
      );
      this.material = this.materialSetting("gearburst", Material.BRASS, "What the dial and the gears are made of. Custom takes the Tint, with a finish.");
      this.finish = this.finishSetting("gearburst", Finish.METALLIC, this.material);
      this.tint = ThemeTint.detail(-3562674);
      this.gears = this.look(this.setting(new SliderSetting("gearburst.gears", "Gears", "How many gears the dial bursts into.", 4.0, 16.0, 8.0, 1.0)));
      this.dial = this.setting(
         new ModeSetting<>("gearburst.dial", "Dial", "What is engraved round the dial.", GearBurstStyle.Dial.class, GearBurstStyle.Dial.TICKS)
            .style(ModeSetting.Style.CHIPS)
      );
      this.sweep = this.setting(
         new ModeSetting<>("gearburst.sweep", "Sweep", "How the hand goes round.", GearBurstStyle.Sweep.class, GearBurstStyle.Sweep.TICKING)
      );
      this.size = this.look(this.setting(new SliderSetting("gearburst.size", "Size", "How big the dial and its gears are.", 50.0, 150.0, 100.0, 5.0).unit("%")));
      this.springs = this.setting(new BooleanSetting("gearburst.springs", "Springs", "A few coil springs burst out with the gears.", true));
   }

   @Override
   public void start(HitFx fx) {
      GearBurstStyle.State s = this.pool.poll();
      if (s == null) {
         s = new GearBurstStyle.State();
      }

      s.burst = false;
      s.burstAt = 0.0F;
      s.seconds = 0.0;
      s.pieces = 0;
      s.floor = fx.hit.groundY;
      fx.data = s;
   }

   @Override
   public void finish(HitFx fx) {
      if (fx.data instanceof GearBurstStyle.State && this.pool.size() < 8) {
         this.pool.push((GearBurstStyle.State)fx.data);
      }

      fx.data = null;
   }

   @Override
   public int passes(HitFx fx) {
      int solid = this.material.get().translucent(this.finish.get()) ? FxPass.TRANSLUCENT.bit() : FxPass.SOLID.bit();
      return fx.lod ? solid : solid | FxPass.GLOW.bit();
   }

   private double radius(HitFx fx) {
      return 0.24 * scale(fx) * this.size.getFloat() / 100.0;
   }

   private void plane(HitFx fx) {
      Basis b = fx.basis;
      this.dx = fx.ix() - b.fx * 0.12;
      this.dy = fx.iy() - b.fy * 0.12;
      this.dz = fx.iz() - b.fz * 0.12;
      this.ux = b.ux;
      this.uy = b.uy;
      this.uz = b.uz;
      this.rx = b.rx;
      this.ry = b.ry;
      this.rz = b.rz;
      this.ox = -b.fx;
      this.oy = -b.fy;
      this.oz = -b.fz;
   }

   private float hand(HitFx fx, float p) {
      double t = Easing.clamp01((p - 0.13333334F) / 0.3333333F);
      double turn = Clockwork.ratchet(t * 12.0, 1.0, Math.PI / 6, this.sweep.get() == GearBurstStyle.Sweep.SMOOTH);
      return (float)Math.min(Math.PI * 2, turn);
   }

   @Override
   public void renderPass(HitFx fx, FxFrame f, FxPass pass) {
      GearBurstStyle.State s = (GearBurstStyle.State)fx.data;
      if (s != null) {
         float p = fx.progress();
         float alpha = fx.alpha() * (1.0F - Easing.smoothstep(0.88F, 1.0F, p));
         if (!(alpha <= 0.003F)) {
            this.plane(fx);
            if (pass == FxPass.GLOW) {
               this.glow(fx, f, s, p, alpha);
            } else {
               boolean custom = this.material.get() == Material.CUSTOM;
               double time = ThemeTint.clock();
               if (p >= 0.46666667F && !s.burst) {
                  this.breakLoose(fx, s, p);
               }

               if (s.burst) {
                  this.step(fx, s, f.dt * 1.8 / Math.max(0.25F, fx.timeScale));
               }

               MeshBatch.begin(this.material.get(), this.finish.get(), fx.light, 0.16F, -1, true, pass == FxPass.SOLID);
               float dialAlpha = p < 0.46666667F ? 1.0F : 1.0F - Easing.clamp01((p - 0.46666667F) / 0.06666669F);
               if (dialAlpha > 0.003F) {
                  this.dials(fx, f, p, alpha * dialAlpha, custom, time);
               }

               if (s.burst) {
                  this.pieces(fx, f, s, alpha, custom, time);
               }

               MeshBatch.end();
               if (dialAlpha > 0.003F && p >= 0.06666667F) {
                  this.engraving(fx, f, p, alpha * dialAlpha);
               }
            }
         }
      }
   }

   private int metal(boolean custom, float along, double time, float a) {
      int c = custom ? this.tint.getColor(along, time) : -1;
      int a8 = Math.round(Easing.clamp01(a) * (custom ? c >>> 24 : 255));
      return a8 << 24 | c & 16777215;
   }

   private void dials(HitFx fx, FxFrame f, float p, float alpha, boolean custom, double time) {
      double r = this.radius(fx);
      float pop = Easing.outBack(Easing.clamp01(p / 0.060000002F), 2.2F);
      float lid = Easing.outCubic(Easing.clamp01((p - 0.040000003F) / 0.093333334F));
      double grow = p > 0.46666667F ? 1.0 + 0.15 * Easing.clamp01((p - 0.46666667F) / 0.06666669F) : 1.0;
      double sr = r * pop * grow;
      if (!(sr <= 1.0E-4)) {
         double cx = f.rx(this.dx);
         double cy = f.ry(this.dy);
         double cz = f.rz(this.dz);
         int col = this.metal(custom, 0.5F, time, alpha * fx.keep.cover(cx, cy, cz));
         float handAngle = this.hand(fx, p);
         this.dial(cx, cy, cz, sr, col, handAngle, fx.crit ? 0.0 : 1.0);
         if (fx.crit) {
            double ir = sr * 0.55;
            double lift = sr * 0.07;
            this.dial(cx + this.ox * lift, cy + this.oy * lift, cz + this.oz * lift, ir, col, -handAngle, 1.0);
         }

         double phi = Math.toRadians(150.0) * lid;
         double t = sr * 0.06;
         double cphi = Math.cos(phi);
         double sphi = Math.sin(phi);
         double hx = cx - this.rx * sr + this.ox * sr * 0.05;
         double hy = cy - this.ry * sr + this.oy * sr * 0.05;
         double hz = cz - this.rz * sr + this.oz * sr * 0.05;
         double mx = this.rx * cphi + this.ox * sphi;
         double my = this.ry * cphi + this.oy * sphi;
         double mz = this.rz * cphi + this.oz * sphi;
         double nx = this.ox * cphi - this.rx * sphi;
         double ny = this.oy * cphi - this.ry * sphi;
         double nz = this.oz * cphi - this.rz * sphi;
         double lx = hx + mx * sr + nx * t * 0.5;
         double ly = hy + my * sr + ny * t * 0.5;
         double lz = hz + mz * sr + nz * t * 0.5;
         int lidCol = this.metal(custom, 0.5F, time, alpha * fx.keep.cover(lx, ly, lz));
         Clockwork.ring(lx, ly, lz, nx, ny, nz, mx, my, mz, this.ux, this.uy, this.uz, sr * 0.03, sr * 0.98, t, 24, lidCol, 0.15F);
         Clockwork.ring(
            lx + nx * t * 0.5,
            ly + ny * t * 0.5,
            lz + nz * t * 0.5,
            nx,
            ny,
            nz,
            mx,
            my,
            mz,
            this.ux,
            this.uy,
            this.uz,
            sr * 0.03,
            sr * 0.25,
            t * 0.6,
            16,
            lidCol,
            0.15F
         );
      }
   }

   private void dial(double cx, double cy, double cz, double r, int col, double angle, double full) {
      double t = r * 0.1;
      Clockwork.ring(cx, cy, cz, this.ox, this.oy, this.oz, this.rx, this.ry, this.rz, this.ux, this.uy, this.uz, r * 0.86, r, t, 32, col, 0.1F);
      double fx = cx - this.ox * t * 0.25;
      double fy = cy - this.oy * t * 0.25;
      double fz = cz - this.oz * t * 0.25;
      Clockwork.ring(
         fx,
         fy,
         fz,
         this.ox,
         this.oy,
         this.oz,
         this.rx,
         this.ry,
         this.rz,
         this.ux,
         this.uy,
         this.uz,
         full > 0.5 ? r * 0.03 : r * 0.55,
         r * 0.88,
         t * 0.4,
         32,
         col,
         0.05F
      );
      double c = Math.cos(angle);
      double s = Math.sin(angle);
      double hx = this.ux * c + this.rx * s;
      double hy = this.uy * c + this.ry * s;
      double hz = this.uz * c + this.rz * s;
      Clockwork.hand(
         cx + this.ox * t * 0.35,
         cy + this.oy * t * 0.35,
         cz + this.oz * t * 0.35,
         this.ox,
         this.oy,
         this.oz,
         hx,
         hy,
         hz,
         r * 0.7,
         r * 0.13,
         r * 0.035,
         col,
         0.3F
      );
   }

   private void engraving(HitFx fx, FxFrame f, float p, float alpha) {
      double r = this.radius(fx) * Easing.outBack(Easing.clamp01(p / 0.060000002F), 2.2F);
      if (!(r <= 1.0E-4)) {
         double cx = f.rx(this.dx);
         double cy = f.ry(this.dy);
         double cz = f.rz(this.dz);
         float a = 0.78F * alpha * fx.keep.cover(cx, cy, cz);
         int dark = ColorUtil.withAlpha(-15068148, a);
         double lift = r * 0.1 * 0.05 + 0.001;
         double lx = this.ox * lift;
         double ly = this.oy * lift;
         double lz = this.oz * lift;
         int face = this.faceSign();
         RenderState.decal(true);
         boolean inner = fx.crit;
         Clockwork.ticks(cx, cy, cz, this.ux, this.uy, this.uz, this.rx, this.ry, this.rz, lx, ly, lz, r * 0.75, r * 0.2, 0.0, dark, null);
         GearBurstStyle.Dial d = this.dial.get();
         double hour = Math.PI / 6;
         if (d != GearBurstStyle.Dial.TICKS) {
            if (inner) {
               double ir = r * 0.55;
               double il = r * 0.07 + lift;
               Clockwork.glyphs(
                  cx,
                  cy,
                  cz,
                  this.ux,
                  this.uy,
                  this.uz,
                  this.rx,
                  this.ry,
                  this.rz,
                  this.ox * il,
                  this.oy * il,
                  this.oz * il,
                  ir * 0.62,
                  ir * 0.3,
                  0.0,
                  hour,
                  face,
                  d == GearBurstStyle.Dial.ROMAN,
                  dark,
                  null
               );
            } else {
               Clockwork.glyphs(
                  cx,
                  cy,
                  cz,
                  this.ux,
                  this.uy,
                  this.uz,
                  this.rx,
                  this.ry,
                  this.rz,
                  lx,
                  ly,
                  lz,
                  r * 0.52,
                  r * 0.22,
                  0.0,
                  hour,
                  face,
                  d == GearBurstStyle.Dial.ROMAN,
                  dark,
                  null
               );
            }
         } else if (inner) {
            double ir = r * 0.55;
            double il = r * 0.07 + lift;
            Clockwork.ticks(
               cx, cy, cz, this.ux, this.uy, this.uz, this.rx, this.ry, this.rz, this.ox * il, this.oy * il, this.oz * il, ir * 0.75, ir * 0.2, 0.0, dark, null
            );
         }

         RenderState.decal(false);
      }
   }

   private int faceSign() {
      double nx = this.uy * this.rz - this.uz * this.ry;
      double ny = this.uz * this.rx - this.ux * this.rz;
      double nz = this.ux * this.ry - this.uy * this.rx;
      return nx * this.ox + ny * this.oy + nz * this.oz > 0.0 ? -1 : 1;
   }

   private void breakLoose(HitFx fx, GearBurstStyle.State s, float p) {
      s.burst = true;
      s.burstAt = p;
      s.seconds = 0.0;
      double r = this.radius(fx);
      int g = Math.min(34, Math.round(this.gears.getInt() * fx.density * (fx.crit ? 2.0F : 1.0F)));
      int sp = this.springs.get() ? Math.min(6, fx.crit ? 4 : 2) : 0;
      s.gears = Math.max(1, g);
      s.pieces = s.gears + sp;
      s.floor = fx.hit.groundY;
      Basis b = fx.basis;

      for (int i = 0; i < s.pieces; i++) {
         boolean gear = i < s.gears;
         int id = fx.seed + i * 31;
         double a = hash(id, 1) * Math.PI * 2.0;
         double d = r * 0.7 * Math.sqrt(hash(id, 2));
         double ca = Math.cos(a);
         double sa = Math.sin(a);
         s.x[i] = s.lx[i] = this.dx + (this.ux * ca + this.rx * sa) * d;
         s.y[i] = s.ly[i] = this.dy + (this.uy * ca + this.ry * sa) * d;
         s.z[i] = s.lz[i] = this.dz + (this.uz * ca + this.rz * sa) * d;
         double cone = Math.toRadians(45.0) * Math.sqrt(hash(id, 3));
         double around = hash(id, 4) * Math.PI * 2.0;
         double sc = Math.sin(cone);
         double cc = Math.cos(cone);
         double wx = b.fx * cc + (b.rx * Math.cos(around) + b.ux * Math.sin(around)) * sc;
         double wy = b.fy * cc + (b.ry * Math.cos(around) + b.uy * Math.sin(around)) * sc;
         double wz = b.fz * cc + (b.rz * Math.cos(around) + b.uz * Math.sin(around)) * sc;
         double speed = 1.0 + 0.8 * hash(id, 5);
         double out = 0.4 + 0.4 * hash(id, 6);
         s.vx[i] = wx * speed + (this.ux * ca + this.rx * sa) * out;
         s.vy[i] = wy * speed + (this.uy * ca + this.ry * sa) * out + 0.2 + 0.7 * hash(id, 7);
         s.vz[i] = wz * speed + (this.uz * ca + this.rz * sa) * out;
         double k = hash(id, 8);
         s.radius[i] = gear ? r * (k < 0.25 ? 0.36 + 0.1 * hash(id, 9) : 0.18 + 0.12 * hash(id, 9)) : r * 0.42;
         s.teeth[i] = gear ? (k < 0.25 ? 12 + 2 * (int)(hash(id, 10) * 3.0) : 8 + 2 * (int)(hash(id, 10) * 2.0)) : 0;
         s.spokes[i] = gear && k < 0.25 ? 3 + (int)(hash(id, 11) * 3.0) : 0;
         s.turn[i] = hash(id, 12) * Math.PI * 2.0;
         s.spin[i] = (hash(id, 13) < 0.5 ? -1 : 1) * (10.0 + 14.0 * hash(id, 14));
         s.axle[i * 3] = this.ox;
         s.axle[i * 3 + 1] = this.oy;
         s.axle[i * 3 + 2] = this.oz;
         double tx = hash(id, 15) - 0.5;
         double ty = hash(id, 16) - 0.5;
         double tz = hash(id, 17) - 0.5;
         double tl = Math.sqrt(tx * tx + ty * ty + tz * tz) + 1.0E-9;
         s.tumbleAxis[i * 3] = tx / tl;
         s.tumbleAxis[i * 3 + 1] = ty / tl;
         s.tumbleAxis[i * 3 + 2] = tz / tl;
         s.tumble[i] = 0.0;
         s.tumbleRate[i] = 5.0 + 7.0 * hash(id, 18);
         s.phase[i] = 0;
         s.bounced[i] = -1.0;
      }
   }

   private void step(HitFx fx, GearBurstStyle.State s, double dt) {
      int steps = s.clock.advance(dt);
      double h = 0.008333333333333333;

      for (int n = 0; n < steps; n++) {
         s.seconds += h;

         for (int i = 0; i < s.pieces; i++) {
            s.lx[i] = s.x[i];
            s.ly[i] = s.y[i];
            s.lz[i] = s.z[i];
            switch (s.phase[i]) {
               case 0:
               case 1:
                  s.vy[i] = s.vy[i] - 18.0 * h;
                  s.x[i] = s.x[i] + s.vx[i] * h;
                  s.y[i] = s.y[i] + s.vy[i] * h;
                  s.z[i] = s.z[i] + s.vz[i] * h;
                  s.turn[i] = s.turn[i] + s.spin[i] * h;
                  s.tumble[i] = s.tumble[i] + s.tumbleRate[i] * h;
                  double low = s.floor + s.radius[i] * 0.5;
                  if (s.y[i] < low && s.vy[i] < 0.0) {
                     s.y[i] = low;
                     if (s.phase[i] == 0) {
                        s.vy[i] = -s.vy[i] * 0.32;
                        s.vx[i] = s.vx[i] * 0.75;
                        s.vz[i] = s.vz[i] * 0.75;
                        s.tumbleRate[i] = s.tumbleRate[i] * 0.5;
                        s.spin[i] = s.spin[i] * 0.6;
                        s.bounced[i] = s.seconds;
                        s.phase[i] = 1;
                     } else if (i < s.gears) {
                        double spx = Math.sqrt(s.vx[i] * s.vx[i] + s.vz[i] * s.vz[i]);
                        double wx = spx > 0.001 ? s.vx[i] / spx : fx.basis.fx;
                        double wz = spx > 0.001 ? s.vz[i] / spx : fx.basis.fz;
                        double wl = Math.sqrt(wx * wx + wz * wz) + 1.0E-9;
                        s.roll[i * 2] = wx / wl;
                        s.roll[i * 2 + 1] = wz / wl;
                        s.vx[i] = Math.max(0.4, spx) + 0.3;
                        s.y[i] = s.floor + s.radius[i];
                        s.vy[i] = 0.0;
                        s.phase[i] = 2;
                     } else {
                        s.phase[i] = 3;
                     }
                  }
                  break;
               case 2:
                  double sp;
                  s.vx[i] = sp = Math.max(0.0, s.vx[i] - 3.0 * h);
                  s.x[i] = s.x[i] + s.roll[i * 2] * sp * h;
                  s.z[i] = s.z[i] + s.roll[i * 2 + 1] * sp * h;
                  s.turn[i] = s.turn[i] - sp / Math.max(0.001, s.radius[i]) * h;
                  if (sp <= 0.0) {
                     s.phase[i] = 3;
                  }
            }
         }
      }
   }

   private void pieces(HitFx fx, FxFrame f, GearBurstStyle.State s, float alpha, boolean custom, double time) {
      double a = s.clock.alpha();
      double thick = this.radius(fx) * 0.09;

      for (int i = 0; i < s.pieces; i++) {
         double x = f.rx(s.lx[i] + (s.x[i] - s.lx[i]) * a);
         double y = f.ry(s.ly[i] + (s.y[i] - s.ly[i]) * a);
         double z = f.rz(s.lz[i] + (s.z[i] - s.lz[i]) * a);
         axle(s, i);
         float k = alpha * fx.keep.solid(x, y, z);
         int col = this.metal(custom, (float)i / s.pieces, time, k);
         float glint = 0.05F;
         if (s.bounced[i] >= 0.0) {
            double since = s.seconds - s.bounced[i];
            glint += (float)(0.9 * Math.exp(-since / 0.06));
         }

         if (i < s.gears && s.spokes[i] > 0) {
            Clockwork.gear(x, y, z, AX[0], AX[1], AX[2], s.radius[i], thick, s.turn[i], s.teeth[i], s.spokes[i], col, glint);
         } else if (i < s.gears) {
            Clockwork.kiteGear(x, y, z, AX[0], AX[1], AX[2], s.radius[i], thick * 1.2, s.turn[i], col);
         } else {
            double half = s.radius[i] * 0.5;
            Clockwork.spring(
               x - AX[0] * half,
               y - AX[1] * half,
               z - AX[2] * half,
               x + AX[0] * half,
               y + AX[1] * half,
               z + AX[2] * half,
               s.radius[i] * 0.22,
               s.radius[i] * 0.05,
               5,
               col,
               glint
            );
         }
      }
   }

   private static void axle(GearBurstStyle.State s, int i) {
      if (s.phase[i] != 2 && (s.phase[i] != 3 || i >= s.gears || !(s.roll[i * 2] * s.roll[i * 2] + s.roll[i * 2 + 1] * s.roll[i * 2 + 1] > 0.5))) {
         double kx = s.tumbleAxis[i * 3];
         double ky = s.tumbleAxis[i * 3 + 1];
         double kz = s.tumbleAxis[i * 3 + 2];
         double x = s.axle[i * 3];
         double y = s.axle[i * 3 + 1];
         double z = s.axle[i * 3 + 2];
         double c = Math.cos(s.tumble[i]);
         double sn = Math.sin(s.tumble[i]);
         double d = kx * x + ky * y + kz * z;
         AX[0] = x * c + (ky * z - kz * y) * sn + kx * d * (1.0 - c);
         AX[1] = y * c + (kz * x - kx * z) * sn + ky * d * (1.0 - c);
         AX[2] = z * c + (kx * y - ky * x) * sn + kz * d * (1.0 - c);
      } else {
         AX[0] = -s.roll[i * 2 + 1];
         AX[1] = 0.0;
         AX[2] = s.roll[i * 2];
      }
   }

   private void glow(HitFx fx, FxFrame f, GearBurstStyle.State s, float p, float alpha) {
      int col = this.color.getColor(0.5F);
      double r = this.radius(fx);
      double cx = f.rx(this.dx);
      double cy = f.ry(this.dy);
      double cz = f.rz(this.dz);
      boolean calm = Glitch.reduceFlashing();
      if (p >= 0.13333334F && p < 0.46666667F) {
         float angle = this.hand(fx, p);
         double c = Math.cos(angle);
         double sn = Math.sin(angle);
         double tip = r * 0.66;
         double lift = r * 0.06;
         double hx = cx + (this.ux * c + this.rx * sn) * tip + this.ox * lift;
         double hy = cy + (this.uy * c + this.ry * sn) * tip + this.oy * lift;
         double hz = cz + (this.uz * c + this.rz * sn) * tip + this.oz * lift;
         double t = Easing.clamp01((p - 0.13333334F) / 0.3333333F) * 12.0;
         float tick = this.sweep.get() == GearBurstStyle.Sweep.SMOOTH ? 0.6F : 0.45F + 0.55F * (float)Math.exp(-(t - Math.floor(t)) * 6.0);
         Vfx.sprite(Textures.GLOW, hx, hy, hz, r * 0.5, 0.0F, alpha(col, 0.55F * alpha * tick));
         Vfx.sprite(Textures.SPARK, hx, hy, hz, r * 0.22, 0.0F, core(col, 0.8F * alpha * tick));
      }

      if (p >= 0.46666667F && p < 0.59333336F) {
         float b = Easing.clamp01((p - 0.46666667F) / 0.1266667F);
         float k = (1.0F - b) * alpha;
         if (calm) {
            Vfx.sprite(Textures.GLOW, cx, cy, cz, r * 2.2, 0.0F, alpha(col, 0.3F * k));
         } else {
            Vfx.sprite(Textures.GLOW, cx, cy, cz, r * (2.4 + 1.6 * b), 0.0F, alpha(col, 0.65F * k));
            Vfx.sprite(Textures.FLARE, cx, cy, cz, r * (1.6 + 2.0 * b), (float)(b * 1.5), core(col, 0.9F * k));
         }
      }

      if (s.burst && !fx.lod) {
         double a = s.clock.alpha();

         for (int i = 0; i < s.pieces; i++) {
            double since;
            if (!(s.bounced[i] < 0.0) && !((since = s.seconds - s.bounced[i]) > 0.12)) {
               float k = (float)(1.0 - since / 0.12) * alpha;
               double x = f.rx(s.lx[i] + (s.x[i] - s.lx[i]) * a);
               double y = f.ry(s.floor + s.radius[i] * 0.4);
               double z = f.rz(s.lz[i] + (s.z[i] - s.lz[i]) * a);
               Vfx.sprite(Textures.SPARK, x, y, z, s.radius[i] * 1.4, (float)(since * 20.0), core(col, 0.7F * k));
            }
         }
      }
   }

   @Override
   public String stateForTest(HitFx fx) {
      GearBurstStyle.State s = (GearBurstStyle.State)fx.data;
      if (s == null) {
         return null;
      } else {
         float p = fx.progress();
         String head = String.format(
            Locale.ROOT,
            "%s, %s, %s, dial %.3f, hand %.0f deg",
            this.material.get().displayName(),
            this.dial.get().displayName(),
            this.sweep.get().displayName(),
            this.radius(fx),
            Math.toDegrees(this.hand(fx, p))
         );
         if (!s.burst) {
            return head + (p < 0.13333334F ? "; popping open" : "; ticking round");
         } else {
            int flying = 0;
            int bounced = 0;
            int rolling = 0;
            int resting = 0;

            for (int i = 0; i < s.pieces; i++) {
               switch (s.phase[i]) {
                  case 0:
                     flying++;
                     break;
                  case 1:
                     bounced++;
                     break;
                  case 2:
                     rolling++;
                     break;
                  default:
                     resting++;
               }
            }

            return head
               + String.format(
                  Locale.ROOT,
                  "; burst: %d gears and %d springs: %d flying, %d bounced, %d rolling, %d resting",
                  s.gears,
                  s.pieces - s.gears,
                  flying,
                  bounced,
                  rolling,
                  resting
               );
         }
      }
   }

   private static double hash(int a, int b) {
      int h = a * 668265261 ^ (b + 374761393) * -1640531535;
      h ^= h >>> 15;
      h *= -2048144789;
      h ^= h >>> 13;
      return (h >>> 8 & 16777215) / 1.6777216E7;
   }

   public static enum Dial implements Displayable {
      TICKS("Ticks", "Tick marks round the dial"),
      ROMAN("Roman numerals", "I to XII round the dial"),
      STARS("Stars", "A star at each hour");

      private final String displayName;
      private final String description;

      private Dial(String displayName, String description) {
         this.displayName = displayName;
         this.description = description;
      }

      @Override
      public String displayName() {
         return this.displayName;
      }

      @Override
      public String description() {
         return this.description;
      }
   }

   private static final class State {
      final PhysicsClock clock = new PhysicsClock();
      boolean burst;
      float burstAt;
      double seconds;
      int gears;
      int pieces;
      final double[] x = new double[40];
      final double[] y = new double[40];
      final double[] z = new double[40];
      final double[] lx = new double[40];
      final double[] ly = new double[40];
      final double[] lz = new double[40];
      final double[] vx = new double[40];
      final double[] vy = new double[40];
      final double[] vz = new double[40];
      final double[] radius = new double[40];
      final double[] turn = new double[40];
      final double[] spin = new double[40];
      final double[] tumble = new double[40];
      final double[] tumbleRate = new double[40];
      final double[] axle = new double[120];
      final double[] tumbleAxis = new double[120];
      final double[] roll = new double[80];
      final int[] teeth = new int[40];
      final int[] spokes = new int[40];
      final int[] phase = new int[40];
      final double[] bounced = new double[40];
      double floor;

      private State() {
      }
   }

   public static enum Sweep implements Displayable {
      TICKING("Ticking", "The hand jumps from hour to hour"),
      SMOOTH("Smooth", "The hand glides round");

      private final String displayName;
      private final String description;

      private Sweep(String displayName, String description) {
         this.displayName = displayName;
         this.description = description;
      }

      @Override
      public String displayName() {
         return this.displayName;
      }

      @Override
      public String description() {
         return this.description;
      }
   }
}
