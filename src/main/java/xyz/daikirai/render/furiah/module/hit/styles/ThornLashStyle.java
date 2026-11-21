package xyz.daikirai.render.furiah.module.hit.styles;

import java.util.ArrayDeque;
import java.util.Locale;
import xyz.daikirai.render.furiah.fx.Basis;
import xyz.daikirai.render.furiah.fx.FxFrame;
import xyz.daikirai.render.furiah.fx.kit.Chains;
import xyz.daikirai.render.furiah.fx.kit.Vines;
import xyz.daikirai.render.furiah.fx.pass.FxPass;
import xyz.daikirai.render.furiah.module.hit.HitFx;
import xyz.daikirai.render.furiah.module.hit.HitStyle;
import xyz.daikirai.render.furiah.module.hit.HitStyleBase;
import xyz.daikirai.render.furiah.render.Camera;
import xyz.daikirai.render.furiah.render.ColorUtil;
import xyz.daikirai.render.furiah.render.Easing;
import xyz.daikirai.render.furiah.render.Vfx;
import xyz.daikirai.render.furiah.render.material.Finish;
import xyz.daikirai.render.furiah.render.material.Material;
import xyz.daikirai.render.furiah.render.material.MeshBatch;
import xyz.daikirai.render.furiah.setting.ThemeTint;
import xyz.daikirai.render.furiah.setting.SliderSetting;

public final class ThornLashStyle extends HitStyleBase {
   private static final float WHIP = 0.13333334F;
   private static final float COIL = 0.35555556F;
   private static final float HOLD = 0.44444448F;
   private static final float SNAP = 0.5555556F;
   private static final int POINTS = 36;
   private static final int MAX_VINES = 4;
   private static final int MAX_PETALS = 60;
   private static final int MAX_SPLINTERS = 28;
   private static final int POOL = 8;
   private static final int NIGHT_RIM = -3549953;
   private static final double SPLINTER_GRAVITY = 12.0;
   private static final double PETAL_GRAVITY = 2.6;
   private static final double PETAL_DRAG = 1.4;
   public final ThemeTint tipColor;
   public final ThemeTint petalColor;
   public final SliderSetting vines;
   public final SliderSetting thornDensity;
   public final SliderSetting coilTurns;
   public final SliderSetting petals;
   public final SliderSetting drift;
   private final Vines kit = new Vines(144);
   private final Basis coil = new Basis();
   private final float[] wind = new float[3];
   private final double[] point = new double[3];
   private final ArrayDeque<ThornLashStyle.State> pool = new ArrayDeque<>();

   public ThornLashStyle() {
      super(
         HitStyle.THORN_LASH,
         0.9F,
         new ThemeTint()
      );
      this.tipColor = ThemeTint.detail(-1452088);
      this.petalColor = ThemeTint.detail(-5041618);
      this.vines = this.look(this.setting(new SliderSetting("thornlash.vines", "Vines", "How many vines lash out.", 1.0, 4.0, 3.0, 1.0)));
      this.thornDensity = this.setting(
         new SliderSetting("thornlash.thorns", "Thorn Density", "How thick with thorns the vines are.", 0.0, 100.0, 60.0, 5.0).unit("%")
      );
      this.coilTurns = this.setting(new SliderSetting("thornlash.turns", "Coil Turns", "How many times the vines wind round.", 0.5, 2.0, 1.0, 0.1).unit("x"));
      this.petals = this.look(this.setting(new SliderSetting("thornlash.petalCount", "Petals", "How many petals burst out as it snaps.", 0.0, 30.0, 14.0, 1.0)));
      this.drift = this.setting(
         new SliderSetting("thornlash.drift", "Petal Drift", "How much the petals drift on the wind (Ambience's, when it's on).", 0.0, 100.0, 40.0, 5.0)
            .unit("%")
      );
   }

   @Override
   public void start(HitFx fx) {
      ThornLashStyle.State s = this.pool.poll();
      if (s == null) {
         s = new ThornLashStyle.State();
      }

      s.snapped = false;
      s.snapAt = 0.0F;
      s.splinters = 0;
      s.petals = 0;
      s.ground = fx.hit.groundY;
      fx.data = s;
   }

