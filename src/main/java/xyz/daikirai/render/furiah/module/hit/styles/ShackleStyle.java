package xyz.daikirai.render.furiah.module.hit.styles;

import xyz.daikirai.render.furiah.fx.kit.Glitch;

import java.util.ArrayDeque;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import xyz.daikirai.render.furiah.fx.Basis;
import xyz.daikirai.render.furiah.fx.FxFrame;
import xyz.daikirai.render.furiah.fx.kit.Chains;
import xyz.daikirai.render.furiah.fx.kit.Surface;
import xyz.daikirai.render.furiah.fx.pass.FxPass;
import xyz.daikirai.render.furiah.module.hit.HitFx;
import xyz.daikirai.render.furiah.module.hit.HitStyle;
import xyz.daikirai.render.furiah.module.hit.HitStyleBase;
import xyz.daikirai.render.furiah.render.Easing;
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

public final class ShackleStyle extends HitStyleBase {
   private static final float WHIP = 0.21428573F;
   private static final float LOCK = 0.35714287F;
   private static final float HOLD = 0.5F;
   private static final float FADE = 0.86F;
   private static final float CLICK = 0.05F;
   private static final float RIM = 0.16F;
   private static final int MAX_LINKS = 36;
   private static final int MAX_TURNS = 3;
   private static final int POOL = 8;
   private static final double FALL = 44.0;
   private static final double LINK_WIDTH = 0.54;
   public final ModeSetting<Material> material;
   public final ModeSetting<Finish> finish;
   public final SliderSetting links;
   public final SliderSetting linkSize;
   public final ModeSetting<ShackleStyle.WrapHeight> wrapHeight;
   public final BooleanSetting padlock;
   public final BooleanSetting glint;
   private final Basis loop = new Basis();
   private final double[] point = new double[3];
   private final double[] point2 = new double[3];
   private static final double[] R = new double[9];
   private int n;
   private int turns;
   private double link;
   private double radius;
   private double coil;
   private final ArrayDeque<ShackleStyle.State> pool = new ArrayDeque<>();

   public ShackleStyle() {
      super(
         HitStyle.SHACKLE,
         0.7F,
         new ThemeTint()
      );
      this.material = this.materialSetting("shackle", Material.GOLD, "What the chain is made of. Custom takes the Tint, with a finish.");
      this.finish = this.finishSetting("shackle", Finish.METALLIC, this.material);
      this.links = this.look(this.setting(new SliderSetting("shackle.links", "Links", "How many links the chain has.", 12.0, 36.0, 20.0, 1.0)));
      this.linkSize = this.look(this.setting(new SliderSetting("shackle.linkSize", "Link Size", "How big the links are.", 50.0, 150.0, 100.0, 5.0).unit("%")));
      this.wrapHeight = this.setting(
         new ModeSetting<>(
               "shackle.wrapHeight", "Wrap Height", "Where the chain wraps round the target.", ShackleStyle.WrapHeight.class, ShackleStyle.WrapHeight.IMPACT
            )
            .style(ModeSetting.Style.CHIPS)
      );
      this.padlock = this.setting(new BooleanSetting("shackle.padlock", "Padlock", "A padlock locks the chain's ends, then springs open.", true));
      this.glint = this.setting(new BooleanSetting("shackle.glint", "Glint", "A glint runs round the chain as it snaps tight.", true));
   }

   @Override
   public void start(HitFx fx) {
      ShackleStyle.State s = this.pool.poll();
      if (s == null) {
         s = new ShackleStyle.State();
      }

      s.burst = false;
      s.burstAt = 0.0F;
      s.n = 0;
      s.floor = fx.hit.groundY;
      fx.data = s;
   }

   @Override
   public void finish(HitFx fx) {
      if (fx.data instanceof ShackleStyle.State && this.pool.size() < 8) {
         this.pool.push((ShackleStyle.State)fx.data);
      }

      fx.data = null;
   }

   @Override
   public int passes(HitFx fx) {
      int solid = this.material.get().translucent(this.finish.get()) ? FxPass.TRANSLUCENT.bit() : FxPass.SOLID.bit();
      float p = fx.progress();
      boolean light = this.glint.get() && !fx.lod && p >= 0.21428573F && p <= Math.max(0.5F, 0.40714288F);
      return light ? solid | FxPass.GLOW.bit() : solid;
   }