   @Override
   public void finish(HitFx fx) {
      if (fx.data instanceof ThornLashStyle.State && this.pool.size() < 8) {
         this.pool.push((ThornLashStyle.State)fx.data);
      }

      fx.data = null;
   }

   @Override
   public int passes(HitFx fx) {
      return FxPass.SOLID.bit();
   }

   private void frame(HitFx fx) {
      Basis b = fx.basis;
      double tip = 0.28 + Math.max(0.0, -b.fy) * 0.6;
      double st = Math.sin(tip);
      double nx = b.fx * st;
      double ny = Math.cos(tip) + b.fy * st;
      double nz = b.fz * st;
      double nl = Math.sqrt(nx * nx + ny * ny + nz * nz);
      this.coil.fx = nx / nl;
      this.coil.fy = ny / nl;
      this.coil.fz = nz / nl;
      double tx = -b.fx;
      double ty = -b.fy;
      double tz = -b.fz;
      double d;
      double tl;
      if ((
            tl = Math.sqrt(
               (tx = tx - this.coil.fx * (d = tx * this.coil.fx + ty * this.coil.fy + tz * this.coil.fz)) * tx
                  + (ty = ty - this.coil.fy * d) * ty
                  + (tz = tz - this.coil.fz * d) * tz
            )
         )
         < 1.0E-6) {
         tx = -b.rx;
         ty = -b.ry;
         tz = -b.rz;
         tl = 1.0;
      }

      this.coil.rx = tx / tl;
      this.coil.ry = ty / tl;
      this.coil.rz = tz / tl;
      this.coil.ux = this.coil.fy * this.coil.rz - this.coil.fz * this.coil.ry;
      this.coil.uy = this.coil.fz * this.coil.rx - this.coil.fx * this.coil.rz;
      this.coil.uz = this.coil.fx * this.coil.ry - this.coil.fy * this.coil.rx;
   }

   private static double height(HitFx fx) {
      return Math.max(fx.hit.height * 0.38, Math.min(fx.hit.height * 0.72, fx.hit.offY));
   }

   private static double radius(HitFx fx) {
      return fx.hit.width * 0.5 + 0.06;
   }

   private void vinePoint(HitFx fx, int k, int count, double u, double loose, double pull) {
      double turns = this.coilTurns.getFloat();
      double dir = (k & 1) == 0 ? 1.0 : -1.0;
      double a = (k - (count - 1) * 0.5) * 0.35 + dir * u * turns * (Math.PI * 2);
      double r = radius(fx) * loose * (1.0 + 0.05 * Math.sin(u * 19.0 + k * 2.3));
      double lift = ((k - (count - 1) * 0.5) * 0.11 + dir * 0.09 * (u - 0.5)) * scale(fx);
      double c = Math.cos(a);
      double s = Math.sin(a);
      double x = fx.cx() + (this.coil.rx * c + this.coil.ux * s) * r + this.coil.fx * lift;
      double y = fx.anchor.feetY() + height(fx) + (this.coil.ry * c + this.coil.uy * s) * r + this.coil.fy * lift;
      double z = fx.cz() + (this.coil.rz * c + this.coil.uz * s) * r + this.coil.fz * lift;
      double w = pull * Math.max(0.0, 1.0 - u / 0.22);
      this.point[0] = x + (fx.ix() - x) * w;
      this.point[1] = y + (fx.iy() - y) * w;
      this.point[2] = z + (fx.iz() - z) * w;
   }