   private void measure(HitFx fx) {
      this.n = Math.min(36, this.links.getInt());
      this.radius = fx.hit.width * 0.5 + 0.06;
      double want = 0.15 * this.linkSize.getFloat() / 100.0 * (0.85 + 0.15 * scale(fx));
      double round = (Math.PI * 2) * this.radius;
      this.turns = Math.max(1, Math.min(3, (int)Math.round(this.n * 0.72 * want / round)));
      this.link = this.turns * round / (this.n * 0.72);
      this.coil = this.turns > 1 ? this.link * 0.54 * 1.15 : 0.0;
   }

   private double height(HitFx fx) {
      switch ((ShackleStyle.WrapHeight)this.wrapHeight.get()) {
         case IMPACT:
            return Math.max(fx.hit.height * 0.4, Math.min(fx.hit.height * 0.75, fx.hit.offY));
         case WAIST:
            return fx.hit.height * 0.45;
         default:
            return fx.hit.height * 0.68;
      }
   }

   private void frame(HitFx fx, int k, int chains) {
      Basis b = fx.basis;
      double tip = 0.32 + Math.max(0.0, -b.fy) * 0.5;
      double st = Math.sin(tip);
      double nx = b.fx * st;
      double ny = Math.cos(tip) + b.fy * st;
      double nz = b.fz * st;
      if (chains == 2) {
         double a = (k == 0 ? 1 : -1) * Math.toRadians(35.0);
         double c = Math.cos(a);
         double s = Math.sin(a);
         double dot = nx * b.fx + ny * b.fy + nz * b.fz;
         double cx = b.fy * nz - b.fz * ny;
         double cy = b.fz * nx - b.fx * nz;
         double cz = b.fx * ny - b.fy * nx;
         nx = nx * c + cx * s + b.fx * dot * (1.0 - c);
         ny = ny * c + cy * s + b.fy * dot * (1.0 - c);
         nz = nz * c + cz * s + b.fz * dot * (1.0 - c);
      }

      double nl = Math.sqrt(nx * nx + ny * ny + nz * nz);
      this.loop.fx = nx / nl;
      this.loop.fy = ny / nl;
      this.loop.fz = nz / nl;
      double tx = -b.fx;
      double ty = -b.fy;
      double tz = -b.fz;
      double d;
      double tl;
      if ((
            tl = Math.sqrt(
               (tx = tx - this.loop.fx * (d = tx * this.loop.fx + ty * this.loop.fy + tz * this.loop.fz)) * tx
                  + (ty = ty - this.loop.fy * d) * ty
                  + (tz = tz - this.loop.fz * d) * tz
            )
         )
         < 1.0E-6) {
         tx = -b.rx;
         ty = -b.ry;
         tz = -b.rz;
         tl = 1.0;
      }

      this.loop.rx = tx / tl;
      this.loop.ry = ty / tl;
      this.loop.rz = tz / tl;
      this.loop.ux = this.loop.fy * this.loop.rz - this.loop.fz * this.loop.ry;
      this.loop.uy = this.loop.fz * this.loop.rx - this.loop.fx * this.loop.rz;
      this.loop.uz = this.loop.fx * this.loop.ry - this.loop.fy * this.loop.rx;
   }

   private void at(HitFx fx, double h, double a, double r, double along, double[] out) {
      double c = Math.cos(a);
      double s = Math.sin(a);
      out[0] = fx.cx() + (this.loop.rx * c + this.loop.ux * s) * r + this.loop.fx * along;
      out[1] = fx.anchor.feetY() + h + (this.loop.ry * c + this.loop.uy * s) * r + this.loop.fy * along;
      out[2] = fx.cz() + (this.loop.rz * c + this.loop.uz * s) * r + this.loop.fz * along;
   }

   private double start(int k) {
      return (k == 0 ? 0.5 : -0.5) * (Math.PI * 2) * this.turns / this.n;
   }

   private double angle(int k, double u) {
      return this.start(k) + u * (Math.PI * 2) * this.turns / this.n;
   }

   private double along(double u) {
      return this.coil * this.turns * (0.5 - u / this.n);
   }