   @Override
   public void renderPass(HitFx fx, FxFrame f, FxPass pass) {
      ThornLashStyle.State s = (ThornLashStyle.State)fx.data;
      if (s != null) {
         float p = fx.progress();
         float alpha = fx.alpha() * (1.0F - Easing.smoothstep(0.85F, 1.0F, p));
         if (!(alpha <= 0.003F)) {
            this.frame(fx);
            Camera cam = Vfx.camera();
            int count = Math.min(4, this.vines.getInt());
            if (p >= 0.5355556F && !s.snapped) {
               this.snap(fx, s, p, count);
            }

            double dt = f.dt / Math.max(0.25F, fx.timeScale);
            float rim = 0.1F + 0.55F * fx.night;
            MeshBatch.begin(Material.CUSTOM, Finish.MATTE, fx.light, rim, -3549953, true, true);
            float vineAlpha = p < 0.44444448F ? 1.0F : 1.0F - Easing.clamp01((p - 0.44444448F) / 0.111111104F);
            if (vineAlpha > 0.003F) {
               for (int k = 0; k < count; k++) {
                  this.vine(fx, f, cam, p, k, count, alpha * vineAlpha);
               }
            }

            if (s.snapped) {
               this.splinters(fx, f, s, p, alpha, dt);
            }

            MeshBatch.end();
            MeshBatch.begin(Material.CUSTOM, Finish.SATIN, fx.light, 0.1F + 0.25F * fx.night, -3549953, false, true);
            if (vineAlpha > 0.003F) {
               this.thorns(fx, p, count, alpha * vineAlpha);
            }

            if (fx.crit) {
               this.rose(fx, f, p, alpha);
            }

            if (s.snapped) {
               this.petals(fx, f, s, p, alpha, dt);
            }

            MeshBatch.end();
         }
      }
   }

   private static double grown(float p) {
      return Easing.outCubic(Easing.clamp01(p / 0.13333334F));
   }

   private static double looseness(float p) {
      if (p < 0.13333334F) {
         return 1.35;
      } else if (p < 0.35555556F) {
         return 1.0 + 0.35 * (1.0 - Easing.outBack(Easing.clamp01((p - 0.13333334F) / 0.22222222F), 2.6F));
      } else {
         return p < 0.44444448F ? 1.0 : 1.0 + 0.3 * Easing.outCubic(Easing.clamp01((p - 0.44444448F) / 0.111111104F));
      }
   }

   private void vine(HitFx fx, FxFrame f, Camera cam, float p, int k, int count, float alpha) {
      double g = grown(p);
      double loose = looseness(p);
      double pull = p < 0.13333334F ? 1.0 - g : 0.0;
      double thick = 0.036 * scale(fx);
      int at = k * 36;
      int bark = this.color.getColor((float)k / Math.max(1, count - 1));

      for (int j = 0; j < 36; j++) {
         double u = g * j / 35.0;
         this.vinePoint(fx, k, count, u, loose, pull);
         int i = at + j;
         this.kit.px[i] = f.rx(this.point[0]);
         this.kit.py[i] = f.ry(this.point[1]);
         this.kit.pz[i] = f.rz(this.point[2]);
         double tip = Easing.clamp01((g - u) / 0.1 + (g >= 1.0 ? 0.35 : 0.0));
         double knots = Math.sin(u * 41.0 + k * 3.1) * Math.sin(u * 17.0 + k * 1.7);
         this.kit.rad[i] = thick * (1.0 - 0.35 * u) * (1.0 + 0.18 * knots) * Math.max(0.15, tip);
         int c = ColorUtil.shade(bark, (float)(0.88 + 0.16 * Math.sin(u * 29.0 + k * 2.3)));
         this.kit.col[i] = 0xFF000000 | c & 16777215;
         this.kit.keep[i] = fx.keep.cover(this.kit.px[i], this.kit.py[i], this.kit.pz[i]);
      }

      this.kit.frames(at, 36, false, f.rx(fx.cx()), f.rz(fx.cz()));
      this.kit.tube(at, 36, false, cam, alpha);
   }

   private void thorns(HitFx fx, float p, int count, float alpha) {
      float density = this.thornDensity.getFloat() / 100.0F;
      if (!(density <= 0.0F) && !(p < 0.06666667F)) {
         double flare = p < 0.35555556F ? 0.35 + 0.65 * Easing.outBack(Easing.clamp01((p - 0.13333334F) / 0.22222222F), 3.0F) : 1.0;
         int tip = this.tipColor.getColor(0.5F);
         double g = grown(p);

         for (int k = 0; k < count; k++) {
            int n = Math.round(density * 16.0F * this.coilTurns.getFloat());

            for (int q = 0; q < n; q++) {
               double u = (q + 0.5 + 0.6 * (hash(k * 31 + q, 3) - 0.5)) / n;
               if (!(u < 0.06) && !(u > g - 0.04)) {
                  double at = u / Math.max(1.0E-6, g) * 35.0;
                  double len = 0.075 * scale(fx) * flare * (0.8 + 0.4 * hash(k * 17 + q, 23));
                  this.kit.thorn(k * 36, 36, at, (Math.PI * 2) * hash(k * 13 + q, 7), len, tip, 0.0, alpha);
               }
            }
         }
      }
   }

   private void rose(HitFx fx, FxFrame f, float p, float alpha) {
      float open = Easing.outCubic(Easing.clamp01((p - 0.38444448F) / 0.1411111F));
      if (!(open <= 0.0F) && !(p > 0.57555556F)) {
         Basis b = fx.basis;
         double x = f.rx(fx.ix() - b.fx * 0.08);
         double y = f.ry(fx.iy() - b.fy * 0.08);
         double z = f.rz(fx.iz() - b.fz * 0.08);
         double size = 0.11 * scale(fx) * (0.4 + 0.6 * open);
         float a = alpha * fx.keep.cover(x, y, z) * (1.0F - Easing.clamp01((p - 0.5555556F) / 0.02F));
         this.kit.rose(fx.seed & 7, x, y, z, -b.fx, -b.fy, -b.fz, size, open, this.petalColor.getColor(0.5F) | 0xFF000000, a);
      }
   }

   private void snap(HitFx fx, ThornLashStyle.State s, float p, int count) {
      s.snapped = true;
      s.snapAt = p;
      Basis b = fx.basis;
      s.splinters = fx.lod ? 0 : Math.min(28, 6 + 5 * count);

      for (int i = 0; i < s.splinters; i++) {
         int id = fx.seed + i * 13;
         int k = i % count;
         this.vinePoint(fx, k, count, 0.08 + 0.9 * hash(id, 1), 1.15, 0.0);
         s.sx[i] = this.point[0];
         s.sy[i] = this.point[1];
         s.sz[i] = this.point[2];
         double ox = this.point[0] - fx.cx();
         double oz = this.point[2] - fx.cz();
         double ol = Math.sqrt(ox * ox + oz * oz) + 1.0E-9;
         double out = 1.2 + 1.4 * hash(id, 2);
         double along = 0.8 + 1.2 * hash(id, 3);
         s.svx[i] = ox / ol * out + b.fx * along;
         s.svy[i] = 1.0 + 1.5 * hash(id, 4) + b.fy * along;
         s.svz[i] = oz / ol * out + b.fz * along;
         s.slen[i] = (0.04 + 0.05 * hash(id, 5)) * scale(fx);
         s.sspin[i] = 10.0 + 14.0 * hash(id, 6);
         double ax = hash(id, 7) - 0.5;
         double ay = hash(id, 8) - 0.5;
         double az = hash(id, 9) - 0.5;
         double al = Math.sqrt(ax * ax + ay * ay + az * az) + 1.0E-9;
         s.saxis[i * 3] = ax / al;
         s.saxis[i * 3 + 1] = ay / al;
         s.saxis[i * 3 + 2] = az / al;
      }

      s.petals = Math.min(60, Math.round(this.petals.getInt() * fx.density * (fx.crit ? 2.0F : 1.0F)));

      for (int var28 = 0; var28 < s.petals; var28++) {
         int id = fx.seed + var28 * 29 + 7;
         boolean fromRose = fx.crit && var28 < s.petals / 2;
         if (fromRose) {
            this.point[0] = fx.ix() - b.fx * 0.08;
            this.point[1] = fx.iy() - b.fy * 0.08;
            this.point[2] = fx.iz() - b.fz * 0.08;
         } else {
            this.vinePoint(fx, var28 % count, count, 0.1 + 0.85 * hash(id, 1), 1.1, 0.0);
         }

         s.px[var28] = this.point[0];
         s.py[var28] = this.point[1];
         s.pz[var28] = this.point[2];
         double out = 1.6 + 1.2 * hash(id, 3);
         double along = 0.5 + 0.6 * hash(id, 4);
         double up = 0.6 + 0.9 * hash(id, 8);
         double cy = fx.anchor.feetY() + height(fx);
         double sx = (this.point[0] - fx.cx()) * b.rx + (this.point[1] - cy) * b.ry + (this.point[2] - fx.cz()) * b.rz;
         double sy = (this.point[0] - fx.cx()) * b.ux + (this.point[1] - cy) * b.uy + (this.point[2] - fx.cz()) * b.uz;
         double sl = Math.sqrt(sx * sx + sy * sy);
         if (fromRose || sl < 0.12 * radius(fx)) {
            double around = hash(id, 2) * (Math.PI * 2);
            sx = Math.cos(around);
            sy = Math.sin(around);
            sl = 1.0;
         }

         double var30;
         double var31;
         s.pvx[var28] = (b.rx * (var30 = sx / sl) + b.ux * (var31 = sy / sl)) * out + b.fx * along;
         s.pvy[var28] = (b.ry * var30 + b.uy * var31) * out + b.fy * along + up;
         s.pvz[var28] = (b.rz * var30 + b.uz * var31) * out + b.fz * along;
         s.pspin[var28] = hash(id, 5) * (Math.PI * 2);
         s.ptumble[var28] = hash(id, 6) * (Math.PI * 2);
         s.psize[var28] = (0.05 + 0.03 * hash(id, 7)) * scale(fx);
         s.landed[var28] = false;
      }
   }