   @Override
   public void renderPass(HitFx fx, FxFrame f, FxPass pass) {
      ShackleStyle.State s = (ShackleStyle.State)fx.data;
      if (s != null) {
         float p = fx.progress();
         float alpha = fx.alpha() * (1.0F - Easing.smoothstep(0.86F, 1.0F, p));
         if (!(alpha <= 0.003F)) {
            int chains = fx.crit ? 2 : 1;
            this.measure(fx);
            if (pass == FxPass.GLOW) {
               this.glow(fx, f, p, alpha, chains);
            } else {
               boolean custom = this.material.get() == Material.CUSTOM;
               double time = ThemeTint.clock();
               MeshBatch.begin(this.material.get(), this.finish.get(), fx.light, 0.16F, -1, true, pass == FxPass.SOLID);
               if (!(p < 0.5F) && !fx.lod) {
                  this.burst(fx, f, s, p, alpha, chains, custom, time);
               } else {
                  for (int k = 0; k < chains; k++) {
                     this.wrapped(fx, f, p, alpha, k, chains, custom, time);
                  }
               }

               if (this.padlock.get()) {
                  this.lock(fx, f, s, p, alpha, chains, custom, time);
               }

               MeshBatch.end();
            }
         }
      }
   }

   private int linkColor(boolean custom, float along, double time, float a) {
      int tint = custom ? this.color.getColor(loop(along), time) : -1;
      int a8 = Math.round(Easing.clamp01(a) * (custom ? tint >>> 24 : 255));
      return a8 << 24 | tint & 16777215;
   }

   private void wrapped(HitFx fx, FxFrame f, float p, float alpha, int k, int chains, boolean custom, double time) {
      this.frame(fx, k, chains);
      double h = this.height(fx);
      float whip = Easing.outCubic(Easing.clamp01(p / 0.21428573F));
      float snap = Easing.clamp01((p - 0.21428573F) / 0.14285713F);
      double r = p < 0.21428573F ? this.radius * (1.0 + 0.3 * (1.0 - whip) + 0.12) : this.radius * (1.0 + 0.12 * (1.0 - Easing.outBack(snap, 2.4F)));
      int shown = p < 0.21428573F ? Math.max(1, (int)Math.ceil(this.n * whip)) : this.n;
      double pull = p < 0.21428573F ? (1.0 - whip) * (1.0 - whip) : 0.0;
      this.at(fx, h, this.start(k), r, this.along(0.0), this.point);
      double ox = fx.ix() - this.point[0];
      double oy = fx.iy() - this.point[1];
      double oz = fx.iz() - this.point[2];
      double glintAt = this.glint.get() && p >= 0.21428573F ? Easing.clamp01((p - 0.21428573F) / 0.28571427F) : -1.0;
      double quarter = this.n * 0.25 / this.turns;

      for (int i = 0; i < shown; i++) {
         double w0 = pull * Math.max(0.0, 1.0 - i / quarter);
         double w1 = pull * Math.max(0.0, 1.0 - (i + 1) / quarter);
         double lead = p < 0.21428573F && i >= shown - 2 ? 1.0 + 0.2 * (1.0 - whip) : 1.0;
         this.at(fx, h, this.angle(k, i), r * lead, this.along(i), this.point);
         this.at(fx, h, this.angle(k, i + 1), r * lead, this.along(i + 1), this.point2);
         double x0 = this.point[0] + ox * w0;
         double y0 = this.point[1] + oy * w0;
         double z0 = this.point[2] + oz * w0;
         double x1 = this.point2[0] + ox * w1;
         double y1 = this.point2[1] + oy * w1;
         double z1 = this.point2[2] + oz * w1;
         float g = 0.0F;
         if (glintAt >= 0.0) {
            double d = Math.abs((double)i / this.n - glintAt);
            g = (float)Math.exp(-(d / 0.07) * (d / 0.07)) * 0.95F;
         }

         double mx = f.rx((x0 + x1) * 0.5);
         double my = f.ry((y0 + y1) * 0.5);
         double mz = f.rz((z0 + z1) * 0.5);
         int col = this.linkColor(custom, (float)i / this.n, time, alpha * fx.keep.cover(mx, my, mz));
         Chains.linkBetween(
            f.rx(x0), f.ry(y0), f.rz(z0), f.rx(x1), f.ry(y1), f.rz(z1), this.loop.fx, this.loop.fy, this.loop.fz, (i & 1) == 1, this.link, col, g
         );
      }
   }