   private void splinters(HitFx fx, FxFrame f, ThornLashStyle.State s, float p, float alpha, double dt) {
      double t = (p - s.snapAt) * fx.life / Math.max(0.25F, fx.timeScale);
      float fade = 1.0F - Easing.smoothstep(0.12F, 0.3F, (float)t);
      if (!(fade <= 0.003F)) {
         int bark = ColorUtil.shade(this.color.getColor(0.5F), 0.8F);
         double w = 0.011 * scale(fx);

         for (int i = 0; i < s.splinters; i++) {
            s.svy[i] = s.svy[i] - 12.0 * dt;
            s.sx[i] = s.sx[i] + s.svx[i] * dt;
            s.sy[i] = s.sy[i] + s.svy[i] * dt;
            s.sz[i] = s.sz[i] + s.svz[i] * dt;
            if (s.sy[i] < s.ground + w) {
               s.sy[i] = s.ground + w;
               s.svy[i] = Math.abs(s.svy[i]) * 0.3;
               s.svx[i] = s.svx[i] * 0.6;
               s.svz[i] = s.svz[i] * 0.6;
            }

            double x = f.rx(s.sx[i]);
            double y = f.ry(s.sy[i]);
            double z = f.rz(s.sz[i]);
            double ang = s.sspin[i] * t;
            double kx = s.saxis[i * 3];
            double ky = s.saxis[i * 3 + 1];
            double kz = s.saxis[i * 3 + 2];
            double ex = Math.abs(ky) < 0.9 ? -kz : 0.0;
            double ey = Math.abs(ky) < 0.9 ? 0.0 : kz;
            double ez = Math.abs(ky) < 0.9 ? kx : -ky;
            double el = Math.sqrt(ex * ex + ey * ey + ez * ez) + 1.0E-9;
            double var66;
            double var67;
            double fx2 = ky * (var67 = ez / el) - kz * (var66 = ey / el);
            double var65;
            double fy2 = kz * (var65 = ex / el) - kx * var67;
            double fz2 = kx * var66 - ky * var65;
            double c = Math.cos(ang);
            double sn = Math.sin(ang);
            double ax = var65 * c + fx2 * sn;
            double ay = var66 * c + fy2 * sn;
            double az = var67 * c + fz2 * sn;
            double qx = ky * az - kz * ay;
            double qy = kz * ax - kx * az;
            double qz = kx * ay - ky * ax;
            float a = alpha * fade * fx.keep.cover(x, y, z);
            int col = Math.round(255.0F * Easing.clamp01(a)) << 24 | bark & 16777215;
            Chains.box(x, y, z, ax, ay, az, kx, ky, kz, qx, qy, qz, s.slen[i] * 0.5, w, w * 0.7, col, 0.0F);
         }
      }
   }