   private void burst(HitFx fx, FxFrame f, ShackleStyle.State s, float p, float alpha, int chains, boolean custom, double time) {
      if (!s.burst) {
         this.breakLoose(fx, s, p, chains);
      }

      double t = (p - s.burstAt) * fx.life;
      double dt = f.dt / Math.max(0.25F, fx.timeScale);

      for (int k = 0; k < chains; k++) {
         Chains.Rope rope = s.ropes[k];
         rope.step(dt, s);
         double[] ax = s.axes[k];
         double[] sp = s.spin[k];

         for (int i = 0; i < s.n; i++) {
            double x = f.rx(rope.x(i));
            double y = f.ry(rope.y(i));
            double z = f.rz(rope.z(i));
            double ang = sp[i * 4 + 3] * (1.0 - Math.exp(-2.0 * t)) * 0.5;
            rotate(ax, i * 9, sp, i * 4, ang);
            int col = this.linkColor(custom, (float)i / s.n, time, alpha * fx.keep.cover(x, y, z));
            Chains.link(x, y, z, R[0], R[1], R[2], R[3], R[4], R[5], R[6], R[7], R[8], this.link, col, 0.0F);
         }
      }
   }

   private void breakLoose(HitFx fx, ShackleStyle.State s, float p, int chains) {
      s.burst = true;
      s.burstAt = p;
      s.n = this.n;
      s.floor = fx.hit.groundY + this.link * 0.2;
      Basis b = fx.basis;
      double h = this.height(fx);

      for (int k = 0; k < chains; k++) {
         this.frame(fx, k, chains);
         Chains.Rope rope = s.ropes[k];
         rope.reset(this.n, this.link * 0.72, fx.cx(), fx.cy(), fx.cz(), 1.0, 0.0, 0.0);
         rope.gravity(44.0);
         double[] ax = s.axes[k];
         double[] sp = s.spin[k];

         for (int i = 0; i < this.n; i++) {
            double a = this.angle(k, i + 0.5);
            this.at(fx, h, a, this.radius, this.along(i + 0.5), this.point);
            rope.place(i, this.point[0], this.point[1], this.point[2]);
            rope.loosen(i);
            double ox = this.point[0] - fx.cx();
            double oz = this.point[2] - fx.cz();
            double ol = Math.sqrt(ox * ox + oz * oz) + 1.0E-9;
            ox /= ol;
            oz /= ol;
            double c = Math.cos(a);
            double sn = Math.sin(a);
            double tx = -sn * this.loop.rx + c * this.loop.ux;
            double ty = -sn * this.loop.ry + c * this.loop.uy;
            double tz = -sn * this.loop.rz + c * this.loop.uz;
            int id = fx.seed + k * 97 + i;
            double kick = 1.6 + 1.2 * hash(id, 3);
            rope.push(i, ox * kick + b.fx * 1.4 + tx * 0.7, 0.3 + 1.0 * hash(id, 5) + b.fy * 0.6, oz * kick + b.fz * 1.4 + tz * 0.7);
            double qx = this.loop.fx;
            double qy = this.loop.fy;
            double qz = this.loop.fz;
            double wx = qy * tz - qz * ty;
            double wy = qz * tx - qx * tz;
            double wz = qx * ty - qy * tx;
            boolean turned = (i & 1) == 1;
            ax[i * 9] = tx;
            ax[i * 9 + 1] = ty;
            ax[i * 9 + 2] = tz;
            ax[i * 9 + 3] = turned ? wx : qx;
            ax[i * 9 + 4] = turned ? wy : qy;
            ax[i * 9 + 5] = turned ? wz : qz;
            ax[i * 9 + 6] = turned ? -qx : wx;
            ax[i * 9 + 7] = turned ? -qy : wy;
            ax[i * 9 + 8] = turned ? -qz : wz;
            double hx = hash(id, 7) - 0.5;
            double hy = hash(id, 11) - 0.5;
            double hz = hash(id, 13) - 0.5;
            double hl = Math.sqrt(hx * hx + hy * hy + hz * hz) + 1.0E-9;
            sp[i * 4] = hx / hl;
            sp[i * 4 + 1] = hy / hl;
            sp[i * 4 + 2] = hz / hl;
            sp[i * 4 + 3] = 8.0 + 10.0 * hash(id, 17);
         }
      }

      this.lockAt(fx, chains);
      s.lx = this.point[0];
      s.ly = this.point[1];
      s.lz = this.point[2];
      s.lvx = b.fx * 1.2 - this.loop.rx * 0.6;
      s.lvy = 2.2;
      s.lvz = b.fz * 1.2 - this.loop.rz * 0.6;
   }

   private static void rotate(double[] ax, int o, double[] sp, int q, double ang) {
      double kx = sp[q];
      double ky = sp[q + 1];
      double kz = sp[q + 2];
      double c = Math.cos(ang);
      double s = Math.sin(ang);

      for (int v = 0; v < 3; v++) {
         double x = ax[o + v * 3];
         double y = ax[o + v * 3 + 1];
         double z = ax[o + v * 3 + 2];
         double d = kx * x + ky * y + kz * z;
         double cx = ky * z - kz * y;
         double cy = kz * x - kx * z;
         double cz = kx * y - ky * x;
         R[v * 3] = x * c + cx * s + kx * d * (1.0 - c);
         R[v * 3 + 1] = y * c + cy * s + ky * d * (1.0 - c);
         R[v * 3 + 2] = z * c + cz * s + kz * d * (1.0 - c);
      }
   }

   private double lockSize(HitFx fx, int chains) {
      return (chains == 2 ? 0.2 : 0.14) * (0.85 + 0.15 * scale(fx)) * this.linkSize.getFloat() / 100.0;
   }

   private void lockAt(HitFx fx, int chains) {
      this.frame(fx, 0, chains);
      this.at(fx, this.height(fx), 0.0, this.radius, this.along(this.n), this.point);
      this.point[1] = this.point[1] - this.lockSize(fx, chains) * 0.75;
   }

   private void lock(HitFx fx, FxFrame f, ShackleStyle.State s, float p, float alpha, int chains, boolean custom, double time) {
      float from = 0.18214288F;
      if (!(p < from)) {
         double size = this.lockSize(fx, chains);
         double hx = -fx.basis.fx;
         double hz = -fx.basis.fz;
         double hl = Math.sqrt(hx * hx + hz * hz);
         if (hl < 1.0E-6) {
            hx = -fx.basis.ux;
            hz = -fx.basis.uz;
            hl = Math.sqrt(hx * hx + hz * hz) + 1.0E-9;
         }

         hx /= hl;
         hz /= hl;
         if (!(p < 0.5F) && s.burst) {
            double t = (p - s.burstAt) * fx.life;
            double open = Easing.outCubic(Easing.clamp01(t / 0.04));
            double wy = s.ly + s.lvy * t - 10.0 * t * t;
            double y = Math.max(fx.hit.groundY + size * 0.45, wy);
            double ang = Math.min(t, 0.3) * 7.0;
            double c = Math.cos(ang);
            double sn = Math.sin(ang);
            double x = f.rx(s.lx + s.lvx * t);
            double z = f.rz(s.lz + s.lvz * t);
            double ry = f.ry(y);
            int col = this.linkColor(custom, 0.0F, time, alpha * fx.keep.cover(x, ry, z));
            Chains.padlock(x, ry, z, hx * c, -sn, hz * c, hx * sn, c, hz * sn, size, open, col, 0.08F);
         } else {
            float in = Easing.outBack(Easing.clamp01((p - from) / 0.08F), 2.0F);
            double open = p < 0.35714287F ? 1.0 - Easing.clamp01((p - 0.21428573F) / 0.14285713F) : 0.0;
            this.lockAt(fx, chains);
            double x = f.rx(this.point[0]);
            double y = f.ry(this.point[1]);
            double z = f.rz(this.point[2]);
            int col = this.linkColor(custom, 0.0F, time, alpha * fx.keep.cover(x, y, z));
            float g = this.glint.get() ? 0.08F + 0.5F * click(p) : 0.08F;
            Chains.padlock(x, y, z, hx, 0.0, hz, 0.0, 1.0, 0.0, size * in, open, col, g);
         }
      }
   }