   private void petals(HitFx fx, FxFrame f, ThornLashStyle.State s, float p, float alpha, double dt) {
      if (s.petals != 0) {
         double t = (p - s.snapAt) * fx.life / Math.max(0.25F, fx.timeScale);
         double wx = fx.basis.fx * 0.6;
         double wz = fx.basis.fz * 0.6;

         double amount = this.drift.getFloat() / 100.0;
         int petal = this.petalColor.getColor(0.5F) | 0xFF000000;

         for (int i = 0; i < s.petals; i++) {
            if (!s.landed[i]) {
               double flutter = Math.sin(t * 9.0 + i * 1.7) * 0.9;
               s.pvx[i] = s.pvx[i] + ((wx * amount - s.pvx[i]) * 1.4 + flutter * Math.cos(s.pspin[i])) * dt;
               s.pvz[i] = s.pvz[i] + ((wz * amount - s.pvz[i]) * 1.4 + flutter * Math.sin(s.pspin[i])) * dt;
               s.pvy[i] = s.pvy[i] + (-2.6 - s.pvy[i] * 1.4 * 0.6) * dt;
               s.px[i] = s.px[i] + s.pvx[i] * dt;
               s.py[i] = s.py[i] + s.pvy[i] * dt;
               s.pz[i] = s.pz[i] + s.pvz[i] * dt;
               s.pspin[i] = s.pspin[i] + 3.0 * dt;
               s.ptumble[i] = s.ptumble[i] + (5.0 + 3.0 * Math.sin(i * 2.1)) * dt;
               if (s.py[i] < s.ground + 0.01) {
                  s.py[i] = s.ground + 0.01;
                  s.landed[i] = true;
                  s.ptumble[i] = 0.0;
               }
            }

            double x = f.rx(s.px[i]);
            double y = f.ry(s.py[i]);
            double z = f.rz(s.pz[i]);
            float a = alpha * Easing.smoothstep(0.0F, 0.04F, (float)t) * fx.keep.cover(x, y, z);
            this.kit.tumblingPetal(x, y, z, s.pspin[i], s.landed[i] ? 1.5707963 : s.ptumble[i], s.psize[i], petal, a);
         }
      }
   }

   @Override
   public String stateForTest(HitFx fx) {
      ThornLashStyle.State s = (ThornLashStyle.State)fx.data;
      if (s == null) {
         return null;
      } else {
         float p = fx.progress();
         String phase = p < 0.13333334F
            ? "whipping out"
            : (p < 0.35555556F ? "coiling" : (p < 0.44444448F ? "holding" : (p < 0.5555556F ? "snapping" : "petals")));
         int landed = 0;

         for (int i = 0; i < s.petals; i++) {
            landed += s.landed[i] ? 1 : 0;
         }

         return String.format(
            Locale.ROOT,
            "%s: %d vine(s) %.1f turn(s), grown %.2f, coil x%.2f; %d splinters, %d petals (%d landed); light %.2f, night %.2f",
            phase,
            Math.min(4, this.vines.getInt()),
            this.coilTurns.getFloat(),
            grown(p),
            looseness(p),
            s.splinters,
            s.petals,
            landed,
            fx.light,
            fx.night
         );
      }
   }

   private static double hash(int a, int b) {
      int h = a * 668265261 ^ (b + 374761393) * -1640531535;
      h ^= h >>> 15;
      h *= -2048144789;
      h ^= h >>> 13;
      return (h >>> 8 & 16777215) / 1.6777216E7;
   }

   private static final class State {
      boolean snapped;
      float snapAt;
      int splinters;
      int petals;
      final double[] sx = new double[28];
      final double[] sy = new double[28];
      final double[] sz = new double[28];
      final double[] svx = new double[28];
      final double[] svy = new double[28];
      final double[] svz = new double[28];
      final double[] slen = new double[28];
      final double[] sspin = new double[28];
      final double[] saxis = new double[84];
      final double[] px = new double[60];
      final double[] py = new double[60];
      final double[] pz = new double[60];
      final double[] pvx = new double[60];
      final double[] pvy = new double[60];
      final double[] pvz = new double[60];
      final double[] pspin = new double[60];
      final double[] ptumble = new double[60];
      final double[] psize = new double[60];
      final boolean[] landed = new boolean[60];
      double ground;

      private State() {
      }
   }
}