   private static float click(float p) {
      return 1.0F - Easing.clamp01(Math.abs(p - 0.35714287F) / 0.05F);
   }

   private void glow(HitFx fx, FxFrame f, float p, float alpha, int chains) {
      if (!fx.lod && !(p < 0.21428573F)) {
         int col = this.material.get() == Material.CUSTOM ? this.color.getColor(0.5F) : this.material.get().swatch();
         if (p <= 0.5F) {
            double gp = Easing.clamp01((p - 0.21428573F) / 0.28571427F);
            float k8 = alpha * (float)Math.sin(Math.PI * gp);

            for (int k = 0; k < chains; k++) {
               this.frame(fx, k, chains);
               double u = gp * this.n;
               this.at(fx, this.height(fx), this.angle(k, u), this.radius, this.along(u), this.point);
               double x = f.rx(this.point[0]);
               double y = f.ry(this.point[1]);
               double z = f.rz(this.point[2]);
               Vfx.sprite(Textures.FLARE, x, y, z, this.link * 2.2, (float)(gp * 6.0), core(col, 0.9F * k8));
               Vfx.sprite(Textures.GLOW, x, y, z, this.link * 2.5, 0.0F, alpha(col, 0.35F * k8));
            }
         }

         float click;
         if ((click = click(p)) > 0.0F && this.padlock.get() && !Glitch.reduceFlashing()) {
            this.lockAt(fx, chains);
            Vfx.sprite(Textures.SPARK, f.rx(this.point[0]), f.ry(this.point[1]), f.rz(this.point[2]), this.link * 1.8, 0.0F, core(col, 0.8F * alpha * click));
         }
      }
   }

   @Override
   public String stateForTest(HitFx fx) {
      ShackleStyle.State s = (ShackleStyle.State)fx.data;
      if (s == null) {
         return null;
      } else {
         this.measure(fx);
         int chains = fx.crit ? 2 : 1;
         StringBuilder sb = new StringBuilder(
            String.format(
               Locale.ROOT,
               "%s, wrap %.2f up (%s), radius %.2f, %d x %d links of %.3f, %d turn(s)",
               this.material.get().displayName(),
               this.height(fx),
               this.wrapHeight.get().displayName(),
               this.radius,
               chains,
               this.n,
               this.link,
               this.turns
            )
         );
         if (s.burst) {
            int grounded = 0;
            double lowest = Double.MAX_VALUE;

            for (int k = 0; k < chains; k++) {
               for (int i = 0; i < s.n; i++) {
                  double above = s.ropes[k].y(i) - s.floor;
                  lowest = Math.min(lowest, above);
                  grounded += above < 0.03 ? 1 : 0;
               }
            }

            sb.append(String.format(Locale.ROOT, "; burst: %d of %d links on the ground, the lowest %.3f above it", grounded, chains * s.n, lowest));
         } else {
            sb.append(fx.progress() < 0.35714287F ? "; whipping round" : "; locked");
         }

         return sb.toString();
      }
   }

   private static double hash(int a, int b) {
      int h = a * 668265261 ^ (b + 374761393) * -1640531535;
      h ^= h >>> 15;
      h *= -2048144789;
      h ^= h >>> 13;
      return (h >>> 8 & 16777215) / 1.6777216E7;
   }

   private static final class State implements Surface {
      final Chains.Rope[] ropes = new Chains.Rope[]{new Chains.Rope(), new Chains.Rope()};
      final double[][] axes = new double[2][324];
      final double[][] spin = new double[2][144];
      boolean burst;
      float burstAt;
      int n;
      double lx;
      double ly;
      double lz;
      double lvx;
      double lvy;
      double lvz;
      double floor;

      private State() {
      }

      @Override
      public double at(double x, double y, double z) {
         return this.floor;
      }
   }

   public static enum WrapHeight implements Displayable {
      IMPACT("Impact point", "At the height you hit, on the torso"),
      CHEST("Chest", "Round the chest"),
      WAIST("Waist", "Round the waist");

      private final String displayName;
      private final String description;

      private WrapHeight(String displayName, String description) {
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
